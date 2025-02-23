package gameboy.data;

// Signal data is made up of an action (press or release) and a controller button (up, down, left, right, a, b, start, or select).
public interface SignalConsumer {
    abstract public void consumeSignal(int action, int button);

    default public void addToSignalProducer(SignalProducer signalProducer) {
        signalProducer.addSignalConsumer(this);
    }
}
