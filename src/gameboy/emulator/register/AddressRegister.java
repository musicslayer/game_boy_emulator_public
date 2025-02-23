package gameboy.emulator.register;

import gameboy.emulator.memory.AddressMap;

// An 8-bit temporary register to interact with a value currently in memory.
public class AddressRegister extends Register8Bit {
    AddressMap addressMap;
    
    int address;

    public AddressRegister(AddressMap addressMap, FlagRegister flagRegister) {
        super(flagRegister);
        this.addressMap = addressMap;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    // Each method:
    // - Loads the value from memory
    // - Calls the superclass method
    // - Stores the value back in memory.

    public void increment() {
        setByte(addressMap.loadByte(address));
        super.increment();
        addressMap.storeByte(address, data);
    }

    public void decrement() {
        setByte(addressMap.loadByte(address));
        super.decrement();
        addressMap.storeByte(address, data);
    }

    public void rl() {
        setByte(addressMap.loadByte(address));
        super.rl();
        addressMap.storeByte(address, data);
    }

    public void rlc() {
        setByte(addressMap.loadByte(address));
        super.rlc();
        addressMap.storeByte(address, data);
    }

    public void rr() {
        setByte(addressMap.loadByte(address));
        super.rr();
        addressMap.storeByte(address, data);
    }

    public void rrc() {
        setByte(addressMap.loadByte(address));
        super.rrc();
        addressMap.storeByte(address, data);
    }

    public void sla() {
        setByte(addressMap.loadByte(address));
        super.sla();
        addressMap.storeByte(address, data);
    }

    public void sra() {
        setByte(addressMap.loadByte(address));
        super.sra();
        addressMap.storeByte(address, data);
    }

    public void srl() {
        setByte(addressMap.loadByte(address));
        super.srl();
        addressMap.storeByte(address, data);
    }

    public void swap() {
        setByte(addressMap.loadByte(address));
        super.swap();
        addressMap.storeByte(address, data);
    }

    public void bit(int n) {
        setByte(addressMap.loadByte(address));
        super.bit(n);
        addressMap.storeByte(address, data);
    }

    public void res(int n) {
        setByte(addressMap.loadByte(address));
        super.res(n);
        addressMap.storeByte(address, data);
    }

    public void set(int n) {
        setByte(addressMap.loadByte(address));
        super.set(n);
        addressMap.storeByte(address, data);
    }
}