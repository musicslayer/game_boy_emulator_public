package gameboy.emulator.memory.mbc;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.memory.AddressMap.LoadCallback;
import gameboy.emulator.memory.AddressMap.StoreCallback;

// MBC5
public class MBC5 extends MemoryBankController {
    public boolean isRAMEnabled = false;

    public int romBankNumberLower = 1; // Lower 8 bits
    public int romBankNumberHigher = 0; // Higher 1 bit
    public int ramBankNumber = 0;

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

        // ROM Bank Number bits 1-8
        for(int i = 0x2000; i <= 0x2FFF; i++) {
            storeROMACallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    romBankNumberLower = b & 0b11111111;
                    refreshBanks();
                }
            };
        }

        // ROM Bank Number bit 9
        for(int i = 0x3000; i <= 0x3FFF; i++) {
            storeROMACallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    romBankNumberHigher = b & 0b00000001;
                    refreshBanks();
                }
            };
        }

        // ROMB
        StoreCallback[] storeROMBCallbacks = storeMap[AddressMap.REGION_ROMB];

        // RAM Bank Number
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    ramBankNumber = b & 0b00001111;
                    refreshBanks();
                }
            };
        }

        // Normal ROM storing
        for(int i = 0x2000; i <= 0x3FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // Writes to ROM are ignored.
                }
            };
        }

        // SRAM
        StoreCallback[] storeSRAMCallbacks = storeMap[AddressMap.REGION_SRAM];

        // Normal RAM storing
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeSRAMCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // RAM must be present and enabled to be written to.
                    if(isRAMPresent && isRAMEnabled) {
                        super.onStore(region, relativeAddress, b);

                        if(isBattery) {
                            // Also write to file.
                            writeToRAM(relativeAddress, b);
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

        // Normal RAM loading
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            loadSRAMCallbacks[i] = addressMap.new LoadCallback() {
                @Override
                public byte onLoad(int region, int relativeAddress) {
                    // RAM that is not present and enabled will give a dummy value.
                    if(isRAMPresent && isRAMEnabled) {
                        return super.onLoad(region, relativeAddress);
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
        // ROM Bank B uses 9 bits total.
        int romBankNumberB = (256 * romBankNumberHigher) + romBankNumberLower;
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
