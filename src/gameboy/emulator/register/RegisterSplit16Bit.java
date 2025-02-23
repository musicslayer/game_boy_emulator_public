package gameboy.emulator.register;

// A 16-bit register made from two 8-bit registers.
public class RegisterSplit16Bit {
    public Register8Bit upperRegister;
    public Register8Bit lowerRegister;
    public FlagRegister flagRegister;

    public RegisterSplit16Bit(Register8Bit upperRegister, Register8Bit lowerRegister, FlagRegister flagRegister) {
        this.upperRegister = upperRegister;
        this.lowerRegister = lowerRegister;
        this.flagRegister = flagRegister;
    }

    public int getInt() {
        return (upperRegister.getInt() << 8) | (lowerRegister.getInt() & 0xFF);
    }

    public void setInt(int value) {
        upperRegister.setInt((value >> 8) & 0x00FF);
        lowerRegister.setInt(value & 0x00FF);
    }

    public short getShort() {
        return (short)((upperRegister.getByte() << 8) | (lowerRegister.getByte() & 0xFF));
    }

    public void setShort(short s) {
        upperRegister.setByte((byte)((s >> 8) & 0x00FF));
        lowerRegister.setByte((byte)(s & 0x00FF));
    }

    public void increment() {
        int oldValue = getInt();
        int result;

        if(oldValue < 0xFFFF) {
            result = oldValue + 1;
        }
        else {
            // Overflow
            result = 0;
        }

        setInt(result);
    }

    public void decrement() {
        int oldValue = getInt();
        int result;

        if(oldValue > 0x0000) {
            result = oldValue - 1;
        }
        else {
            // Overflow
            result = 0xFFFF;
        }

        setInt(result);
    }



    public void add(RegisterSplit16Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = (thisValue + otherValue) & 0xFFFF;

        setInt(result);

        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0FFF) + (otherValue & 0x0FFF)) & 0x1000) == 0x1000 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue) & 0x10000) == 0x10000 ? 1 : 0);
    }

    public void add(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue + otherValue) & 0xFFFF;

        setInt(result);

        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0FFF) + (otherValue & 0x0FFF)) & 0x1000) == 0x1000 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue) & 0x10000) == 0x10000 ? 1 : 0);
    }


    public void add2(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue + otherValue) & 0xFFFF;

        setInt(result);

        flagRegister.setSubtractFlag(0);

        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag((((thisValue & 0xFF) + (otherValue & 0xFF)) & 0x100) == 0x100 ? 1 : 0);
    }
}
