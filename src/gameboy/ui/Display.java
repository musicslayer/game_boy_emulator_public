package gameboy.ui;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;

import javax.swing.JFrame;

import gameboy.data.ImageConsumer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.visual.Screen;

public class Display implements ImageConsumer {
    public final int PIXEL_WIDTH = 3;
    public final int PIXEL_HEIGHT = 3;

    Screen screen;
    JFrame frame;
    
    Canvas canvas;
    Graphics gCanvas;
    BufferedImage bufferedImage;
    WritableRaster raster;
    int pixels[];

    int[][] imageData;

    // Store all possible colors of a Game Boy pixel.
    // Each value is 0xRRGGBB.
    int[] colors = new int[] {
        0xFFFFFF, // White
        0xB2B2B2, // Light Gray
        0x666666, // Dark Gray
        0x000000, // Black
        0xFFDDDD, // White With Red Tint (Debug Only Color)
        0xD29292, // Light Gray With Red Tint (Debug Only Color)
        0x864646, // Dark Gray With Red Tint (Debug Only Color)
        0x200000 // Black With Red Tint (Debug Only Color)
    };

    public Display(Screen screen, JFrame frame) {
        this.screen = screen;
        this.frame = frame;

        this.canvas = new Canvas() {
            @Override
            public Dimension getPreferredSize() {
                // This is only called once, so no need to cache.
                return new Dimension(getScaledWidth(), getScaledHeight());
            }
        };

        // The canvas needs the listeners or else it will block the frame from firing them.
        for(KeyListener kl : frame.getKeyListeners()) {
            canvas.addKeyListener(kl);
        }

        frame.add(canvas);
        frame.pack();
        frame.setVisible(true);

        gCanvas = canvas.getGraphics();

        bufferedImage = new BufferedImage(getScaledWidth(), getScaledHeight(), BufferedImage.TYPE_INT_RGB);
        DataBufferInt dataBuffer = ((DataBufferInt)bufferedImage.getRaster().getDataBuffer());
        pixels = dataBuffer.getData();

        WritableRaster.createWritableRaster(bufferedImage.getSampleModel(), dataBuffer, null);
        
        imageData = new int[screen.getHeight()][screen.getWidth()];

        addToImageProducer(screen);
    }

    public void attachClock(HybridClock hybridClock) {
        hybridClock.addFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                // Copy the screen data now before it can be altered.
                // The copy will be an int[] and have scaling applied so it can be drawn directly.
                int W = getScaledWidth();
                int M = W * getScaledHeight();

                for(int i = 0; i < M; i++) {
                    int xc = i % W;
                    int yc = i / W;

                    pixels[i] = colors[imageData[yc / PIXEL_WIDTH][xc / PIXEL_WIDTH]];
                }
            }
        });

        hybridClock.addAsynchronousFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                gCanvas.drawImage(bufferedImage, 0, 0, null);
            }
        });
    }

    public int getScaledWidth() {
        // Match the Game Boy screen but scale it.
        return screen.getWidth() * PIXEL_WIDTH;
    }

    public int getScaledHeight() {
        // Match the Game Boy screen but scale it.
        return screen.getHeight() * PIXEL_HEIGHT;
    }

    // ImageConsumer
    @Override
    public void consumeImage(int x, int y, int colorIndex) {
        imageData[y][x] = colorIndex;
    }

    @Override
    public void consumeDebugImage(int[][] imageData) {
        this.imageData = imageData;
    }
}