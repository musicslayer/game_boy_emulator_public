package gameboy.emulator.processor;

import gameboy.data.StateConsumer;
import gameboy.data.StateProducer;
import gameboy.emulator.memory.AddressMap;
import gameboy.emulator.register.AddressRegister;
import gameboy.emulator.register.FlagRegister;
import gameboy.emulator.register.RegisterSplit16Bit;
import gameboy.emulator.register.Register8Bit;

// The class that performs the arithmetic operations for the processor.
public class ALU implements StateProducer {
    public AddressMap addressMap;

    // Registers - Index [1] for higher 8 bits (letter on the left), index [0] for the lower 8 bits (letter on the right).
    // Status Bits of register F (high to low) = [zero, subtract, half-carry, carry, unused, unused, unused, unused]
    public RegisterSplit16Bit AF;
    public RegisterSplit16Bit BC;
    public RegisterSplit16Bit DE;
    public RegisterSplit16Bit HL;
    public RegisterSplit16Bit SP;
    public RegisterSplit16Bit PC;

    public Register8Bit A;
    public FlagRegister F;
    public Register8Bit B;
    public Register8Bit C;
    public Register8Bit D;
    public Register8Bit E;
    public Register8Bit H;
    public Register8Bit L;

    // This fake register is used to perform arithmetic operations on values stored in memory.
    public AddressRegister addressRegister;

    public int flagHalt = 0;
    public boolean isHaltBug = false;
    public int flagIME = 0;
    public int pendingEICounter = -1;

    // Store the most recent data so that it can be accessed later.
    public int address = -1;
    public String instruction = "?";
    public int opcode;
    public int numMCycles = -1;
    public int numTCycles = -1;

    public ALU(AddressMap addressMap) {
        this.addressMap = addressMap;

        F = new FlagRegister();

        A = new Register8Bit(F);
        B = new Register8Bit(F);
        C = new Register8Bit(F);
        D = new Register8Bit(F);
        E = new Register8Bit(F);
        H = new Register8Bit(F);
        L = new Register8Bit(F);

        AF = new RegisterSplit16Bit(A, F, F);
        BC = new RegisterSplit16Bit(B, C, F);
        DE = new RegisterSplit16Bit(D, E, F);
        HL = new RegisterSplit16Bit(H, L, F);
        SP = new RegisterSplit16Bit(new Register8Bit(F), new Register8Bit(F), F);
        PC = new RegisterSplit16Bit(new Register8Bit(F), new Register8Bit(F), F);

        addressRegister = new AddressRegister(addressMap, F);

        // All other initial values will be set by the BIOS.
        PC.setInt(0);
    }

    public void checkPendingEI() {
        if(pendingEICounter > 0) {
            pendingEICounter--;
        }
        else if(pendingEICounter == 0) {
            pendingEICounter = -1;
            flagIME = 1;
        }
    }

    public void pop(RegisterSplit16Bit R) {
        R.setShort(addressMap.loadShort(SP.getInt()));
        SP.increment();
        SP.increment();
    }

    public void push(RegisterSplit16Bit R) {
        SP.decrement();
        SP.decrement();
        addressMap.storeShort(SP.getInt(), R.getShort());
    }

    public void jump(int address) {
        PC.setInt(address);
    }

    public void jumpRelative(int offset) {
        int address = PC.getInt();
        PC.setInt(address + offset);
    }

    public void call(int address) {
        push(PC);
        jump(address);
    }

    public void ret() {
        pop(PC);
    }

    public void ei() {
        // The IME flag will be set after the next instruction, unless that instruction is DI in which case the flag will remain unset.
        pendingEICounter = 1;
    }

    public void di() {
        // This instruction acts immediately and cancels out a prior EI.
        pendingEICounter = -1;
        flagIME = 0;
    }

    public void halt(boolean isInitialHalt) {
        // Enter or exit halt mode based on whether any interrupt is both enabled and requested.
        // The value of the IME flag doesn't matter here.
        // Note: We do not implement the halt skip bug.
        int reg_ie = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_IE))) & 0b00011111;
        int reg_if = (Byte.toUnsignedInt(addressMap.loadByte(AddressMap.ADDRESS_IF))) & 0b00011111;
        flagHalt = (reg_ie & reg_if) == 0 ? 1 : 0;

        if(isInitialHalt && flagIME == 0 && flagHalt == 0) {
            // This state triggers the halt bug.
            isHaltBug = true;
        }
    }

    public byte fetchByte() {
        // Fetch the next byte and increment PC.
        int address = PC.getInt();
        PC.increment();

        //The halt bug causes the PC register to not increment once.
        if(isHaltBug) {
            PC.decrement();
            isHaltBug = false;
        }
        
        return addressMap.loadByte(address);
    }

    public short fetchShort() {
        // Fetch the next 2 bytes to form a short and increment PC twice.
        // The lower byte is in the lower address.
        int address = PC.getInt();
        PC.increment();

        //The halt bug causes the PC register to not increment once.
        if(isHaltBug) {
            PC.decrement();
            isHaltBug = false;
        }
        
        PC.increment();
        return addressMap.loadShort(address);
    }

    public int fetchSignedInt() {
        // Fetch a unsigned int and increment PC.
        return (int)fetchByte();
    }

    public int fetchUnsignedInt() {
        // Fetch an unsigned int and increment PC.
        return Byte.toUnsignedInt(fetchByte());
    }

    public int fetchAddress() {
        // Fetch the next 2 bytes to form an int address and increment PC twice.
        // The lower byte is in the lower address.
        return Short.toUnsignedInt(fetchShort());
    }

    public int fetchOpcode() {
        // Interrupts have highest priority.
        int opcode = getInterruptOpcode(false);

        if(opcode != -1) {
            // When we push PC to the stack, if the higher byte alters IE and/or IF, it could affect the interrupt dispatch.
            // If the lower byte is altered, then it is too late to have any effect.
            // This means we must manually do the push in two separate steps.
            SP.decrement();
            addressMap.storeByte(SP.getInt(), PC.upperRegister.getByte());

            // At this point, update the determination of what interrupt is present.
            // Even if the interrupt is cancelled, we still move forward with interrupt dispatching.
            opcode = getInterruptOpcode(true);

            SP.decrement();
            addressMap.storeByte(SP.getInt(), PC.lowerRegister.getByte());
        }
        else if(flagHalt == 0) {
            // If there are no interrupts, fetch a real opcode unless we are halting.
            opcode = fetchUnsignedInt();
            if(opcode == 0xCB) {
                // Extended opcode requires another byte.
                opcode = (opcode << 8) | fetchUnsignedInt();
            }
        }

        return opcode;
    }

    public int getInterruptOpcode(boolean canCancel) {
        // Returns a fake opcode that tells the ALU to execute the interrupt.
        // If there are more than one possible interrupt, the highest priority one (lowest bit) is returned.
        int opcode = -1;

        if(flagIME == 1) {
            int reg_ie = addressMap.loadByte(AddressMap.ADDRESS_IE);
            int reg_if = addressMap.loadByte(AddressMap.ADDRESS_IF);

            // VBlank
            if(((reg_ie & 0b1) == 1) && ((reg_if & 0b1) == 1)) {
                opcode = 900;
            }

            // STAT
            else if((((reg_ie >>> 1) & 0b1) == 1) && (((reg_if >>> 1) & 0b1) == 1)) {
                opcode = 901;
            }

            // Timer
            else if((((reg_ie >>> 2) & 0b1) == 1) && (((reg_if >>> 2) & 0b1) == 1)) {
                opcode = 902;
            }

            // Serial
            else if((((reg_ie >>> 3) & 0b1) == 1) && (((reg_if >>> 3) & 0b1) == 1)) {
                opcode = 903;
            }

            // Joypad
            else if((((reg_ie >>> 4) & 0b1) == 1) && (((reg_if >>> 4) & 0b1) == 1)) {
                opcode = 904;
            }

            // Cancelled Interrupt
            else if(canCancel){
                opcode = 800;
            }
        }

        return opcode;
    }

    public void unimplementedOpcode(int opcode) {
        throw new IllegalStateException("Unrecognized Opcode: " + opcode);
    }

    public void processOpcode() {
        checkPendingEI();

        address = PC.getInt();
        opcode = fetchOpcode();

        // Produce state here so we have the address and instruction prior to executing and changing state.
        produceState(address, opcode, A.getInt(), F.getInt(), B.getInt(), C.getInt(), D.getInt(), E.getInt(), H.getInt(), L.getInt(), SP.getInt(), PC.getInt(), flagHalt, flagIME);

        switch(opcode) {
        // Halting
        case -1:
            instruction = "[HALTING]";

            numMCycles = 0;

            halt(false);
            break;

        // Interrupts
        case 800:
            instruction = "<(CANCELLED)>";

            numMCycles = 5;

            flagIME = 0;
            jump(0x0000);
            break;

        case 900:
            instruction = "<VBLANK>";

            numMCycles = 5;

            flagIME = 0;
            flagHalt = 0;
            addressMap.storeBit(AddressMap.ADDRESS_IF, 0, 0);
            jump(0x0040);
            break;

        case 901:
            instruction = "<STAT>";

            numMCycles = 5;

            flagIME = 0;
            flagHalt = 0;
            addressMap.storeBit(AddressMap.ADDRESS_IF, 1, 0);
            jump(0x0048);
            break;

        case 902:
            instruction = "<TIMER>";

            numMCycles = 5;

            flagIME = 0;
            flagHalt = 0;
            addressMap.storeBit(AddressMap.ADDRESS_IF, 2, 0);
            jump(0x0050);
            break;

        case 903:
            instruction = "<SERIAL>";

            numMCycles = 5;

            flagIME = 0;
            flagHalt = 0;
            addressMap.storeBit(AddressMap.ADDRESS_IF, 3, 0);
            jump(0x0058);
            break;

        case 904:
            instruction = "<JOYPAD>";

            numMCycles = 5;

            flagIME = 0;
            flagHalt = 0;
            addressMap.storeBit(AddressMap.ADDRESS_IF, 4, 0);
            jump(0x0060);
            break;

        // Regular Opcodes
        case 0x00:
            instruction = "NOP";

            numMCycles = 1;
            break;

        case 0x01:
            instruction = "LD BC, d16";

            numMCycles = 3;

            BC.setShort(fetchShort());
            break;

        case 0x02:
            instruction = "LD (BC), A";

            numMCycles = 2;

            addressMap.storeByte(BC.getInt(), A.getByte());
            break;

        case 0x03:
            instruction = "INC BC";

            numMCycles = 2;

            BC.increment();
            break;

        case 0x04:
            instruction = "INC B";

            numMCycles = 1;

            B.increment();
            break;

        case 0x05:
            instruction = "DEC B";

            numMCycles = 1;

            B.decrement();
            break;

        case 0x06:
            instruction = "LD B, d8";

            numMCycles = 2;
            
            B.setByte(fetchByte());
            break;
        
        case 0x07:
            instruction = "RLCA";

            numMCycles = 1;

            A.rlc();
            F.setZeroFlag(0);
            break;
            
        case 0x08:
            instruction = "LD (a16), SP";

            numMCycles = 5;

            addressMap.storeShort(fetchAddress(), SP.getShort());
            break;

        case 0x09:
            instruction = "ADD HL, BC";

            numMCycles = 2;

            HL.add(BC);
            break;

        case 0x0A:
            instruction = "LD A, (BC)";

            numMCycles = 2;

            A.setByte(addressMap.loadByte(BC.getInt()));
            break;

        case 0x0B:
            instruction = "DEC BC";

            numMCycles = 2;

            BC.decrement();
            break;

        case 0x0C:
            instruction = "INC C";

            numMCycles = 1;

            C.increment();
            break;

        case 0x0D:
            instruction = "DEC C";

            numMCycles = 1;

            C.decrement();
            break;

        case 0x0E:
            instruction = "LD C, d8";

            numMCycles = 2;

            C.setByte(fetchByte());
            break;

        case 0x0F:
            instruction = "RRCA";

            numMCycles = 1;

            A.rrc();
            F.setZeroFlag(0);
            break;

        case 0x10:
            instruction = "STOP";

            numMCycles = 1;

            // Licensed games don't ever use this so just treat it like a NOP.
            break;

        case 0x11:
            instruction = "LD DE, d16";

            numMCycles = 3;

            DE.setShort(fetchShort());
            break;

        case 0x12:
            instruction = "LD (DE), A";

            numMCycles = 2;

            addressMap.storeByte(DE.getInt(), A.getByte());
            break;

        case 0x13:
            instruction = "INC DE";

            numMCycles = 2;

            DE.increment();
            break;

        case 0x14:
            instruction = "INC D";

            numMCycles = 1;

            D.increment();
            break;

        case 0x15:
            instruction = "DEC D";

            numMCycles = 1;

            D.decrement();
            break;

        case 0x16:
            instruction = "LD D, d8";

            numMCycles = 2;
            
            D.setByte(fetchByte());
            break;
        
        case 0x17:
            instruction = "RLA";

            numMCycles = 1;

            A.rl();
            F.setZeroFlag(0);
            break;
            
        case 0x18:
            instruction = "JR s8";

            numMCycles = 3;

            jumpRelative(fetchSignedInt());
            break;

        case 0x19:
            instruction = "ADD HL, DE";

            numMCycles = 2;

            HL.add(DE);
            break;

        case 0x1A:
            instruction = "LD A, (DE)";

            numMCycles = 2;

            A.setByte(addressMap.loadByte(DE.getInt()));
            break;

        case 0x1B:
            instruction = "DEC DE";

            numMCycles = 2;

            DE.decrement();
            break;

        case 0x1C:
            instruction = "INC E";

            numMCycles = 1;

            E.increment();
            break;

        case 0x1D:
            instruction = "DEC E";

            numMCycles = 1;

            E.decrement();
            break;

        case 0x1E:
            instruction = "LD E, d8";

            numMCycles = 2;

            E.setByte(fetchByte());
            break;

        case 0x1F:
            instruction = "RRA";

            numMCycles = 1;

            A.rr();
            F.setZeroFlag(0);
            break;

        case 0x20:
            instruction = "JR NZ, s8";

            if(F.getZeroFlag() == 0) {
                numMCycles = 3;
                jumpRelative(fetchSignedInt());
            }
            else {
                // We still must consume the next byte.
                numMCycles = 2;
                fetchSignedInt();
            }
            break;

        case 0x21:
            instruction = "LD HL, d16";

            numMCycles = 3;

            HL.setShort(fetchShort());
            break;

        case 0x22:
            instruction = "LD (HL+), A";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), A.getByte());
            HL.increment();
            break;

        case 0x23:
            instruction = "INC HL";

            numMCycles = 2;

            HL.increment();
            break;

        case 0x24:
            instruction = "INC H";

            numMCycles = 1;

            H.increment();
            break;

        case 0x25:
            instruction = "DEC H";

            numMCycles = 1;

            H.decrement();
            break;

        case 0x26:
            instruction = "LD H, d8";

            numMCycles = 2;
            
            H.setByte(fetchByte());
            break;
        
        case 0x27:
            instruction = "DAA";

            numMCycles = 1;

            A.decimalAdjust();
            break;
            
        case 0x28:
            instruction = "JR Z, s8";

            if(F.getZeroFlag() == 1) {
                numMCycles = 3;
                jumpRelative(fetchSignedInt());
            }
            else {
                // We still must consume the next byte.
                numMCycles = 2;
                fetchSignedInt();
            }
            break;

        case 0x29:
            instruction = "ADD HL, HL";

            numMCycles = 2;

            HL.add(HL);
            break;

        case 0x2A:
            instruction = "LD A, (HL+)";

            numMCycles = 2;

            A.setByte(addressMap.loadByte(HL.getInt()));
            HL.increment();
            break;

        case 0x2B:
            instruction = "DEC HL";

            numMCycles = 2;

            HL.decrement();
            break;

        case 0x2C:
            instruction = "INC L";

            numMCycles = 1;

            L.increment();
            break;

        case 0x2D:
            instruction = "DEC L";

            numMCycles = 1;

            L.decrement();
            break;

        case 0x2E:
            instruction = "LD L, d8";

            numMCycles = 2;

            L.setByte(fetchByte());
            break;

        case 0x2F:
            instruction = "CPL";

            numMCycles = 1;

            A.flip();
            break;

        case 0x30:
            instruction = "JR NC, s8";
            
            if(F.getCarryFlag() == 0) {
                numMCycles = 3;
                jumpRelative(fetchSignedInt());
            }
            else {
                // We still must consume the next byte.
                numMCycles = 2;
                fetchSignedInt();
            }
            break;

        case 0x31:
            instruction = "LD SP, d16";

            numMCycles = 3;

            SP.setShort(fetchShort());
            break;

        case 0x32:
            instruction = "LD (HL-), A";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), A.getByte());
            HL.decrement();
            break;

        case 0x33:
            instruction = "INC SP";

            numMCycles = 2;

            SP.increment();
            break;

        case 0x34:
            instruction = "INC (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.increment();
            break;

        case 0x35:
            instruction = "DEC (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.decrement();
            break;

        case 0x36:
            instruction = "LD (HL), d8";

            numMCycles = 3;

            addressMap.storeByte(HL.getInt(), fetchByte());
            break;
        
        case 0x37:
            instruction = "SCF";

            numMCycles = 1;

            F.setSubtractFlag(0);
            F.setHalfCarryFlag(0);
            F.setCarryFlag(1);
            break;
            
        case 0x38:
            instruction = "JR C, s8";

            if(F.getCarryFlag() == 1) {
                numMCycles = 3;
                jumpRelative(fetchSignedInt());
            }
            else {
                // We still must consume the next byte.
                numMCycles = 2;
                fetchSignedInt();
            }
            break;

        case 0x39:
            instruction = "ADD HL, SP";

            numMCycles = 2;

            HL.add(SP);
            break;

        case 0x3A:
            instruction = "LD A, (HL-)";

            numMCycles = 2;

            A.setByte(addressMap.loadByte(HL.getInt()));
            HL.decrement();
            break;

        case 0x3B:
            instruction = "DEC SP";

            numMCycles = 2;

            SP.decrement();
            break;

        case 0x3C:
            instruction = "INC A";

            numMCycles = 1;

            A.increment();
            break;

        case 0x3D:
            instruction = "DEC A";

            numMCycles = 1;

            A.decrement();
            break;

        case 0x3E:
            instruction = "LD A, d8";

            numMCycles = 2;

            A.setByte(fetchByte());
            break;

        case 0x3F:
            instruction = "CCF";

            numMCycles = 1;

            F.setSubtractFlag(0);
            F.setHalfCarryFlag(0);
            F.flipCarryFlag();
            break;

        case 0x40:
            instruction = "LD B, B";

            numMCycles = 1;

            B.setByte(B.getByte());
            break;

        case 0x41:
            instruction = "LD B, C";

            numMCycles = 1;

            B.setByte(C.getByte());
            break;

        case 0x42:
            instruction = "LD B, D";

            numMCycles = 1;

            B.setByte(D.getByte());
            break;

        case 0x43:
            instruction = "LD B, E";

            numMCycles = 1;

            B.setByte(E.getByte());
            break;

        case 0x44:
            instruction = "LD B, H";

            numMCycles = 1;

            B.setByte(H.getByte());
            break;

        case 0x45:
            instruction = "LD B, L";

            numMCycles = 1;

            B.setByte(L.getByte());
            break;

        case 0x46:
            instruction = "LD B, (HL)";

            numMCycles = 2;

            B.setByte(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0x47:
            instruction = "LD B, A";

            numMCycles = 1;

            B.setByte(A.getByte());
            break;
            
        case 0x48:
            instruction = "LD C, B";

            numMCycles = 1;

            C.setByte(B.getByte());
            break;

        case 0x49:
            instruction = "LD C, C";

            numMCycles = 1;

            C.setByte(C.getByte());
            break;

        case 0x4A:
            instruction = "LD C, D";

            numMCycles = 1;

            C.setByte(D.getByte());
            break;

        case 0x4B:
            instruction = "LD C, E";

            numMCycles = 1;

            C.setByte(E.getByte());
            break;

        case 0x4C:
            instruction = "LD C, H";

            numMCycles = 1;

            C.setByte(H.getByte());
            break;

        case 0x4D:
            instruction = "LD C, L";

            numMCycles = 1;

            C.setByte(L.getByte());
            break;

        case 0x4E:
            instruction = "LD C, (HL)";

            numMCycles = 2;

            C.setByte(addressMap.loadByte(HL.getInt()));
            break;

        case 0x4F:
            instruction = "LD C, A";

            numMCycles = 1;

            C.setByte(A.getByte());
            break;

        case 0x50:
            instruction = "LD D, B";

            numMCycles = 1;

            D.setByte(B.getByte());
            break;

        case 0x51:
            instruction = "LD D, C";

            numMCycles = 1;

            D.setByte(C.getByte());
            break;

        case 0x52:
            instruction = "LD D, D";

            numMCycles = 1;

            D.setByte(D.getByte());
            break;

        case 0x53:
            instruction = "LD D, E";

            numMCycles = 1;

            D.setByte(E.getByte());
            break;

        case 0x54:
            instruction = "LD D, H";

            numMCycles = 1;

            D.setByte(H.getByte());
            break;

        case 0x55:
            instruction = "LD D, L";

            numMCycles = 1;

            D.setByte(L.getByte());
            break;

        case 0x56:
            instruction = "LD D, (HL)";

            numMCycles = 2;

            D.setByte(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0x57:
            instruction = "LD D, A";

            numMCycles = 1;

            D.setByte(A.getByte());
            break;
            
        case 0x58:
            instruction = "LD E, B";

            numMCycles = 1;

            E.setByte(B.getByte());
            break;

        case 0x59:
            instruction = "LD E, C";

            numMCycles = 1;

            E.setByte(C.getByte());
            break;

        case 0x5A:
            instruction = "LD E, D";

            numMCycles = 1;

            E.setByte(D.getByte());
            break;

        case 0x5B:
            instruction = "LD E, E";

            numMCycles = 1;

            E.setByte(E.getByte());
            break;

        case 0x5C:
            instruction = "LD E, H";

            numMCycles = 1;

            E.setByte(H.getByte());
            break;

        case 0x5D:
            instruction = "LD E, L";

            numMCycles = 1;

            E.setByte(L.getByte());
            break;

        case 0x5E:
            instruction = "LD E, (HL)";

            numMCycles = 2;

            E.setByte(addressMap.loadByte(HL.getInt()));
            break;

        case 0x5F:
            instruction = "LD E, A";

            numMCycles = 1;

            E.setByte(A.getByte());
            break;

        case 0x60:
            instruction = "LD H, B";

            numMCycles = 1;

            H.setByte(B.getByte());
            break;

        case 0x61:
            instruction = "LD H, C";

            numMCycles = 1;

            H.setByte(C.getByte());
            break;

        case 0x62:
            instruction = "LD H, D";

            numMCycles = 1;

            H.setByte(D.getByte());
            break;

        case 0x63:
            instruction = "LD H, E";

            numMCycles = 1;

            H.setByte(E.getByte());
            break;

        case 0x64:
            instruction = "LD H, H";

            numMCycles = 1;

            H.setByte(H.getByte());
            break;

        case 0x65:
            instruction = "LD H, L";

            numMCycles = 1;

            H.setByte(L.getByte());
            break;

        case 0x66:
            instruction = "LD H, (HL)";

            numMCycles = 2;

            H.setByte(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0x67:
            instruction = "LD H, A";

            numMCycles = 1;

            H.setByte(A.getByte());
            break;
            
        case 0x68:
            instruction = "LD L, B";

            numMCycles = 1;

            L.setByte(B.getByte());
            break;

        case 0x69:
            instruction = "LD L, C";

            numMCycles = 1;

            L.setByte(C.getByte());
            break;

        case 0x6A:
            instruction = "LD L, D";

            numMCycles = 1;

            L.setByte(D.getByte());
            break;

        case 0x6B:
            instruction = "LD L, E";

            numMCycles = 1;

            L.setByte(E.getByte());
            break;

        case 0x6C:
            instruction = "LD L, H";

            numMCycles = 1;

            L.setByte(H.getByte());
            break;

        case 0x6D:
            instruction = "LD L, L";

            numMCycles = 1;

            L.setByte(L.getByte());
            break;

        case 0x6E:
            instruction = "LD L, (HL)";

            numMCycles = 2;

            L.setByte(addressMap.loadByte(HL.getInt()));
            break;

        case 0x6F:
            instruction = "LD L, A";

            numMCycles = 1;

            L.setByte(A.getByte());
            break;

        case 0x70:
            instruction = "LD (HL), B";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), B.getByte());
            break;

        case 0x71:
            instruction = "LD (HL), C";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), C.getByte());
            break;

        case 0x72:
            instruction = "LD (HL), D";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), D.getByte());
            break;

        case 0x73:
            instruction = "LD (HL), E";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), E.getByte());
            break;

        case 0x74:
            instruction = "LD (HL), H";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), H.getByte());
            break;

        case 0x75:
            instruction = "LD (HL), L";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), L.getByte());
            break;

        case 0x76:
            instruction = "HALT";

            numMCycles = 1;

            halt(true);
            break;
        
        case 0x77:
            instruction = "LD (HL), A";

            numMCycles = 2;

            addressMap.storeByte(HL.getInt(), A.getByte());
            break;
            
        case 0x78:
            instruction = "LD A, B";

            numMCycles = 1;

            A.setByte(B.getByte());
            break;

        case 0x79:
            instruction = "LD A, C";

            numMCycles = 1;

            A.setByte(C.getByte());
            break;

        case 0x7A:
            instruction = "LD A, D";

            numMCycles = 1;

            A.setByte(D.getByte());
            break;

        case 0x7B:
            instruction = "LD A, E";

            numMCycles = 1;

            A.setByte(E.getByte());
            break;

        case 0x7C:
            instruction = "LD A, H";

            numMCycles = 1;

            A.setByte(H.getByte());
            break;

        case 0x7D:
            instruction = "LD A, L";

            numMCycles = 1;

            A.setByte(L.getByte());
            break;

        case 0x7E:
            instruction = "LD A, (HL)";

            numMCycles = 2;

            A.setByte(addressMap.loadByte(HL.getInt()));
            break;

        case 0x7F:
            instruction = "LD A, A";

            numMCycles = 1;

            A.setByte(A.getByte());
            break;

        case 0x80:
            instruction = "ADD A, B";

            numMCycles = 1;

            A.add(B);
            break;

        case 0x81:
            instruction = "ADD A, C";

            numMCycles = 1;

            A.add(C);
            break;

        case 0x82:
            instruction = "ADD A, D";

            numMCycles = 1;

            A.add(D);
            break;

        case 0x83:
            instruction = "ADD A, E";

            numMCycles = 1;

            A.add(E);
            break;

        case 0x84:
            instruction = "ADD A, H";

            numMCycles = 1;

            A.add(H);
            break;

        case 0x85:
            instruction = "ADD A, L";

            numMCycles = 1;

            A.add(L);
            break;

        case 0x86:
            instruction = "ADD A, (HL)";

            numMCycles = 2;

            A.add(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0x87:
            instruction = "ADD A, A";

            numMCycles = 1;

            A.add(A);
            break;
            
        case 0x88:
            instruction = "ADC A, B";

            numMCycles = 1;

            A.adc(B);
            break;

        case 0x89:
            instruction = "ADC A, C";

            numMCycles = 1;

            A.adc(C);
            break;

        case 0x8A:
            instruction = "ADC A, D";

            numMCycles = 1;

            A.adc(D);
            break;

        case 0x8B:
            instruction = "ADC A, E";

            numMCycles = 1;

            A.adc(E);
            break;

        case 0x8C:
            instruction = "ADC A, H";

            numMCycles = 1;

            A.adc(H);
            break;

        case 0x8D:
            instruction = "ADC A, L";

            numMCycles = 1;

            A.adc(L);
            break;

        case 0x8E:
            instruction = "ADC A, (HL)";

            numMCycles = 2;

            A.adc(addressMap.loadByte(HL.getInt()));
            break;

        case 0x8F:
            instruction = "ADC A, A";

            numMCycles = 1;

            A.adc(A);
            break;

        case 0x90:
            instruction = "SUB A, B";

            numMCycles = 1;

            A.sub(B);
            break;

        case 0x91:
            instruction = "SUB A, C";

            numMCycles = 1;

            A.sub(C);
            break;

        case 0x92:
            instruction = "SUB A, D";

            numMCycles = 1;

            A.sub(D);
            break;

        case 0x93:
            instruction = "SUB A, E";

            numMCycles = 1;

            A.sub(E);
            break;

        case 0x94:
            instruction = "SUB A, H";

            numMCycles = 1;

            A.sub(H);
            break;

        case 0x95:
            instruction = "SUB A, L";

            numMCycles = 1;

            A.sub(L);
            break;

        case 0x96:
            instruction = "SUB A, (HL)";

            numMCycles = 2;

            A.sub(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0x97:
            instruction = "SUB A, A";

            numMCycles = 1;

            A.sub(A);
            break;
            
        case 0x98:
            instruction = "SBC A, B";

            numMCycles = 1;

            A.sbc(B);
            break;

        case 0x99:
            instruction = "SBC A, C";

            numMCycles = 1;

            A.sbc(C);
            break;

        case 0x9A:
            instruction = "SBC A, D";

            numMCycles = 1;

            A.sbc(D);
            break;

        case 0x9B:
            instruction = "SBC A, E";

            numMCycles = 1;

            A.sbc(E);
            break;

        case 0x9C:
            instruction = "SBC A, H";

            numMCycles = 1;

            A.sbc(H);
            break;

        case 0x9D:
            instruction = "SBC A, L";

            numMCycles = 1;

            A.sbc(L);
            break;

        case 0x9E:
            instruction = "SBC A, (HL)";

            numMCycles = 2;

            A.sbc(addressMap.loadByte(HL.getInt()));
            break;

        case 0x9F:
            instruction = "SBC A, A";

            numMCycles = 1;

            A.sbc(A);
            break;

        case 0xA0:
            instruction = "AND A, B";

            numMCycles = 1;

            A.and(B);
            break;

        case 0xA1:
            instruction = "AND A, C";

            numMCycles = 1;

            A.and(C);
            break;

        case 0xA2:
            instruction = "AND A, D";

            numMCycles = 1;

            A.and(D);
            break;

        case 0xA3:
            instruction = "AND A, E";

            numMCycles = 1;

            A.and(E);
            break;

        case 0xA4:
            instruction = "AND A, H";

            numMCycles = 1;

            A.and(H);
            break;

        case 0xA5:
            instruction = "AND A, L";

            numMCycles = 1;

            A.and(L);
            break;

        case 0xA6:
            instruction = "AND A, (HL)";

            numMCycles = 2;

            A.and(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0xA7:
            instruction = "AND A, A";

            numMCycles = 1;

            A.and(A);
            break;
            
        case 0xA8:
            instruction = "XOR A, B";

            numMCycles = 1;

            A.xor(B);
            break;

        case 0xA9:
            instruction = "XOR A, C";

            numMCycles = 1;

            A.xor(C);
            break;

        case 0xAA:
            instruction = "XOR A, D";

            numMCycles = 1;

            A.xor(D);
            break;

        case 0xAB:
            instruction = "XOR A, E";

            numMCycles = 1;

            A.xor(E);
            break;

        case 0xAC:
            instruction = "XOR A, H";

            numMCycles = 1;

            A.xor(H);
            break;

        case 0xAD:
            instruction = "XOR A, L";

            numMCycles = 1;

            A.xor(L);
            break;

        case 0xAE:
            instruction = "XOR A, (HL)";

            numMCycles = 2;

            A.xor(addressMap.loadByte(HL.getInt()));
            break;

        case 0xAF:
            instruction = "XOR A, A";

            numMCycles = 1;

            A.xor(A);
            break;

        case 0xB0:
            instruction = "OR A, B";

            numMCycles = 1;

            A.or(B);
            break;

        case 0xB1:
            instruction = "OR A, C";

            numMCycles = 1;

            A.or(C);
            break;

        case 0xB2:
            instruction = "OR A, D";

            numMCycles = 1;

            A.or(D);
            break;

        case 0xB3:
            instruction = "OR A, E";

            numMCycles = 1;

            A.or(E);
            break;

        case 0xB4:
            instruction = "OR A, H";

            numMCycles = 1;

            A.or(H);
            break;

        case 0xB5:
            instruction = "OR A, L";

            numMCycles = 1;

            A.or(L);
            break;

        case 0xB6:
            instruction = "OR A, (HL)";

            numMCycles = 2;

            A.or(addressMap.loadByte(HL.getInt()));
            break;
        
        case 0xB7:
            instruction = "OR A, A";

            numMCycles = 1;

            A.or(A);
            break;
            
        case 0xB8:
            instruction = "CP A, B";

            numMCycles = 1;

            A.compare(B);
            break;

        case 0xB9:
            instruction = "CP A, C";

            numMCycles = 1;

            A.compare(C);
            break;

        case 0xBA:
            instruction = "CP A, D";

            numMCycles = 1;

            A.compare(D);
            break;

        case 0xBB:
            instruction = "CP A, E";

            numMCycles = 1;

            A.compare(E);
            break;

        case 0xBC:
            instruction = "CP A, H";

            numMCycles = 1;

            A.compare(H);
            break;

        case 0xBD:
            instruction = "CP A, L";

            numMCycles = 1;

            A.compare(L);
            break;

        case 0xBE:
            instruction = "CP A, (HL)";

            numMCycles = 2;

            A.compare(addressMap.loadByte(HL.getInt()));
            break;

        case 0xBF:
            instruction = "CP A, A";

            numMCycles = 1;

            A.compare(A);
            break;

        case 0xC0:
            instruction = "RET NZ";

            if(F.getZeroFlag() == 0) {
                numMCycles = 5;
                ret();
            }
            else {
                numMCycles = 2;
            }
            break;

        case 0xC1:
            instruction = "POP BC";

            numMCycles = 3;

            pop(BC);
            break;

        case 0xC2:
            instruction = "JP NZ, a16";

            if(F.getZeroFlag() == 0) {
                numMCycles = 4;
                jump(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xC3:
            instruction = "JP a16";

            numMCycles = 4;

            jump(fetchAddress());
            break;

        case 0xC4:
            instruction = "CALL NZ, a16";
            
            if(F.getZeroFlag() == 0) {
                numMCycles = 6;
                call(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xC5:
            instruction = "PUSH BC";

            numMCycles = 4;

            push(BC);
            break;

        case 0xC6:
            instruction = "ADD A, d8";

            numMCycles = 2;

            A.add(fetchByte());
            break;
        
        case 0xC7:
            instruction = "RST 0";

            numMCycles = 4;

            call(0);
            break;
            
        case 0xC8:
            instruction = "RET Z";

            if(F.getZeroFlag() == 1) {
                numMCycles = 5;
                ret();
            }
            else {
                numMCycles = 2;
            }
            break;

        case 0xC9:
            instruction = "RET";

            numMCycles = 4;

            ret();
            break;

        case 0xCA:
            instruction = "JP Z, a16";

            if(F.getZeroFlag() == 1) {
                numMCycles = 4;
                jump(fetchAddress());
            }
            else {
                // We still must consume the next address.
                fetchAddress();
                numMCycles = 3;
            }
            break;

        // 0xCB Indicates an extended opcode.

        case 0xCC:
            instruction = "CALL Z, a16";

            if(F.getZeroFlag() == 1) {
                numMCycles = 6;
                call(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xCD:
            instruction = "CALL a16";

            numMCycles = 6;

            call(fetchAddress());
            break;

        case 0xCE:
            instruction = "ADC A, d8";

            numMCycles = 2;

            A.adc(fetchByte());
            break;

        case 0xCF:
            instruction = "RST 1";

            numMCycles = 4;

            call(0x08);
            break;

        case 0xD0:
            instruction = "RET NC";

            if(F.getCarryFlag() == 0) {
                numMCycles = 5;
                ret();
            }
            else {
                numMCycles = 2;
            }
            break;

        case 0xD1:
            instruction = "POP DE";

            numMCycles = 3;

            pop(DE);
            break;

        case 0xD2:
            instruction = "JP NC, a16";

            if(F.getCarryFlag() == 0) {
                numMCycles = 4;
                jump(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xD3:
            unimplementedOpcode(opcode);
            break;

        case 0xD4:
            instruction = "CALL NC, a16";

            if(F.getCarryFlag() == 0) {
                numMCycles = 6;
                call(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xD5:
            instruction = "PUSH DE";

            numMCycles = 4;

            push(DE);
            break;

        case 0xD6:
            instruction = "SUB A, d8";

            numMCycles = 2;

            A.sub(fetchByte());
            break;
        
        case 0xD7:
            instruction = "RST 2";

            numMCycles = 4;

            call(0x10);
            break;
            
        case 0xD8:
            instruction = "RET C";

            if(F.getCarryFlag() == 1) {
                numMCycles = 5;
                ret();
            }
            else {
                numMCycles = 2;
            }
            break;

        case 0xD9:
            instruction = "RETI";

            numMCycles = 4;

            flagIME = 1;
            ret();
            break;

        case 0xDA:
            instruction = "JP C, a16";

            if(F.getCarryFlag() == 1) {
                numMCycles = 4;
                jump(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xDB:
            unimplementedOpcode(opcode);
            break;

        case 0xDC:
            instruction = "CALL C, a16";

            if(F.getCarryFlag() == 1) {
                numMCycles = 6;
                call(fetchAddress());
            }
            else {
                // We still must consume the next address.
                numMCycles = 3;
                fetchAddress();
            }
            break;

        case 0xDD:
            unimplementedOpcode(opcode);
            break;

        case 0xDE:
            instruction = "SBC A, d8";

            numMCycles = 2;

            A.sbc(fetchByte());
            break;

        case 0xDF:
            instruction = "RST 3";

            numMCycles = 4;

            call(0x18);
            break;

        case 0xE0:
            instruction = "LD (a8), A";

            numMCycles = 3;

            addressMap.storeByte(0xFF00 | fetchUnsignedInt(), A.getByte());
            break;

        case 0xE1:
            instruction = "POP HL";

            numMCycles = 3;

            pop(HL);
            break;

        case 0xE2:
            instruction = "LD (C), A";

            numMCycles = 2;

            addressMap.storeByte(0xFF00 | C.getInt(), A.getByte());
            break;

        case 0xE3:
            unimplementedOpcode(opcode);
            break;

        case 0xE4:
            unimplementedOpcode(opcode);
            break;

        case 0xE5:
            instruction = "PUSH HL";

            numMCycles = 4;

            push(HL);
            break;

        case 0xE6:
            instruction = "AND A, d8";

            numMCycles = 2;

            A.and(fetchByte());
            break;
        
        case 0xE7:
            instruction = "RST 4";

            numMCycles = 4;

            call(0x20);
            break;
            
        case 0xE8:
            instruction = "ADD SP, s8";

            numMCycles = 4;

            SP.add2(fetchSignedInt());
            F.setZeroFlag(0);
            break;

        case 0xE9:
            instruction = "JP HL";

            numMCycles = 1;

            jump(HL.getInt());
            break;

        case 0xEA:
            instruction = "LD (a16), A";

            numMCycles = 4;

            addressMap.storeByte(fetchAddress(), A.getByte());
            break;

        case 0xEB:
            unimplementedOpcode(opcode);
            break;

        case 0xEC:
            unimplementedOpcode(opcode);
            break;

        case 0xED:
            unimplementedOpcode(opcode);
            break;

        case 0xEE:
            instruction = "XOR A, d8";

            numMCycles = 2;

            A.xor(fetchByte());
            break;

        case 0xEF:
            instruction = "RST 5";

            numMCycles = 4;

            call(0x28);
            break;

        case 0xF0:
            instruction = "LD A, (a8)";

            numMCycles = 3;

            A.setByte(addressMap.loadByte(0xFF00 | fetchUnsignedInt()));
            break;

        case 0xF1:
            instruction = "POP AF";

            numMCycles = 3;

            pop(AF);
            break;

        case 0xF2:
            instruction = "LD A, (C)";

            numMCycles = 2;

            A.setByte(addressMap.loadByte(0xFF00 | C.getInt()));
            break;

        case 0xF3:
            instruction = "DI";

            numMCycles = 1;

            di();
            break;

        case 0xF4:
            unimplementedOpcode(opcode);
            break;

        case 0xF5:
            instruction = "PUSH AF";

            numMCycles = 4;

            push(AF);
            break;

        case 0xF6:
            instruction = "OR A, d8";

            numMCycles = 2;

            A.or(fetchByte());
            break;
        
        case 0xF7:
            instruction = "RST 6";

            numMCycles = 4;

            call(0x30);
            break;
            
        case 0xF8:
            instruction = "LD HL, SP+s8";

            numMCycles = 3;
            
            HL.setInt(SP.getInt());
            HL.add2(fetchSignedInt());
            F.setZeroFlag(0);
            break;

        case 0xF9:
            instruction = "LD SP, HL";

            numMCycles = 2;

            SP.setInt(HL.getInt());
            break;

        case 0xFA:
            instruction = "LD A, (a16)";

            numMCycles = 4;

            A.setByte(addressMap.loadByte(fetchAddress()));
            break;

        case 0xFB:
            instruction = "EI";

            numMCycles = 1;

            ei();
            break;

        case 0xFC:
            unimplementedOpcode(opcode);
            break;

        case 0xFD:
            unimplementedOpcode(opcode);
            break;

        case 0xFE:
            instruction = "CP A, d8";

            numMCycles = 2;

            A.compare(fetchByte());
            break;

        case 0xFF:
            instruction = "RST 7";

            numMCycles = 4;

            call(0x38);
            break;

        // Extended Opcodes
        case 0xCB00:
            instruction = "RLC B";

            numMCycles = 2;

            B.rlc();
            break;

        case 0xCB01:
            instruction = "RLC C";

            numMCycles = 2;

            C.rlc();
            break;

        case 0xCB02:
            instruction = "RLC D";

            numMCycles = 2;

            D.rlc();
            break;

        case 0xCB03:
            instruction = "RLC E";

            numMCycles = 2;

            E.rlc();
            break;

        case 0xCB04:
            instruction = "RLC H";

            numMCycles = 2;

            H.rlc();
            break;

        case 0xCB05:
            instruction = "RLC L";

            numMCycles = 2;

            L.rlc();
            break;

        case 0xCB06:
            instruction = "RLC (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.rlc();
            break;

        case 0xCB07:
            instruction = "RLC A";

            numMCycles = 2;

            A.rlc();
            break;

        case 0xCB08:
            instruction = "RRC B";

            numMCycles = 2;

            B.rrc();
            break;

        case 0xCB09:
            instruction = "RRC C";

            numMCycles = 2;

            C.rrc();
            break;

        case 0xCB0A:
            instruction = "RRC D";

            numMCycles = 2;

            D.rrc();
            break;

        case 0xCB0B:
            instruction = "RRC E";

            numMCycles = 2;

            E.rrc();
            break;

        case 0xCB0C:
            instruction = "RRC H";

            numMCycles = 2;

            H.rrc();
            break;

        case 0xCB0D:
            instruction = "RRC L";

            numMCycles = 2;

            L.rrc();
            break;

        case 0xCB0E:
            instruction = "RRC (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.rrc();
            break;

        case 0xCB0F:
            instruction = "RRC A";

            numMCycles = 2;

            A.rrc();
            break;

        case 0xCB10:
            instruction = "RL B";

            numMCycles = 2;

            B.rl();
            break;

        case 0xCB11:
            instruction = "RL C";

            numMCycles = 2;

            C.rl();
            break;

        case 0xCB12:
            instruction = "RL D";

            numMCycles = 2;

            D.rl();
            break;

        case 0xCB13:
            instruction = "RL E";

            numMCycles = 2;

            E.rl();
            break;

        case 0xCB14:
            instruction = "RL H";

            numMCycles = 2;

            H.rl();
            break;

        case 0xCB15:
            instruction = "RL L";

            numMCycles = 2;

            L.rl();
            break;

        case 0xCB16:
            instruction = "RL (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.rl();
            break;

        case 0xCB17:
            instruction = "RL A";

            numMCycles = 2;

            A.rl();
            break;

        case 0xCB18:
            instruction = "RR B";

            numMCycles = 2;

            B.rr();
            break;

        case 0xCB19:
            instruction = "RR C";

            numMCycles = 2;

            C.rr();
            break;

        case 0xCB1A:
            instruction = "RR D";

            numMCycles = 2;

            D.rr();
            break;

        case 0xCB1B:
            instruction = "RR E";

            numMCycles = 2;

            E.rr();
            break;

        case 0xCB1C:
            instruction = "RR H";

            numMCycles = 2;

            H.rr();
            break;

        case 0xCB1D:
            instruction = "RR L";

            numMCycles = 2;

            L.rr();
            break;

        case 0xCB1E:
            instruction = "RR (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.rr();
            break;

        case 0xCB1F:
            instruction = "RR A";

            numMCycles = 2;

            A.rr();
            break;

        case 0xCB20:
            instruction = "SLA B";

            numMCycles = 2;

            B.sla();
            break;

        case 0xCB21:
            instruction = "SLA C";

            numMCycles = 2;

            C.sla();
            break;

        case 0xCB22:
            instruction = "SLA D";

            numMCycles = 2;

            D.sla();
            break;

        case 0xCB23:
            instruction = "SLA E";

            numMCycles = 2;

            E.sla();
            break;

        case 0xCB24:
            instruction = "SLA H";

            numMCycles = 2;

            H.sla();
            break;

        case 0xCB25:
            instruction = "SLA L";

            numMCycles = 2;

            L.sla();
            break;

        case 0xCB26:
            instruction = "SLA (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.sla();
            break;

        case 0xCB27:
            instruction = "SLA A";

            numMCycles = 2;

            A.sla();
            break;

        case 0xCB28:
            instruction = "SRA B";

            numMCycles = 2;

            B.sra();
            break;

        case 0xCB29:
            instruction = "SRA C";

            numMCycles = 2;

            C.sra();
            break;

        case 0xCB2A:
            instruction = "SRA D";

            numMCycles = 2;

            D.sra();
            break;

        case 0xCB2B:
            instruction = "SRA E";

            numMCycles = 2;

            E.sra();
            break;

        case 0xCB2C:
            instruction = "SRA H";

            numMCycles = 2;

            H.sra();
            break;

        case 0xCB2D:
            instruction = "SRA L";

            numMCycles = 2;

            L.sra();
            break;

        case 0xCB2E:
            instruction = "SRA (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.sra();
            break;

        case 0xCB2F:
            instruction = "SRA A";

            numMCycles = 2;

            A.sra();
            break;

        case 0xCB30:
            instruction = "SWAP B";

            numMCycles = 2;

            B.swap();
            break;

        case 0xCB31:
            instruction = "SWAP C";

            numMCycles = 2;

            C.swap();
            break;

        case 0xCB32:
            instruction = "SWAP D";

            numMCycles = 2;

            D.swap();
            break;

        case 0xCB33:
            instruction = "SWAP E";

            numMCycles = 2;

            E.swap();
            break;

        case 0xCB34:
            instruction = "SWAP H";

            numMCycles = 2;

            H.swap();
            break;

        case 0xCB35:
            instruction = "SWAP L";

            numMCycles = 2;

            L.swap();
            break;

        case 0xCB36:
            instruction = "SWAP (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.swap();
            break;

        case 0xCB37:
            instruction = "SWAP A";

            numMCycles = 2;

            A.swap();
            break;

        case 0xCB38:
            instruction = "SRL B";

            numMCycles = 2;

            B.srl();
            break;

        case 0xCB39:
            instruction = "SRL C";

            numMCycles = 2;

            C.srl();
            break;

        case 0xCB3A:
            instruction = "SRL D";

            numMCycles = 2;

            D.srl();
            break;

        case 0xCB3B:
            instruction = "SRL E";

            numMCycles = 2;

            E.srl();
            break;

        case 0xCB3C:
            instruction = "SRL H";

            numMCycles = 2;

            H.srl();
            break;

        case 0xCB3D:
            instruction = "SRL L";

            numMCycles = 2;

            L.srl();
            break;

        case 0xCB3E:
            instruction = "SRL (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.srl();
            break;

        case 0xCB3F:
            instruction = "SRL A";

            numMCycles = 2;

            A.srl();
            break;

        case 0xCB40:
            instruction = "BIT 0, B";

            numMCycles = 2;

            B.bit(0);
            break;

        case 0xCB41:
            instruction = "BIT 0, C";

            numMCycles = 2;

            C.bit(0);
            break;

        case 0xCB42:
            instruction = "BIT 0, D";

            numMCycles = 2;

            D.bit(0);
            break;

        case 0xCB43:
            instruction = "BIT 0, E";

            numMCycles = 2;

            E.bit(0);
            break;

        case 0xCB44:
            instruction = "BIT 0, H";

            numMCycles = 2;

            H.bit(0);
            break;

        case 0xCB45:
            instruction = "BIT 0, L";

            numMCycles = 2;

            L.bit(0);
            break;

        case 0xCB46:
            instruction = "BIT 0, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(0);
            break;

        case 0xCB47:
            instruction = "BIT 0, A";

            numMCycles = 2;

            A.bit(0);
            break;

        case 0xCB48:
            instruction = "BIT 1, B";

            numMCycles = 2;

            B.bit(1);
            break;

        case 0xCB49:
            instruction = "BIT 1, C";

            numMCycles = 2;

            C.bit(1);
            break;

        case 0xCB4A:
            instruction = "BIT 1, D";

            numMCycles = 2;

            D.bit(1);
            break;

        case 0xCB4B:
            instruction = "BIT 1, E";

            numMCycles = 2;

            E.bit(1);
            break;

        case 0xCB4C:
            instruction = "BIT 1, H";

            numMCycles = 2;

            H.bit(1);
            break;

        case 0xCB4D:
            instruction = "BIT 1, L";

            numMCycles = 2;

            L.bit(1);
            break;

        case 0xCB4E:
            instruction = "BIT 1, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(1);
            break;

        case 0xCB4F:
            instruction = "BIT 1, A";

            numMCycles = 2;

            A.bit(1);
            break;

        case 0xCB50:
            instruction = "BIT 2, B";

            numMCycles = 2;

            B.bit(2);
            break;

        case 0xCB51:
            instruction = "BIT 2, C";

            numMCycles = 2;

            C.bit(2);
            break;

        case 0xCB52:
            instruction = "BIT 2, D";

            numMCycles = 2;

            D.bit(2);
            break;

        case 0xCB53:
            instruction = "BIT 2, E";

            numMCycles = 2;

            E.bit(2);
            break;

        case 0xCB54:
            instruction = "BIT 2, H";

            numMCycles = 2;

            H.bit(2);
            break;

        case 0xCB55:
            instruction = "BIT 2, L";

            numMCycles = 2;

            L.bit(2);
            break;

        case 0xCB56:
            instruction = "BIT 2, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(2);
            break;

        case 0xCB57:
            instruction = "BIT 2, A";

            numMCycles = 2;

            A.bit(2);
            break;

        case 0xCB58:
            instruction = "BIT 3, B";

            numMCycles = 2;

            B.bit(3);
            break;

        case 0xCB59:
            instruction = "BIT 3, C";

            numMCycles = 2;

            C.bit(3);
            break;

        case 0xCB5A:
            instruction = "BIT 3, D";

            numMCycles = 2;

            D.bit(3);
            break;

        case 0xCB5B:
            instruction = "BIT 3, E";

            numMCycles = 2;

            E.bit(3);
            break;

        case 0xCB5C:
            instruction = "BIT 3, H";

            numMCycles = 2;

            H.bit(3);
            break;

        case 0xCB5D:
            instruction = "BIT 3, L";

            numMCycles = 2;

            L.bit(3);
            break;

        case 0xCB5E:
            instruction = "BIT 3, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(3);
            break;

        case 0xCB5F:
            instruction = "BIT 3, A";

            numMCycles = 2;

            A.bit(3);
            break;

        case 0xCB60:
            instruction = "BIT 4, B";

            numMCycles = 2;

            B.bit(4);
            break;

        case 0xCB61:
            instruction = "BIT 4, C";

            numMCycles = 2;

            C.bit(4);
            break;

        case 0xCB62:
            instruction = "BIT 4, D";

            numMCycles = 2;

            D.bit(4);
            break;

        case 0xCB63:
            instruction = "BIT 4, E";

            numMCycles = 2;

            E.bit(4);
            break;

        case 0xCB64:
            instruction = "BIT 4, H";

            numMCycles = 2;

            H.bit(4);
            break;

        case 0xCB65:
            instruction = "BIT 4, L";

            numMCycles = 2;

            L.bit(4);
            break;

        case 0xCB66:
            instruction = "BIT 4, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(4);
            break;

        case 0xCB67:
            instruction = "BIT 4, A";

            numMCycles = 2;

            A.bit(4);
            break;

        case 0xCB68:
            instruction = "BIT 5, B";

            numMCycles = 2;

            B.bit(5);
            break;

        case 0xCB69:
            instruction = "BIT 5, C";

            numMCycles = 2;

            C.bit(5);
            break;

        case 0xCB6A:
            instruction = "BIT 5, D";

            numMCycles = 2;

            D.bit(5);
            break;

        case 0xCB6B:
            instruction = "BIT 5, E";

            numMCycles = 2;

            E.bit(5);
            break;

        case 0xCB6C:
            instruction = "BIT 5, H";

            numMCycles = 2;

            H.bit(5);
            break;

        case 0xCB6D:
            instruction = "BIT 5, L";

            numMCycles = 2;

            L.bit(5);
            break;

        case 0xCB6E:
            instruction = "BIT 5, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(5);
            break;

        case 0xCB6F:
            instruction = "BIT 5, A";

            numMCycles = 2;

            A.bit(5);
            break;

        case 0xCB70:
            instruction = "BIT 6, B";

            numMCycles = 2;

            B.bit(6);
            break;

        case 0xCB71:
            instruction = "BIT 6, C";

            numMCycles = 2;

            C.bit(6);
            break;

        case 0xCB72:
            instruction = "BIT 6, D";

            numMCycles = 2;

            D.bit(6);
            break;

        case 0xCB73:
            instruction = "BIT 6, E";

            numMCycles = 2;

            E.bit(6);
            break;

        case 0xCB74:
            instruction = "BIT 6, H";

            numMCycles = 2;

            H.bit(6);
            break;

        case 0xCB75:
            instruction = "BIT 6, L";

            numMCycles = 2;

            L.bit(6);
            break;

        case 0xCB76:
            instruction = "BIT 6, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(6);
            break;

        case 0xCB77:
            instruction = "BIT 6, A";

            numMCycles = 2;

            A.bit(6);
            break;

        case 0xCB78:
            instruction = "BIT 7, B";

            numMCycles = 2;

            B.bit(7);
            break;

        case 0xCB79:
            instruction = "BIT 7, C";

            numMCycles = 2;

            C.bit(7);
            break;

        case 0xCB7A:
            instruction = "BIT 7, D";

            numMCycles = 2;

            D.bit(7);
            break;

        case 0xCB7B:
            instruction = "BIT 7, E";

            numMCycles = 2;

            E.bit(7);
            break;

        case 0xCB7C:
            instruction = "BIT 7, H";

            numMCycles = 2;

            H.bit(7);
            break;

        case 0xCB7D:
            instruction = "BIT 7, L";

            numMCycles = 2;

            L.bit(7);
            break;

        case 0xCB7E:
            instruction = "BIT 7, (HL)";

            numMCycles = 3;

            addressRegister.setAddress(HL.getInt());
            addressRegister.bit(7);
            break;

        case 0xCB7F:
            instruction = "BIT 7, A";

            numMCycles = 2;

            A.bit(7);
            break;

        case 0xCB80:
            instruction = "RES 0, B";

            numMCycles = 2;

            B.res(0);
            break;

        case 0xCB81:
            instruction = "RES 0, C";

            numMCycles = 2;

            C.res(0);
            break;

        case 0xCB82:
            instruction = "RES 0, D";

            numMCycles = 2;

            D.res(0);
            break;

        case 0xCB83:
            instruction = "RES 0, E";

            numMCycles = 2;

            E.res(0);
            break;

        case 0xCB84:
            instruction = "RES 0, H";

            numMCycles = 2;

            H.res(0);
            break;

        case 0xCB85:
            instruction = "RES 0, L";

            numMCycles = 2;

            L.res(0);
            break;

        case 0xCB86:
            instruction = "RES 0, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(0);
            break;

        case 0xCB87:
            instruction = "RES 0, A";

            numMCycles = 2;

            A.res(0);
            break;

        case 0xCB88:
            instruction = "RES 1, B";

            numMCycles = 2;

            B.res(1);
            break;

        case 0xCB89:
            instruction = "RES 1, C";

            numMCycles = 2;

            C.res(1);
            break;

        case 0xCB8A:
            instruction = "RES 1, D";

            numMCycles = 2;

            D.res(1);
            break;

        case 0xCB8B:
            instruction = "RES 1, E";

            numMCycles = 2;

            E.res(1);
            break;

        case 0xCB8C:
            instruction = "RES 1, H";

            numMCycles = 2;

            H.res(1);
            break;

        case 0xCB8D:
            instruction = "RES 1, L";

            numMCycles = 2;

            L.res(1);
            break;

        case 0xCB8E:
            instruction = "RES 1, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(1);
            break;

        case 0xCB8F:
            instruction = "RES 1, A";

            numMCycles = 2;

            A.res(1);
            break;

        case 0xCB90:
            instruction = "RES 2, B";

            numMCycles = 2;

            B.res(2);
            break;

        case 0xCB91:
            instruction = "RES 2, C";

            numMCycles = 2;

            C.res(2);
            break;

        case 0xCB92:
            instruction = "RES 2, D";

            numMCycles = 2;

            D.res(2);
            break;

        case 0xCB93:
            instruction = "RES 2, E";

            numMCycles = 2;

            E.res(2);
            break;

        case 0xCB94:
            instruction = "RES 2, H";

            numMCycles = 2;

            H.res(2);
            break;

        case 0xCB95:
            instruction = "RES 2, L";

            numMCycles = 2;

            L.res(2);
            break;

        case 0xCB96:
            instruction = "RES 2, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(2);
            break;

        case 0xCB97:
            instruction = "RES 2, A";

            numMCycles = 2;

            A.res(2);
            break;

        case 0xCB98:
            instruction = "RES 3, B";

            numMCycles = 2;

            B.res(3);
            break;

        case 0xCB99:
            instruction = "RES 3, C";

            numMCycles = 2;

            C.res(3);
            break;

        case 0xCB9A:
            instruction = "RES 3, D";

            numMCycles = 2;

            D.res(3);
            break;

        case 0xCB9B:
            instruction = "RES 3, E";

            numMCycles = 2;

            E.res(3);
            break;

        case 0xCB9C:
            instruction = "RES 3, H";

            numMCycles = 2;

            H.res(3);
            break;

        case 0xCB9D:
            instruction = "RES 3, L";

            numMCycles = 2;

            L.res(3);
            break;

        case 0xCB9E:
            instruction = "RES 3, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(3);
            break;

        case 0xCB9F:
            instruction = "RES 3, A";

            numMCycles = 2;

            A.res(3);
            break;

        case 0xCBA0:
            instruction = "RES 4, B";

            numMCycles = 2;

            B.res(4);
            break;

        case 0xCBA1:
            instruction = "RES 4, C";

            numMCycles = 2;

            C.res(4);
            break;

        case 0xCBA2:
            instruction = "RES 4, D";

            numMCycles = 2;

            D.res(4);
            break;

        case 0xCBA3:
            instruction = "RES 4, E";

            numMCycles = 2;

            E.res(4);
            break;

        case 0xCBA4:
            instruction = "RES 4, H";

            numMCycles = 2;

            H.res(4);
            break;

        case 0xCBA5:
            instruction = "RES 4, L";

            numMCycles = 2;

            L.res(4);
            break;

        case 0xCBA6:
            instruction = "RES 4, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(4);
            break;

        case 0xCBA7:
            instruction = "RES 4, A";

            numMCycles = 2;

            A.res(4);
            break;

        case 0xCBA8:
            instruction = "RES 5, B";

            numMCycles = 2;

            B.res(5);
            break;

        case 0xCBA9:
            instruction = "RES 5, C";

            numMCycles = 2;

            C.res(5);
            break;

        case 0xCBAA:
            instruction = "RES 5, D";

            numMCycles = 2;

            D.res(5);
            break;

        case 0xCBAB:
            instruction = "RES 5, E";

            numMCycles = 2;

            E.res(5);
            break;

        case 0xCBAC:
            instruction = "RES 5, H";

            numMCycles = 2;

            H.res(5);
            break;

        case 0xCBAD:
            instruction = "RES 5, L";

            numMCycles = 2;

            L.res(5);
            break;

        case 0xCBAE:
            instruction = "RES 5, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(5);
            break;

        case 0xCBAF:
            instruction = "RES 5, A";

            numMCycles = 2;

            A.res(5);
            break;

        case 0xCBB0:
            instruction = "RES 6, B";

            numMCycles = 2;

            B.res(6);
            break;

        case 0xCBB1:
            instruction = "RES 6, C";

            numMCycles = 2;

            C.res(6);
            break;

        case 0xCBB2:
            instruction = "RES 6, D";

            numMCycles = 2;

            D.res(6);
            break;

        case 0xCBB3:
            instruction = "RES 6, E";

            numMCycles = 2;

            E.res(6);
            break;

        case 0xCBB4:
            instruction = "RES 6, H";

            numMCycles = 2;

            H.res(6);
            break;

        case 0xCBB5:
            instruction = "RES 6, L";

            numMCycles = 2;

            L.res(6);
            break;

        case 0xCBB6:
            instruction = "RES 6, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(6);
            break;

        case 0xCBB7:
            instruction = "RES 6, A";

            numMCycles = 2;

            A.res(6);
            break;

        case 0xCBB8:
            instruction = "RES 7, B";

            numMCycles = 2;

            B.res(7);
            break;

        case 0xCBB9:
            instruction = "RES 7, C";

            numMCycles = 2;

            C.res(7);
            break;

        case 0xCBBA:
            instruction = "RES 7, D";

            numMCycles = 2;

            D.res(7);
            break;

        case 0xCBBB:
            instruction = "RES 7, E";

            numMCycles = 2;

            E.res(7);
            break;

        case 0xCBBC:
            instruction = "RES 7, H";

            numMCycles = 2;

            H.res(7);
            break;

        case 0xCBBD:
            instruction = "RES 7, L";

            numMCycles = 2;

            L.res(7);
            break;

        case 0xCBBE:
            instruction = "RES 7, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.res(7);
            break;

        case 0xCBBF:
            instruction = "RES 7, A";

            numMCycles = 2;

            A.res(7);
            break;

        case 0xCBC0:
            instruction = "SET 0, B";

            numMCycles = 2;

            B.set(0);
            break;

        case 0xCBC1:
            instruction = "SET 0, C";

            numMCycles = 2;

            C.set(0);
            break;

        case 0xCBC2:
            instruction = "SET 0, D";

            numMCycles = 2;

            D.set(0);
            break;

        case 0xCBC3:
            instruction = "SET 0, E";

            numMCycles = 2;

            E.set(0);
            break;

        case 0xCBC4:
            instruction = "SET 0, H";

            numMCycles = 2;

            H.set(0);
            break;

        case 0xCBC5:
            instruction = "SET 0, L";

            numMCycles = 2;

            L.set(0);
            break;

        case 0xCBC6:
            instruction = "SET 0, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(0);
            break;

        case 0xCBC7:
            instruction = "SET 0, A";

            numMCycles = 2;

            A.set(0);
            break;

        case 0xCBC8:
            instruction = "SET 1, B";

            numMCycles = 2;

            B.set(1);
            break;

        case 0xCBC9:
            instruction = "SET 1, C";

            numMCycles = 2;

            C.set(1);
            break;

        case 0xCBCA:
            instruction = "SET 1, D";

            numMCycles = 2;

            D.set(1);
            break;

        case 0xCBCB:
            instruction = "SET 1, E";

            numMCycles = 2;

            E.set(1);
            break;

        case 0xCBCC:
            instruction = "SET 1, H";

            numMCycles = 2;

            H.set(1);
            break;

        case 0xCBCD:
            instruction = "SET 1, L";

            numMCycles = 2;

            L.set(1);
            break;

        case 0xCBCE:
            instruction = "SET 1, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(1);
            break;

        case 0xCBCF:
            instruction = "SET 1, A";

            numMCycles = 2;

            A.set(1);
            break;

        case 0xCBD0:
            instruction = "SET 2, B";

            numMCycles = 2;

            B.set(2);
            break;

        case 0xCBD1:
            instruction = "SET 2, C";

            numMCycles = 2;

            C.set(2);
            break;

        case 0xCBD2:
            instruction = "SET 2, D";

            numMCycles = 2;

            D.set(2);
            break;

        case 0xCBD3:
            instruction = "SET 2, E";

            numMCycles = 2;

            E.set(2);
            break;

        case 0xCBD4:
            instruction = "SET 2, H";

            numMCycles = 2;

            H.set(2);
            break;

        case 0xCBD5:
            instruction = "SET 2, L";

            numMCycles = 2;

            L.set(2);
            break;

        case 0xCBD6:
            instruction = "SET 2, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(2);
            break;

        case 0xCBD7:
            instruction = "SET 2, A";

            numMCycles = 2;

            A.set(2);
            break;

        case 0xCBD8:
            instruction = "SET 3, B";

            numMCycles = 2;

            B.set(3);
            break;

        case 0xCBD9:
            instruction = "SET 3, C";

            numMCycles = 2;

            C.set(3);
            break;

        case 0xCBDA:
            instruction = "SET 3, D";

            numMCycles = 2;

            D.set(3);
            break;

        case 0xCBDB:
            instruction = "SET 3, E";

            numMCycles = 2;

            E.set(3);
            break;

        case 0xCBDC:
            instruction = "SET 3, H";

            numMCycles = 2;

            H.set(3);
            break;

        case 0xCBDD:
            instruction = "SET 3, L";

            numMCycles = 2;

            L.set(3);
            break;

        case 0xCBDE:
            instruction = "SET 3, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(3);
            break;

        case 0xCBDF:
            instruction = "SET 3, A";

            numMCycles = 2;

            A.set(3);
            break;

        case 0xCBE0:
            instruction = "SET 4, B";

            numMCycles = 2;

            B.set(4);
            break;

        case 0xCBE1:
            instruction = "SET 4, C";

            numMCycles = 2;

            C.set(4);
            break;

        case 0xCBE2:
            instruction = "SET 4, D";

            numMCycles = 2;

            D.set(4);
            break;

        case 0xCBE3:
            instruction = "SET 4, E";

            numMCycles = 2;

            E.set(4);
            break;

        case 0xCBE4:
            instruction = "SET 4, H";

            numMCycles = 2;

            H.set(4);
            break;

        case 0xCBE5:
            instruction = "SET 4, L";

            numMCycles = 2;

            L.set(4);
            break;

        case 0xCBE6:
            instruction = "SET 4, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(4);
            break;

        case 0xCBE7:
            instruction = "SET 4, A";

            numMCycles = 2;

            A.set(4);
            break;

        case 0xCBE8:
            instruction = "SET 5, B";

            numMCycles = 2;

            B.set(5);
            break;

        case 0xCBE9:
            instruction = "SET 5, C";

            numMCycles = 2;

            C.set(5);
            break;

        case 0xCBEA:
            instruction = "SET 5, D";

            numMCycles = 2;

            D.set(5);
            break;

        case 0xCBEB:
            instruction = "SET 5, E";

            numMCycles = 2;

            E.set(5);
            break;

        case 0xCBEC:
            instruction = "SET 5, H";

            numMCycles = 2;

            H.set(5);
            break;

        case 0xCBED:
            instruction = "SET 5, L";

            numMCycles = 2;

            L.set(5);
            break;

        case 0xCBEE:
            instruction = "SET 5, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(5);
            break;

        case 0xCBEF:
            instruction = "SET 5, A";

            numMCycles = 2;

            A.set(5);
            break;


        case 0xCBF0:
            instruction = "SET 6, B";

            numMCycles = 2;

            B.set(6);
            break;

        case 0xCBF1:
            instruction = "SET 6, C";

            numMCycles = 2;

            C.set(6);
            break;

        case 0xCBF2:
            instruction = "SET 6, D";

            numMCycles = 2;

            D.set(6);
            break;

        case 0xCBF3:
            instruction = "SET 6, E";

            numMCycles = 2;

            E.set(6);
            break;

        case 0xCBF4:
            instruction = "SET 6, H";

            numMCycles = 2;

            H.set(6);
            break;

        case 0xCBF5:
            instruction = "SET 6, L";

            numMCycles = 2;

            L.set(6);
            break;

        case 0xCBF6:
            instruction = "SET 6, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(6);
            break;

        case 0xCBF7:
            instruction = "SET 6, A";

            numMCycles = 2;

            A.set(6);
            break;

        case 0xCBF8:
            instruction = "SET 7, B";

            numMCycles = 2;

            B.set(7);
            break;

        case 0xCBF9:
            instruction = "SET 7, C";

            numMCycles = 2;

            C.set(7);
            break;

        case 0xCBFA:
            instruction = "SET 7, D";

            numMCycles = 2;

            D.set(7);
            break;

        case 0xCBFB:
            instruction = "SET 7, E";

            numMCycles = 2;

            E.set(7);
            break;

        case 0xCBFC:
            instruction = "SET 7, H";

            numMCycles = 2;

            H.set(7);
            break;

        case 0xCBFD:
            instruction = "SET 7, L";

            numMCycles = 2;

            L.set(7);
            break;

        case 0xCBFE:
            instruction = "SET 7, (HL)";

            numMCycles = 4;

            addressRegister.setAddress(HL.getInt());
            addressRegister.set(7);
            break;

        case 0xCBFF:
            instruction = "SET 7, A";

            numMCycles = 2;

            A.set(7);
            break;

        default:
            unimplementedOpcode(opcode);
        }

        numTCycles = numMCycles * 4;
    }

    // StateProducer
    StateConsumer[] stateConsumers = new StateConsumer[0];

    @Override
    public StateConsumer[] getStateConsumers() {
        return stateConsumers;
    }

    @Override
    public void addStateConsumer(StateConsumer stateConsumer) {
        // Create a new array with one more element.
        StateConsumer[] oldArray = stateConsumers;
        stateConsumers = new StateConsumer[oldArray.length + 1];

        // Copy over elements from old array.
        for(int i = 0; i < oldArray.length; i++) {
            stateConsumers[i] = oldArray[i];
        }

        // Add new element.
        stateConsumers[oldArray.length] = stateConsumer;
    }
}