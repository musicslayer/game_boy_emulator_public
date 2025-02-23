package gameboy.emulator.debug;

import gameboy.data.ImageConsumer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.visual.Screen;

public class DebugScreenOAM extends Screen {
    public int[] colorIndexMap0 = new int[4];
    public int[] colorIndexMap1 = new int[4];

    public int[][] imageData = new int[getHeight()][getWidth()];
    
    public DebugScreenOAM(AddressMap addressMap) {
        super(addressMap);

        // Use two fixed color palettes that cannot be manipulated.
        // This means that the colors on the debug screen may not match that on the actual Game Boy.
        colorIndexMap0[0] = 0;
        colorIndexMap0[1] = 1;
        colorIndexMap0[2] = 2;
        colorIndexMap0[3] = 3;

        colorIndexMap1[0] = 4;
        colorIndexMap1[1] = 5;
        colorIndexMap1[2] = 6;
        colorIndexMap1[3] = 7;
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

    public void resetImageData() {
        imageData = new int[getHeight()][getWidth()];
    }

    public int getWidth() {
        // The width in pixels - 40 tile blocks - 8 pixels / tile
        return 320;
    }

    public int getHeight() {
        // The height in pixels - 2 tile blocks - 8 pixels / tile
        return 16;
    }

    public void drawScreen() {
        // We will draw all 40 object in a row, neglecting their intended x and y positions.
        boolean isLargeMode = addressMap.loadBit(AddressMap.ADDRESS_LCDC, 2) == 1;
        int[][] objectAttributes = getObjectAttributes();
        for(int i = 0; i < 40; i++) {
            // Each object attribute is 4 bytes.
            // Byte 0 -> Y position
            // Byte 1 -> X position
            // Byte 2 -> Tile Index
            // Byte 3 -> Flags
            // 3.7 -> Priority 0 = No, 1 = BG and Window colors 1–3 are drawn over this OBJ
            // 3.6 -> Y-Flip 0 = Normal, 1 = Entire OBJ is vertically mirrored
            // 3.5 -> X-Flip 0 = Normal, 1 = Entire OBJ is horizontally mirrored
            // 3.4 -> Palette 0 = OBP0, 1 = OBP1
            int[] objectAttribute = objectAttributes[i];

            int tileDataAddressBase = 0x8000;
            int tileDataAddressOffset = objectAttribute[2];
            int tileDataAddress = tileDataAddressBase + (tileDataAddressOffset * 16);
            
            DebugTile tile = DebugTile.fromAddress(tileDataAddress, addressMap, isLargeMode);

            int tileX = i * 8;
            int tileY = 0;

            int bit3_6 = (objectAttribute[3] >>> 6) & 0b1;
            int bit3_5 = (objectAttribute[3] >>> 5) & 0b1;
            int bit3_4 = (objectAttribute[3] >>> 4) & 0b1;

            // Each object can use one of two palettes.
            int[] colorIndexMap;
            if(bit3_4 == 0) {
                colorIndexMap = colorIndexMap0;
            }
            else {
                colorIndexMap = colorIndexMap1;
            }

            boolean flipX = bit3_5 == 1;
            boolean flipY = bit3_6 == 1;
            
            tile.draw(imageData, getWidth(), getHeight(), tileX, tileY, colorIndexMap, flipX, flipY);
        }

        produceDebugImage(imageData);
    }

    // LCDC Register @FF40 Bits
    // 2 = OBJ Size -> 0 = 8x8, 1 = 8x16
    // 1 = OBJ enable -> 0 = off, 1 = on

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
    public int[][] getObjectAttributes() {
        // Objects always use this tile data addressing method.
        int tileMapAddressBase = 0xFE00;

        int[][] objectAttributes = new int[40][4];
        for(int objectID = 0; objectID < 40; objectID++) {
            // For objects, each map entry is 4 bytes of information.
            int tileMapAddress = tileMapAddressBase + (objectID * 4);

            for(int i = 0; i < 4; i++) {
                objectAttributes[objectID][i] = Byte.toUnsignedInt(addressMap.loadByte(tileMapAddress + i));
            }
        }
        return objectAttributes;
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
