package gameboy.data;

// Image data is made up of points on the screen (x, y, colorIndex).
public interface ImageProducer {
    abstract public ImageConsumer[] getImageConsumers();
    abstract public void addImageConsumer(ImageConsumer imageConsumer);

    default public void produceImage(int x, int y, int colorIndex) {
        for(ImageConsumer imageConsumer : getImageConsumers()) {
            imageConsumer.consumeImage(x, y, colorIndex);
        }
    }

    default public void produceDebugImage(int[][] imageData) {
        for(ImageConsumer imageConsumer : getImageConsumers()) {
            imageConsumer.consumeDebugImage(imageData);
        }
    }
}
