package gameboy.emulator.processor;

import gameboy.emulator.clock.HybridClock;
import gameboy.emulator.memory.AddressMap;

// This class is a separate processor responsible for performing DMA transfers.
public class DMAProcessor {
    public AddressMap addressMap;

    byte lastDMAByte;

    int numTCycles = 0;

    boolean isDMATransfer = false;
    int sourceAddressBase;
    int bytesWritten;

    public DMAProcessor(AddressMap addressMap) {
        this.addressMap = addressMap;

        initStoreMap();
        initLoadMap();
    }

    public void initStoreMap() {
        addressMap.addStoreCallback(AddressMap.ADDRESS_DMA, addressMap.new StoreCallback() {
            @Override
            public void onStore(int region, int relativeAddress, byte b) {
                lastDMAByte = b;

                // If we are already in a DMA transfer, take no further action.
                if(isDMATransfer) {
                    return;
                }

                numTCycles = 3;

                isDMATransfer = true;
                sourceAddressBase = Byte.toUnsignedInt(b) << 8;
                bytesWritten = 0;
            }
        });
    }

    public void initLoadMap() {
        addressMap.addLoadCallback(AddressMap.ADDRESS_DMA, addressMap.new LoadCallback() {
            @Override
            public byte onLoad(int region, int relativeAddress) {
                return lastDMAByte;
            }
        });
    }

    public void attachClock(HybridClock hybridClock) {
        // f = 4194304L
        hybridClock.addTickCallback(new HybridClock.TickCallback() {
            @Override
            public void onTick() {
                // Do nothing if we are not in the middle of a DMA transfer.
                if(!isDMATransfer) {
                    return;
                }

                // Simulate waiting for prior instruction to complete.
                if(numTCycles > 0) {
                    numTCycles--;
                    return;
                }

                // The DMA transfer loads data with the external bus.
                addressMap.storeByte(0xFE00 + bytesWritten, addressMap.loadByte(sourceAddressBase + bytesWritten, addressMap.externalBus));
                bytesWritten++;

                if(bytesWritten < 160) {
                    // Keep going.    
                    numTCycles = 3;
                }
                else {
                    // Complete DMA transfer.
                    isDMATransfer = false;
                }
            }
        });
    }
}
