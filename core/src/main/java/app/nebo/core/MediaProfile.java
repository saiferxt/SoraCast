package app.nebo.core;

/** Capture bounds are rotated with the source; no upscaling or forced 16:9. */
public enum MediaProfile {
    LIGHT(960, 540, 24, 2_000_000), BALANCED(1280, 720, 30, 4_000_000), HD_60(1280, 720, 60, 6_000_000),
    FULL_HD(1920, 1080, 30, 8_000_000), SMOOTH(1920, 1080, 60, 12_000_000),
    QUAD_HD(2560, 1440, 30, 14_000_000), QUAD_HD_60(2560, 1440, 60, 22_000_000);
    public final int width, height, fps, bitrate;
    MediaProfile(int width, int height, int fps, int bitrate) {
        this.width = width; this.height = height; this.fps = fps; this.bitrate = bitrate;
    }
    public Size fit(int sourceWidth, int sourceHeight) {
        if (sourceWidth < 2 || sourceHeight < 2 || sourceWidth > 16384 || sourceHeight > 16384)
            throw new IllegalArgumentException("Invalid capture dimensions");
        boolean portrait = sourceHeight > sourceWidth;
        int maxWidth = portrait ? height : width;
        int maxHeight = portrait ? width : height;
        double scale = Math.min(1.0, Math.min(maxWidth / (double) sourceWidth, maxHeight / (double) sourceHeight));
        return new Size(Math.max(2, ((int) (sourceWidth * scale)) & ~1),
                Math.max(2, ((int) (sourceHeight * scale)) & ~1));
    }
    public static final class Size {
        public final int width, height;
        Size(int width, int height) { this.width = width; this.height = height; }
    }
}
