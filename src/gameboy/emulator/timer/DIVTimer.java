package gameboy.emulator.timer;

import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.register.DIVRegister;

public class DIVTimer {
    AddressMap addressMap;

    DIVListener divListener;

    // Internally, DIV is a 16-bit register whose upper 8 bits map to 0xFF04.
    public DIVRegister divRegister = new DIVRegister();

    public int timaCycle = 0; // 0 = normal, 1 = A, 2 = B
    public int numTCycles = 0;

    public DIVTimer(AddressMap addressMap) {
        this.addressMap = addressMap;

        initStoreMap();
        initLoadMap();
    }

    public void initStoreMap() {
        addressMap.addStoreCallback(AddressMap.ADDRESS_DIV, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                // Writing anything to DIV causes it to reset and ignore the written value.
                onResetDIV();
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_TIMA, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                onWriteTIMA();

                // Conditionally write to simulate a hardware quirk.
                if(timaCycle != 2) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_TMA, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                onWriteTMA(b);
                super.onStore(region, relativeAddress, b);
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_TAC, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                onWriteTAC(b);
                super.onStore(region, relativeAddress, b);
            }
        });
    }

    public void initLoadMap() {
        addressMap.addLoadCallback(AddressMap.ADDRESS_DIV, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // The byte value of this register is the upper byte of the full DIV Register.
                return (byte)((Short.toUnsignedInt(divRegister.data) & 0xFF00) >>> 8);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_TAC, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)(super.onLoad(region, relativeAddress) | 0b11111000);
            }
        });
    }

    public void attachClock(HybridClock hybridClock) {
        // f = 4194304L
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                incrementDIV();
            }
        });
    }

    public void onResetDIV() {
        int fallingEdges = divRegister.reset();
        checkFallingEdge(fallingEdges);
    }

    public void onWriteTIMA() {
        // If we write at this point, the overflow will never happen.
        if(timaCycle == 1) {
            numTCycles = 0;
            timaCycle = 0;
        }
    }

    public void onWriteTMA(byte b) {
        // If we write at this point, the value written to TMA gets copied into TIMA.
        if(timaCycle == 2) {
            addressMap.storeByte(AddressMap.ADDRESS_TIMA, b, true);
        }
    }

    public void onWriteTAC(byte b) {
        int oldTACBit2 = addressMap.loadBit(AddressMap.ADDRESS_TAC, 2);
        int newTACBit2 = (b >>> 2) & 0b1;

        int n = getTIMABit();
        int timaBitN = (divRegister.data >>> n) & 0b1;

        // This could potentially trigger a TIMA increment due to a hardware quirk.
        if((oldTACBit2 & timaBitN) == 1 && (newTACBit2 & timaBitN) == 0) {
            incrementTIMA();
        }
    }

    public void incrementDIV() {
        int fallingEdges = divRegister.increment();
        checkFallingEdge(fallingEdges);

        if(timaCycle == 1) {
            // TIMA Overflow happens one M-Cycle later, so check here instead of waiting for a falling edge.
            numTCycles++;
            if(numTCycles == 4) {
                numTCycles = 0;
                timaCycle = 2;

                // Request Timer interrupt.
                addressMap.storeBit(AddressMap.ADDRESS_IF, 2, 1);

                // Reset TIMA to the TMA value.
                addressMap.storeByte(AddressMap.ADDRESS_TIMA, addressMap.loadByte(AddressMap.ADDRESS_TMA), true);
            }
        }
        else if(timaCycle == 2) {
            numTCycles++;
            if(numTCycles == 4) {
                numTCycles = 0;
                timaCycle = 0;
            }
        }
    }

    public void checkFallingEdge(int fallingEdges) {
        if(((fallingEdges >>> 12) & 0b1) == 1) {
            if(divListener != null) {
                divListener.onTick();
            }
        }

        if(((fallingEdges >>> getTIMABit()) & 0b1) == 1) {
            incrementTIMA();
        }
    }

    public void incrementTIMA() {
        if(addressMap.loadBit(AddressMap.ADDRESS_TAC, 2) == 0) {
            // TIMA is disabled.
            return;
        }

        int value = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_TIMA));
        value++;

        if(value == 256) {
            value = 0;
            
            // When TIMA overflows, changes do not happen until the next M-Cycle.
            timaCycle = 1;
        }
        
        addressMap.storeByte(AddressMap.ADDRESS_TIMA, (byte)value, true);
    }

    public int getTIMABit() {
        // This period is relative to the number of ticks the TIMA callback will execute.
        // ClockSelect will decide which bit of DIV can trigger a TIMA increment.
        int clockSelect = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_TAC)) & 0b00000011;

        int n;
        if(clockSelect == 0) {
            n = 9;
        }
        else if(clockSelect == 1) {
            n = 3;
        }
        else if(clockSelect == 2) {
            n = 5;
        }
        else {
            n = 7;
        }

        return n;
    }

    public void setOnTickListener(DIVListener divListener) {
        this.divListener = divListener;
    }

    abstract public static class DIVListener {
        abstract public void onTick();
    }
}
