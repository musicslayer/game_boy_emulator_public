package gameboy.emulator.debug;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.visual.Screen;

// This cycles through the 4 different modes and only generates white pixels.
public class DebugPixelFetcher {
    public final static int STATE_HBLANK = 0;
    public final static int STATE_VBLANK = 1;
    public final static int STATE_OAM_SCAN = 2;
    public final static int STATE_DRAW = 3;

    AddressMap addressMap;
    Screen screen;

    int state;
    public int x;
    public int y;

    // Number of dots processed in the current frame.
    int frameDotsProcessed = 0;

    // Number of cycles to wait until we can proceed to the next state.
    int oamCounter = 0;
    int drawCounter = 0;

    public DebugPixelFetcher(AddressMap addressMap, Screen screen) {
        this.addressMap = addressMap;
        this.screen = screen;
    }

    public void reset() {
        frameDotsProcessed = 0;
        x = 0;
        y = 0;
    }

    public void onTick() {
        if(state == STATE_OAM_SCAN) {
            // Do nothing
        }
        else if(state == STATE_DRAW) {
            screen.onPixel(x, y, 0);

            x++;

            // Once we have drawn the last pixel in a line, Mode 3 is finished.
            if(x == 160) {
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
        y = frameDotsProcessed / 456;

        processFrameDot();
    }

    public void processFrameDot() {
        // Set state based on the current frame dot. Because of hardware quirks, these will not all be in sync.

        // PixelFetcher mode: Look at frame count to figure out what mode we should enter.
        // Note that transitions from Mode 3 to Mode 0 are not handled here since they do not occur at a fixed frame count.
        if(y < 144) {
            if(frameDotsProcessed % 456 == 0) {
                state = STATE_OAM_SCAN;
                oamCounter = 0;
            }
            else if(frameDotsProcessed % 456 == 80) {
                state = STATE_DRAW;
                x = 0;
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
            if(frameDotsProcessed % 456 == 0) {
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
}
