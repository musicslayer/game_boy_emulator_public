package gameboy.emulator.software;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// The class for a Game Boy cartridge.
public class Cartridge {
    public Path path;
    public byte[] data;

    // Create a cartridge for the given file.
    public Cartridge(String filename) {
        this.path = Paths.get(filename);

        try {
            this.data = Files.readAllBytes(path);
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public int[] getMemoryInfo() {
        int mbc = -1;
        int numROMBanks = -1;
        int numRAMBanks = -1;
        int battery = -1;

        // $0147 tells the MBC and battery, $0148 tells the ROM size, and $0149 tells the RAM size.
        switch(Byte.toUnsignedInt(data[0x0147])) {
            case 0x00:
                mbc = 0;
                battery = 0;
                break;

            case 0x01:
                mbc = 1;
                battery = 0;
                break;

            case 0x02:
                mbc = 1;
                battery = 0;
                break;

            case 0x03:
                mbc = 1;
                battery = 1;
                break;

            case 0x05:
                mbc = 2;
                battery = 0;
                break;

            case 0x06:
                mbc = 2;
                battery = 1;
                break;

            case 0x08:
                mbc = 0;
                battery = 0;
                break;

            case 0x09:
                mbc = 0;
                battery = 1;
                break;

            case 0x0B:
                mbc = -1; // MMM01 currently unsupported
                battery = -1;
                break;

            case 0x0C:
                mbc = -1; // MMM01 currently unsupported
                battery = -1;
                break;

            case 0x0D:
                mbc = -1; // MMM01 currently unsupported
                battery = -1;
                break;

            case 0x0F:
                mbc = 3;
                battery = 1;
                break;

            case 0x10:
                mbc = 3;
                battery = 1;
                break;

            case 0x11:
                mbc = 3;
                battery = 0;
                break;

            case 0x12:
                mbc = 3;
                battery = 0;
                break;

            case 0x13:
                mbc = 3;
                battery = 1;
                break;

            case 0x19:
                mbc = 5;
                battery = 0;
                break;

            case 0x1A:
                mbc = 5;
                battery = 0;
                break;

            case 0x1B:
                mbc = 5;
                battery = 1;
                break;

            case 0x1C:
                mbc = 5;
                battery = 0;
                break;

            case 0x1D:
                mbc = 5;
                battery = 0;
                break;

            case 0x1E:
                mbc = 5;
                battery = 1;
                break;

            case 0x20:
                mbc = -1; // MBC6 currently unsupported
                battery = -1;
                break;

            case 0x22:
                mbc = -1; // MBC7 currently unsupported
                battery = -1;
                break;

            case 0xFC:
                mbc = -1; // POCKET CAMERA currently unsupported
                battery = -1;
                break;

            case 0xFD:
                mbc = -1; // BANDAI TAMA5 currently unsupported
                battery = -1;
                break;

            case 0xFE:
                mbc = -1; // HuC3 currently unsupported
                battery = -1;
                break;

            case 0xFF:
                mbc = -1; // HUC1 currently unsupported
                battery = -1;
                break;

            default:
                // Do nothing.
        }

        switch(Byte.toUnsignedInt(data[0x0148])) {
            case 0x00:
                numROMBanks = 2;
                break;

            case 0x01:
                numROMBanks = 4;
                break;

            case 0x02:
                numROMBanks = 8;
                break;

            case 0x03:
                numROMBanks = 16;
                break;

            case 0x04:
                numROMBanks = 32;
                break;

            case 0x05:
                numROMBanks = 64;
                break;

            case 0x06:
                numROMBanks = 128;
                break;

            case 0x07:
                numROMBanks = 256;
                break;

            case 0x08:
                numROMBanks = 512;
                break;

            default:
                // Do nothing.
        }

        switch(Byte.toUnsignedInt(data[0x0149])) {
            case 0x00:
                numRAMBanks = 0;
                break;

            case 0x01:
                numRAMBanks = -1;
                break;

            case 0x02:
                numRAMBanks = 1;
                break;

            case 0x03:
                numRAMBanks = 4;
                break;

            case 0x04:
                numRAMBanks = 16;
                break;

            case 0x05:
                numRAMBanks = 8;
                break;

            default:
                // Do nothing.
        }

        return new int[] { mbc, numROMBanks, numRAMBanks, battery };
    }

    public String getTitle() {
        // Returns the title stored in the cartridge header.
        // Look at $0134 - $0143.
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 16; i++) {
            sb.append((char)Byte.toUnsignedInt(data[0x0134 + i]));
        }
        return sb.toString();
    }

    public String getSaveFile() {
        // Returns the file where the RAM backup should be stored (if this cartridge supports battery backups).
        File file = new File(path.toString());
        String fileName = file.getName();
        fileName = fileName.substring(0, fileName.lastIndexOf("."));
        fileName += ".sav";
        return fileName;
    }
}
