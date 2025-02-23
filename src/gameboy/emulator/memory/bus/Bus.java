package gameboy.emulator.memory.bus;

abstract public class Bus {
    abstract public int decodeRegion(int address);
    abstract public int decodeRelativeAddress(int address);
}