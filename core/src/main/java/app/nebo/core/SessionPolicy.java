package app.nebo.core;

/** Local ownership policy. Call only after peer authentication; TV confirmation is trusted UI. */
public final class SessionPolicy {
    public enum Request { START, ALREADY_ACTIVE, NEEDS_TV_CONFIRMATION, BUSY }
    private String active, pending;
    public synchronized Request request(String deviceId) {
        validate(deviceId);
        if (deviceId.equals(active)) return Request.ALREADY_ACTIVE;
        if (active == null) { active = deviceId; return Request.START; }
        if (pending != null && !pending.equals(deviceId)) return Request.BUSY;
        pending = deviceId; return Request.NEEDS_TV_CONFIRMATION;
    }
    public synchronized boolean confirmSwitch(String deviceId) {
        validate(deviceId);
        if (!deviceId.equals(pending)) return false;
        active = pending; pending = null; return true;
    }
    public synchronized boolean stop(String deviceId) {
        validate(deviceId);
        if (!deviceId.equals(active)) return false;
        active = null; pending = null; return true;
    }
    public synchronized void revoke(String deviceId) {
        validate(deviceId);
        if (deviceId.equals(active)) { active = null; pending = null; }
        else if (deviceId.equals(pending)) pending = null;
    }
    public synchronized String active() { return active; }
    private static void validate(String id) {
        if (id == null || id.isEmpty() || id.length() > 128) throw new IllegalArgumentException("Invalid device identity");
    }
}
