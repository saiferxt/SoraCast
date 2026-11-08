package app.nebo.core;

/** Permission choice and media negotiation have separate monotonic deadlines. */
public final class SessionDeadline {
    private final long created;
    private long media=-1;
    public SessionDeadline(long now){if(now<0)throw new IllegalArgumentException("Invalid clock");created=now;}
    public synchronized void mediaStarted(long now){if(media<0&&!expired(now))media=now;}
    public synchronized boolean expired(long now){return now<created || (media<0?now-created>=120000:now-media>=30000);}
}
