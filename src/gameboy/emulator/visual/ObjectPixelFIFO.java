package gameboy.emulator.visual;

import gameboy.emulator.memory.AddressMap;

public class ObjectPixelFIFO {
    AddressMap addressMap;

    // Represents the Pixel FIFO.
    int[][] pixelData = new int[8][3];
    int[] poppedPixel = new int[3]; // 0 -> colorID, 1 -> priority, 2 -> palette

    // Stores up to 10 visible objects sprites ordered by priority.
    int[][] sortedOAMIndex = new int[10][2]; // 0 -> x, 1 -> objectID
    int numVisibleObjects;
    int lastDrawnObject;
    int objectID;

    boolean isObjectEnabled;
    boolean isLargeMode;

    int tileObjectID;
    byte tileID;
    int tileDataLow;
    int tileDataHigh;

    int oamCounter = 0;
    int drawCounter = 0;

    boolean isActive;
    boolean isWaiting;

    int x;
    int y;

    public ObjectPixelFIFO(AddressMap addressMap) {
        this.addressMap = addressMap;
    }

    public void onLineStart() {
        oamCounter = 0;
        drawCounter = 0;

        numVisibleObjects = 0;
        lastDrawnObject = -1;
        objectID = 0;

        for(int i = 0; i < 8; i++) {
            pixelData[i][0] = 0;
            pixelData[i][1] = 0;
            pixelData[i][2] = 0;
        }

        isActive = false;
        isWaiting = false;
    }

    public void updateDrawValues() {
        isLargeMode = addressMap.loadBit(AddressMap.ADDRESS_LCDC, 2) == 1;
        isObjectEnabled = addressMap.loadBit(AddressMap.ADDRESS_LCDC, 1) == 1;
    }

    public void advanceOAMState() {
        if(oamCounter % 2 == 0) {
            oamScan();
        }

        oamCounter++;
    }

    public void advanceDrawState() {
        if(drawCounter == 0) {
            fetchTileID();

            drawCounter++;
        }
        else if(drawCounter == 1) {
            drawCounter++;
        }
        else if(drawCounter == 2) {
            fetchLowTileData();

            drawCounter++;
        }
        else if(drawCounter == 3) {
            drawCounter++;
        }
        else if(drawCounter == 4) {
            fetchHighTileData();

            drawCounter++;
        }
        else if(drawCounter == 5) {
            pushPixelSet();
        }
    }

    public void fetchTileID() {
        tileID = getObjectTileID();
    }

    public void fetchLowTileData() {
        tileDataLow = getObjectTileData(0);
    }

    public void fetchHighTileData() {
        tileDataHigh = getObjectTileData(1);
    }

    public void checkForSprite() {
        if(isWaiting || isActive) {
            // We are already processing something so don't look for anything else.
            return;
        }

        int c = lastDrawnObject + 1;
        for(int i = lastDrawnObject + 1; i < numVisibleObjects; i++) {
            int xObj = sortedOAMIndex[i][0];
    
            if(x == xObj) {
                break;
            }

            c++;
        }

        if(c < numVisibleObjects) {
            lastDrawnObject = c;
            tileObjectID = sortedOAMIndex[c][1];
            isWaiting = true;
        }
    }

    public void popPixel() {
        poppedPixel[0] = pixelData[0][0];
        poppedPixel[1] = pixelData[0][1];
        poppedPixel[2] = pixelData[0][2];

        for(int i = 0; i < 7; i++) {
            pixelData[i][0] = pixelData[i + 1][0];
            pixelData[i][1] = pixelData[i + 1][1];
            pixelData[i][2] = pixelData[i + 1][2];
        }

        pixelData[7][0] = 0;
        pixelData[7][1] = 0;
        pixelData[7][2] = 0;
    }

    public void pushPixelSet() {
        // Push 8 pixels to the Object FIFO.
        int tileMapAddressBase = 0xFE00;
        int tileMapAddress = tileMapAddressBase + (tileObjectID * 4);
        int objectAttributeByte3 = Byte.toUnsignedInt(addressMap.loadByte(tileMapAddress + 3));
        int palette = (objectAttributeByte3 >>> 4) & 0b1;
        int priority = (objectAttributeByte3 >>> 7) & 0b1;
        int bit3_5 = (objectAttributeByte3 >>> 5) & 0b1;
        boolean flipX = bit3_5 == 1;

        for(int i = 0; i < 8; i++) {
            // If this pixel is already visible, don't allow subsequent pixel to replace it.
            if(pixelData[i][0] != 0) {
                continue;
            }

            int bitNum;
            if(flipX) {
                bitNum = i;
            }
            else {
                bitNum = 7 - i;
            }
            int highBit = (tileDataHigh >>> bitNum) & 0b1;
            int lowBit = (tileDataLow >>> bitNum) & 0b1;
            int colorID = (highBit << 1) | lowBit;

            pixelData[i][0] = colorID;
            pixelData[i][1] = priority;
            pixelData[i][2] = palette;
        }

        drawCounter = 0;
        isActive = false;
    }

    public byte getObjectTileID() {
        int tileMapAddressBase = 0xFE00;
        int tileMapAddress = tileMapAddressBase + (tileObjectID * 4);

        byte tileID = addressMap.loadByte(tileMapAddress + 2);
        if(isLargeMode) {
            tileID &= 0b11111110;
        }

        return tileID;
    }

    public int getObjectTileData(int lowHighOffset) {
        int tileMapAddressBase = 0xFE00;
        int tileMapAddress = tileMapAddressBase + (tileObjectID * 4);
        int yObj = Byte.toUnsignedInt(addressMap.loadByte(tileMapAddress)) - 16;
        int objectAttributeByte3 = Byte.toUnsignedInt(addressMap.loadByte(tileMapAddress + 3));
        int bit3_6 = (objectAttributeByte3 >>> 6) & 0b1;
        boolean flipY = bit3_6 == 1;

        int tileDataAddressBase = 0x8000;
        int tileDataAddressOffset = Byte.toUnsignedInt(tileID);

        // We need to offset the tileDataAddress based on which line of the tile we need.
        // Note that each line has two bytes (low byte then high byte).
        int tileLineOffset;
        if(isLargeMode) {
            if(flipY) {
                tileLineOffset = 15 - ((y - yObj) % 16);
            }
            else {
                tileLineOffset = (y - yObj) % 16;
            }
        }
        else {
            if(flipY) {
                tileLineOffset = 7 - ((y - yObj) % 8);
            }
            else {
                tileLineOffset = (y - yObj) % 8;
            }
        }

        int tileDataAddress = tileDataAddressBase + (tileDataAddressOffset * 16) + (tileLineOffset * 2) + lowHighOffset;

        return Byte.toUnsignedInt(addressMap.loadByte(tileDataAddress));
    }

    public void oamScan() {
        // Search OAM for up to 10 objects that will be visible on the current line.
        if(numVisibleObjects == 10) {
            return;
        }

        int oamAddressBase = 0xFE00;
        int oamAddress = oamAddressBase + (objectID * 4);
        int yObj = Byte.toUnsignedInt(addressMap.loadByte(oamAddress)) - 16;

        // A visible object has y within [o.y, o.y + height].
        int height = isLargeMode ? 16 : 8;
        if(y >= yObj && y < yObj + height) {
            // Insert this OAM entry into the array while keeping it sorted for priority.
            int xObj = Byte.toUnsignedInt(addressMap.loadByte(oamAddress + 1)) - 8;
            
            int newEntryX = xObj;
            int newEntryID = objectID;

            // Find proper location of the new element.
            int c = 0;
            for(int i = 0; i < numVisibleObjects; i++) {
                // The new entry always has the highest OAM index seen so far, so we only need to check x.
                if(newEntryX < sortedOAMIndex[i][0]) {
                    break;
                }
                c++;
            }

            // Insert the new element here and shift everything else to the right, lowering their priority.
            for(int i = numVisibleObjects; i > c; i--) {
                sortedOAMIndex[i][0] = sortedOAMIndex[i - 1][0];
                sortedOAMIndex[i][1] = sortedOAMIndex[i - 1][1];
            }
            sortedOAMIndex[c][0] = newEntryX;
            sortedOAMIndex[c][1] = newEntryID;

            numVisibleObjects++;
        }

        objectID++;
    }
}
