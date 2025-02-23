package gameboy.emulator.memory.mbc;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.memory.AddressMap.LoadCallback;
import gameboy.emulator.memory.AddressMap.StoreCallback;

// This class represents not having a memory controller.
public class MBC0 extends MemoryBankController {
    @Override
    public void initStoreMap(StoreCallback[][] storeMap) {
        // ROMA
        StoreCallback[] storeROMACallbacks = storeMap[AddressMap.REGION_ROMA];

        // Normal ROM storing
        for(int i = 0x0000; i <= 0x3FFF; i++) {
            storeROMACallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // Writes to ROM are ignored.
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

        // Normal RAM storing
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeSRAMCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // RAM must be present to be written to.
                    if(isRAMPresent) {
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
                    // RAM that is not present will give a dummy value.
                    if(isRAMPresent) {
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
        // ROMB is hardcoded to be bank 1.
        return 1;
    }

    @Override
    public int getSRAMBank() {
        // SRAM is hardcoded to be bank 0.
        return 0;
    }
}
