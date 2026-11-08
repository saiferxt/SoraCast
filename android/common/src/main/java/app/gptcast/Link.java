package app.gptcast;

import app.nebo.core.Protocol;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocket;
import org.json.JSONObject;

/** One bounded writer queue; a blocked or stale connection is closed instead of accumulating work. */
public final class Link implements Closeable {
    public interface Listener {void message(JSONObject message);void closed();}
    private final SSLSocket socket;
    private final String sid;
    private final ArrayBlockingQueue<String> outbound=new ArrayBlockingQueue<>(32);
    private final AtomicBoolean closed=new AtomicBoolean();
    private final AtomicBoolean started=new AtomicBoolean();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private volatile Listener listener;
    private volatile long lastWrite=System.nanoTime();
    private volatile boolean writing;
    public Link(SSLSocket socket,String sid){this.socket=socket;this.sid=sid;}
    public void start(Listener listener) throws IOException {
        this.listener=listener;if(closed.get())throw new IOException("Connection closed");if(!started.compareAndSet(false,true))return;socket.setSoTimeout(20000);
        Thread writer=new Thread(()->{
            try {DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                while(!closed.get()){String s=outbound.poll(5,TimeUnit.SECONDS);if(s!=null){writing=true;lastWrite=System.nanoTime();Protocol.write(out,s);writing=false;}}
            }catch(Exception e){close();}
        },"SoraCast-write");writer.setDaemon(true);writer.start();
        Thread reader=new Thread(()->{
            try {DataInputStream in=new DataInputStream(socket.getInputStream());
                while(!closed.get()) {JSONObject m=new JSONObject(Protocol.read(in));
                    if(!sid.equals(m.optString("sid")))throw new IOException("Session mismatch");
                    if(!"ping".equals(m.optString("type"))) this.listener.message(m);}
            }catch(Exception e){close();}
        },"SoraCast-read");reader.setDaemon(true);reader.start();
        timer.scheduleWithFixedDelay(()->{if(writing && System.nanoTime()-lastWrite>10_000_000_000L)close();else send(json("ping"));},0,5,TimeUnit.SECONDS);
    }
    public void send(JSONObject message) {
        if(closed.get())return;
        try {message.put("sid",sid);String s=message.toString();if(s.length()>Protocol.MAX_FRAME||!outbound.offer(s))close();}catch(Exception e){close();}
    }
    public boolean isClosed(){return closed.get();}
    public void close(){if(!closed.compareAndSet(false,true))return;timer.shutdownNow();outbound.clear();try{socket.close();}catch(IOException ignored){} Listener l=listener;if(l!=null)l.closed();}
    public static JSONObject json(String type){JSONObject j=new JSONObject();try{j.put("type",type);}catch(Exception impossible){throw new IllegalStateException(impossible);}return j;}
}

