package gameboy.emulator.visual;

import gameboy.data.ImageConsumer;
import gameboy.data.ImageProducer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;

// Screen Buffer (background): 256x256 pixels or 32x32 tiles (tile is 8x8 pixels)
// Only 160x144 pixels (20x18 tiles) can be displayed on the screen

// Tile Data Table located either at $8000-8FFF or $8800-97FF

// Tiles 00-7F @ $9000 - $97FF (If $FF40 bit 4=0) patterns have signed numbers from -128 to 127
// Tiles 00-7F @ $8000 - $87FF (If $FF40 bit 4=1) patterns have unsigned numbers from 0 to 255 (Objects always use this!)
// Tiles 80-FF @ $8800 - $8FFF

// Tile Map
// Tile Map 1 @ $9800 - 9BFF
// Tile Map 2 @ $9C00 - 9FFF

// LCDC Register @FF40 Bits
// 7 = LCD/PPU enable -> 0 = off, 1 = on
// 6 = Window Tile Map Area -> 0 = 9800–9BFF, 1 = 9C00–9FFF
// 5 = Window Enable -> 0 = off, 1 = on (But always off if bit 0 is off)
// 4 = BG and Window Tile Data Area -> 0 = 8800–97FF, 1 = 8000–8FFF
// 3 = BG Tile Map Area -> 0 = 9800–9BFF, 1 = 9C00–9FFF
// 2 = OBJ Size -> 0 = 8x8, 1 = 8x16
// 1 = OBJ enable -> 0 = off, 1 = on
// 0 = Window enable -> 0 = off, 1 = on

// BG and Window Palette Register @ $FF47
// Object Palette Registers OBP0 @ $FF48 and OBP1 @ $FF49

// Object Attribute Memory (OAM) @ $FE00-FE9F
// Each object is 4 bytes.
// Byte 0 -> Y position
// Byte 1 -> X position
// Byte 2 -> Tile Index
// Byte 3 -> Flags
// 3.7 -> Priority 0 = No, 1 = BG and Window colors 1–3 are drawn over this OBJ
// 3.6 -> Y-Flip 0 = Normal, 1 = Entire OBJ is vertically mirrored
// 3.5 -> X-Flip 0 = Normal, 1 = Entire OBJ is horizontally mirrored
// 3.4 -> Palette 0 = OBP0, 1 = OBP1

// $FF42, $FF43 -> BG SCY, SCX (Top Left Corner)
// bottom := (SCY + 143) % 256 and right := (SCX + 159) % 256

// Write to $FF46 to initiate transfer of data to OAM region.

public class Screen implements ImageProducer {
    public AddressMap addressMap;

    public boolean isPoweredOn = false;
    public int frameEnableCount = 0;

    public PixelFetcher pixelFetcher;

    public boolean isSTATHigh = false;

    // Needed to emulate a hardware quirk:
    // Upon entering VBlank on line 144, there is a brief opportunity to fire an OAM-STAT interrupt.
    public boolean isLine144InterruptAllowed = false;
    public boolean isLine144InterruptRequested = false;

    public Screen(AddressMap addressMap) {
        this.addressMap = addressMap;

        this.pixelFetcher = new PixelFetcher(addressMap, this);

        // Set initial mode to Mode 2 - OAM Scan and LY to 0.
        setSTATMode(0);
        setLY(0);

        initStoreMap();
        initLoadMap();
    }

    public void initStoreMap() {
        addressMap.addStoreCallback(AddressMap.ADDRESS_LCDC, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                boolean oldIsPoweredOn = isPoweredOn;
                isPoweredOn = ((b >>> 7) & 0b1) == 1;

                if(!oldIsPoweredOn && isPoweredOn) {
                    // When transitioning from off to on, we don't display the next few frames.
                    frameEnableCount = 2;

                    // Check comparison bit now.
                    setLYCompareBit();
                }
                else if(!isPoweredOn) {
                    // Power off LCD - Reset Screen and PixelFetcher.
                    setLY(0);
                    setSTATMode(0);
                    pixelFetcher.reset();
                }

                super.onStore(region, relativeAddress, b);
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_STAT, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                // The lower 3 bits are read only, so writes should not affect them.
                byte oldValue = addressMap.data[region][relativeAddress];
                int oldLowerBits = Byte.toUnsignedInt(oldValue) & 0b00000111;
                int newUpperBits = Byte.toUnsignedInt(b) & 0b11111000;
                super.onStore(region, relativeAddress, (byte)(newUpperBits | oldLowerBits));
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_LY, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                // This register is read only.
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_LYC, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                super.onStore(region, relativeAddress, b);

                // After storing LYC, immediately update comparison bit if the LCD is powered on.
                if(isPoweredOn) {
                    setLYCompareBit();
                }
            }
        });
    }

    public void initLoadMap() {
        addressMap.addLoadCallback(AddressMap.ADDRESS_STAT, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)(super.onLoad(region, relativeAddress) | 0b10000000);
            }
        });
    }

    public void attachClock(HybridClock hybridClock) {
        // Simulate performing one "Dot" per cycle.
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                processDot();
            }
        });
    }

    public int getWidth() {
        // The width of the Game Boy screen in pixels.
        return 160;
    }

    public int getHeight() {
        // The height of the Game Boy screen in pixels.
        return 144;
    }

    public void onFrameEnd() {
        if(frameEnableCount > 0) {
            frameEnableCount--;
        }
    }

    public void onPixel(int x, int y, int colorIndex) {
        if(frameEnableCount == 0) {
            produceImage(x, y, colorIndex);
        }
    }

    public void processDot() {
        if(!isPoweredOn) {
            return;
        }

        pixelFetcher.onTick();

        // Check if we have triggered a STAT interrupt.
        checkSTATInterrupt();
    }

    public void checkSTATInterrupt() {
        // Note that STAT interrupts cannot occur one right after the other because of STAT Blocking.
        int statRegister = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_STAT));

        int selectLYC = (statRegister >>> 6) & 0b1;
        int selectMode2 = (statRegister >>> 5) & 0b1;
        int selectMode1 = (statRegister >>> 4) & 0b1;
        int selectMode0 = (statRegister >>> 3) & 0b1;
        int matchLYC = (statRegister >>> 2) & 0b1;

        int mode = statRegister & 0b00000011;

        if((selectLYC == 1 && matchLYC == 1) ||
            (selectMode2 == 1 && mode == 2) ||
            (selectMode1 == 1 && mode == 1) ||
            (selectMode0 == 1 && mode == 0)) {

            if(!isSTATHigh) {
                // Request STAT Interrupt.
                isSTATHigh = true;
                addressMap.storeBit(AddressMap.ADDRESS_IF, 1, 1);

                // A real interrupt takes priority over and cancels out a fake one.
                isLine144InterruptRequested = false;
            }
        }
        else if(selectMode2 == 1 && isLine144InterruptAllowed) {
            if(!isSTATHigh) {
                // Request Fake OAM-STAT Interrupt caused by a hardware quirk.
                isLine144InterruptRequested = true;
                isSTATHigh = true;
                addressMap.storeBit(AddressMap.ADDRESS_IF, 1, 1);
            }
        }
        else {
            isSTATHigh = false;
        }
    }

    public void setSTATMode(int mode) {
        addressMap.storeBit(AddressMap.ADDRESS_STAT, 1, (mode & 0b10) >>> 1, true);
        addressMap.storeBit(AddressMap.ADDRESS_STAT, 0, mode & 0b01, true);

        if(mode == 1) {
            // Request VBlank Interrupt.
            addressMap.storeBit(AddressMap.ADDRESS_IF, 0, 1);
        }
    }

    public void setLY(int y) {
        // Store LY.
        addressMap.storeByte(AddressMap.ADDRESS_LY, (byte)y, true);

        // After storing LY, immediately update comparison bit if the LCD is powered on.
        if(isPoweredOn) {
            setLYCompareBit();
        }
    }

    public void setLYCompareBit() {
        // Store LY vs. LYC.
        int LY = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_LY));
        int LYC = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_LYC));
        addressMap.storeBit(AddressMap.ADDRESS_STAT, 2, LYC == LY ? 1 : 0, true);
    }

    public void setLine144Allowed(boolean isAllowed) {
        // Sets whether or not an OAM-STAT interrupt on line 144 is allowed due to a hardware quirk.
        isLine144InterruptAllowed = isAllowed;

        if(!isAllowed && isLine144InterruptRequested) {
            // Remove fake interrupt flag - it already had its chance to cause a STAT interrupt.
            isLine144InterruptRequested = false;
            isSTATHigh = false;
            addressMap.storeBit(AddressMap.ADDRESS_IF, 1, 0);
        }
    }

    // ImageProducer
    ImageConsumer[] imageConsumers = new ImageConsumer[0];

    @Override
    public ImageConsumer[] getImageConsumers() {
        return imageConsumers;
    }

    @Override
    public void addImageConsumer(ImageConsumer imageConsumer) {
        // Create a new array with one more element.
        ImageConsumer[] oldArray = imageConsumers;
        imageConsumers = new ImageConsumer[oldArray.length + 1];

        // Copy over elements from old array.
        for(int i = 0; i < oldArray.length; i++) {
            imageConsumers[i] = oldArray[i];
        }

        // Add new element.
        imageConsumers[oldArray.length] = imageConsumer;
    }
}
