import gameboy.GameBoy;
import gameboy.emulator.software.BIOS;
import gameboy.emulator.software.Cartridge;
import gameboy.emulator.software.CartridgeInfo;

class Main {
    public static void main(String[] args) {
        BIOS bios = getBIOS();
        Cartridge cartridge = getCartridge();

        System.out.println(CartridgeInfo.getInfo(cartridge));
        
        GameBoy gameboy = new GameBoy();
        gameboy.loadBIOS(bios);
        gameboy.loadCartridge(cartridge);
        gameboy.initialize();
        gameboy.powerOn();
    }

    public static BIOS getBIOS() {
        BIOS bios = new BIOS("bios/bootix_dmg.bin");
        return bios;
    }

    public static Cartridge getCartridge() {
        // Games - Uncomment a line to choose a game.
        //Cartridge cartridge = new Cartridge("rom/evoland.gb");
        Cartridge cartridge = new Cartridge("rom/pocket.gb");

        return cartridge;
    }
}