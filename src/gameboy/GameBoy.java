package gameboy;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

import gameboy.emulator.audio.Mixer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.clock.TimingInfo;
import gameboy.emulator.debug.DebugScreenTileData;
import gameboy.emulator.debug.DebugScreenWindow;
import gameboy.emulator.debug.DebugScreenBackground;
import gameboy.emulator.debug.DebugScreenOAM;
import gameboy.emulator.debug.DebugScreenObject;
import gameboy.emulator.interaction.Controller;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.processor.DMAProcessor;
import gameboy.emulator.processor.Processor;
import gameboy.emulator.processor.SerialProcessor;
import gameboy.emulator.software.BIOS;
import gameboy.emulator.software.Cartridge;
import gameboy.emulator.timer.DIVAPUTimer;
import gameboy.emulator.timer.DIVTimer;
import gameboy.emulator.visual.Screen;
import gameboy.log.ProcessorLogger;
import gameboy.ui.Display;
import gameboy.ui.Input;
import gameboy.ui.Speaker;

// The class for the overall Game Boy.
public class GameBoy {
    public boolean isPoweredOn = false;

    CloseableResourceManager closeableResourceManager;
    FrameManager frameManager;

    BIOS bios;
    Cartridge cartridge;

    TimingInfo timingInfo;
    HybridClock hybridClock;
    AddressMap addressMap;
    JFrame frame;

    Processor processor;

    // Load a bios into the Game Boy.
    public void loadBIOS(BIOS bios) {
        this.bios = bios;
    }

    // Load a cartridge into the Game Boy.
    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
    }

    public void initialize() {
        if(bios == null) {
            throw new IllegalStateException("Game Boy cannot power on with a null BIOS.");
        }

        if(cartridge == null) {
            throw new IllegalStateException("Game Boy cannot power on with a null cartridge.");
        }

        closeableResourceManager = new CloseableResourceManager();
        frameManager = new FrameManager();

        // Use the Game Boy's frequency and frame rate.
        timingInfo = new TimingInfo(4194304L, 59.7275);
        hybridClock = new HybridClock(timingInfo);
        addressMap = new AddressMap(closeableResourceManager, bios, cartridge);
        frame = createMainJFrame(cartridge.getTitle());

        addComponents();
        //addLogging(); // Uncomment to add logging - NOTE: This drastically slows performance down.
        //addDebugDisplay(); // Uncomment to also display debug screens (complete tile maps, OAM viewer, etc...)
    }

    public void addComponents() {
        // Internal Components
        processor = new Processor(addressMap);
        processor.attachClock(hybridClock);

        DMAProcessor dmaProcessor = new DMAProcessor(addressMap);
        dmaProcessor.attachClock(hybridClock);

        SerialProcessor serialProcessor = new SerialProcessor(addressMap);
        serialProcessor.attachClock(hybridClock);

        DIVTimer divTimer = new DIVTimer(addressMap);
        divTimer.attachClock(hybridClock);

        DIVAPUTimer divapuTimer = new DIVAPUTimer();
        divapuTimer.attachDIVTimer(divTimer);

        Controller controller = new Controller(addressMap);
        controller.attachClock(hybridClock);

        Screen screen = new Screen(addressMap);
        screen.attachClock(hybridClock);

        Mixer mixer = new Mixer(addressMap);
        mixer.attachClock(hybridClock);
        mixer.attachDIVAPUTimer(divapuTimer);

        // User Interface
        Input input = new Input(controller);
        frame.addKeyListener(input);
        
        Display display = new Display(screen, frame);
        display.attachClock(hybridClock);
        
        Speaker speaker = new Speaker(mixer, timingInfo, 44100);
        speaker.attachClock(hybridClock);
    }

    public void addLogging() {
        ProcessorLogger processorLogger = new ProcessorLogger(closeableResourceManager, addressMap, processor, timingInfo);
        processorLogger.attachClock(hybridClock);
    }

    public void addDebugDisplay() {
        DebugScreenTileData debugScreenTileData = new DebugScreenTileData(addressMap);
        debugScreenTileData.attachClock(hybridClock);
        JFrame debugFrameTileData = createJFrame("Tile Data");
        Display debugDisplayTileData = new Display(debugScreenTileData, debugFrameTileData);
        debugDisplayTileData.attachClock(hybridClock);

        DebugScreenBackground debugScreenBackground = new DebugScreenBackground(addressMap);
        debugScreenBackground.attachClock(hybridClock);
        JFrame debugFrameBackground = createJFrame("Background");
        Display debugDisplayBackground = new Display(debugScreenBackground, debugFrameBackground);
        debugDisplayBackground.attachClock(hybridClock);

        DebugScreenWindow debugScreenWindow = new DebugScreenWindow(addressMap);
        debugScreenWindow.attachClock(hybridClock);
        JFrame debugFrameWindow = createJFrame("Window");
        Display debugDisplayWindow = new Display(debugScreenWindow, debugFrameWindow);
        debugDisplayWindow.attachClock(hybridClock);

        DebugScreenObject debugScreenObject = new DebugScreenObject(addressMap);
        debugScreenObject.attachClock(hybridClock);
        JFrame debugFrameObject = createJFrame("Object");
        Display debugDisplayObject = new Display(debugScreenObject, debugFrameObject);
        debugDisplayObject.attachClock(hybridClock);

        DebugScreenOAM debugScreenOAM = new DebugScreenOAM(addressMap);
        debugScreenOAM.attachClock(hybridClock);
        JFrame debugFrameOAM = createJFrame("OAM");
        Display debugDisplayOAM = new Display(debugScreenOAM, debugFrameOAM);
        debugDisplayOAM.attachClock(hybridClock);
    }

    public JFrame createMainJFrame(String title) {
        // Creates a JFrame that will stop the emulator when closed.
        JFrame frame = createJFrame(title);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                powerOff();
            }
        });
        return frame;
    }

    public JFrame createJFrame(String title) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.setTitle(title);

        frameManager.addFrame(frame);
        
        return frame;
    }

    // Turn on the Game Boy.
    public void powerOn() {
        hybridClock.start();
        isPoweredOn = true;
    }

    // Turn off the Game Boy.
    public void powerOff() {
        hybridClock.stop();

        closeableResourceManager.closeAll();
        frameManager.disposeAll();

        isPoweredOn = false;
    }

    public static class FrameManager {
        ArrayList<JFrame> frames = new ArrayList<>();

        public void addFrame(JFrame frame) {
            frames.add(frame);
        }

        public void disposeAll() {
            for(JFrame frame : frames) {
                try {
                    frame.dispose();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class CloseableResourceManager {
        ArrayList<Closeable> closeables = new ArrayList<>();

        public void addCloseable(Closeable closeable) {
            closeables.add(closeable);
        }

        public void removeCloseable(Closeable closeable) {
            closeables.remove(closeable);
        }

        public void closeAll() {
            for(Closeable closeable : closeables) {
                try {
                    closeable.close();
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
