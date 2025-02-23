package gameboy.emulator.memory;

import gameboy.GameBoy.CloseableResourceManager;
import gameboy.emulator.memory.bus.Bus;
import gameboy.emulator.memory.bus.ExternalBus;
import gameboy.emulator.memory.bus.MainBus;
import gameboy.emulator.memory.mbc.MemoryBankController;
import gameboy.emulator.software.BIOS;
import gameboy.emulator.software.Cartridge;

public class AddressMap {
    // Index of regions in arrays.
    public final static int REGION_BIOS = 0;
    public final static int REGION_ROMA = 1;
    public final static int REGION_ROMB = 2;
    public final static int REGION_VRAM = 3;
    public final static int REGION_SRAM = 4;
    public final static int REGION_WRAMA = 5;
    public final static int REGION_WRAMB = 6;
    public final static int REGION_OAM = 7;
    public final static int REGION_IO = 8;
    public final static int REGION_HRAM = 9;
    public final static int REGION_IE = 10;

    // Length of regions in arrays.
    public final static int LENGTH_BIOS = 256;
    public final static int LENGTH_ROMA = 16384;
    public final static int LENGTH_ROMB = 16384;
    public final static int LENGTH_VRAM = 8192;
    public final static int LENGTH_SRAM = 8192;
    public final static int LENGTH_WRAMA = 4096;
    public final static int LENGTH_WRAMB = 4096;
    public final static int LENGTH_OAM = 256;
    public final static int LENGTH_IO = 128;
    public final static int LENGTH_HRAM = 127;
    public final static int LENGTH_IE = 1;

    // IO Register Addresses
    public final static int ADDRESS_JOYP = 0xFF00;
    public final static int ADDRESS_SB = 0xFF01; // Unimplemented
    public final static int ADDRESS_SC = 0xFF02; // Unimplemented
    public final static int ADDRESS_DIV = 0xFF04;
    public final static int ADDRESS_TIMA = 0xFF05;
    public final static int ADDRESS_TMA = 0xFF06;
    public final static int ADDRESS_TAC = 0xFF07;
    public final static int ADDRESS_IF = 0xFF0F;
    public final static int ADDRESS_NR10 = 0xFF10;
    public final static int ADDRESS_NR11 = 0xFF11;
    public final static int ADDRESS_NR12 = 0xFF12;
    public final static int ADDRESS_NR13 = 0xFF13;
    public final static int ADDRESS_NR14 = 0xFF14;
    public final static int ADDRESS_NR21 = 0xFF16;
    public final static int ADDRESS_NR22 = 0xFF17;
    public final static int ADDRESS_NR23 = 0xFF18;
    public final static int ADDRESS_NR24 = 0xFF19;
    public final static int ADDRESS_NR30 = 0xFF1A;
    public final static int ADDRESS_NR31 = 0xFF1B;
    public final static int ADDRESS_NR32 = 0xFF1C;
    public final static int ADDRESS_NR33 = 0xFF1D;
    public final static int ADDRESS_NR34 = 0xFF1E;
    public final static int ADDRESS_NR41 = 0xFF20;
    public final static int ADDRESS_NR42 = 0xFF21;
    public final static int ADDRESS_NR43 = 0xFF22;
    public final static int ADDRESS_NR44 = 0xFF23;
    public final static int ADDRESS_NR50 = 0xFF24;
    public final static int ADDRESS_NR51 = 0xFF25;
    public final static int ADDRESS_NR52 = 0xFF26;
    public final static int ADDRESS_WAVERAM = 0xFF30; // First byte of Wave RAM
    public final static int ADDRESS_LCDC = 0xFF40;
    public final static int ADDRESS_STAT = 0xFF41;
    public final static int ADDRESS_SCY = 0xFF42;
    public final static int ADDRESS_SCX = 0xFF43;
    public final static int ADDRESS_LY = 0xFF44;
    public final static int ADDRESS_LYC = 0xFF45;
    public final static int ADDRESS_DMA = 0xFF46;
    public final static int ADDRESS_BGP = 0xFF47;
    public final static int ADDRESS_OBP0 = 0xFF48;
    public final static int ADDRESS_OBP1 = 0xFF49;
    public final static int ADDRESS_WY = 0xFF4A;
    public final static int ADDRESS_WX = 0xFF4B;
    public final static int ADDRESS_IE = 0xFFFF;

    public int flagBIOS = 1;

    public byte[][] data = new byte[][] {
        null, // BIOS (Accessed from BIOS)
        null, // ROM - bank 0 of cartridge ROM (Accessed from Cartridge)
        null, // ROM - bank N from cartridge (Accessed from Cartridge)
        new byte[LENGTH_VRAM], // Video RAM
        null, // Storage RAM - bank N from cartridge (Accessed from Cartridge)
        new byte[LENGTH_WRAMA], // Work RAM
        new byte[LENGTH_WRAMB], // Work RAM
        new byte[LENGTH_OAM], // Object Attribute Memory (including the "unusable" region)
        new byte[LENGTH_IO], // IO Registers
        new byte[LENGTH_HRAM], // High RAM
        new byte[LENGTH_IE], // Interrupt Enable Register
    };

    public MainBus mainBus = new MainBus(this);
    public ExternalBus externalBus = new ExternalBus(this);

    StoreCallback[][] storeMap = new StoreCallback[11][];
    LoadCallback[][] loadMap = new LoadCallback[11][];

    public BIOS bios;
    public Cartridge cartridge;
    public MemoryBankController memoryBankController;

    public byte lastDMAByte;

    public AddressMap(CloseableResourceManager closeableResourceManager, BIOS bios, Cartridge cartridge) {
        this.bios = bios;
        this.cartridge = cartridge;

        this.memoryBankController = MemoryBankController.createMemoryBankController(closeableResourceManager, this, cartridge);

        initialize();
    }

    public void initialize() {
        if(bios != null) {
            data[REGION_BIOS] = bios.data;
        }

        initStoreMap();
        initLoadMap();

        mainBus.initDecodeMap();
        externalBus.initDecodeMap();
    }

    public void storeByte(int address, byte b, boolean bypass) {
        int region = mainBus.decodeRegion(address);
        int relativeAddress = mainBus.decodeRelativeAddress(address);
        data[region][relativeAddress] = b;
    }

    public byte loadByte(int address, boolean bypass) {
        int region = mainBus.decodeRegion(address);
        int relativeAddress = mainBus.decodeRelativeAddress(address);
        return data[region][relativeAddress];
    }

    public void storeByte(int address, byte b, Bus bus) {
        int region = bus.decodeRegion(address);
        int relativeAddress = bus.decodeRelativeAddress(address);
        StoreCallback storeCallback = storeMap[region][relativeAddress];
        storeCallback.onStore(region, relativeAddress, b);
    }

    public byte loadByte(int address, Bus bus) {
        int region = bus.decodeRegion(address);
        int relativeAddress = bus.decodeRelativeAddress(address);
        LoadCallback loadCallback = loadMap[region][relativeAddress];
        return loadCallback.onLoad(region, relativeAddress);
    }

    public void storeByte(int address, byte b) {
        storeByte(address, b, mainBus);
    }

    public byte loadByte(int address) {
        return loadByte(address, mainBus);
    }

    public void storeShort(int address, short s) {
        // The lower byte is in the lower address.
        storeByte(address, (byte)(s & 0x00FF));
        storeByte(address + 1, (byte)((s >> 8) & 0x00FF));
    }

    public short loadShort(int address) {
        // The lower byte is in the lower address.
        return (short)((loadByte(address) & 0xFF) | (loadByte(address + 1) << 8));
    }

    public void storeBit(int address, int n, int value, boolean bypass) {
        int data = Byte.toUnsignedInt(loadByte(address));
        int mask = (0b00000001 << n);
        if(value == 1) {
            data |= mask;
        }
        else {
            data &= ~mask;
        }
        storeByte(address, (byte)data, true);
    }

    public void storeBit(int address, int n, int value) {
        int data = Byte.toUnsignedInt(loadByte(address));
        int mask = (0b00000001 << n);
        if(value == 1) {
            data |= mask;
        }
        else {
            data &= ~mask;
        }
        storeByte(address, (byte)data);
    }

    public int loadBit(int address, int n, boolean bypass) {
        int data = Byte.toUnsignedInt(loadByte(address, true));
        int mask = (0b00000001 << n);
        return (data & mask) == mask ? 1 : 0;
    }

    public int loadBit(int address, int n) {
        int data = Byte.toUnsignedInt(loadByte(address));
        int mask = (0b00000001 << n);
        return (data & mask) == mask ? 1 : 0;
    }

    public void initStoreMap() {
        // BIOS
        StoreCallback[] storeBIOSCallbacks = new StoreCallback[LENGTH_BIOS];
        for(int i = 0; i < LENGTH_BIOS; i++) {
            storeBIOSCallbacks[i] = new StoreCallback() {
                @Override
                public void onStore(int region, int relativeAddress, byte b) {
                    // The BIOS is read only.
                }
            };
        }
        storeMap[REGION_BIOS] = storeBIOSCallbacks;
        
        // VRAM
        StoreCallback[] storeVRAMCallbacks = new StoreCallback[LENGTH_VRAM];
        for(int i = 0; i < LENGTH_VRAM; i++) {
            storeVRAMCallbacks[i] = new StoreCallback();
        }
        storeMap[REGION_VRAM] = storeVRAMCallbacks;

        // WRAMA
        StoreCallback[] storeWRAMACallbacks = new StoreCallback[LENGTH_WRAMA];
        for(int i = 0; i < LENGTH_WRAMA; i++) {
            storeWRAMACallbacks[i] = new StoreCallback();
        }
        storeMap[REGION_WRAMA] = storeWRAMACallbacks;

        // WRAMB
        StoreCallback[] storeWRAMBCallbacks = new StoreCallback[LENGTH_WRAMB];
        for(int i = 0; i < LENGTH_WRAMB; i++) {
            storeWRAMBCallbacks[i] = new StoreCallback();
        }
        storeMap[REGION_WRAMB] = storeWRAMBCallbacks;

        // OAM
        StoreCallback[] storeOAMCallbacks = new StoreCallback[LENGTH_OAM];
        for(int i = 0; i < LENGTH_OAM; i++) {
            storeOAMCallbacks[i] = new StoreCallback();
        }
        storeMap[REGION_OAM] = storeOAMCallbacks;

        // IO
        StoreCallback[] storeIOCallbacks = new StoreCallback[LENGTH_IO];
        for(int i = 0; i < LENGTH_IO; i++) {
            storeIOCallbacks[i] = new StoreCallback();
        }

        // 0xFF50 - Stop using BIOS
        storeIOCallbacks[0x0050] = new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                flagBIOS = 0;
            }
        };

        storeMap[REGION_IO] = storeIOCallbacks;

        // HRAM
        StoreCallback[] storeHRAMCallbacks = new StoreCallback[LENGTH_HRAM];
        for(int i = 0; i < LENGTH_HRAM; i++) {
            storeHRAMCallbacks[i] = new StoreCallback();
        }
        storeMap[REGION_HRAM] = storeHRAMCallbacks;

        // IE
        StoreCallback[] storeIECallbacks = new StoreCallback[LENGTH_IE];
        for(int i = 0; i < LENGTH_IE; i++) {
            storeIECallbacks[i] = new StoreCallback();
        }
        storeMap[REGION_IE] = storeIECallbacks;

        // ROMA, ROMB, and SRAM are handled by the memory controller.
        storeMap[REGION_ROMA] = new StoreCallback[LENGTH_ROMA];
        storeMap[REGION_ROMB] = new StoreCallback[LENGTH_ROMB];
        storeMap[REGION_SRAM] = new StoreCallback[LENGTH_SRAM];
        memoryBankController.initStoreMap(storeMap);
    }

    public void initLoadMap() {
        // BIOS
        LoadCallback[] loadBIOSCallbacks = new LoadCallback[LENGTH_BIOS];
        for(int i = 0; i < LENGTH_BIOS; i++) {
            loadBIOSCallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_BIOS] = loadBIOSCallbacks;

        // VRAM
        LoadCallback[] loadVRAMCallbacks = new LoadCallback[LENGTH_VRAM];
        for(int i = 0; i < LENGTH_VRAM; i++) {
            loadVRAMCallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_VRAM] = loadVRAMCallbacks;

        // WRAMA
        LoadCallback[] loadWRAMACallbacks = new LoadCallback[LENGTH_WRAMA];
        for(int i = 0; i < LENGTH_WRAMA; i++) {
            loadWRAMACallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_WRAMA] = loadWRAMACallbacks;

        // WRAMB
        LoadCallback[] loadWRAMBCallbacks = new LoadCallback[LENGTH_WRAMB];
        for(int i = 0; i < LENGTH_WRAMB; i++) {
            loadWRAMBCallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_WRAMB] = loadWRAMBCallbacks;

        // OAM
        LoadCallback[] loadOAMCallbacks = new LoadCallback[LENGTH_OAM];
        for(int i = 0; i < LENGTH_OAM; i++) {
            loadOAMCallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_OAM] = loadOAMCallbacks;

        // IO
        LoadCallback[] loadIOCallbacks = new LoadCallback[LENGTH_IO];
        for(int i = 0; i < LENGTH_IO; i++) {
            loadIOCallbacks[i] = new LoadCallback();
        }

        // 0xFF03 - (Unused)
        loadIOCallbacks[0x0003] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF08 - (Unused)
        loadIOCallbacks[0x0008] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF09 - (Unused)
        loadIOCallbacks[0x0009] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF0A - (Unused)
        loadIOCallbacks[0x000A] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF0B - (Unused)
        loadIOCallbacks[0x000B] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF0C - (Unused)
        loadIOCallbacks[0x000C] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF0D - (Unused)
        loadIOCallbacks[0x000D] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF0E - (Unused)
        loadIOCallbacks[0x000E] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF15 - (Unused)
        loadIOCallbacks[0x0015] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF1F - (Unused)
        loadIOCallbacks[0x001F] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF27 - (Unused)
        loadIOCallbacks[0x0027] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF28 - (Unused)
        loadIOCallbacks[0x0028] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF29 - (Unused)
        loadIOCallbacks[0x0029] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF2A - (Unused)
        loadIOCallbacks[0x002A] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF2B - (Unused)
        loadIOCallbacks[0x002B] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF2C - (Unused)
        loadIOCallbacks[0x002C] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF2D - (Unused)
        loadIOCallbacks[0x002D] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF2E - (Unused)
        loadIOCallbacks[0x002E] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF2F - (Unused)
        loadIOCallbacks[0x002F] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF4C - (Unused)
        loadIOCallbacks[0x004C] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF4D - (Unused)
        loadIOCallbacks[0x004D] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF4E - (Unused)
        loadIOCallbacks[0x004E] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF4F - (Unused)
        loadIOCallbacks[0x004F] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF50 - Stop using BIOS
        loadIOCallbacks[0x0050] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF51 - (Unused)
        loadIOCallbacks[0x0051] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF52 - (Unused)
        loadIOCallbacks[0x0052] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF53 - (Unused)
        loadIOCallbacks[0x0053] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF54 - (Unused)
        loadIOCallbacks[0x0054] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF55 - (Unused)
        loadIOCallbacks[0x0055] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF56 - (Unused)
        loadIOCallbacks[0x0056] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF57 - (Unused)
        loadIOCallbacks[0x0057] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF58 - (Unused)
        loadIOCallbacks[0x0058] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF59 - (Unused)
        loadIOCallbacks[0x0059] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF5A - (Unused)
        loadIOCallbacks[0x005A] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF5B - (Unused)
        loadIOCallbacks[0x005B] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF5C - (Unused)
        loadIOCallbacks[0x005C] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF5D - (Unused)
        loadIOCallbacks[0x005D] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF5E - (Unused)
        loadIOCallbacks[0x005E] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF5F - (Unused)
        loadIOCallbacks[0x005F] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF60 - (Unused)
        loadIOCallbacks[0x0060] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF61 - (Unused)
        loadIOCallbacks[0x0061] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF62 - (Unused)
        loadIOCallbacks[0x0062] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF63 - (Unused)
        loadIOCallbacks[0x0063] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF64 - (Unused)
        loadIOCallbacks[0x0064] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF65 - (Unused)
        loadIOCallbacks[0x0065] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF66 - (Unused)
        loadIOCallbacks[0x0066] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF67 - (Unused)
        loadIOCallbacks[0x0067] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF68 - (Unused)
        loadIOCallbacks[0x0068] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF69 - (Unused)
        loadIOCallbacks[0x0069] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF6A - (Unused)
        loadIOCallbacks[0x006A] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF6B - (Unused)
        loadIOCallbacks[0x006B] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF6C - (Unused)
        loadIOCallbacks[0x006C] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF6D - (Unused)
        loadIOCallbacks[0x006D] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF6E - (Unused)
        loadIOCallbacks[0x006E] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF6F - (Unused)
        loadIOCallbacks[0x006F] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF70 - (Unused)
        loadIOCallbacks[0x0070] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF71 - (Unused)
        loadIOCallbacks[0x0071] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF72 - (Unused)
        loadIOCallbacks[0x0072] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF73 - (Unused)
        loadIOCallbacks[0x0073] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF74 - (Unused)
        loadIOCallbacks[0x0074] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF75 - (Unused)
        loadIOCallbacks[0x0075] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF76 - (Unused)
        loadIOCallbacks[0x0076] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF77 - (Unused)
        loadIOCallbacks[0x0077] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF78 - (Unused)
        loadIOCallbacks[0x0078] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF79 - (Unused)
        loadIOCallbacks[0x0079] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF7A - (Unused)
        loadIOCallbacks[0x007A] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF7B - (Unused)
        loadIOCallbacks[0x007B] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF7C - (Unused)
        loadIOCallbacks[0x007C] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF7D - (Unused)
        loadIOCallbacks[0x007D] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF7E - (Unused)
        loadIOCallbacks[0x007E] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        // 0xFF7F - (Unused)
        loadIOCallbacks[0x007F] = new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)0b11111111;
            }
        };

        loadMap[REGION_IO] = loadIOCallbacks;

        // HRAM
        LoadCallback[] loadHRAMCallbacks = new LoadCallback[LENGTH_HRAM];
        for(int i = 0; i < LENGTH_HRAM; i++) {
            loadHRAMCallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_HRAM] = loadHRAMCallbacks;

        // IE
        LoadCallback[] loadIECallbacks = new LoadCallback[LENGTH_IE];
        for(int i = 0; i < LENGTH_IE; i++) {
            loadIECallbacks[i] = new LoadCallback();
        }
        loadMap[REGION_IE] = loadIECallbacks;

        // ROMA, ROMB, and SRAM are handled by the memory controller.
        loadMap[REGION_ROMA] = new LoadCallback[LENGTH_ROMA];
        loadMap[REGION_ROMB] = new LoadCallback[LENGTH_ROMB];
        loadMap[REGION_SRAM] = new LoadCallback[LENGTH_SRAM];
        memoryBankController.initLoadMap(loadMap);
    }

    public void addStoreCallback(int address, StoreCallback storeCallback) {
        int region = mainBus.decodeRegion(address);
        int relativeAddress = mainBus.decodeRelativeAddress(address);
        storeMap[region][relativeAddress] = storeCallback;
    }

    public void addLoadCallback(int address, LoadCallback loadCallback) {
        int region = mainBus.decodeRegion(address);
        int relativeAddress = mainBus.decodeRelativeAddress(address);
        loadMap[region][relativeAddress] = loadCallback;
    }

    public class StoreCallback {
        public void onStore(int region, int relativeAddress, byte b) {
            // Default store
            data[region][relativeAddress] = b;
        }
    }

    public class LoadCallback {
        public byte onLoad(int region, int relativeAddress) {
            // Default load
            return data[region][relativeAddress];
        }
    }
}
