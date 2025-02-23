package gameboy.emulator.clock;

import java.util.ArrayList;

// A clock that can schedule a variety of tick and frame callbacks.
public class HybridClock {
    public ArrayList<TickCallback> tickCallbacks = new ArrayList<>();
    public TickCallback[] tickCallbacksArray;
    public ArrayList<FrameCallback> frameCallbacks = new ArrayList<>();
    public FrameCallback[] frameCallbacksArray;
    public ArrayList<Thread> asyncFrameThreads = new ArrayList<>();
    public Thread frameThread;
    public Thread tickThread;
    public Thread baseThread;

    TimingInfo timingInfo;

    // Synchronization locks
    public int startLockCount = 0;
    public int startLockTotal = 0;
    public Object startLock = new Object();
    public int asyncFrameCompleteLockTotal = 0;
    public ArrayList<Object> asyncFrameActionLocks = new ArrayList<>();
    public Object[] asyncFrameActionLocksArray;
    public Object frameActionLock = new Object();
    public Object frameCompleteLock = new Object();
    public Object tickActionLock = new Object();
    public Object tickCompleteLock = new Object();
   
    public HybridClock(TimingInfo timingInfo) {
        this.timingInfo = timingInfo;
    }

    public void addAsynchronousFrameCallback(FrameCallback frameCallback) {
        // Add an asynchronous callback that occurs every frame.
        startLockTotal++;

        final int asyncFrameLockNumber = asyncFrameCompleteLockTotal++;

        asyncFrameActionLocks.add(new Object());
        
        asyncFrameThreads.add(new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized(startLock) {
                    startLockCount++;
                    if(startLockCount == startLockTotal) {
                        startLock.notify();
                    }
                }

                while(true) {
                    try {
                        synchronized(asyncFrameActionLocksArray[asyncFrameLockNumber]) {
                            asyncFrameActionLocksArray[asyncFrameLockNumber].wait();
                        }
                    }
                    catch(InterruptedException e) {
                        return;
                    }

                    frameCallback.onFrame();
                }
            }
        }));
    }

    public void addFrameCallback(FrameCallback frameCallback) {
        // Add a callback that occurs every frame.
        frameCallbacks.add(frameCallback);
    }

    public void addTickCallback(TickCallback tickCallback) {
        // Add a callback that occurs every tick.
        tickCallbacks.add(tickCallback);
    }

    public void start() {
        startLockTotal++;
        frameThread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized(frameActionLock) {
                    synchronized(startLock) {
                        startLockCount++;
                        if(startLockCount == startLockTotal) {
                            startLock.notify();
                        }
                    }

                    while(true) {
                        try {
                            frameActionLock.wait();
                        }
                        catch(InterruptedException e) {
                            return;
                        }

                        for(FrameCallback frameCallback : frameCallbacksArray) {
                            frameCallback.onFrame();
                        }

                        synchronized(frameCompleteLock) {
                            frameCompleteLock.notify();
                        }
                    }
                }
            }
        });

        startLockTotal++;
        tickThread = new Thread(new Runnable() {
            long cyclesPerFrame = (long)(timingInfo.frequency / timingInfo.framesPerSecond);

            @Override
            public void run() {
                synchronized(tickActionLock) {
                    synchronized(startLock) {
                        startLockCount++;
                        if(startLockCount == startLockTotal) {
                            startLock.notify();
                        }
                    }

                    while(true) {
                        try {
                            tickActionLock.wait();
                        }
                        catch(InterruptedException e) {
                            return;
                        }

                        // Perform one frame's worth of ticks.
                        for(int i = 0; i < cyclesPerFrame; i++) {
                            for(TickCallback tickCallback : tickCallbacksArray) {
                                tickCallback.onTick();
                            }
                        }

                        synchronized(tickCompleteLock) {
                            tickCompleteLock.notify();
                        }
                    }
                }
            }
        });

        baseThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long frameDeltaTime = (long)(Math.pow(10, 9) / timingInfo.framesPerSecond);
                long startTime = System.nanoTime();

                while(true) {
                    // If we are ahead of schedule, wait for the frame to complete.
                    while(System.nanoTime() - startTime <= frameDeltaTime);
                    startTime = System.nanoTime();

                    //long debugTime = System.nanoTime();

                    // Unlock tick callbacks. 
                    synchronized(tickCompleteLock) {
                        synchronized(tickActionLock) {
                            tickActionLock.notify();
                        }

                        try {
                            tickCompleteLock.wait();
                        }
                        catch(InterruptedException e) {
                            return;
                        }
                    }

                    //long debugDeltaTime = System.nanoTime() - debugTime;
                    //if(debugDeltaTime >= 16666666) {
                    //    System.out.println("FRAME = " + debugDeltaTime);
                    //}

                    // Unlock frame callbacks.
                    synchronized(frameCompleteLock) {
                        synchronized(frameActionLock) {
                            frameActionLock.notify();
                        }

                        try {
                            frameCompleteLock.wait();
                        }
                        catch(InterruptedException e) {
                            return;
                        }
                    }

                    // Unlock asynchronous frame callbacks. We do not wait for these to complete.
                    for(Object asyncFrameActionLock : asyncFrameActionLocksArray) {
                        synchronized(asyncFrameActionLock) {
                            asyncFrameActionLock.notify();
                        }
                    }
                }
            }
        });

        // For performance reasons, transfer items from ArrayLists to Arrays so we can iterate over the arrays.
        tickCallbacksArray = tickCallbacks.toArray(new TickCallback[0]);
        frameCallbacksArray = frameCallbacks.toArray(new FrameCallback[0]);
        asyncFrameActionLocksArray = asyncFrameActionLocks.toArray(new Object[0]);

        // Make sure the other threads are ready to go before we begin.
        synchronized(startLock) {
            for(Thread asyncFrameThread : asyncFrameThreads) {
                asyncFrameThread.start();
            }
            frameThread.start();
            tickThread.start();

            try {
                startLock.wait();
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // Start the thread with the main emulation loop.
        baseThread.start();
    }

    public void stop() {
        // Immediately interrupt all threads.
        baseThread.interrupt();
        tickThread.interrupt();
        frameThread.interrupt();
        for(Thread asyncFrameThread : asyncFrameThreads) {
            asyncFrameThread.interrupt();
        }
    }

    abstract public static class TickCallback {
        abstract public void onTick();
    }

    abstract public static class FrameCallback {
        abstract public void onFrame();
    }
}