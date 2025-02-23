package gameboy.emulator.debug;

import gameboy.data.ImageConsumer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.visual.Screen;

public class DebugScreenWindow extends Screen {
    public int[] colorIndexMap = new int[4];

    public int[][] imageData = new int[getHeight()][getWidth()];
    
    public DebugScreenWindow(AddressMap addressMap) {
        super(addressMap);

        // Use a fixed color palette that cannot be manipulated.
        // This means that the colors on the debug screen may not match that on the actual Game Boy.
        colorIndexMap[0] = 0;
        colorIndexMap[1] = 1;
        colorIndexMap[2] = 2;
        colorIndexMap[3] = 3;
    }

    @Override
    public void initStoreMap() {
        // Do nothing.
    }

    public void attachClock(HybridClock hybridClock) {
        // The debug screen does not have to be in sync with the rest of the gameboy.
        hybridClock.addAsynchronousFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                drawScreen();
            }
        });
    }

    public int getWidth() {
        // The width in pixels - 32 tile blocks - 8 pixels / tile
        return 256;
    }

    public int getHeight() {
        // The height in pixels - 32 tile blocks - 8 pixels / tile
        return 256;
    }

    public void drawScreen() {
        // Draw the complete background.
        imageData = new int[getHeight()][getWidth()];

        DebugTile[] tiles = getTiles();

        for(int ty = 0; ty < 32; ty++) {
            for(int tx = 0; tx < 32; tx++) {
                int tileNum = tx + (32 * ty);
                DebugTile tile = tiles[tileNum];

                tile.draw(imageData, getWidth(), getHeight(), tx * 8, ty * 8, colorIndexMap, false, false);
            }
        }

        produceDebugImage(imageData);
    }

    // LCDC Register @FF40 Bits
    // 6 = Window Tile Map Area -> 0 = 9800–9BFF, 1 = 9C00–9FFF
    // 4 = Window Tile Data Area -> 0 = 8800–97FF, 1 = 8000–8FFF
    public DebugTile[] getTiles() {
        int tileDataAddressBase;
        int tileDataMode;
        int tileMapAddressBase;
        if(addressMap.loadBit(AddressMap.ADDRESS_LCDC, 4) == 0) {
            tileDataAddressBase = 0x9000;
            tileDataMode = 0;
        }
        else {
            tileDataAddressBase = 0x8000;
            tileDataMode = 1;
        }
        if(addressMap.loadBit(AddressMap.ADDRESS_LCDC, 6) == 0) {
            tileMapAddressBase = 0x9800;
        }
        else {
            tileMapAddressBase = 0x9C00;
        }

        DebugTile[] tiles = new DebugTile[1024];
        for(int offset = 0; offset < 1024; offset++) {
            // For the background, each map entry is a 1-byte address pointing to a tile's information in the tile data area.
            int tileMapAddress = tileMapAddressBase + offset;
            int tileDataAddressOffset;
            if(tileDataMode == 0) {
                tileDataAddressOffset = (int)(addressMap.loadByte(tileMapAddress));
            }
            else {
                tileDataAddressOffset = Byte.toUnsignedInt(addressMap.loadByte(tileMapAddress));
            }
            int tileDataAddress = tileDataAddressBase + (tileDataAddressOffset * 16);
            tiles[offset] = DebugTile.fromAddress(tileDataAddress, addressMap, false);
        }
        return tiles;
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
