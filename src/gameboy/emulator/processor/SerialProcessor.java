package gameboy.emulator.processor;

import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;

// This class is a separate processor responsible for dealing with the serial data transfer registers
public class SerialProcessor {
    public AddressMap addressMap;

    int clockCounter = 0;

    public int serialCounter = 0;
    public boolean isTransfer = false;

    public SerialProcessor(AddressMap addressMap) {
        this.addressMap = addressMap;

        initStoreMap();
        initLoadMap();
    }

    public void initStoreMap() {
        addressMap.addStoreCallback(AddressMap.ADDRESS_SC, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                int oldValue = addressMap.data[region][relativeAddress];
                int oldBit7 = (oldValue >>> 7) & 0b1;
                int newBit7 = (b >>> 7) & 0b1;
                int newBit0 = b & 0b1;

                super.onStore(region, relativeAddress, b);

                if(oldBit7 == 0 && newBit7 == 1 && newBit0 == 1) {
                    serialCounter = 0;
                    isTransfer = true;
                }
            }
        });
    }

    public void initLoadMap() {
        addressMap.addLoadCallback(AddressMap.ADDRESS_SC, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)(super.onLoad(region, relativeAddress) | 0b01111110);
            }
        });
    }

    public void attachClock(HybridClock hybridClock) {
        // f = 8192L
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                clockCounter++;
                if(clockCounter == 512) {
                    clockCounter = 0;
                    
                    if(isTransfer) {
                        serialCounter++;

                        // Shift SB by one bit.
                        // The output byte is discarded, and the input byte will always be 1.
                        byte reg_sb = addressMap.loadByte(AddressMap.ADDRESS_SB, true);
                        reg_sb <<= 1;
                        reg_sb |= 0b1;
                        addressMap.storeByte(AddressMap.ADDRESS_SB, reg_sb, true);

                        if(serialCounter == 8) {
                            serialCounter = 0;

                            // Transfer is finished.
                            isTransfer = false;
                            addressMap.storeBit(AddressMap.ADDRESS_SC, 7, 0, true);

                            // Request Serial Interrupt.
                            addressMap.storeBit(AddressMap.ADDRESS_IF, 3, 1);
                        }
                    }
                }
            }
        });
    }
}
