package gameboy.emulator.register;

// An 8-bit register specifically used for status flags and not any arithmetic.
public class FlagRegister extends Register8Bit {
    public FlagRegister() {
        super(null);
    }

    @Override
    public void setByte(byte b) {
        // No matter what is written, the lower bits must remain zero.
        data = (byte)(b & 0b11110000);
    }

    @Override
    public void setInt(int value) {
        // No matter what is written, the lower bits must remain zero.
        data = (byte)(value & 0b11110000);
    }

    @Override
    public void setBit(int n, int value) {
        // No matter what is written, the lower bits must remain zero.
        int mask = (0b00000001 << n);
        if(value == 1) {
            data |= mask;
        }
        else {
            data &= ~mask;
        }

        data &= 0b11110000;
    }

    public int getZeroFlag() {
        return getBit(7);
    }

    public void setZeroFlag(int value) {
        setBit(7, value);
    }

    public int getSubtractFlag() {
        return getBit(6);
    }

    public void setSubtractFlag(int value) {
        setBit(6, value);
    }

    public int getHalfCarryFlag() {
        return getBit(5);
    }

    public void setHalfCarryFlag(int value) {
        setBit(5, value);
    }

    public int getCarryFlag() {
        return getBit(4);
    }

    public void setCarryFlag(int value) {
        setBit(4, value);
    }

    public void flipCarryFlag() {
        setBit(4, 1 - getBit(4));
    }
}
