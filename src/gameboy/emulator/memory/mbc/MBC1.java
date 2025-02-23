package gameboy.emulator.memory.mbc;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.memory.AddressMap.LoadCallback;
import gameboy.emulator.memory.AddressMap.StoreCallback;

public class MBC1 extends MemoryBankController {
    public final static int[] NINTENDO_LOGO = new int[] {
        0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B, 0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D,
        0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E, 0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99,
        0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC, 0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E
    };

    public boolean isMultiCart;

    public boolean isRAMEnabled = false;

    // The lower 8 bits of a ROM Bank number.
    public int romBankNumber = 0;

    // This can either be used as 2 more bits of a ROM Bank number or directly as a 2-bit RAM Bank number.
    public int extra = 0; 

    // 0 - [$0000, $3FFF] is locked to ROM bank 0, [$A000, $BFFF] is locked to RAM bank 0.
    // 1 - Those regions may be mapped based on what is written to extra.
    public int bankingSelectMode = 0;

    @Override
    public void createBanks(byte[] data) {
        super.createBanks(data);

        // After banks are created, figure out if this is a multicart.
        // A multicart will have the header in at least 3 of the following rom banks.
        int numHeaders = 0;
        numHeaders += hasLogo(0) ? 1 : 0;
        numHeaders += hasLogo(16) ? 1 : 0;
        numHeaders += hasLogo(32) ? 1 : 0;
        numHeaders += hasLogo(48) ? 1 : 0;
        isMultiCart = numHeaders >= 3;
    }

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
                    romBankNumber = b & 0b00011111;
                    refreshBanks();
                }
            };
        }

        // ROMB
        StoreCallback[] storeROMBCallbacks = storeMap[AddressMap.REGION_ROMB];

        // RAM Bank Number or ROM Bank Number upper bits.
        for(int i = 0x0000; i <= 0x1FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    extra = b & 0b00000011;
                    refreshBanks();
                }
            };
        }

        // Banking Mode Select
        for(int i = 0x2000; i <= 0x3FFF; i++) {
            storeROMBCallbacks[i] = addressMap.new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // Banking Select Mode
                    bankingSelectMode = b & 0b00000001;
                    refreshBanks();
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
        // ROM Bank A treats the romBankNumber as zero, and conditionally uses the extra bits.
        int romBankNumberA;
        if(isMultiCart) {
            romBankNumberA = bankingSelectMode == 1 ? 16 * extra : 0;
        }
        else {
            romBankNumberA = bankingSelectMode == 1 ? 32 * extra : 0;
        }
        romBankNumberA = maskROMBank(romBankNumberA);
        return romBankNumberA;
    }

    @Override
    public int getROMBBank() {
        // ROM Bank B uses both romBankNumber and extra (unconditionally), with a 0 value for romBankNumber getting incremented to 1.

        // Five bits of romBankNumber are always used to determine if we should change 0 to 1.
        int romBankNumberB_T = romBankNumber == 0 ? 1 : romBankNumber;

        int romBankNumberB;
        if(isMultiCart) {
            // Use 4 bits of romBankNumber and then take next two bits from extra.
            romBankNumberB = (16 * extra) + (romBankNumberB_T & 0b00001111);
        }
        else {
            // Use 5 bits of romBankNumber and then take next two bits from extra.
            romBankNumberB = (32 * extra) + (romBankNumberB_T & 0b00011111);
        }

        romBankNumberB = maskROMBank(romBankNumberB);
        return romBankNumberB;
    }

    @Override
    public int getSRAMBank() {
        // SRAM conditionally uses the extra bits.
        int ramBankNumber = bankingSelectMode == 1 ? extra : 0;
        ramBankNumber = maskRAMBank(ramBankNumber);
        return ramBankNumber;
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

    public boolean hasLogo(int romBankNumber) {
        // MultiCart cartrides will have the Nintendo logo starting at 0x58104, or 0x0104 in romBanks[22].
        if(numROMBanks < romBankNumber + 1) {
            return false;
        }

        for(int i = 0; i < NINTENDO_LOGO.length; i++) {
            if(NINTENDO_LOGO[i] != Byte.toUnsignedInt(romBanks[romBankNumber][0x0104 + i])) {
                return false;
            }
        }

        return true;
    }
}
