package gameboy.data;

// Sound data is made up of 4-byte int samples, 2 bytes for the left channel and 2 bytes for the right channel.
public interface SoundConsumer {
    abstract public void consumeSound(int data);

    default public void addToSoundProducer(SoundProducer soundProducer) {
        soundProducer.addSoundConsumer(this);
    }
}
