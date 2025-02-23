package gameboy.emulator.debug;

import gameboy.data.ImageConsumer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.visual.Screen;

public class DebugScreenTileData extends Screen {
    public int[] colorIndexMap = new int[4];

    public int[][] imageData = new int[getHeight()][getWidth()];
    
    public DebugScreenTileData(AddressMap addressMap) {
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
        // The width of three tile blocks (48 tiles x 8 tiles, 8 pixels / tile).
        return 384;
    }

    public int getHeight() {
        // The height of one tile block (16 tiles x 8 tiles, 8 pixels / tile).
        return 64;
    }

    public void drawScreen() {
        // Draw all the tile data block.
        DebugTile[] tiles0 = getTilesFromBlock(0);
        DebugTile[] tiles1 = getTilesFromBlock(1);
        DebugTile[] tiles2 = getTilesFromBlock(2);

        drawTileBlock(imageData, tiles0, 0);
        drawTileBlock(imageData, tiles1, 1);
        drawTileBlock(imageData, tiles2, 2);

        produceDebugImage(imageData);
    }

    public DebugTile[] getTilesFromBlock(int block) {
        int tileDataAddressBase = 0x8000 + (0x0800 * block);
        DebugTile[] tiles = new DebugTile[128];
        for(int n = 0; n < 128; n++) {
            // For the background, each map entry is a 1-byte address pointing to a tile's information in the tile data area.
            int tileDataAddress = tileDataAddressBase + (n * 16);
            tiles[n] = DebugTile.fromAddress(tileDataAddress, addressMap, false);
        }
        return tiles;
    }

    // Block: 16 tiles x 8 tiles, 8 pixels / tile, 128 tiles total
    public void drawTileBlock(int[][] imageData, DebugTile[] tiles, int block) {
        int blockOffetX = block * 128;

        for(int ty = 0; ty < 8; ty++) {
            for(int tx = 0; tx < 16; tx++) {
                int tileNum = tx + (16 * ty);
                DebugTile tile = tiles[tileNum];

                tile.draw(imageData, getWidth(), getHeight(), (tx * 8) + blockOffetX, ty * 8, colorIndexMap, false, false);
            }
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
