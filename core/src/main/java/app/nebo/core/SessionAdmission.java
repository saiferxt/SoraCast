package app.nebo.core;

/** Revoking saved peers also invalidates authentication already waiting for TV approval. */
public final class SessionAdmission {
    private long epoch;
    public static final class Ticket {
        private final long epoch;
        private boolean consumed;
        public final boolean needsApproval;
        private Ticket(long epoch,boolean approval){this.epoch=epoch;needsApproval=approval;}
    }
    public synchronized Ticket begin(boolean known,boolean busy){return new Ticket(epoch,!known||busy);}
    public synchronized boolean accept(Ticket ticket){if(ticket.epoch!=epoch||ticket.consumed)return false;ticket.consumed=true;return true;}
    public synchronized void revoke(){epoch++;}
}
