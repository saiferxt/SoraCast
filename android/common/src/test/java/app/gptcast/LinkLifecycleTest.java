package app.gptcast;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LinkLifecycleTest {
    @Test public void closedPendingLinkCannotBeHandedToCapture() throws Exception {
        SSLSocket socket=(SSLSocket)SSLContext.getDefault().getSocketFactory().createSocket();
        Link link=new Link(socket,"session");
        // Reproduce the state after the temporary permission-waiting listener had started.
        Field started=Link.class.getDeclaredField("started");started.setAccessible(true);((AtomicBoolean)started.get(link)).set(true);
        link.close();
        try{link.start(new Link.Listener(){public void message(JSONObject m){}public void closed(){}});fail("A closed permission-waiting link must not start capture");}
        catch(IOException expected){}
    }
}
