package app.nebo.core;

/** WebRTC time counters are cumulative seconds; UI and adaptation need the latest interval. */
public final class IntervalAverage {
    private double previousSeconds;
    private long previousCount;
    private boolean initialized;
    public long sample(double totalSeconds,long count) {
        if(!Double.isFinite(totalSeconds)||totalSeconds<0||count<0)throw new IllegalArgumentException("Invalid media counter");
        boolean reset=initialized&&(totalSeconds<previousSeconds||count<previousCount);
        long delta=count-previousCount;double elapsed=totalSeconds-previousSeconds;
        previousSeconds=totalSeconds;previousCount=count;initialized=true;
        if(reset||delta==0)return -1;
        return Math.min(60000,Math.max(0,Math.round(elapsed*1000/delta)));
    }
}
