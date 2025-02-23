package gameboy.emulator.memory.mbc;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import gameboy.GameBoy.CloseableResourceManager;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.memory.AddressMap.LoadCallback;
import gameboy.emulator.memory.AddressMap.StoreCallback;
import gameboy.emulator.software.Cartridge;

// MBC1
abstract public class MemoryBankController {
    final static String RAM_PATH = "sav";
    final static int RAM_BANK_SIZE = 8192;

    CloseableResourceManager closeableResourceManager;
    public AddressMap addressMap;

    // If the cartridge has a battery, then RAM will be backed up to a file.
    public String saveFile;
    public boolean isBattery;
    public RandomAccessFile ramFile;

    public int romBankA;
    public int romBankB;
    public int ramBank;
    public byte[][] romBanks;
    public byte[][] ramBanks;

    public int numROMBanks;
    public int numRAMBanks;
    public boolean isRAMPresent;

    // Subclasses should define behaviors when reading/writing to ROM and SRAM.
    abstract public void initStoreMap(StoreCallback[][] storeMap);
    abstract public void initLoadMap(LoadCallback[][] loadMap);

    // Each memory controller may have different ways of representing the bank numbers.
    abstract public int getROMABank();
    abstract public int getROMBBank();
    abstract public int getSRAMBank();

    public static MemoryBankController createMemoryBankController(CloseableResourceManager closeableResourceManager, AddressMap addressMap, Cartridge cartridge) {
        int[] info = cartridge.getMemoryInfo();

        // Determines appropriate MBC from the cartridge data.
        MemoryBankController memoryBankController;
        switch(info[0]) {
        case 0:
            memoryBankController = new MBC0();
            break;
        case 1:
            memoryBankController = new MBC1();
            break;
        case 2:
            memoryBankController = new MBC2();
            break;
        case 3:
            memoryBankController = new MBC3();
            break;
        case 5:
            memoryBankController = new MBC5();
            break;
        default:
            throw new IllegalStateException("Unsupported mbc = " + info[0]);
        }

        memoryBankController.closeableResourceManager = closeableResourceManager;
        memoryBankController.addressMap = addressMap;

        memoryBankController.numROMBanks = info[1];
        memoryBankController.numRAMBanks = info[2] + memoryBankController.numBuiltinSRAMBanks();
        memoryBankController.isBattery = info[3] == 1;

        memoryBankController.saveFile = cartridge.getSaveFile();
        memoryBankController.createBanks(cartridge.data);

        return memoryBankController;
    }

    // Some memory controllers may have builtin RAM that is not indicated by the cartridge header.
    public int numBuiltinSRAMBanks() {
        return 0;
    }

    public void createBanks(byte[] data) {
        // Create ROM banks from the supplied data.
        romBanks = new byte[numROMBanks][0x4000];

        int c = 0;
        for(int bank = 0; bank < numROMBanks; bank++) {
            for(int i = 0x0000; i <= 0x3FFF; i++) {
                romBanks[bank][i] = data[c++];
            }
        }
        
        // Create RAM Banks.
        ramBanks = new byte[numRAMBanks][0x2000];
        isRAMPresent = numRAMBanks > 0;

        if(isBattery) {
            // First try to load RAM banks from existing file. If that doesn't work, then create a new file.
            boolean canLoad;

            Path path = Paths.get(RAM_PATH, saveFile);
            File folder = new File(RAM_PATH);
            File file = new File(path.toString());
            if(!folder.exists()) {
                folder.mkdirs();
                canLoad = false;
            }
            else if(!file.exists() || file.length() != RAM_BANK_SIZE * numRAMBanks) {
                canLoad = false;
            }
            else {
                canLoad = true;
            }

            try {
                ramFile = new RandomAccessFile(path.toString(), "rw");
                closeableResourceManager.addCloseable(ramFile);

                if(canLoad) {
                    byte[] ramData = Files.readAllBytes(path);

                    int cc = 0;
                    for(int bank = 0; bank < numRAMBanks; bank++) {
                        for(int i = 0x0000; i <= 0x1FFF; i++) {
                            ramBanks[bank][i] = ramData[cc++];
                        }
                    }
                }
                else {
                    // Zero out all content.
                    ramFile.setLength(0);
                    ramFile.setLength(RAM_BANK_SIZE * numRAMBanks);
                }
            }
            catch(IOException e) {
                throw new IllegalStateException(e);
            }
        }

        // Set initial mapping.
        refreshBanks();
    }

    public void writeToRAM(int relativeAddress, byte b) {
        int address = relativeAddress + (RAM_BANK_SIZE * ramBank);

        try {
            ramFile.seek(address);
            ramFile.writeByte(b);
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void refreshBanks() {
        romBankA = getROMABank();
        mapROMA(romBankA);

        romBankB = getROMBBank();
        mapROMB(romBankB);

        if(isRAMPresent) {
            ramBank = getSRAMBank();
            mapSRAM(ramBank);
        }
    }

    public void mapROMA(int bank) {
        addressMap.data[AddressMap.REGION_ROMA] = romBanks[bank];
    }

    public void mapROMB(int bank) {
        addressMap.data[AddressMap.REGION_ROMB] = romBanks[bank];
    }

    public void mapSRAM(int bank) {
        addressMap.data[AddressMap.REGION_SRAM] = ramBanks[bank];
    }
}
