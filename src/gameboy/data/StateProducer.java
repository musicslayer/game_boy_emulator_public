package gameboy.data;

// State data is made up of processor register values and other internal information.
public interface StateProducer {
    abstract public StateConsumer[] getStateConsumers();
    abstract public void addStateConsumer(StateConsumer stateConsumer);

    default public void produceState(int address, int opcode, int a, int f, int b, int c, int d, int e, int h, int l, int sp, int pc, int flagHalt, int flagIME) {
        for(StateConsumer stateConsumer : getStateConsumers()) {
            stateConsumer.consumeState(address, opcode, a, f, b, c, d, e, h, l, sp, pc, flagHalt, flagIME);
        }
    }
}