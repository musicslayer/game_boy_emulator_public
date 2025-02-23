package gameboy.data;

// Sound data is made up of 4-byte int samples, 2 bytes for the left channel and 2 bytes for the right channel.
public interface SoundProducer {
    abstract public SoundConsumer[] getSoundConsumers();
    abstract public void addSoundConsumer(SoundConsumer soundConsumer);

    default public void produceSound(int data) {
        for(SoundConsumer soundConsumer : getSoundConsumers()) {
            soundConsumer.consumeSound(data);
        }
    }
}
