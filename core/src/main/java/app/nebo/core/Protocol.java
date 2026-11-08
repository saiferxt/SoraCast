package app.nebo.core;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Bounds apply before allocation or key parsing; QR authenticates a LAN endpoint, never a web URL. */
public final class Protocol {
    public static final int MAX_FRAME = 262144;
    public static final class Offer {
        public final String host, pin;
        public final int port;
        public final byte[] token;
        public Offer(String host, int port, String pin, byte[] token) {
            if (!privateIpv4(host) || port < 1 || port > 65535 || !pin.matches("[0-9a-f]{64}") || token.length != 32)
                throw new IllegalArgumentException("Некорректный QR SoraCast");
            this.host=host; this.port=port; this.pin=pin; this.token=token.clone();
        }
        public String uri() { return "soracast://pair?v=1&host="+host+"&port="+port+"&pin="+pin+"&token="+hex(token); }
    }
    public static Offer parseOffer(String raw) {
        if (raw == null || raw.length() > 2048) throw new IllegalArgumentException("Слишком большой QR");
        try {
            URI u = new URI(raw);
            if (!"soracast".equals(u.getScheme()) || !"pair".equals(u.getRawAuthority()) || !u.getRawPath().isEmpty() || u.getRawFragment()!=null)
                throw new IllegalArgumentException("Это не QR SoraCast");
            Map<String,String> q = new HashMap<>();
            for (String kv : Objects.requireNonNull(u.getRawQuery()).split("&", -1)) {
                String[] p=kv.split("=", -1);
                if(p.length!=2 || q.put(p[0],p[1])!=null) throw new IllegalArgumentException("Повтор поля QR");
            }
            if(q.size()!=5 || !q.keySet().containsAll(Arrays.asList("v","host","port","pin","token")) || !"1".equals(q.get("v")))
                throw new IllegalArgumentException("Версия QR не поддерживается");
            return new Offer(q.get("host"),Integer.parseInt(q.get("port")),q.get("pin"),unhex(q.get("token")));
        } catch (Exception e) { throw new IllegalArgumentException("Некорректный QR SoraCast",e); }
    }
    public static boolean privateIpv4(String host) {
        if(host==null || !host.matches("[0-9.]{7,15}")) return false;
        String[] p=host.split("\\.",-1); if(p.length!=4) return false;
        int[] n=new int[4];
        for(int i=0;i<4;i++) { if(p[i].isEmpty() || p[i].length()>3 || p[i].length()>1&&p[i].startsWith("0")) return false;
            n[i]=Integer.parseInt(p[i]); if(n[i]>255) return false; }
        return n[0]==10 || n[0]==192&&n[1]==168 || n[0]==172&&n[1]>=16&&n[1]<=31;
    }
    public static String hex(byte[] bytes) {
        char[] a=new char[bytes.length*2]; char[] h="0123456789abcdef".toCharArray();
        for(int i=0;i<bytes.length;i++) { a[i*2]=h[(bytes[i]&255)>>>4];a[i*2+1]=h[bytes[i]&15]; } return new String(a);
    }
    public static byte[] unhex(String s) {
        if(s==null || !s.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid 256-bit value");
        byte[] b=new byte[32];for(int i=0;i<32;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return b;
    }
    public static byte[] digest(byte[] value) {
        try {return MessageDigest.getInstance("SHA-256").digest(value);}catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public static byte[] transcript(String pin, byte[] serverNonce, byte[] clientNonce) {
        byte[] p=unhex(pin);
        if(serverNonce.length!=32 || clientNonce.length!=32) throw new IllegalArgumentException("Invalid nonce");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try {out.write("SoraCast/1/client-proof\0".getBytes(StandardCharsets.UTF_8));out.write(p);out.write(serverNonce);out.write(clientNonce);}
        catch(IOException impossible){throw new IllegalStateException(impossible);}return out.toByteArray();
    }
    public static boolean verify(byte[] publicKey, byte[] transcript, byte[] signature) {
        if(publicKey.length>512 || signature.length>128) return false;
        try {PublicKey k=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKey));
            Signature s=Signature.getInstance("SHA256withECDSA");s.initVerify(k);s.update(transcript);return s.verify(signature);
        } catch(GeneralSecurityException e){return false;}
    }
    public static String read(DataInputStream in) throws IOException {
        int length=in.readInt();if(length<1||length>MAX_FRAME) throw new IOException("Invalid message length");
        byte[] b=new byte[length];in.readFully(b);return new String(b,StandardCharsets.UTF_8);
    }
    public static void write(DataOutputStream out,String message) throws IOException {
        byte[] b=message.getBytes(StandardCharsets.UTF_8);if(b.length<1||b.length>MAX_FRAME)throw new IOException("Invalid message length");
        out.writeInt(b.length);out.write(b);out.flush();
    }
}

