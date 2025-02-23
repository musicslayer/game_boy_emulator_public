package gameboy.emulator.audio;

import gameboy.emulator.memory.AddressMap;

public class SquareWaveChannel {
    public AddressMap addressMap;

    ChannelActivityListener channelActivityListener;

    boolean isActive = false;
    boolean isDACEnabled = false;

    int currentLength = 0;
    int currentEnvelopeCount = 0;
    int currentVolume = 0;

    // Set if triggering the channel cause length to reset.
    boolean wasUnfrozen = false;

    // Square Wave Patterns
    public int[] patterns = new int[] { 0b00000001, 0b10000001, 0b10000111, 0b01111110 };
    public int currentPosition = 0;
    public int valueCount = 0;

    public SquareWaveChannel(AddressMap addressMap) {
        this.addressMap = addressMap;
    }

    public byte produceSampleByte() {
        // If the channel is not active, still produce a sample with zero volume.
        if(!isActive) {
            return 0;
        }

        // Square wave shift
        valueCount++;
        if(valueCount >= 2048) {
            valueCount = getPeriodOffset();

            currentPosition++;
            if(currentPosition == 8) {
                currentPosition = 0;
            }
        }
        
        int pattern = getChosenPattern();
        int mask = 0b00000001 << currentPosition;
        int currentValue = (pattern & mask) == mask ? 1 : 0;
        return (byte) (currentValue * currentVolume);
    }

    public int getPeriodOffset() {
        // Period of position switching is (2048 - offset) APU ticks.

        // Upper 3 bits
        int upper = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR24, true)) & 0b00000111;

        // Lower 8 bits
        int lower = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR23, true));

        return (upper << 8) | lower;
    }

    public int getChosenPattern() {
        int chosenPattern = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR21, true)) >>> 6) & 0b11;
        return patterns[chosenPattern];
    }

    public void onTrigger() {
        setIsActive(true);

        // Set initial values
        currentEnvelopeCount = 0;
        currentVolume = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR22, true)) & 0b11110000) >>> 4;
        currentPosition = 0;
        valueCount = getPeriodOffset();

        if(currentLength == 64) {
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
        currentLength = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR21, true)) & 0b00111111;
    }

    public void onLengthTick() {
        // Length tick does not depend on the channel being active, only the length enable bit.
        if(addressMap.loadBit(AddressMap.ADDRESS_NR24, 6, true) == 0) {
            return;
        }

        currentLength++;
        if(currentLength == 0x40) {
            setIsActive(false);
        }
    }

    public void onEnvelopeTick() {
        if(!isActive) {
            return;
        }

        int envelopePeriod = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR22, true)) & 0b00000111;
        if(envelopePeriod == 0) {
            return;
        }

        currentEnvelopeCount++;
        if(currentEnvelopeCount >= envelopePeriod) {
            currentEnvelopeCount = 0;

            // 0 = decrease volume, 1 = increase volume
            int bit3 = addressMap.loadBit(AddressMap.ADDRESS_NR22, 3, true);
            if(bit3 == 0) {
                currentVolume = currentVolume == 0 ? 0 : currentVolume - 1;
            }
            else {
                currentVolume = currentVolume == 15 ? 15 : currentVolume + 1;
            }
        }
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
        if(channelActivityListener != null) {
            channelActivityListener.onActivity(isActive);
        }
    }

    abstract public static class ChannelActivityListener {
        abstract public void onActivity(boolean isActive);
    }
}
