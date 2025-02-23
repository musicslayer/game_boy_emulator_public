package gameboy.emulator.visual;

import gameboy.emulator.memory.AddressMap;

public class BackgroundPixelFIFO {
    AddressMap addressMap;

    // Represents the Pixel FIFO.
    int pixelIndex;
    int[][] pixelData = new int[8][1];
    int[] poppedPixel = new int[1]; // 0 -> colorID

    int scrollX;
    int scrollY;
    int WLY;
    int pixelsToDiscard;

    boolean isWindowFrame;
    boolean isWindowLine;
    boolean isWindowEnabled;
    boolean isBackgroundEnabled;

    byte tileID;
    int tileDataLow;
    int tileDataHigh;
    int tileFetchX;
    boolean isTileSourceWindow;

    int drawCounterOld = 0;
    int drawCounter = 0;

    boolean isActive;
    boolean canYield;

    int x;
    int y;

    public BackgroundPixelFIFO(AddressMap addressMap) {
        this.addressMap = addressMap;
    }

    public void onFrameStart() {
        WLY = 0;
        isWindowFrame = false;
    }

    public void onLineStart() {
        pixelIndex = 0;
        scrollX = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_SCX));
        pixelsToDiscard = scrollX % 8;
        isWindowLine = false;
        tileFetchX = -8;
        isTileSourceWindow = false;
        drawCounter = 0;
        
        // If the prior line enabled the window, update the internal Window Y position.
        if(isWindowEnabled) {
            isWindowEnabled = false;
            WLY++;
        }

        isActive = true;
        canYield = false;
    }

    public void updateDrawValues() {
        isBackgroundEnabled = addressMap.loadBit(AddressMap.ADDRESS_LCDC, 0) == 1;

        checkWindowLine();
        checkWindowEnabled();
    }

    public void advanceDrawState() {
        if(drawCounter == 0) {
            fetchTileID();

            canYield = false;

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
            drawCounter++;

            pushPixelSet();

            canYield = true;
        }
        else if(drawCounter == 6) {
            pushPixelSet();
        }
    }

    public void popPixel() {
        poppedPixel[0] = pixelData[0][0];
        
        pixelIndex--;
        for(int i = 0; i < pixelIndex; i++) {
            pixelData[i][0] = pixelData[i + 1][0];
        }
    }

    public void pushPixelSet() {
        // Push 8 pixels to the FIFO. This can only be done if there is nothing already in the FIFO.
        if(pixelIndex == 0) {
            for(int i = 0; i < 8; i++) {
                int bitNum = 7 - i;

                int highBit = (tileDataHigh >>> bitNum) & 0b1;
                int lowBit = (tileDataLow >>> bitNum) & 0b1;
                int colorID = (highBit << 1) | lowBit;

                pixelData[pixelIndex][0] = colorID;

                pixelIndex++;
            }

            drawCounter = 0;
        }
    }

    public void fetchTileID() {
        // Update upper 5 bits of SCX and all of SCY here.
        scrollX = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_SCX)) & 0b11111000) | (scrollX & 0b00000111);
        scrollY = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_SCY));

        tileID = getTileID();
    }

    public void fetchLowTileData() {
        tileDataLow = getTileData(0);
    }

    public void fetchHighTileData() {
        tileDataHigh = getTileData(1);
    }

    public byte getTileID() {
        byte tileID;
        if(isTileSourceWindow) {
            tileID = getWindowTileID();
        }
        else {
            tileID = getBackgroundTileID();
        }

        tileFetchX += 8;

        return tileID;
    }

    public byte getBackgroundTileID() {
        // For the background, each map entry is a 1-byte address pointing to a tile's information in the tile data area.
        int tileMapAddressBase;
        if(addressMap.loadBit(AddressMap.ADDRESS_LCDC, 3) == 0) {
            tileMapAddressBase = 0x9800;
        }
        else {
            tileMapAddressBase = 0x9C00;
        }

        int effectiveTileFetchX = tileFetchX == -8 ? 0 : tileFetchX;
        
        int xTile = ((effectiveTileFetchX + scrollX) % 256) / 8;
        int yTile = ((y + scrollY) % 256) / 8;
        int offset = xTile + (32 * yTile);

        return addressMap.loadByte(tileMapAddressBase + offset);
    }

    public byte getWindowTileID() {
        int tileMapAddressBase;
        if(addressMap.loadBit(AddressMap.ADDRESS_LCDC, 6) == 0) {
            tileMapAddressBase = 0x9800;
        }
        else {
            tileMapAddressBase = 0x9C00;
        }

        int effectiveTileFetchX = tileFetchX == -8 ? 0 : tileFetchX;

        int xTile = effectiveTileFetchX / 8;
        int yTile = WLY / 8;
        int offset = xTile + (32 * yTile);

        return addressMap.loadByte(tileMapAddressBase + offset);
    }

    public int getTileData(int lowHighOffset) {
        int tileData;
        if(isTileSourceWindow) {
            tileData = getWindowTileData(lowHighOffset);
        }
        else {
            tileData = getBackgroundTileData(lowHighOffset);
        }

        return tileData;
    }

    public int getBackgroundTileData(int lowHighOffset) {
        int tileDataAddressBase;
        int tileDataMode;
        if(addressMap.loadBit(AddressMap.ADDRESS_LCDC, 4) == 0) {
            tileDataAddressBase = 0x9000;
            tileDataMode = 0;
        }
        else {
            tileDataAddressBase = 0x8000;
            tileDataMode = 1;
        }

        int tileDataAddressOffset;
        if(tileDataMode == 0) {
            tileDataAddressOffset = (int)(tileID);
        }
        else {
            tileDataAddressOffset = Byte.toUnsignedInt(tileID);
        }

        // We need to offset the tileDataAddress based on which line of the tile we need.
        // Note that each line has two bytes (low byte then high byte).
        int tileLineOffset = (y + scrollY) % 8;
        int tileDataAddress = tileDataAddressBase + (tileDataAddressOffset * 16) + (tileLineOffset * 2) + lowHighOffset;

        return Byte.toUnsignedInt(addressMap.loadByte(tileDataAddress));
    }

    public int getWindowTileData(int lowHighOffset) {
        int tileDataAddressBase;
        int tileDataMode;
        if(addressMap.loadBit(AddressMap.ADDRESS_LCDC, 4) == 0) {
            tileDataAddressBase = 0x9000;
            tileDataMode = 0;
        }
        else {
            tileDataAddressBase = 0x8000;
            tileDataMode = 1;
        }

        int tileDataAddressOffset;
        if(tileDataMode == 0) {
            tileDataAddressOffset = (int)(tileID);
        }
        else {
            tileDataAddressOffset = Byte.toUnsignedInt(tileID);
        }

        // We need to offset the tileDataAddress based on which line of the tile we need.
        // Note that each line has two bytes (low byte then high byte).
        int tileLineOffset = WLY % 8;
        int tileDataAddress = tileDataAddressBase + (tileDataAddressOffset * 16) + (tileLineOffset * 2) + lowHighOffset;

        return Byte.toUnsignedInt(addressMap.loadByte(tileDataAddress));
    }

    public void checkWindowFrame() {
        // Look at y to determine if the frame window condition is met.
        // Once this condition is met, it stays active for the rest of the frame.
        if(!isWindowFrame) {
            int windowY = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_WY));
            isWindowFrame = y == windowY;
        }
    }

    public void checkWindowLine() {
        // If the frame window condition was already met, now look at x to determine if the line window condition is met.
        // Once this condition is met, it stays active for the rest of the line.
        if(!isWindowLine && isWindowFrame) {
            int windowX = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_WX)) - 7;
            isWindowLine = x == windowX;
        }
    }

    public void checkWindowEnabled() {
        // If the frame and line conditions are already met, use LCDC bit 5 to determine if we should transition from background to window.
        // Note that this transition only occurs in this direction and stays active for the entire line.
        if(!isWindowEnabled && isWindowLine && isWindowFrame) {
            boolean isLCDCWindowOn = isBackgroundEnabled && addressMap.loadBit(AddressMap.ADDRESS_LCDC, 5) == 1;
            if(isLCDCWindowOn) {
                isWindowEnabled = true;
                isTileSourceWindow = true;

                // Reset draw state and clear out any existing background pixels in the queue.
                pixelIndex = 0;
                drawCounter = 0;
                tileFetchX = 0;
            }
        }
    }
}
