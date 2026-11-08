package org.webrtc.audio;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProjectionOwnerTest {
    @Test public void lateCleanupCannotClearReplacement(){
        PlaybackRecordFactory.Owner<Object> owner=new PlaybackRecordFactory.Owner<>();Object old=new Object(),replacement=new Object();
        owner.set(old);owner.set(replacement);owner.clear(old);assertSame(replacement,owner.get());
        owner.clear(replacement);assertNull(owner.get());
    }
}
