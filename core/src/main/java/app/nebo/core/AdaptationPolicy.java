package app.nebo.core;

/** One sample per second. Targets for the hardware spike, not measured codec guarantees. */
public final class AdaptationPolicy {
    private MediaProfile current;
    private final boolean automatic;
    private int badSamples, goodSamples;
    public AdaptationPolicy() { this(StreamQuality.AUTO); }
    public AdaptationPolicy(StreamQuality quality) {
        if (quality == null) throw new IllegalArgumentException("Missing quality");
        current=quality.initial; automatic=quality==StreamQuality.AUTO;
    }
    public MediaProfile current() { return current; }
    public MediaProfile sample(long encodeMs, long decodeMs, long jitterMs, int lossPercent) {
        return sample(encodeMs,decodeMs,jitterMs,lossPercent,false);
    }
    /** Called only for an interval where the sender encoded new frames. */
    public MediaProfile sample(long encodeMs, long decodeMs, long jitterMs, int lossPercent, boolean stalled) {
        if (encodeMs < 0 || decodeMs < 0 || jitterMs < 0 || lossPercent < 0 || lossPercent > 100)
            throw new IllegalArgumentException("Invalid media statistics");
        if (!automatic) return current;
        // Sustained misses of a 30fps frame budget make 720p undesirable even on a fast LAN.
        boolean bad = stalled || encodeMs > 33 || decodeMs > 33 || jitterMs > 150 || lossPercent > 5;
        boolean good = !stalled && encodeMs < 20 && decodeMs < 20 && jitterMs < 80 && lossPercent < 2;
        badSamples = bad ? Math.min(3, badSamples + 1) : 0;
        goodSamples = good ? Math.min(20, goodSamples + 1) : 0;
        if (current != MediaProfile.LIGHT && badSamples >= 3) {
            current = current==MediaProfile.FULL_HD ? MediaProfile.BALANCED : MediaProfile.LIGHT;
            goodSamples = 0; badSamples = 0;
        } else if (current != MediaProfile.FULL_HD && goodSamples >= 20) {
            current = current==MediaProfile.LIGHT ? MediaProfile.BALANCED : MediaProfile.FULL_HD;
            goodSamples = 0; badSamples = 0;
        }
        return current;
    }
}
