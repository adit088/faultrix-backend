package com.adit.mockDemo.chaos;



public class ChaosDecision {

    boolean fail;
    boolean delay;
    long delayMs;

    public ChaosDecision(boolean fail, boolean delay, long delayMs) {
        this.fail = fail;
        this.delay = delay;
        this.delayMs = delayMs;
    }

    public boolean shouldFail(){
        return fail;
    }

    public boolean shouldDelay(){
        return delay;
    }

    @SuppressWarnings("uncheck")
    public long getDelayMs() {
        return delayMs;
    }

}
