package gameboy.emulator.register;

// A 16-bit LFSR register.
public class LFSRRegister {
    public short data;
    public int width = 0;

    public void reset() {
        // The initial seed is just zero.
        setInt(0);
    }

    // Every time the register shifts:
    // Step 1: The result of LFSR 0 ⊙ LFSR 1 (1 if bit 0 and bit 1 are identical, 0 otherwise) is written to bit 15.
    // Step 2: If "short mode" was selected in NR43, then bit 15 is copied to bit 7 as well.
    // Step 3: The entire LFSR is shifted right.
    public void shift() {
        int bit0 = getBit(0);
        int bit1 = getBit(1);
        int bit01 = bit0 == bit1 ? 1 : 0;

        setBit(15, bit01);

        // 0 = long mode
        // 1 = short mode
        if(width == 1) {
            setBit(6, bit01);
        }

        int value = getInt();
        value >>>= 1;
        setInt(value);
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