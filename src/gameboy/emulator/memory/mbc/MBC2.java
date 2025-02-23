package gameboy.emulator.memory.mbc;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.memory.AddressMap.LoadCallback;
import gameboy.emulator.memory.AddressMap.StoreCallback;

// Note that MBC2 has a builtin RAM bank that is not indicated by the cartridge header.
public class MBC2 extends MemoryBankController {
    public boolean isRAMEnabled = false;

    public int romBankNumber = 1;

    // 0 - [$0000, $3FFF] is locked to ROM bank 0, [$A000, $BFFF] is locked to RAM bank 0.
    // 1 - Those regions may be mapped based on what is written to extra.
    public int bankingSelectMode = 0;
    
    @Override
    public int numBuiltinSRAMBanks() {
        return 1;
    }

    @Override
    public void initStoreMap(StoreCallback[][] storeMap) {
        StoreCallback[] storeROMACallbacks = storeMap[AddressMap.REGION_ROMA];

        // RAM Enable and ROM Bank Number
        for(int i = 0x0000; i <= 0x3FFF; i++) {
            storeROMACallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // If bit 8 of the address is 0, enable/disable ram, otherwise change the ROM bank number.
                    int bit8 = (relativeAddress >>> 8) & 0b1;
                    if(bit8 == 0) {
                        int value = b & 0b00001111;
                        isRAMEnabled = value == 0xA;
                    }
                    else {
                        romBankNumber = b & 0b00001111;
                        refreshBanks();
                    }
                }
            };
        }

        // ROMB
        StoreCallback[] storeROMBCallbacks = storeMap[AddressMap.REGION_ROMB];

        // Normal ROM storing
        for(int i = 0x0000; i <= 0x3FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // Writes to ROM are ignored.
                }
            };
        }

        // SRAM
        StoreCallback[] storeSRAMCallbacks = storeMap[AddressMap.REGION_SRAM];
        
        // Normal RAM storing, but only lower 4 bits of a value are used.
        // There is also a RAM echo because only first 9 bits of RAM addresses are considered.
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeSRAMCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    if(isRAMPresent && isRAMEnabled) {
                        super.onStore(region, relativeAddress &= 0x01FF, b);

                        if(isBattery) {
                            // Also write to file.
                            writeToRAM(relativeAddress &= 0x01FF, b);
                        }
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

        // Normal RAM loading, but only lower 4 bits of a value are used.
        // There is also a RAM echo because only first 9 bits of RAM addresses are considered.
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            loadSRAMCallbacks[i] = addressMap.new LoadCallback() {
                @Override
                public byte onLoad(int region, int relativeAddress) {
                    if(isRAMPresent && isRAMEnabled) {
                        return (byte)(super.onLoad(region, relativeAddress &= 0x01FF) | 0b11110000);
                    }
                    else {
                        return (byte)0xFF;
                    }
                }
            };
        }
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
        // SRAM is hardcoded to be bank 0.
        return 0;
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
