package gameboy.emulator.visual;

import gameboy.emulator.memory.AddressMap;

public class PixelFetcher {
    public final static int STATE_HBLANK = 0;
    public final static int STATE_VBLANK = 1;
    public final static int STATE_OAM_SCAN = 2;
    public final static int STATE_DRAW = 3;
    
    AddressMap addressMap;
    Screen screen;

    BackgroundPixelFIFO backgroundPixelFIFO;
    ObjectPixelFIFO objectPixelFIFO;

    int x;
    int y;

    // Number of dots processed in the current frame.
    int frameDotsProcessed = 0;

    // Current mode of rendering.
    int state;

    public PixelFetcher(AddressMap addressMap, Screen screen) {
        this.addressMap = addressMap;
        this.screen = screen;
        this.backgroundPixelFIFO = new BackgroundPixelFIFO(addressMap);
        this.objectPixelFIFO = new ObjectPixelFIFO(addressMap);
    }

    public void setX(int x) {
        this.x = x;
        backgroundPixelFIFO.x = x;
        objectPixelFIFO.x = x;
    }

    public void setY(int y) {
        this.y = y;
        backgroundPixelFIFO.y = y;
        objectPixelFIFO.y = y;
    }

    public void reset() {
        frameDotsProcessed = 0;
        setX(-8);
        setY(0);
    }

    public void onTick() {
        if(frameDotsProcessed == 0) {
            backgroundPixelFIFO.onFrameStart();
        }
        if(frameDotsProcessed % 456 == 0) {
            setX(-8);
            backgroundPixelFIFO.onLineStart();
            objectPixelFIFO.onLineStart();
        }

        if(state == STATE_OAM_SCAN) {
            objectPixelFIFO.advanceOAMState();
        }
        else if(state == STATE_DRAW) {
            backgroundPixelFIFO.updateDrawValues();
            objectPixelFIFO.updateDrawValues();

            // Check to see if this x-position has an object sprite that starts here.
            objectPixelFIFO.checkForSprite();

            if(backgroundPixelFIFO.canYield && objectPixelFIFO.isWaiting) {
                backgroundPixelFIFO.isActive = false;
                objectPixelFIFO.isActive = true;
                objectPixelFIFO.isWaiting = false;
            }

            if(backgroundPixelFIFO.isActive) {
                if(!objectPixelFIFO.isWaiting) {
                    pushPixel();
                }

                backgroundPixelFIFO.advanceDrawState();
            }
            else {
                objectPixelFIFO.advanceDrawState();

                if(!objectPixelFIFO.isActive) {
                    backgroundPixelFIFO.isActive = true;
                }
            }

            if(x == 160) {
                // Once we have drawn the last pixel in a line, Mode 3 is finished.
                state = STATE_HBLANK;
                screen.setSTATMode(0);
            }
        }
        else if(state == STATE_HBLANK) {
            // Do nothing
        }
        else if(state == STATE_VBLANK) {
            // Do nothing
        }

        frameDotsProcessed++;
        frameDotsProcessed %= 70224;
        setY(frameDotsProcessed / 456);

        processFrameDot();
    }

    public void processFrameDot() {
        // Set state based on the current frame dot. Because of hardware quirks, these will not all be in sync.

        // PixelFetcher mode: Look at frame count to figure out what mode we should enter.
        // Note that transitions from Mode 3 to Mode 0 are not handled here since they do not occur at a fixed frame count.
        if(y < 144) {
            if(frameDotsProcessed % 456 == 0) {
                state = STATE_OAM_SCAN;
            }
            else if(frameDotsProcessed % 456 == 80) {
                state = STATE_DRAW;

                // This check must be done at the start of Mode 3.
                backgroundPixelFIFO.checkWindowFrame();
            }
        }
        else if(y == 144) {
            if(frameDotsProcessed % 456 == 0) {
                state = STATE_VBLANK;
            }
        }

        // STAT mode: The same as PixelFetcher mode except for line 0.
        // Line 0 spends 4 T-Cycles as Mode 0 before transitioning to Mode 2 as expected.
        if(y < 144) {
            if(y == 0) {
                if(frameDotsProcessed % 456 == 0) {
                    screen.setSTATMode(0);
                }
                else if(frameDotsProcessed % 456 == 4) {
                    screen.setSTATMode(2);
                }
                else if(frameDotsProcessed % 456 == 80) {
                    screen.setSTATMode(3);
                }
            }
            else {
                if(frameDotsProcessed % 456 == 0) {
                    screen.setSTATMode(2);
                }
                else if(frameDotsProcessed % 456 == 80) {
                    screen.setSTATMode(3);
                }
            }
        }
        else if(y == 144) {
            if(frameDotsProcessed % 456 == 8) {
                screen.setSTATMode(1);
            }
        }

        // LY: LY mostly follows pixelFetcher.y except for line 153.
        // Line 153 spends 4 T-Cycles as LY = 153 before transitioning to LY = 0 early.
        if(y == 153) {
            if(frameDotsProcessed % 456 == 0) {
                screen.setLY(y);
            }
            else if(frameDotsProcessed % 456 == 4) {
                screen.setLY(0);
            }
        }
        else {
            if(frameDotsProcessed % 456 == 0) {
                screen.setLY(y);
            }
        }

        // Hardware Quirk: On line 144, there is a brief opportunity for an OAM-STAT interrupt.
        if(y == 144) {
            if(frameDotsProcessed % 456 == 0) {
                screen.setLine144Allowed(true);
            }
            else if(frameDotsProcessed % 456 == 8) {
                screen.setLine144Allowed(false);
            }
        }

        // Process any screen actions when the frame ends.
        if(frameDotsProcessed == 0) {
            screen.onFrameEnd();
        }
    }

    public void pushPixel() {
        // Push a pixel to the screen if there is one available to push.
        if(backgroundPixelFIFO.pixelIndex > 0) {
            // Pop background and object pixels and merge them.
            backgroundPixelFIFO.popPixel();
            objectPixelFIFO.popPixel();

            if(backgroundPixelFIFO.pixelsToDiscard > 0) {
                // This pixel wouldn't be visible because of horizontal scrolling, so just discard it.
                backgroundPixelFIFO.pixelsToDiscard--;
                return;
            }

            if(x < 0) {
                // Do not draw offscreen pixels.
                setX(x + 1);
                return;
            }

            boolean isBackgroundVisible = backgroundPixelFIFO.isBackgroundEnabled;
            boolean isObjectVisible = objectPixelFIFO.isObjectEnabled && objectPixelFIFO.poppedPixel[0] != 0;
            if(isBackgroundVisible && isObjectVisible) {
                // If the background and object are both visible, decide which one takes priority.
                if(objectPixelFIFO.poppedPixel[1] == 1 && backgroundPixelFIFO.poppedPixel[0] != 0) {
                    isObjectVisible = false;
                }
            }

            // Retrieve the appropriate color based on the source and colorID of the pixel.
            int colorIndex;
            if(isObjectVisible) {
                int paletteAddress = objectPixelFIFO.poppedPixel[2] == 0 ? AddressMap.ADDRESS_OBP0 : AddressMap.ADDRESS_OBP1;
                int paletteData = Byte.toUnsignedInt(addressMap.loadByte(paletteAddress));
                int shift = objectPixelFIFO.poppedPixel[0] * 2;
                colorIndex = (paletteData >>> shift) & 0b11;
            }
            else if(isBackgroundVisible) {
                int paletteAddress = AddressMap.ADDRESS_BGP;
                int paletteData = Byte.toUnsignedInt(addressMap.loadByte(paletteAddress));
                int shift = backgroundPixelFIFO.poppedPixel[0] * 2;
                colorIndex = (paletteData >>> shift) & 0b11;
            }
            else {
                // If neither source can produce a visible pixel, just draw a white pixel.
                colorIndex = 0;
            }

            screen.onPixel(x, y, colorIndex);

            setX(x + 1);
        }
    }
}
