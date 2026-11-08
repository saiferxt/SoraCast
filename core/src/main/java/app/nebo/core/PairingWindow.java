package app.nebo.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Single-use token gate only. It does not authenticate TLS, SDP or device keys.
 * Caller supplies Android elapsedRealtime() milliseconds, never wall-clock time,
 * and generates the 32 token bytes with SecureRandom. */
public final class PairingWindow {
    private byte[] tokenHash;
    private long issued;
    private int failures;
    public synchronized void open(byte[] token, long elapsedMillis) {
        if (token == null || token.length != 32 || elapsedMillis < 0)
            throw new IllegalArgumentException("Invalid pairing token");
        clear(); tokenHash = digest(token); issued = elapsedMillis; failures = 0;
    }
    public synchronized boolean claim(byte[] token, long elapsedMillis) {
        if (tokenHash == null) return false;
        if (elapsedMillis < issued || elapsedMillis - issued >= 120_000) {
            clear(); return false;
        }
        if (token == null || token.length != 32 || !MessageDigest.isEqual(tokenHash, digest(token))) {
            if (++failures >= 5) clear();
            return false;
        }
        clear(); return true;
    }
    public synchronized void clear() {
        if (tokenHash != null) Arrays.fill(tokenHash, (byte) 0);
        tokenHash = null; issued = 0; failures = 0;
    }
    private static byte[] digest(byte[] token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
