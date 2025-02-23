package gameboy.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import gameboy.GameBoy.CloseableResourceManager;
import gameboy.data.StateConsumer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.clock.TimingInfo;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.processor.Decoder;
import gameboy.emulator.processor.Processor;

public class ProcessorLogger implements StateConsumer {
    final static int BUFFER_SIZE = 1048576 * 200; // 200 MB - At least as big as the max possible file.
    final static long MAX_WRITE_PER_FILE = 1000000L;
    final static int MAX_FILES = 5;

    final static String LOG_PATH = Paths.get("log", "ProcessorLogger").toString();
    final static String LOG_PREFIX = "ProcessorLogger_";

    CloseableResourceManager closeableResourceManager;
    AddressMap addressMap;

    int cyclesPerFrame;

    BufferedWriter bufferedWriter;

    long writeCounter = 0;
    int currentLogFileNum = 0;

    int stateIndex = 0;
    int copyIndex;
    int[][] stateData;
    int[][] copyData;

    public boolean isOn = false;

    public ProcessorLogger(CloseableResourceManager closeableResourceManager, AddressMap addressMap, Processor processor, TimingInfo timingInfo) {
        this.closeableResourceManager = closeableResourceManager;
        this.addressMap = addressMap;

        // This is the maximum number of times we may log data during a frame,
        // however not every processor tick will result in something being logged.
        this.cyclesPerFrame = (int)(timingInfo.frequency / timingInfo.framesPerSecond);

        this.stateData = new int[cyclesPerFrame][14];

        addToStateProducer(processor.alu);
    }

    public void init() {
        File folder = new File(LOG_PATH);
        if(folder.exists()) {
            // Delete existing log files and start fresh every time.
            // Any other folders/files in the log folder will be preserved, but there shouldn't be anything else in there.
            File[] allContents = folder.listFiles();
            if(allContents != null) {
                for(File file : allContents) {
                    if(file.getName().startsWith(LOG_PREFIX)) {
                        file.delete();
                    }
                }
            }
            folder.delete();
        }
        folder.mkdirs();

        swapFile();
    }

    public void attachClock(HybridClock hybridClock) {
        isOn = true;
        init();

        hybridClock.addFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                // Copy the state data now before it can be altered.
                copyData = new int[stateData.length][14];
                for(int i = 0; i < stateData.length; i++) {
                    int lengthI = stateData[i].length;
                    copyData[i] = new int[lengthI];
                    System.arraycopy(stateData[i], 0, copyData[i], 0, lengthI);
                }

                copyIndex = stateIndex;
                stateIndex = 0;

                // For now, logging has to be synchronous, which means it will be really slow.
                logState();
            }
        });

        hybridClock.addAsynchronousFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                //logState();
            }
        });
    }

    public void logState() {
        if(!isOn) {
            return;
        }

        for(int i = 0; i < copyIndex; i++) {
            int address = copyData[i][0];
            int opcode = copyData[i][1];
            int a = copyData[i][2];
            int f = copyData[i][3];
            int b = copyData[i][4];
            int c = copyData[i][5];
            int d = copyData[i][6];
            int e = copyData[i][7];
            int h = copyData[i][8];
            int l = copyData[i][9];
            int sp = copyData[i][10];
            int pc = copyData[i][11];
            int flagHalt = copyData[i][12];
            int flagIME = copyData[i][13];

            int flagZ = (f >>> 7) & 0b1;
            int flagN = (f >>> 6) & 0b1;
            int flagH = (f >>> 5) & 0b1;
            int flagC = (f >>> 4) & 0b1;

            int pc0 = Byte.toUnsignedInt(addressMap.loadByte(pc));
            int pc1 = Byte.toUnsignedInt(addressMap.loadByte(pc + 1));
            int pc2 = Byte.toUnsignedInt(addressMap.loadByte(pc + 2));
            int pc3 = Byte.toUnsignedInt(addressMap.loadByte(pc + 3));

            int reg_ie = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_IE));
            int reg_if = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_IF));

            String instruction = Decoder.decodeOpcode(opcode);

            String logLine = String.format("%s %s A:%s F:%s B:%s C:%s D:%s E:%s H:%s L:%s SP:%s PC:%s PCMEM:%s,%s,%s,%s     FLAGS:%s%s%s%s  IE:%s  IF:%s  HALT:%s  IME:%s",
                toHex4(address), pad(instruction, 15),
                toHex2(a), toHex2(f), toHex2(b), toHex2(c), toHex2(d), toHex2(e), toHex2(h), toHex2(l), toHex4(sp), toHex4(pc),
                toHex2(pc0), toHex2(pc1), toHex2(pc2), toHex2(pc3),
                flagZ == 1 ? "Z" : "_", flagN == 1 ? "N" : "_", flagH == 1 ? "H" : "_", flagC == 1 ? "C" : "_",
                toBinary(reg_ie), toBinary(reg_if), flagHalt == 1 ? "true" : "false", flagIME == 1 ? "true" : "false");

            try {
                bufferedWriter.append(logLine);
                bufferedWriter.newLine();
                bufferedWriter.flush();

                writeCounter++;
                if(writeCounter == MAX_WRITE_PER_FILE) {
                    writeCounter = 0;
                    bufferedWriter.close();
                    closeableResourceManager.removeCloseable(bufferedWriter);

                    // Swap to a new file.
                    swapFile();
                }
            }
            catch(IOException ee) {
                ee.printStackTrace();

                // Give up on logging.
                isOn = false;
                return;
            }
        }
    }

    public void swapFile() {
        try {
            // If we have already reached the max allowed files, delete the oldest one.
            if(currentLogFileNum >= MAX_FILES) {
                Path pathToDelete = Paths.get(LOG_PATH, LOG_PREFIX + (currentLogFileNum - MAX_FILES) + ".txt");
                File fileToDelete = new File(pathToDelete.toString());
                fileToDelete.delete();
            }

            Path path = Paths.get(LOG_PATH, LOG_PREFIX + currentLogFileNum + ".txt");
            File file = new File(path.toString());
            file.createNewFile();
            file.setWritable(true);

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            bufferedWriter = new BufferedWriter(fw, BUFFER_SIZE);
            closeableResourceManager.addCloseable(bufferedWriter);

            currentLogFileNum++;
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // Utility Methods
    public static String toHex2(int value) {
        return String.format("%02X", value);
    }

    public static String toHex4(int value) {
        return String.format("%04X", value);
    }

    public static String toBinary(int value) {
        String b = Integer.toBinaryString(value);
        while(b.length() < 8) {
            b = "0" + b;
        }
        return b;
    }

    public static String pad(String s, int length) {
        while(s.length() < length) {
            s += " ";
        }
        return s;
    }

    // StateConsumer
    @Override
    public void consumeState(int address, int opcode, int a, int f, int b, int c, int d, int e, int h, int l, int sp, int pc, int flagHalt, int flagIME) {
        if(!isOn) {
            return;
        }

        stateData[stateIndex][0] = address;
        stateData[stateIndex][1] = opcode;
        stateData[stateIndex][2] = a;
        stateData[stateIndex][3] = f;
        stateData[stateIndex][4] = b;
        stateData[stateIndex][5] = c;
        stateData[stateIndex][6] = d;
        stateData[stateIndex][7] = e;
        stateData[stateIndex][8] = h;
        stateData[stateIndex][9] = l;
        stateData[stateIndex][10] = sp;
        stateData[stateIndex][11] = pc;
        stateData[stateIndex][12] = flagHalt;
        stateData[stateIndex][13] = flagIME;

        stateIndex++;
    }
}
