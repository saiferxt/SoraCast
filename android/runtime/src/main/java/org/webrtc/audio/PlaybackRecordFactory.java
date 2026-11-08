package org.webrtc.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;

/** Local Java-only patch: the JNI contract stays unchanged; capture never opens the microphone. */
public final class PlaybackRecordFactory {
    static final class Owner<T> {
        private T value;
        synchronized void set(T next){value=next;}
        synchronized T get(){return value;}
        synchronized void clear(T expected){if(value==expected)value=null;}
    }
    private static final Owner<MediaProjection> owner=new Owner<>();
    public static void configure(MediaProjection value) { owner.set(value); }
    public static void clear(MediaProjection expected){owner.clear(expected);}
    public static AudioRecord create(int rate,int channels,int encoding,int buffer) {
        MediaProjection p=owner.get();
        if(Build.VERSION.SDK_INT<29 || p==null) throw new IllegalArgumentException("Playback capture is unavailable");
        AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(p)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
        return new AudioRecord.Builder().setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(channels).setEncoding(encoding).build())
            .setBufferSizeInBytes(buffer).build();
    }
}
