package gameboy.emulator.memory.mbc;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.memory.AddressMap.LoadCallback;
import gameboy.emulator.memory.AddressMap.StoreCallback;

// Note that the real time clock will always return dummy values.
public class MBC3 extends MemoryBankController {
    public boolean isRAMEnabled = false;

    public int romBankNumber = 0;
    public int ramBankNumber = 0;
    public int rtcRegisterAddress = -1;

    // Used for the real time clock.
    byte[] rtcValues = new byte[5];
    public int value1 = 0;
    public int value2 = 0;

    @Override
    public void initStoreMap(StoreCallback[][] storeMap) {
        // ROMA
        StoreCallback[] storeROMACallbacks = storeMap[AddressMap.REGION_ROMA];

        // RAM Enable
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeROMACallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // If lower bits of b = 0xA then RAM is enabled, otherwise RAM is disabled.
                    int value = b & 0b00001111;
                    isRAMEnabled = value == 0xA;
                }
            };
        }

        // ROM Bank Number
        for(int i = 0x2000; i <= 0x3FFF; i++) {
            storeROMACallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // Swap ROM region B bank.
                    romBankNumber = b & 0b01111111;
                    refreshBanks();
                }
            };
        }

        // ROMB
        StoreCallback[] storeROMBCallbacks = storeMap[AddressMap.REGION_ROMB];

        // RAM Bank Number or RTC Register Select
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    int value = Byte.toUnsignedInt(b);
                    if(value <= 0x07) {
                        ramBankNumber = value;
                        rtcRegisterAddress = -1;
                        refreshBanks();
                    }
                    else if(value <= 0x0C) {
                        rtcRegisterAddress = value;
                    }
                }
            };
        }

        // Latch RTC Data
        for(int i = 0x2000; i <= 0x3FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    value1 = value2;
                    value2 = Byte.toUnsignedInt(b);

                    if(value1 == 0 && value2 == 1) {
                        latchRTC();
                    }
                }
            };
        }

        // SRAM
        StoreCallback[] storeSRAMCallbacks = storeMap[AddressMap.REGION_SRAM];

        // Normal RAM storing or RTC Register storing
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeSRAMCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    if(rtcRegisterAddress == -1) {
                        // RAM must be present and enabled to be written to.
                        if(isRAMPresent && isRAMEnabled) {
                            super.onStore(region, relativeAddress, b);

                            if(isBattery) {
                                // Also write to file.
                                writeToRAM(relativeAddress, b);
                            }
                        }
                    }
                    else {
                        // Write to RTC Register.
                        writeRTC(rtcRegisterAddress, b);
                    }
                }
            };
        }
    }

    @Override
    public void initLoadMap(LoadCallback[][] loadMap) {
        // ROMA
        LoadCallback[] loadROMACallbacks = loadMap[AddressMap.REGION_ROMA];

        // Normal ROM loading
        for(int i = 0x0000; i <= 0x3FFF; i++) {
            loadROMACallbacks[i] = addressMap.new LoadCallback() {
                @Override
                public byte onLoad(int region, int relativeAddress) {
                    return super.onLoad(region, relativeAddress);
                }
            };
        }

        // ROMB
        LoadCallback[] loadROMBCallbacks = loadMap[AddressMap.REGION_ROMB];

        // Normal ROM loading
        for(int i = 0x0000; i <= 0x3FFF; i++) {
            loadROMBCallbacks[i] = addressMap.new LoadCallback() {
                @Override
                public byte onLoad(int region, int relativeAddress) {
                    return super.onLoad(region, relativeAddress);
                }
            };
        }

        // SRAM
        LoadCallback[] loadSRAMCallbacks = loadMap[AddressMap.REGION_SRAM];

        // Normal RAM loading or RTC Register loading
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            loadSRAMCallbacks[i] = addressMap.new LoadCallback() {
                @Override
                public byte onLoad(int region, int relativeAddress) {
                    if(rtcRegisterAddress == -1) {
                        // RAM that is not present and enabled will give a dummy value.
                        if(isRAMPresent && isRAMEnabled) {
                            return super.onLoad(region, relativeAddress);
                        }
                        else {
                            return (byte)0xFF;
                        }
                    }
                    else {
                        return readRTC(rtcRegisterAddress);
                    }
                }
            };
        }
    }

    public void latchRTC() {
        // Write fake time values to the RTC register.
        writeRTC(0x08, (byte)0);
        writeRTC(0x09, (byte)0);
        writeRTC(0x0A, (byte)0);
        writeRTC(0x0B, (byte)0);
        writeRTC(0x0C, (byte)0);
    }

    public byte readRTC(int address) {
        return rtcValues[address - 0x08];
    }

    public void writeRTC(int address, byte b) {
        rtcValues[address - 0x08] = b;
    }

    @Override
    public int getROMABank() {
        // ROMA is hardcoded to be bank 0.
        return 0;
    }

    @Override
    public int getROMBBank() {
        // ROM Bank B uses romBankNumber, with a 0 value for romBankNumber getting incremented to 1.
        int romBankNumberB = romBankNumber == 0 ? 1 : romBankNumber;
        romBankNumberB = maskROMBank(romBankNumberB);
        return romBankNumberB;
    }

    @Override
    public int getSRAMBank() {
        // SRAM uses ramBankNumber.
        return maskRAMBank(ramBankNumber);
    }

    public int maskROMBank(int bank) {
        int n = 0;
        int mask = 0;
        while((0b1 << n) < numROMBanks) {
            mask |= (0b1 << n);
            n++;
        }
        return bank & mask;
    }

    public int maskRAMBank(int bank) {
        int n = 0;
        int mask = 0;
        while((0b1 << n) < numRAMBanks) {
            mask |= (0b1 << n);
            n++;
        }
        return bank & mask;
    }
}
