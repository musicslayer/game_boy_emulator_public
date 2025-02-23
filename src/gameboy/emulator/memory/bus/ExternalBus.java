package gameboy.emulator.memory.bus;

import gameboy.emulator.memory.AddressMap;

// A bus that can only access ROM/RAM.
public class ExternalBus extends Bus {
    AddressMap addressMap;

    int[] addressMasks = new int[0x10000];
    int[] regions = new int[0x10000];

    public ExternalBus(AddressMap addressMap) {
        this.addressMap = addressMap;
    }

    public int decodeRegion(int address) {
        // Mapping to the BIOS has the highest priority if active.
        int region;
        if(addressMap.flagBIOS == 1 && address <= 0x00FF) {
            region = AddressMap.REGION_BIOS;
        }
        else {
            region = regions[address];
        }
        return region;
    }

    public int decodeRelativeAddress(int address) {
        // Mapping to the BIOS has the highest priority if active.
        int relativeAddress;
        if(addressMap.flagBIOS == 1 && address <= 0x00FF) {
            relativeAddress = address;
        }
        else {
            relativeAddress = address & addressMasks[address];
        }
        return relativeAddress;
    }

    public void initDecodeMap() {
        // ROM Region A - Ignore first 2 bits of address
        for(int address = 0x0000; address <= 0x3FFF; address++) {
            addressMasks[address] = 0x3FFF;
            regions[address] = AddressMap.REGION_ROMA;
        }

        // ROM Region B - Ignore first 2 bits of address
        for(int address = 0x4000; address <= 0x7FFF; address++) {
            addressMasks[address] = 0x3FFF;
            regions[address] = AddressMap.REGION_ROMB;
        }

        // VRAM - Ignore first 3 bits of address
        for(int address = 0x8000; address <= 0x9FFF; address++) {
            addressMasks[address] = 0x1FFF;
            regions[address] = AddressMap.REGION_VRAM;
        }

        // SRAM - Ignore first 3 bits of address
        for(int address = 0xA000; address <= 0xBFFF; address++) {
            addressMasks[address] = 0x1FFF;
            regions[address] = AddressMap.REGION_SRAM;
        }

        // WRAM Region A - Ignore first 4 bits of address
        for(int address = 0xC000; address <= 0xCFFF; address++) {
            addressMasks[address] = 0x0FFF;
            regions[address] = AddressMap.REGION_WRAMA;
        }

        // WRAM Region B - Ignore first 4 bits of address
        for(int address = 0xD000; address <= 0xDFFF; address++) {
            addressMasks[address] = 0x0FFF;
            regions[address] = AddressMap.REGION_WRAMB;
        }

        // Mirror WRAM Region A - Ignore first 4 bits of address
        for(int address = 0xE000; address <= 0xEFFF; address++) {
            addressMasks[address] = 0x0FFF;
            regions[address] = AddressMap.REGION_WRAMA;
        }

        // Mirror WRAM Region B - Ignore first 4 bits of address
        for(int address = 0xF000; address <= 0xFFFF; address++) {
            addressMasks[address] = 0x0FFF;
            regions[address] = AddressMap.REGION_WRAMB;
        }
    }
}