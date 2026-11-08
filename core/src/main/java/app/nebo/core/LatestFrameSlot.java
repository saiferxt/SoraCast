package app.nebo.core;

/** Capacity one for READY/DECODED frames only, never dependent encoded H.264 packets.
 * Caller owns and must release every displaced/taken frame, outside this monitor. */
public final class LatestFrameSlot<T> {
    private T latest;
    private long displaced;
    public synchronized T replace(T frame) {
        if (frame == null) throw new IllegalArgumentException("Null frame");
        T old = latest;
        latest = frame;
        if (old != null && displaced < Long.MAX_VALUE) displaced++;
        return old;
    }
    public synchronized T take() {
        T frame = latest; latest = null; return frame;
    }
    public synchronized long displacedCount() { return displaced; }
}
