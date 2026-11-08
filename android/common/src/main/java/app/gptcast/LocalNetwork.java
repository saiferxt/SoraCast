package app.gptcast;

import android.content.Context;
import android.net.*;
import android.net.nsd.*;
import app.nebo.core.Protocol;
import java.net.*;

public final class LocalNetwork {
    public static String address(Context c) {
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        for(Network network:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);LinkProperties props=cm.getLinkProperties(network);
            if(caps==null||props==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||
                !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;
            for(LinkAddress a:props.getLinkAddresses()){String ip=a.getAddress().getHostAddress();if(Protocol.privateIpv4(ip))return ip;}
        }
        return null;
    }
    public interface Found {void receiver(String host,int port);}
    public static NsdManager.DiscoveryListener discover(Context c,String pin,Found found){
        NsdManager nsd=(NsdManager)c.getSystemService(Context.NSD_SERVICE);
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){}public void onDiscoveryStopped(String type){}
            public void onStartDiscoveryFailed(String type,int error){}public void onStopDiscoveryFailed(String type,int error){}
            public void onServiceLost(NsdServiceInfo service){}
            public void onServiceFound(NsdServiceInfo service){nsd.resolveService(service,new NsdManager.ResolveListener(){
                public void onResolveFailed(NsdServiceInfo service,int error){}
                public void onServiceResolved(NsdServiceInfo s){byte[] attr=s.getAttributes().get("pin");String ip=s.getHost()==null?null:s.getHost().getHostAddress();
                    if(attr!=null&&pin.equals(new String(attr,java.nio.charset.StandardCharsets.US_ASCII))&&Protocol.privateIpv4(ip)&&s.getPort()>0&&s.getPort()<=65535)found.receiver(ip,s.getPort());}
            });}
        };
        nsd.discoverServices("_soracast._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);return listener;
    }
}

