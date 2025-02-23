package gameboy.data;

// Signal data is made up of an action (press or release) and a controller button (up, down, left, right, a, b, start, or select).
public interface SignalProducer {
    abstract public SignalConsumer[] getSignalConsumers();
    abstract public void addSignalConsumer(SignalConsumer signalConsumer);

    default public void produceSignal(int action, int button) {
        for(SignalConsumer signalConsumer : getSignalConsumers()) {
            signalConsumer.consumeSignal(action, button);
        }
    }
}
