package gameboy.emulator.audio;

import gameboy.data.SoundConsumer;
import gameboy.data.SoundProducer;
import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.timer.DIVAPUTimer;
import gameboy.emulator.timer.DIVAPUTimer.DIVAPUListener;

public class Mixer implements SoundProducer {
    public AddressMap addressMap;

    int clockCounter = 0;

    public boolean isPoweredOn = false;

    SquareWaveSweepChannel channel1;
    SquareWaveChannel channel2;
    SampleChannel channel3;
    NoiseChannel channel4;

    // Start the DIVAPU cycle offset by 1.
    int divAPUCount = 1;
    
    public Mixer(AddressMap addressMap) {
        this.addressMap = addressMap;

        channel1 = new SquareWaveSweepChannel(addressMap);
        channel2 = new SquareWaveChannel(addressMap);
        channel3 = new SampleChannel(addressMap);
        channel4 = new NoiseChannel(addressMap);

        initStoreMap();
        initLoadMap();
        initActivityListeners();
    }

    public void initStoreMap() {
        // Attach callbacks for channel registers.
        // Note that nearly every sound register is read only when powered off.
        addressMap.addStoreCallback(AddressMap.ADDRESS_NR10, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);

                    int bit3 = (b >>> 3) & 0b1;
                    if(bit3 == 0) {
                        channel1.onSweepDirectionClear();
                    }
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR11, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
                else {
                    // The bottom 6 bits of this register can be written to even when powered off.
                    int oldNR11 = addressMap.loadByte(AddressMap.ADDRESS_NR11, true);
                    int newValue = (oldNR11 & 0b11000000) | (b & 0b00111111);
                    super.onStore(region, relativeAddress, (byte)newValue);
                }

                channel1.updateLength();
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR12, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);

                    channel1.isDACEnabled = (b & 0b11111000) != 0;
                    channel1.isActive &= channel1.isDACEnabled;
                    setActivityBit(0, channel1.isActive ? 1 : 0);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR13, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR14, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    int oldBit6 = (addressMap.data[region][relativeAddress] >>> 6) & 0b1;
                    int newBit6 = (b >>> 6) & 0b1;
                    int newBit7 = (b >>> 7) & 0b1;

                    super.onStore(region, relativeAddress, b);

                    // Hardware quirk - length may be clocked an extra time.
                    if(divAPUCount % 2 == 0) {
                        if(oldBit6 == 0 && newBit6 == 1 && channel1.currentLength != 64) {
                            channel1.onLengthTick();
                        }
                    }

                    if(newBit7 == 1) {
                        channel1.onTrigger();

                        // Hardware quirk - length may be clocked an extra time.
                        if(divAPUCount % 2 == 0) {
                            if(channel1.wasUnfrozen) {
                                channel1.onLengthTick();
                            }
                        }
                    }
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR21, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
                else {
                    // The bottom 6 bits of this register can be written to even when powered off.
                    int oldNR21 = addressMap.loadByte(AddressMap.ADDRESS_NR21, true);
                    int newValue = (oldNR21 & 0b11000000) | (b & 0b00111111);
                    super.onStore(region, relativeAddress, (byte)newValue);
                }

                channel2.updateLength();
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR22, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);

                    channel2.isDACEnabled = (b & 0b11111000) != 0;
                    channel2.isActive &= channel2.isDACEnabled;
                    setActivityBit(1, channel2.isActive ? 1 : 0);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR23, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR24, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    int oldBit6 = (addressMap.data[region][relativeAddress] >>> 6) & 0b1;
                    int newBit6 = (b >>> 6) & 0b1;
                    int newBit7 = (b >>> 7) & 0b1;

                    super.onStore(region, relativeAddress, b);

                    // Hardware quirk - length may be clocked an extra time.
                    if(divAPUCount % 2 == 0) {
                        if(oldBit6 == 0 && newBit6 == 1 && channel2.currentLength != 64) {
                            channel2.onLengthTick();
                        }
                    }

                    if(newBit7 == 1) {
                        channel2.onTrigger();

                        // Hardware quirk - length may be clocked an extra time.
                        if(divAPUCount % 2 == 0) {
                            if(channel2.wasUnfrozen) {
                                channel2.onLengthTick();
                            }
                        }
                    }
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR30, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);

                    channel3.isDACEnabled = ((b >>> 7) & 0b1) == 1;
                    channel3.isActive &= channel3.isDACEnabled;
                    setActivityBit(2, channel3.isActive ? 1 : 0);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR31, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                // This register can be written to even when powered off.
                super.onStore(region, relativeAddress, b);

                channel3.updateLength();
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR32, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR33, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR34, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    int oldBit6 = (addressMap.data[region][relativeAddress] >>> 6) & 0b1;
                    int newBit6 = (b >>> 6) & 0b1;
                    int newBit7 = (b >>> 7) & 0b1;

                    super.onStore(region, relativeAddress, b);

                    // Hardware quirk - length may be clocked an extra time.
                    if(divAPUCount % 2 == 0) {
                        if(oldBit6 == 0 && newBit6 == 1 && channel3.currentLength != 256) {
                            channel3.onLengthTick();
                        }
                    }

                    if(newBit7 == 1) {
                        channel3.onTrigger();

                        // Hardware quirk - length may be clocked an extra time.
                        if(divAPUCount % 2 == 0) {
                            if(channel3.wasUnfrozen) {
                                channel3.onLengthTick();
                            }
                        }
                    }
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR41, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                // This register can be written to even when powered off.
                super.onStore(region, relativeAddress, b);

                channel4.updateLength();
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR42, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);

                    channel4.isDACEnabled = (b & 0b11111000) != 0;
                    channel4.isActive &= channel4.isDACEnabled;
                    setActivityBit(3, channel4.isActive ? 1 : 0);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR43, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR44, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    int oldBit6 = (addressMap.data[region][relativeAddress] >>> 6) & 0b1;
                    int newBit6 = (b >>> 6) & 0b1;
                    int newBit7 = (b >>> 7) & 0b1;

                    super.onStore(region, relativeAddress, b);

                    // Hardware quirk - length may be clocked an extra time.
                    if(divAPUCount % 2 == 0) {
                        if(oldBit6 == 0 && newBit6 == 1 && channel4.currentLength != 64) {
                            channel4.onLengthTick();
                        }
                    }

                    if(newBit7 == 1) {
                        channel4.onTrigger();

                        // Hardware quirk - length may be clocked an extra time.
                        if(divAPUCount % 2 == 0) {
                            if(channel4.wasUnfrozen) {
                                channel4.onLengthTick();
                            }
                        }
                    }
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR50, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR51, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                if(isPoweredOn) {
                    super.onStore(region, relativeAddress, b);
                }
            }
        });

        addressMap.addStoreCallback(AddressMap.ADDRESS_NR52, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                byte oldValue = addressMap.data[region][relativeAddress];

                // The lower 4 bits are read only, so writes should not affect them.
                int oldLowerBits = Byte.toUnsignedInt(oldValue) & 0b00001111;
                int newUpperBits = Byte.toUnsignedInt(b) & 0b11110000;
                super.onStore(region, relativeAddress, (byte)(newUpperBits | oldLowerBits));

                // Update powered on status.
                boolean isPoweredOnOld = ((oldValue >>> 7) & 0b1) == 1;
                isPoweredOn = ((b >>> 7) & 0b1) == 1;
                
                channel1.isActive &= isPoweredOn;
                channel2.isActive &= isPoweredOn;
                channel3.isActive &= isPoweredOn;
                channel4.isActive &= isPoweredOn;

                channel1.isDACEnabled &= false;
                channel2.isDACEnabled &= false;
                channel3.isDACEnabled &= false;
                channel4.isDACEnabled &= false;

                setActivityBit(0, channel1.isActive ? 1 : 0);
                setActivityBit(1, channel2.isActive ? 1 : 0);
                setActivityBit(2, channel3.isActive ? 1 : 0);
                setActivityBit(3, channel4.isActive ? 1 : 0);

                // If we are powering off, clear all other sound registers except highest bit of NR52 and length timers in NRx1.
                if(!isPoweredOn) {
                    addressMap.storeByte(AddressMap.ADDRESS_NR10, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR11, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR12, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR13, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR14, (byte)0, true);

                    addressMap.storeByte(AddressMap.ADDRESS_NR21, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR22, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR23, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR24, (byte)0, true);

                    addressMap.storeByte(AddressMap.ADDRESS_NR30, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR31, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR32, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR33, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR34, (byte)0, true);

                    addressMap.storeByte(AddressMap.ADDRESS_NR41, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR42, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR43, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR44, (byte)0, true);

                    addressMap.storeByte(AddressMap.ADDRESS_NR50, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR51, (byte)0, true);
                    addressMap.storeByte(AddressMap.ADDRESS_NR51, (byte)0, true);

                    channel3.onPowerOff();
                }

                if(!isPoweredOnOld && isPoweredOn) {
                    // Manipulate DIVAPU phase.
                    divAPUCount = 1;
                }
            }
        });
    }

    public void initLoadMap() {
        // Most sound registers are "ORed" with a certain value upon reading.
        addressMap.addLoadCallback(AddressMap.ADDRESS_NR10, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0x80);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR11, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0x3F);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR13, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xFF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR14, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xBF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR21, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0x3F);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR23, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xFF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR24, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xBF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR30, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0x7F);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR31, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xFF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR32, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0x9F);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR33, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xFF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR34, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xBF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR41, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xFF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR44, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0xBF);
            }
        });

        addressMap.addLoadCallback(AddressMap.ADDRESS_NR52, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return (byte)(super.onLoad(region, relativeAddress) | 0x70);
            }
        });
    }

    public void initActivityListeners() {
        // Attach callbacks for channel registers changing their activity status.
        channel1.channelActivityListener = new SquareWaveSweepChannel.ChannelActivityListener() {
            @Override
            public void onActivity(boolean isActive) {
                setActivityBit(0, isActive ? 1 : 0);
            }
        };

        channel2.channelActivityListener = new SquareWaveChannel.ChannelActivityListener() {
            @Override
            public void onActivity(boolean isActive) {
                setActivityBit(1, isActive ? 1 : 0);
            }
        };

        channel3.channelActivityListener = new SampleChannel.ChannelActivityListener() {
            @Override
            public void onActivity(boolean isActive) {
                setActivityBit(2, isActive ? 1 : 0);
            }
        };

        channel4.channelActivityListener = new NoiseChannel.ChannelActivityListener() {
            @Override
            public void onActivity(boolean isActive) {
                setActivityBit(3, isActive ? 1 : 0);
            }
        };
    }

    public void setActivityBit(int n, int value) {
        // Sets the appropriate activity bit of NR52.
        addressMap.storeBit(AddressMap.ADDRESS_NR52, n, value, true);
    }

    public void attachClock(HybridClock hybridClock) {
        // f = 1048576L
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                clockCounter++;
                if(clockCounter == 4) {
                    clockCounter = 0;
                    
                    produceSample();
                }
            }
        });
    }

    public void attachDIVAPUTimer(DIVAPUTimer divapuTimer) {
        divapuTimer.setOnTickListener(new DIVAPUListener() {
            @Override
            public void onTick() {
                onDIVAPU();
            }
        });
    }

    public void onDIVAPU() {
        divAPUCount++;
        divAPUCount %= 8;

        if(divAPUCount % 2 == 0) {
            // Sound Length
            channel1.onLengthTick();
            channel2.onLengthTick();
            channel3.onLengthTick();
            channel4.onLengthTick();
        }

        if(divAPUCount % 4 == 0) {
            // Frequency Sweep
            channel1.onSweepTick();
        }

        if(divAPUCount == 0) {
            // Envelope Sweep
            channel1.onEnvelopeTick();
            channel2.onEnvelopeTick();
            channel4.onEnvelopeTick();
        }
    }

    public void produceSample() {
        if(!isPoweredOn) {
            // The APU is powered off, so don't produce any sound bytes at all.
            return;
        }

        // Note that we mix the digital signals without converting to analog.
        // Also, we will convert the samples to shorts so that we can have enough dynamic range.
        short sample1 = channel1.produceSampleByte();
        short sample2 = channel2.produceSampleByte();
        short sample3 = channel3.produceSampleByte();
        short sample4 = channel4.produceSampleByte();

        // The DAC being off will make a sample act as 0.
        sample1 = channel1.isDACEnabled ? sample1 : 0;
        sample2 = channel2.isDACEnabled ? sample2 : 0;
        sample3 = channel3.isDACEnabled ? sample3 : 0;
        sample4 = channel4.isDACEnabled ? sample4 : 0;

        int nr51 = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR51));
        boolean isCH4Left = ((nr51 >>> 7) & 0b1) == 1;
        boolean isCH3Left = ((nr51 >>> 6) & 0b1) == 1;
        boolean isCH2Left = ((nr51 >>> 5) & 0b1) == 1;
        boolean isCH1Left = ((nr51 >>> 4) & 0b1) == 1;
        boolean isCH4Right = ((nr51 >>> 3) & 0b1) == 1;
        boolean isCH3Right = ((nr51 >>> 2) & 0b1) == 1;
        boolean isCH2Right = ((nr51 >>> 1) & 0b1) == 1;
        boolean isCH1Right = ((nr51) & 0b1) == 1;

        short sampleLeft = 0;
        sampleLeft += isCH1Left ? sample1 : 0;
        sampleLeft += isCH2Left ? sample2 : 0;
        sampleLeft += isCH3Left ? sample3 : 0;
        sampleLeft += isCH4Left ? sample4 : 0;

        short sampleRight = 0;
        sampleRight += isCH1Right ? sample1 : 0;
        sampleRight += isCH2Right ? sample2 : 0;
        sampleRight += isCH3Right ? sample3 : 0;
        sampleRight += isCH4Right ? sample4 : 0;

        // Use the left/right volume in a way that allows an approriate dynamic range.
        int nr50 = Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_NR50));
        int volumeLeft = (nr50 >>> 4) & 0b111;
        int volumeRight = nr50 & 0b111;
        sampleLeft <<= volumeLeft;
        sampleRight <<= volumeRight;

        int fullSample = (sampleLeft << 16) | sampleRight;
        produceSound(fullSample);
    }

    // SoundProducer
    SoundConsumer[] soundConsumers = new SoundConsumer[0];

    @Override
    public SoundConsumer[] getSoundConsumers() {
        return soundConsumers;
    }

    @Override
    public void addSoundConsumer(SoundConsumer soundConsumer) {
        // Create a new array with one more element.
        SoundConsumer[] oldArray = soundConsumers;
        soundConsumers = new SoundConsumer[oldArray.length + 1];

        // Copy over elements from old array.
        for(int i = 0; i < oldArray.length; i++) {
            soundConsumers[i] = oldArray[i];
        }

        // Add new element.
        soundConsumers[oldArray.length] = soundConsumer;
    }
}