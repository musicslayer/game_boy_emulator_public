package gameboy.data;

// Image data is made up of points on the screen (x, y, colorIndex).
public interface ImageConsumer {
    abstract public void consumeImage(int x, int y, int colorIndex);
    abstract public void consumeDebugImage(int[][] imageData);

    default public void addToImageProducer(ImageProducer imageProducer) {
        imageProducer.addImageConsumer(this);
    }
}
