package gameboy.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import gameboy.data.SoundConsumer;
import gameboy.emulator.audio.Mixer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.clock.TimingInfo;

public class Speaker implements SoundConsumer {
    Mixer mixer;
    
    int samplesPerSecond;
    int samplesPerFrame;
    double downsampleRatio;

    SourceDataLine line;

    double sampleCount = 0;

    int bufferIndex = 0;
    int copyIndex;
    byte[] buffer;
    byte[] copyBuffer;

    public Speaker(Mixer mixer, TimingInfo timingInfo, int samplesPerSecond) {
        this.mixer = mixer;
        this.samplesPerSecond = samplesPerSecond;

        this.samplesPerFrame = (int)Math.ceil((double)samplesPerSecond / timingInfo.framesPerSecond);

        // Downsample based on the APU frequency and the desired output byte rate.
        long apuFrequency = timingInfo.frequency / 4;
        this.downsampleRatio = (double)apuFrequency / (double)samplesPerSecond;

        buffer = new byte[samplesPerFrame * 4];
        copyBuffer = new byte[samplesPerFrame * 4];

        this.line = createLine();

        addToSoundProducer(mixer);
    }

    public void attachClock(HybridClock hybridClock) {
        hybridClock.addFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                // Copy the sound data now before it can be altered.
                System.arraycopy(buffer, 0, copyBuffer, 0, bufferIndex);
                copyIndex = bufferIndex;
                bufferIndex = 0;
            }
        });

        hybridClock.addAsynchronousFrameCallback(new HybridClock.FrameCallback() {
            @Override
            public void onFrame() {
                line.write(copyBuffer, 0, copyIndex);
            }
        });
    }

    public SourceDataLine createLine() {
        try {
            // To ensure proper dynamic range, we use 16 bits per sample i.e. this class consumes shorts rather than bytes.
            AudioFormat format = new AudioFormat(samplesPerSecond, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine)AudioSystem.getLine(info);
            line.open(format);
            line.start();    
            return line;
        }
        catch(Exception e) {
            throw(new IllegalStateException(e));
        }
    }

    // SoundConsumer
    @Override
    public void consumeSound(int data) {
        // Each int has 4 bytes: 2 for left channel sound and 2 for right channel sound.

        // These quantities must be doubles to avoid integer rounding errors in the downsampling.
        sampleCount++;
        if(sampleCount >= downsampleRatio) {
            sampleCount -= downsampleRatio;

            // Interleave left and right channel data into the array.
            if(bufferIndex < samplesPerFrame * 4) {
                buffer[bufferIndex++] = (byte)(data & 0xFF);
                buffer[bufferIndex++] = (byte)((data >> 8) & 0xFF);
                buffer[bufferIndex++] = (byte)((data >> 16) & 0xFF);
                buffer[bufferIndex++] = (byte)((data >> 24) & 0xFF);
            }
        }
    }
}