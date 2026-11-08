package app.nebo.core;

import java.util.concurrent.atomic.AtomicInteger;

/** Cancellation and installer handoff must have exactly one winner. One instance per install. */
public final class UpdateCommitGate {
    private final AtomicInteger state=new AtomicInteger();
    public boolean beginCommit(){return state.compareAndSet(0,1);}
    public boolean cancel(){return state.compareAndSet(0,2)||state.get()==2;}
}
