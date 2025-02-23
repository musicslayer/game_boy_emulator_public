package gameboy.data;

// State data is made up of processor register values and other internal information.
public interface StateConsumer {
    abstract public void consumeState(int address, int opcode, int a, int f, int b, int c, int d, int e, int h, int l, int sp, int pc, int flagHalt, int flagIME);

    default public void addToStateProducer(StateProducer stateProducer) {
        stateProducer.addStateConsumer(this);
    }
}
