package gameboy.emulator.software;

// Parses a Game Boy cartridge header for debugging purposes.
public class CartridgeInfo {
    public static String getInfo(Cartridge cartridge) {
        if(cartridge == null) {
            return "Cartridge is null";
        }

        String s = "Cartridge Info:";

        byte[] data = cartridge.data;

        // $0134 - $0143 Title
        s += "\n  Title: "
        + (char)data[0x134]
        + (char)data[0x135]
        + (char)data[0x136]
        + (char)data[0x137]
        + (char)data[0x138]
        + (char)data[0x139]
        + (char)data[0x13A]
        + (char)data[0x13B]
        + (char)data[0x13C]
        + (char)data[0x13D]
        + (char)data[0x13E]
        + (char)data[0x13F]
        + (char)data[0x140]
        + (char)data[0x141]
        + (char)data[0x142]
        + (char)data[0x143];

        // $0147 - Cartridge type
        s += "\n  Cartridge Type: " + toHex2(data[0x0147]) + " " + getCartridgeTypeString(data[0x0147]);

        // $0148 - ROM Size
        s += "\n  ROM Size: " + toHex2(data[0x0148]) + " " + getROMSizeString(data[0x0148]);

        // $0149 - RAM Size
        s += "\n  RAM Size: " + toHex2(data[0x0149]) + " " + getRAMSizeString(data[0x0149]);

        return s;
    }

    public static String toHex2(int value) {
        return String.format("%02X", value);
    }

    public static String getCartridgeTypeString(int value) {
        String s;
        switch(value) {
        case 0x00:
            s = "ROM ONLY";
            break;
        case 0x01:
            s = "MBC1";
            break;
        case 0x02:
            s = "MBC1+RAM";
            break;
        case 0x03:
            s = "MBC1+RAM+BATTERY";
            break;
        case 0x05:
            s = "MBC2";
            break;
        case 0x06:
            s = "MBC2+BATTERY";
            break;
        case 0x08:
            s = "ROM+RAM";
            break;
        case 0x09:
            s = "ROM+RAM+BATTERY";
            break;
        case 0x0B:
            s = "MMM01";
            break;
        case 0x0C:
            s = "MMM01+RAM";
            break;
        case 0x0D:
            s = "MMM01+RAM+BATTERY";
            break;
        case 0x0F:
            s = "MBC3+TIMER+BATTERY";
            break;
        case 0x10:
            s = "MBC3+TIMER+RAM+BATTERY";
            break;
        case 0x11:
            s = "MBC3";
            break;
        case 0x12:
            s = "MBC3+RAM";
            break;
        case 0x13:
            s = "MBC3+RAM+BATTERY";
            break;
        case 0x19:
            s = "MBC5";
            break;
        case 0x1A:
            s = "MBC5+RAM";
            break;
        case 0x1B:
            s = "MBC5+RAM+BATTERY";
            break;
        case 0x1C:
            s = "MBC5+RUMBLE";
            break;
        case 0x1D:
            s = "MBC5+RUMBLE+RAM";
            break;
        case 0x1E:
            s = "MBC5+RUMBLE+RAM+BATTERY";
            break;
        case 0x20:
            s = "MBC6";
            break;
        case 0x22:
            s = "MBC7+SENSOR+RUMBLE+RAM+BATTERY";
            break;
        case 0xFC:
            s = "POCKET CAMERA";
            break;
        case 0xFD:
            s = "BANDAI TAMA5";
            break;
        case 0xFE:
            s = "HuC3";
            break;
        case 0xFF:
            s = "HuC1+RAM+BATTERY";
            break;
        default:
            s = "[Unknown]";
        }
        return s;
    }

    public static String getROMSizeString(int value) {
        String s;
        switch(value) {
        case 0x00:
            s = "32 KiB - 2 Banks";
            break;
        case 0x01:
            s = "64 KiB - 4 Banks";
            break;
        case 0x02:
            s = "128 KiB - 8 Banks";
            break;
        case 0x03:
            s = "256 KiB - 16 Banks";
            break;
        case 0x04:
            s = "512 KiB - 32 Banks";
            break;
        case 0x05:
            s = "1 MiB - 64 Banks";
            break;
        case 0x06:
            s = "2 MiB - 128 Banks";
            break;
        case 0x07:
            s = "4 MiB - 256 Banks";
            break;
        case 0x08:
            s = "8 MiB - 512 Banks";
            break;
        default:
            s = "[Unknown]";
        }
        return s;
    }

    public static String getRAMSizeString(int value) {
        String s;
        switch(value) {
        case 0x00:
            s = "0 KiB - 0 Banks";
            break;
        case 0x02:
            s = "8 KiB - 1 Bank";
            break;
        case 0x03:
            s = "32 KiB - 4 Banks";
            break;
        case 0x04:
            s = "128 KiB - 16 Banks";
            break;
        case 0x05:
            s = "64 KiB - 8 Banks";
            break;
        default:
            s = "[Unknown]";
        }
        return s;
    }
}