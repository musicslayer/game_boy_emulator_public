package gameboy.emulator.interaction;

import java.util.HashMap;

import gameboy.data.SignalConsumer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;

public class Controller implements SignalConsumer {
    public final static int BUTTON_OTHER = -1;
    public final static int BUTTON_UP = 0;
    public final static int BUTTON_DOWN = 1;
    public final static int BUTTON_LEFT = 2;
    public final static int BUTTON_RIGHT = 3;
    public final static int BUTTON_A = 4;
    public final static int BUTTON_B = 5;
    public final static int BUTTON_START = 6;
    public final static int BUTTON_SELECT = 7;

    public final static int ACTION_PRESS = 0;
    public final static int ACTION_RELEASE = 1;

    AddressMap addressMap;

    // Keeps track of which buttons are pressed.
    HashMap<Integer, Boolean> buttonMap = new HashMap<>();

    public Controller(AddressMap addressMap) {
        this.addressMap = addressMap;

        // All buttons are initially unpressed.
        buttonMap.put(BUTTON_UP, false);
        buttonMap.put(BUTTON_DOWN, false);
        buttonMap.put(BUTTON_LEFT, false);
        buttonMap.put(BUTTON_RIGHT, false);
        buttonMap.put(BUTTON_A, false);
        buttonMap.put(BUTTON_B, false);
        buttonMap.put(BUTTON_START, false);
        buttonMap.put(BUTTON_SELECT, false);

        initStoreMap();
        initLoadMap();
    }

    public void initStoreMap() {
        addressMap.addStoreCallback(AddressMap.ADDRESS_JOYP, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                // The lower 4 bits are read only, so writes should not affect them.
                byte oldValue = addressMap.data[region][relativeAddress];
                int oldLowerBits = Byte.toUnsignedInt(oldValue) & 0b00001111;
                int newUpperBits = Byte.toUnsignedInt(b) & 0b11110000;
                super.onStore(region, relativeAddress, (byte)(newUpperBits | oldLowerBits));
            }
        });
    }

    public void initLoadMap() {
        addressMap.addLoadCallback(AddressMap.ADDRESS_JOYP, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)(Byte.toUnsignedInt(super.onLoad(region, relativeAddress)) | 0b11000000);
            }
        });
    }

    public void attachClock(HybridClock hybridClock) {
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                // Note that JOYP must be updated every tick because the buttons that are connected to the register's output may switch at any time.
                updateJOYP();
            }
        });
    }

    public void updateJOYP() {
        // The logic of JOYP is the opposite of most registers.
        // For bits 4 and 5, 0 means selected and 1 means not selected.
        // For bits 1-3, pressed keys reset joystick pins and released keys set joystick pins.
        int oldJOYP = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_JOYP));
        int oldJOYPHigh = oldJOYP & 0b00001111;

        int bit5 = (oldJOYP >>> 5) & 0b1;
        int bit4 = (oldJOYP >>> 4) & 0b1;
        int bit3 = (oldJOYP >>> 3) & 0b1;
        int bit2 = (oldJOYP >>> 2) & 0b1;
        int bit1 = (oldJOYP >>> 1) & 0b1;
        int bit0 = (oldJOYP >>> 0) & 0b1;

        if(bit5 == 0 && bit4 == 1) {
            bit3 = buttonMap.get(BUTTON_START) ? 0 : 1;
            bit2 = buttonMap.get(BUTTON_SELECT) ? 0 : 1;
            bit1 = buttonMap.get(BUTTON_B) ? 0 : 1;
            bit0 = buttonMap.get(BUTTON_A) ? 0 : 1;
        }
        else if(bit5 == 1 && bit4 == 0) {
            bit3 = buttonMap.get(BUTTON_DOWN) ? 0 : 1;
            bit2 = buttonMap.get(BUTTON_UP) ? 0 : 1;
            bit1 = buttonMap.get(BUTTON_LEFT) ? 0 : 1;
            bit0 = buttonMap.get(BUTTON_RIGHT) ? 0 : 1;
        }
        else {
            // In these two cases, nothing is selected.
            bit3 = 1;
            bit2 = 1;
            bit1 = 1;
            bit0 = 1;
        }

        int newJOYPLow = (bit3 << 3) | (bit2 << 2) | (bit1 << 1) | (bit0 << 0);
        int newJOYP = (bit5 << 5) | (bit4 << 4) | newJOYPLow;

        addressMap.storeByte(AddressMap.ADDRESS_JOYP, (byte)newJOYP, true);
        
        if((oldJOYPHigh & ~newJOYPLow) != 0) {
            // Request Joypad Interrupt.
            addressMap.storeBit(AddressMap.ADDRESS_IF, 4, 1);
        }
    }

    // SignalConsumer
    @Override
    public void consumeSignal(int action, int button) {
        // Reject unrecognized keys up front.
        if(button == BUTTON_OTHER) {
            return;
        }

        switch(action) {
        case ACTION_PRESS:
            buttonMap.put(button, true);
            break;

        case ACTION_RELEASE:
            buttonMap.put(button, false);
            break;

        default:
            throw new IllegalStateException("Unrecognized Action. action = " + action + " key = " + button);
        }
    }
}
