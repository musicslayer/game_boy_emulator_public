package gameboy.emulator.audio;

import gameboy.emulator.memory.AddressMap;

public class SampleChannel {
    public AddressMap addressMap;

    ChannelActivityListener channelActivityListener;

    boolean isActive = false;
    boolean isDACEnabled = false;

    int currentLength = 0;

    // Set if triggering the channel cause length to reset.
    boolean wasUnfrozen = false;

    public byte lastSample = 0;

    // Start position at 1, not 0, to match Game Boy behavior.
    public int currentPosition = 1;
    public int valueCount = 0;

    public SampleChannel(AddressMap addressMap) {
        this.addressMap = addressMap;
    }

    public byte produceSampleByte() {
        // If the channel is not active, still produce a sample with zero volume.
        if(!isActive) {
            return 0;
        }

        // Square wave shift
        valueCount++;
        if(valueCount >= 1024) {
            valueCount = getPeriodOffset();

            readStoredSample(currentPosition);

            currentPosition++;
            if(currentPosition == 32) {
                currentPosition = 0;
            }
        }

        byte sample = lastSample;

        // For this channel, volume mutes or right shifts the sample.
        int volume = getVolume();
        if(volume == 0) {
            sample = 0;
        }
        else if(volume == 2) {
            sample >>>= 1;
        }
        else if(volume == 3) {
            sample >>>= 2;
        }

        return sample;
    }

    public int getVolume() {
        int volumeData = addressMap.loadByte(AddressMap.ADDRESS_NR32, true);
        return (volumeData >>> 5) & 0b11;
    }

    public int getPeriodOffset() {
        // Period of sample switching is (1024 - offset/2) APU ticks.

        // Upper 3 bits
        int upper = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR34, true)) & 0b00000111;

        // Lower 8 bits
        int lower = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR33, true));

        return ((upper << 8) | lower) >>> 1;
    }

    public void readStoredSample(int currentPosition) {
        int nibble = currentPosition % 2; // 0 = upper nibble, 1 = lower nibble
        int addressOffset = currentPosition / 2;
        int address = AddressMap.ADDRESS_WAVERAM + addressOffset;
        
        int sample;
        if(nibble == 0) {
            sample = (Byte.toUnsignedInt(addressMap.loadByte(address)) & 0b11110000) >>> 4;
        }
        else {
            sample = Byte.toUnsignedInt(addressMap.loadByte(address)) & 0b00001111;
        }

        lastSample = (byte)sample;
    }

    public void onTrigger() {
        setIsActive(true);

        // Set initial values
        currentPosition = 0;
        valueCount = getPeriodOffset();

        if(currentLength == 256) {
            currentLength = 0;
            wasUnfrozen = true;
        }
        else {
            wasUnfrozen = false;
        }

        // At this point, channel will only remain active if the DAC is on.
        setIsActive(isDACEnabled);
    }

    public void updateLength() {
        currentLength = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR31, true));
    }

    public void onLengthTick() {
        // Length tick does not depend on the channel being active, only the length enable bit.
        if(addressMap.loadBit(AddressMap.ADDRESS_NR34, 6, true) == 0) {
            return;
        }

        currentLength++;
        if(currentLength == 0x100) {
            setIsActive(false);
        }
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
        if(channelActivityListener != null) {
            channelActivityListener.onActivity(isActive);
        }
    }

    public void onPowerOff() {
        lastSample = 0;
    }

    abstract public static class ChannelActivityListener {
        abstract public void onActivity(boolean isActive);
    }
}
