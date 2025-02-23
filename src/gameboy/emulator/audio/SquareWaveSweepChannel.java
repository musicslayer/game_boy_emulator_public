package gameboy.emulator.audio;

import gameboy.emulator.memory.AddressMap;

public class SquareWaveSweepChannel {
    public AddressMap addressMap;

    ChannelActivityListener channelActivityListener;

    boolean isActive = false;
    boolean isDACEnabled = false;

    boolean isSweepEnabled = false;
    int shadowPeriodOffset = 0;
    int currentLength = 0;
    int sweepTimer = 0;
    int currentEnvelopeCount = 0;
    int currentVolume = 0;

    // Was there at least one sweep calculation after the channel was triggered with a negative direction.
    boolean wasNegativeSweepCalculation = false;

    // Set if triggering the channel cause length to reset.
    boolean wasUnfrozen = false;

    // Square Wave Patterns
    public int[] patterns = new int[] { 0b00000001, 0b10000001, 0b10000111, 0b01111110 };
    public int currentPosition = 0;
    public int valueCount = 0;

    public SquareWaveSweepChannel(AddressMap addressMap) {
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
        int upper = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR14, true)) & 0b00000111;

        // Lower 8 bits
        int lower = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR13, true));

        return (upper << 8) | lower;
    }

    public void setPeriodOffset(int periodOffset) {
        // Upper 3 bits
        addressMap.storeBit(AddressMap.ADDRESS_NR14, 2, (periodOffset >>> 10) & 0b1, true);
        addressMap.storeBit(AddressMap.ADDRESS_NR14, 1, (periodOffset >>> 9) & 0b1, true);
        addressMap.storeBit(AddressMap.ADDRESS_NR14, 0, (periodOffset >>> 8) & 0b1, true);

        // Lower 8 bits
        addressMap.storeByte(AddressMap.ADDRESS_NR13, (byte)(periodOffset & 0xFF));
    }

    public int getChosenPattern() {
        int chosenPattern = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR11, true)) >>> 6) & 0b11;
        return patterns[chosenPattern];
    }

    public void onTrigger() {
        // Sweep is enabled if the pace and/or the step is nonzero.
        int nr10 = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR10, true));
        int pace = (nr10 >>> 4) & 0b111;
        int direction = (nr10 >>> 3) & 0b1;
        int step = nr10 & 0b00000111;
        isSweepEnabled = pace != 0 || step != 0; // Only updated on trigger
        wasNegativeSweepCalculation = false;

        // Copy real period to the shadow period.
        shadowPeriodOffset = getPeriodOffset();

        // Freqeuncy calculation without storing new value. This occurs even if pace = 0.
        if(step != 0) {
            int newPeriodOffset = getNewPeriodOffset(direction, step);
            if(newPeriodOffset == 2048) {
                // Overflow - disable the channel.
                setIsActive(false);
                return;
            }

            if(direction == 1) {
                wasNegativeSweepCalculation = true;
            }
        }

        // Set initial values
        sweepTimer = pace == 0 ? 8 : pace;
        currentEnvelopeCount = 0;
        currentVolume = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR12, true)) & 0b11110000) >>> 4;
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

    public int getNewPeriodOffset(int direction, int step) {
        int deltaPeriodOffset = shadowPeriodOffset >> step;
        int newPeriodOffset;
        if(direction == 0) {
            newPeriodOffset = shadowPeriodOffset + deltaPeriodOffset;
        }
        else {
            newPeriodOffset = shadowPeriodOffset - deltaPeriodOffset;
        }

        if(newPeriodOffset > 2047) {
            // Value to indicate overflow.
            return 2048;
        }
        else if(newPeriodOffset < 0) {
            // Set the new period offset as the old period offset.
            return shadowPeriodOffset;
        }
        else {
            return newPeriodOffset;
        }
    }

    public void updateLength() {
        currentLength = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR11, true)) & 0b00111111;
    }

    public void onLengthTick() {
        // Length tick does not depend on the channel being active, only the length enable bit.
        if(addressMap.loadBit(AddressMap.ADDRESS_NR14, 6, true) == 0) {
            return;
        }

        currentLength++;
        if(currentLength == 0x40) {
            setIsActive(false);
        }
    }

    public void onSweepTick() {
        if(!isActive) {
            return;
        }

        int nr10 = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR10, true));
        int pace = (nr10 >>> 4) & 0b111;
        int direction = (nr10 >>> 3) & 0b1; // 0 = increase period, 1 = decrease period
        int step = nr10 & 0b00000111;

        sweepTimer--;
        if(sweepTimer <= 0) {
            sweepTimer = pace == 0 ? 8 : pace;

            if(isSweepEnabled && pace != 0) {
                // Freqeuncy calculation.
                int newPeriodOffset = getNewPeriodOffset(direction, step);
                if(newPeriodOffset == 2048) {
                    // Overflow - disable the channel.
                    setIsActive(false);
                    return;
                }

                if(direction == 1) {
                    wasNegativeSweepCalculation = true;
                }

                if(step != 0) {
                    // Store new period offset in the shadow value and the real value.
                    shadowPeriodOffset = newPeriodOffset;
                    setPeriodOffset(newPeriodOffset);

                    // Perform one more frequency calculation without storing any new value.
                    if(getNewPeriodOffset(direction, step) == 2048) {
                        // Overflow - disable the channel.
                        setIsActive(false);
                    }
                }
            }
        }
    }

    public void onEnvelopeTick() {
        if(!isActive) {
            return;
        }

        int envelopePeriod = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR12, true)) & 0b00000111;
        if(envelopePeriod == 0) {
            return;
        }

        currentEnvelopeCount++;
        if(currentEnvelopeCount >= envelopePeriod) {
            currentEnvelopeCount = 0;

            // 0 = decrease volume, 1 = increase volume
            int bit3 = addressMap.loadBit(AddressMap.ADDRESS_NR12, 3, true);
            if(bit3 == 0) {
                currentVolume = currentVolume == 0 ? 0 : currentVolume - 1;
            }
            else {
                currentVolume = currentVolume == 15 ? 15 : currentVolume + 1;
            }
        }
    }

    public void onSweepDirectionClear() {
        if(wasNegativeSweepCalculation) {
            setIsActive(false);
        }
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
        if(!isActive) {
            wasNegativeSweepCalculation = false;
        }

        if(channelActivityListener != null) {
            channelActivityListener.onActivity(isActive);
        }
    }

    abstract public static class ChannelActivityListener {
        abstract public void onActivity(boolean isActive);
    }
}
