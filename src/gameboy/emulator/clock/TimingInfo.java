package gameboy.emulator.clock;

public class TimingInfo {
    public long frequency;
    public double framesPerSecond;

    public TimingInfo(long frequency, double framesPerSecond) {
        this.frequency = frequency;
        this.framesPerSecond = framesPerSecond;
    }
}
