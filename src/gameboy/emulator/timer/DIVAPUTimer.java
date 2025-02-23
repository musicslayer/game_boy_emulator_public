package gameboy.emulator.timer;

public class DIVAPUTimer {
    DIVAPUListener divapuListener;

    public void attachDIVTimer(DIVTimer divTimer) {
        divTimer.setOnTickListener(new DIVTimer.DIVListener() {
            @Override
            public void onTick() {
                if(divapuListener != null) {
                    divapuListener.onTick();
                }
            }
        });
    }

    public void setOnTickListener(DIVAPUListener divapuListener) {
        this.divapuListener = divapuListener;
    }

    abstract public static class DIVAPUListener {
        abstract public void onTick();
    }
}
