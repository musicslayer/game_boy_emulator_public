package gameboy.emulator.processor;

import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;

/*
    T-Cycles -> What the processor's crystal oscillates at (4MHz) i.e. 1 tick.
    M-Cycles -> What opcodes are measured in.
    1 M-Cycle = 4 T-Cycles
*/

// The class for the Game Boy processor.
public class Processor {
    public AddressMap addressMap;
    
    public ALU alu;
    int numTCycles = 0;

    public Processor(AddressMap addressMap) {
        this.addressMap = addressMap;

        alu = new ALU(addressMap);

        initLoadMap();
    }

    public void initLoadMap() {
        addressMap.addLoadCallback(AddressMap.ADDRESS_IF, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                // Unconnected bits are seen as 1.
                return (byte)(super.onLoad(region, relativeAddress) | 0b11100000);
            }
        });
    }

    public void attachClock(HybridClock hybridClock) {
        // f = 4194304L
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                // Simulate waiting for prior instruction to complete.
                if(numTCycles > 0) {
                    numTCycles--;
                    return;
                }

                alu.processOpcode();

                // Subtract off the cycle we just performed.
                numTCycles = alu.numTCycles - 1;
            }
        });
    }
}