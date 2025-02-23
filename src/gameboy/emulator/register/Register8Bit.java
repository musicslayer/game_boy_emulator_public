package gameboy.emulator.register;

// An 8-bit register
public class Register8Bit {
    public FlagRegister flagRegister;
    public byte data;

    public Register8Bit(FlagRegister flagRegister) {
        this.flagRegister = flagRegister;
    }

    public byte getByte() {
        return data;
    }

    public void setByte(byte b) {
        data = b;
    }

    public int getInt() {
        return Byte.toUnsignedInt(data);
    }

    public void setInt(int value) {
        data = (byte)(value & 0xFF);
    }

    public int getBit(int n) {
        int mask = (0b00000001 << n);
        return (data & mask) == mask ? 1 : 0;
    }

    public void setBit(int n, int value) {
        int mask = (0b00000001 << n);
        if(value == 1) {
            data |= mask;
        }
        else {
            data &= ~mask;
        }
    }




    public void increment() {
        int oldValue = getInt();
        int result;

        if(oldValue < 0x00FF) {
            result = oldValue + 1;
        }
        else {
            result = 0;
        }

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((oldValue & 0x000F) + 1) & 0x0010) == 0x0010 ? 1 : 0);
    }

    public void decrement() {
        int oldValue = getInt();
        int result;
        
        if(oldValue > 0x0000) {
            result = oldValue - 1;
        }
        else {
            result = 0x00FF;
        }

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((oldValue & 0x000F) - 1) & 0x0010) == 0x0010 ? 1 : 0);
    }

    public void flip() {
        int result = getInt();
        result = ~result;
        result &= 0xFF;

        setInt(result);

        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag(1);
    }

    public void and(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = thisValue & otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(1);
        flagRegister.setCarryFlag(0);
    }

    public void and(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = thisValue & otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(1);
        flagRegister.setCarryFlag(0);
    }

    public void and(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = thisValue & otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(1);
        flagRegister.setCarryFlag(0);
    }

    public void xor(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = thisValue ^ otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void xor(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = thisValue ^ otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void xor(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = thisValue ^ otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void or(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = thisValue | otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void or(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = thisValue | otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void or(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = thisValue | otherValue;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void compare(Register8Bit R) {
        // Perform a subtraction, but don't store the result in "byteArray".
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = (thisValue - otherValue) & 0xFF;

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void compare(byte b) {
        // Perform a subtraction, but don't store the result in "byteArray".
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = (thisValue - otherValue) & 0xFF;

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void compare(int value) {
        // Perform a subtraction, but don't store the result in "byteArray".
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue - otherValue) & 0xFF;

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue) & 0x100) == 0x100 ? 1 : 0);
    }





    public void add(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = (thisValue + otherValue) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void add(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = (thisValue + otherValue) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void add(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue + otherValue) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void adc(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = (thisValue + otherValue + flagRegister.getCarryFlag()) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F) + (flagRegister.getCarryFlag() & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue + flagRegister.getCarryFlag()) & 0x100) == 0x100 ? 1 : 0);
    }

    public void adc(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = (thisValue + otherValue + flagRegister.getCarryFlag()) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F) + (flagRegister.getCarryFlag() & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue + flagRegister.getCarryFlag()) & 0x100) == 0x100 ? 1 : 0);
    }

    public void adc(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue + otherValue + flagRegister.getCarryFlag()) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) + (otherValue & 0x0F) + (flagRegister.getCarryFlag() & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue + otherValue + flagRegister.getCarryFlag()) & 0x100) == 0x100 ? 1 : 0);
    }



    public void sub(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = (thisValue - otherValue) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void sub(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = (thisValue - otherValue) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void sub(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue - otherValue) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue) & 0x100) == 0x100 ? 1 : 0);
    }

    public void sbc(Register8Bit R) {
        int thisValue = getInt();
        int otherValue = R.getInt();
        int result = (thisValue - otherValue - flagRegister.getCarryFlag()) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F) - (flagRegister.getCarryFlag() & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue - flagRegister.getCarryFlag()) & 0x100) == 0x100 ? 1 : 0);
    }

    public void sbc(byte b) {
        int thisValue = getInt();
        int otherValue = Byte.toUnsignedInt(b);
        int result = (thisValue - otherValue - flagRegister.getCarryFlag()) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F) - (flagRegister.getCarryFlag() & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue - flagRegister.getCarryFlag()) & 0x100) == 0x100 ? 1 : 0);
    }

    public void sbc(int value) {
        int thisValue = getInt();
        int otherValue = value;
        int result = (thisValue - otherValue - flagRegister.getCarryFlag()) & 0xFF;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(1);
        flagRegister.setHalfCarryFlag((((thisValue & 0x0F) - (otherValue & 0x0F) - (flagRegister.getCarryFlag() & 0x0F)) & 0x10) == 0x10 ? 1 : 0);
        flagRegister.setCarryFlag(((thisValue - otherValue - flagRegister.getCarryFlag()) & 0x100) == 0x100 ? 1 : 0);
    }










    

    public void rl() {
        int oldBit7 = getBit(7);
        int result = getInt();
        result <<= 1;
        result &= 0b11111111;
        if(flagRegister.getCarryFlag() == 1) {
            result |= 0b00000001;
        }
        else {
            result &= ~0b00000001;
        }

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit7);
    }

    public void rlc() {
        int oldBit7 = getBit(7);
        int result = getInt();
        result <<= 1;
        result &= 0b11111111;
        if(oldBit7 == 1) {
            result |= 0b00000001;
        }
        else {
            result &= ~0b00000001;
        }

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit7);
    }

    public void rr() {
        int oldBit0 = getBit(0);
        int oldValue = getInt();
        int result = oldValue;
        result >>>= 1;
        result &= 0b11111111;
        if(flagRegister.getCarryFlag() == 1) {
            result |= 0b10000000;
        }
        else {
            result &= ~0b10000000;
        }

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit0);
    }

    public void rrc() {
        int oldBit0 = getBit(0);
        int oldValue = getInt();
        int result = oldValue;
        result >>>= 1;
        result &= 0b11111111;
        if(oldBit0 == 1) {
            result |= 0b10000000;
        }
        else {
            result &= ~0b10000000;
        }

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit0);
    }

    // CF   <-    7...0     <-   0
    public void sla() {
        int oldBit7 = getBit(7);
        int oldValue = getInt();
        int result = oldValue;
        result <<= 1;
        result &= 0b11111111;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit7);
    }

    // (7)   ->    7...0     ->   CF
    public void sra() {
        int oldBit0 = getBit(0);
        int oldBit7 = getBit(7);
        int result = getInt();
        result >>>= 1;
        result &= 0b11111111;

        setInt(result);
        setBit(7, oldBit7);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit0);
    }

    // 0   ->    7...0     ->   CF
    public void srl() {
        int oldBit0 = getBit(0);
        int result = getInt();
        result >>>= 1;
        result &= 0b11111111;

        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(oldBit0);
    }

    // [7654] <-> [3210]
    public void swap() {
        int oldBit0 = getBit(0);
        int oldBit1 = getBit(1);
        int oldBit2 = getBit(2);
        int oldBit3 = getBit(3);

        setBit(0, getBit(4));
        setBit(1, getBit(5));
        setBit(2, getBit(6));
        setBit(3, getBit(7));

        setBit(4, oldBit0);
        setBit(5, oldBit1);
        setBit(6, oldBit2);
        setBit(7, oldBit3);

        int result = getInt();

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(0);
    }

    public void bit(int n) {
        int result = getBit(n);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setSubtractFlag(0);
        flagRegister.setHalfCarryFlag(1);
    }

    public void res(int n) {
        setBit(n, 0);
    }

    public void set(int n) {
        setBit(n, 1);
    }

    public void decimalAdjust() {
        int shouldCarry = 0;

        int result = getInt();

        if(flagRegister.getSubtractFlag() == 0) {
            if((flagRegister.getCarryFlag() == 1) || (result > 0x99)) {
                result += 0x60;
                shouldCarry = 1;
            }

            if((flagRegister.getHalfCarryFlag() == 1) || ((result & 0x0F) > 0x09)) {
                result += 0x06;
            }
        }
        else {
            if(flagRegister.getCarryFlag() == 1) {
                result -= 0x60;
                shouldCarry = 1;
            }

            if(flagRegister.getHalfCarryFlag() == 1) {
                result -= 0x06;
            }
        }

        result &= 0xFF;
        setInt(result);

        flagRegister.setZeroFlag(result == 0 ? 1 : 0);
        flagRegister.setHalfCarryFlag(0);
        flagRegister.setCarryFlag(shouldCarry);
    }
}
