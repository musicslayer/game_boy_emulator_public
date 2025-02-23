package gameboy.emulator.register;

// A 16-bit DIV register. The upper 8 bits map to 0xFF04.
public class DIVRegister {
    public short data;

    public int setValue(int value) {
        // Sets the value and returns an array of bit numbers that experienced a falling edge.
        int oldValue = getInt();
        setInt(value);

        return (oldValue & 0xFFFF) & (~value & 0xFFFF);
    }

    public int reset() {
        return setValue(0);
    }

    public byte getValue() {
        // Returns the upper 8 bits that are mapped to memory.
        return (byte)((data >>> 8) & 0xFF);
    }

    public int increment() {
        int value = getInt();
        value++;
        value &= 0xFFFF;

        return setValue(value);
    }

    public int getInt() {
        return Short.toUnsignedInt(data);
    }

    public void setInt(int value) {
        data = (short)(value & 0xFFFF);
    }

    public int getBit(int n) {
        int mask = (0b1 << n);
        return (data & mask) == mask ? 1 : 0;
    }

    public void setBit(int n, int value) {
        int mask = (0b1 << n);
        if(value == 1) {
            data |= mask;
        }
        else {
            data &= ~mask;
        }
    }
}