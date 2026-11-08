package app.gptcast;

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import app.nebo.core.Protocol;
import java.io.*;
import javax.net.ssl.SSLSocket;
import org.json.JSONObject;

public final class Handshake {
    public static Link connect(Context context,Protocol.Offer offer) throws Exception {
        SSLSocket socket=null;String phase="SC01";
        try {
            socket=Identity.connect(offer);phase="SC02";
            Identity identity=new Identity();phase="SC03";DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            JSONObject challenge=new JSONObject(Protocol.read(in));
            if(!"challenge".equals(challenge.getString("type")))throw new IOException("Неверный протокол приставки");
            byte[] serverNonce=Protocol.unhex(challenge.getString("nonce")),clientNonce=Identity.nonce();
            phase="SC04";JSONObject auth=Link.json("auth");
            String name=Build.MANUFACTURER+" "+Build.MODEL;
            auth.put("name",name.substring(0,Math.min(40,name.length()))).put("nonce",Protocol.hex(clientNonce))
                .put("token",Protocol.hex(offer.token)).put("key",b64(identity.publicKey()))
                .put("proof",b64(identity.sign(Protocol.transcript(offer.pin,serverNonce,clientNonce))));
            Protocol.write(out,auth.toString());
            phase="SC05";JSONObject response=new JSONObject(Protocol.read(in));
            if(!"accepted".equals(response.optString("type")) || !Protocol.hex(serverNonce).equals(response.optString("sid")))
                throw new Rejected(response.optString("error","Приставка не подтвердила подключение"));
            return new Link(socket,Protocol.hex(serverNonce));
        }catch(Exception e){if(socket!=null)try{socket.close();}catch(Exception ignored){}if(e instanceof Rejected)throw e;throw ConnectionIssue.at(phase,e);}
    }
    private static final class Rejected extends IOException {Rejected(String text){super(text);}}
    public static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}
    public static byte[] decode(String s,int limit) throws IOException {
        if(s.length()>limit*2)throw new IOException("Oversized key");
        try{byte[] b=Base64.decode(s,Base64.NO_WRAP);if(b.length>limit)throw new IOException("Oversized key");return b;}
        catch(IllegalArgumentException e){throw new IOException("Invalid key",e);}
    }
}
