package app.gptcast.tv;

import android.content.*;
import android.net.nsd.*;
import android.os.SystemClock;
import app.gptcast.*;
import app.nebo.core.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.json.JSONObject;

final class ReceiverServer implements AutoCloseable {
    interface Events {boolean approve(String name,boolean switchSession);void offer(Protocol.Offer offer);void connected(Link link,String name);void error(String text);}
    private final Context context;
    private final Events events;
    private final PairingWindow pairing=new PairingWindow();
    private final SessionAdmission admission=new SessionAdmission();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2));
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean stopped;
    private volatile Link current;
    private SSLServerSocket socket;
    private Identity identity;
    private String host,pin;
    private NsdManager.RegistrationListener registration;
    private final Set<SSLSocket> pending=ConcurrentHashMap.newKeySet();
    ReceiverServer(Context c,Events e){context=c.getApplicationContext();events=e;}
    void start(){new Thread(()->{
        try{
            host=LocalNetwork.address(context);if(host==null)throw new IOException("Подключите приставку к домашней сети Wi-Fi или Ethernet");
            identity=new Identity();pin=identity.pin();socket=identity.listen(host);
            if(stopped){socket.close();return;}
            NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("SoraCast-"+pin.substring(0,6));info.setServiceType("_soracast._tcp.");info.setPort(socket.getLocalPort());info.setAttribute("pin",pin);
            registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo i){}public void onRegistrationFailed(NsdServiceInfo i,int e){}public void onServiceUnregistered(NsdServiceInfo i){}public void onUnregistrationFailed(NsdServiceInfo i,int e){}};
            ((NsdManager)context.getSystemService(Context.NSD_SERVICE)).registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
            rotate();timer.scheduleWithFixedDelay(this::rotate,120,120,TimeUnit.SECONDS);
            while(!stopped){SSLSocket client=(SSLSocket)socket.accept();
                if(!Protocol.privateIpv4(client.getInetAddress().getHostAddress())){client.close();continue;}
                pending.add(client);try{workers.execute(()->authenticate(client));}catch(RejectedExecutionException e){pending.remove(client);client.close();}}
        }catch(Exception e){if(!stopped)events.error(e.getMessage()==null?"Не удалось открыть приёмник":e.getMessage());close();}
    },"SoraCast-listen").start();}
    synchronized void rotate(){if(stopped||socket==null)return;byte[] token=Identity.nonce();pairing.open(token,SystemClock.elapsedRealtime());events.offer(new Protocol.Offer(host,socket.getLocalPort(),pin,token));}
    private void authenticate(SSLSocket client){boolean handed=false;String phase="SC11";
        try{
            events.error("Телефон найден · защищённое подключение… [SC11]");
            client.setSoTimeout(45000);client.setTcpNoDelay(true);client.startHandshake();
            phase="SC12";events.error("Защищённое подключение установлено · проверка телефона… [SC12]");
            DataInputStream in=new DataInputStream(client.getInputStream());DataOutputStream out=new DataOutputStream(client.getOutputStream());
            byte[] nonce=Identity.nonce();JSONObject challenge=Link.json("challenge");challenge.put("nonce",Protocol.hex(nonce));Protocol.write(out,challenge.toString());
            JSONObject auth=new JSONObject(Protocol.read(in));
            phase="SC13";byte[] publicKey=Handshake.decode(auth.getString("key"),512),proof=Handshake.decode(auth.getString("proof"),128);
            if(!"auth".equals(auth.getString("type"))||!Protocol.verify(publicKey,Protocol.transcript(pin,nonce,Protocol.unhex(auth.getString("nonce"))),proof))throw new IOException("Подпись устройства не подтверждена");
            String id=Protocol.hex(Protocol.digest(publicKey)),name=auth.getString("name").replaceAll("[\\p{Cntrl}]","").trim();
            if(name.isEmpty()||name.length()>40)throw new IOException("Некорректное имя телефона");
            SharedPreferences prefs=context.getSharedPreferences("paired",Context.MODE_PRIVATE);
            boolean known,busy;Link previous;SessionAdmission.Ticket ticket;
            phase="SC14";synchronized(this){
                known=prefs.contains(id);previous=current;busy=previous!=null&&!previous.isClosed();ticket=admission.begin(known,busy);
                if(!known){if(!pairing.claim(Protocol.unhex(auth.getString("token")),SystemClock.elapsedRealtime()))throw new IOException("QR истёк или использован. Отсканируйте новый QR на телевизоре.");rotate();}
            }
            phase="SC15";if(ticket.needsApproval&&!events.approve(name,busy))throw new IOException("Подключение не подтверждено на телевизоре");
            synchronized(this){
                if(stopped||!admission.accept(ticket)||prefs.contains(id)!=known||current!=previous)throw new IOException("Сопряжение изменилось. Подключитесь снова.");
                if(!known&&!prefs.edit().putString(id,name).commit())throw new IOException("Не удалось сохранить сопряжение");
                JSONObject accepted=Link.json("accepted");accepted.put("sid",Protocol.hex(nonce));Protocol.write(out,accepted.toString());
                if(previous!=null)previous.close();
                phase="SC16";current=new Link(client,Protocol.hex(nonce));events.connected(current,name);handed=true;
            }
        }catch(Exception e){String detail=ConnectionIssue.describe(phase,e);events.error(detail);try{JSONObject error=Link.json("error");error.put("error",detail);Protocol.write(new DataOutputStream(client.getOutputStream()),error.toString());}catch(Exception ignored){}}
        finally{pending.remove(client);if(!handed)try{client.close();}catch(IOException ignored){}}
    }
    void stopSession(){Link link=current;if(link!=null)link.close();}
    synchronized void forget(){admission.revoke();stopSession();context.getSharedPreferences("paired",Context.MODE_PRIVATE).edit().clear().commit();rotate();for(SSLSocket client:pending)try{client.close();}catch(IOException ignored){}}
    @Override public void close(){stopped=true;pairing.clear();stopSession();timer.shutdownNow();workers.shutdownNow();
        for(SSLSocket client:pending)try{client.close();}catch(IOException ignored){}
        if(socket!=null)try{socket.close();}catch(IOException ignored){}
        if(registration!=null)try{((NsdManager)context.getSystemService(Context.NSD_SERVICE)).unregisterService(registration);}catch(Exception ignored){}
    }
}

