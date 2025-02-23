package gameboy.emulator.debug;

import gameboy.emulator.memory.AddressMap;

public class DebugTile {
    int[] tileData;
    boolean isLarge;

    public static DebugTile fromAddress(int address, AddressMap addressMap, boolean isLarge) {
        int height = isLarge ? 16 : 8;

        DebugTile tile = new DebugTile();
        tile.tileData = new int[height * 2];
        tile.isLarge = isLarge;

        for(int i = 0; i < height * 2; i++) {
            tile.tileData[i] = Byte.toUnsignedInt(addressMap.loadByte(address + i));
        }
        return tile;
    }

    public void draw(int[][] imageData, int screenWidth, int screenHeight, int tileX, int tileY, int[] colorIndexMap, boolean flipX, boolean flipY) {
        int height = isLarge ? 16 : 8;

        int[] rangeX;
        int[] rangeY;
        if(!flipX && !flipY) {
            rangeX = createPlusArray(8);
            rangeY = createPlusArray(height);
        }
        else if(flipX && !flipY) {
            rangeX = createMinusArray(8);
            rangeY = createPlusArray(height);
        }
        else if(!flipX && flipY) {
            rangeX = createPlusArray(8);
            rangeY = createMinusArray(height);
        }
        else {
            rangeX = createMinusArray(8);
            rangeY = createMinusArray(height);
        }

        // Each value of j is a line of the 8 pixel x [8/16] pixel tile
        for(int j : rangeY) {
            int y = tileY + j;
            if(y < 0 || y >= screenHeight) {
                continue;
            }

            int dataA = tileData[j * 2]; // LSB
            int dataB = tileData[(j * 2) + 1]; // MSB
            
            int c = 0;
            for(int i : rangeX) {
                int x = tileX + i;
                if(x < 0 || x >= screenWidth) {
                    continue;
                }

                int bitNum = 7 - c++;
                int dataA_n = (dataA & (0b00000001 << bitNum)) == (0b00000001 << bitNum) ? 1 : 0;
                int dataB_n = (dataB & (0b00000001 << bitNum)) == (0b00000001 << bitNum) ? 1 : 0;
                int dataBA = (dataB_n * 2) + dataA_n;

                // Draw a pixel.
                int colorIndex = colorIndexMap[dataBA];
                imageData[y][x] = colorIndex;
            }
        }
    }

    public static int[] createPlusArray(int n) {
        int[] array = new int[n];
        for (int i = 0; i < n; i++) {
            array[i] = i;
        }
        return array;
    }

    public static int[] createMinusArray(int n) {
        int[] array = new int[n];
        for (int i = 0; i < n; i++) {
            array[i] = n - 1 - i;
        }
        return array;
    }
}
