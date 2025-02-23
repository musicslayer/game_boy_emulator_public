package gameboy.emulator.audio;

import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.register.LFSRRegister;

public class NoiseChannel {
    public AddressMap addressMap;
    LFSRRegister noiseShiftRegister = new LFSRRegister();

    ChannelActivityListener channelActivityListener;

    boolean isActive = false;
    boolean isDACEnabled = false;

    int currentLength = 0;
    int currentEnvelopeCount = 0;
    int currentVolume = 0;

    // Set if triggering the channel cause length to reset.
    boolean wasUnfrozen = false;

    public int valueCount = 0;

    public NoiseChannel(AddressMap addressMap) {
        this.addressMap = addressMap;

        initStoreMap();
    }

    public void initStoreMap() {
        addressMap.addStoreCallback(AddressMap.ADDRESS_NR43, addressMap.new StoreCallback() {
            @Override
           public void onStore(int region, int relativeAddress, byte b) {
                noiseShiftRegister.width = (b >>> 3) & 0b1;
                super.onStore(region, relativeAddress, b);
            }
        });
    }

    public byte produceSampleByte() {
        // If the channel is not active, still produce a sample with zero volume.
        if(!isActive) {
            return 0;
        }

        // Square wave shift
        valueCount++;
        if(valueCount >= getPeriod()) {
            valueCount = 0;
            noiseShiftRegister.shift();
        }

        return (byte)(noiseShiftRegister.getBit(0) * currentVolume);
    }

    public int getPeriod() {
        // Period of noise register shifting is (4 * divider * Math.pow(2, shift)) APU ticks.
        int nr43 = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR43, true));
        int shift = (nr43 >>> 4) & 0b1111;
        int divider = nr43 & 0b00000111;

        if(divider == 0) {
            divider = 1;
            shift--;
        }

        return (divider << (shift + 2));
    }

    public void onTrigger() {
        setIsActive(true);

        // Reset the LFDR
        noiseShiftRegister.reset();
        
        // Set initial values
        currentEnvelopeCount = 0;
        currentVolume = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR42, true)) & 0b11110000) >>> 4;
        valueCount = 0;
        noiseShiftRegister.reset();

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
        currentLength = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR41, true)) & 0b00111111;
    }

    public void onLengthTick() {
        // Length tick does not depend on the channel being active, only the length enable bit.
        if(addressMap.loadBit(AddressMap.ADDRESS_NR44, 6, true) == 0) {
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

        int envelopePeriod = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR42, true)) & 0b00000111;
        if(envelopePeriod == 0) {
            return;
        }

        currentEnvelopeCount++;
        if(currentEnvelopeCount >= envelopePeriod) {
            currentEnvelopeCount = 0;

            // 0 = decrease volume, 1 = increase volume
            int bit3 = addressMap.loadBit(AddressMap.ADDRESS_NR42, 3, true);
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
