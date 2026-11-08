package app.gptcast;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import app.nebo.core.Protocol;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/** Trust is the exact QR certificate digest; no global trust-all manager or hostname bypass. */
public final class Identity {
    private final KeyStore store;
    private static final String ALIAS="gptcast-device-v1";
    private static final String TLS_ALIAS="soracast-tls-v2";
    public Identity() throws Exception {
        store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
            Calendar end=Calendar.getInstance();end.add(Calendar.YEAR,20);
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
                .setCertificateSubject(new X500Principal("CN=SoraCast"))
                .setCertificateSerialNumber(new BigInteger(128,new SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(new Date(1577836800000L)).setCertificateNotAfter(end.getTime()).build());
            g.generateKeyPair();
        }
    }
    private synchronized void ensureTlsKey() throws Exception {
        if(store.containsAlias(TLS_ALIAS))return;
        KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
        Calendar end=Calendar.getInstance();end.add(Calendar.YEAR,20);
        g.initialize(new KeyGenParameterSpec.Builder(TLS_ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(new X500Principal("CN=SoraCast TLS"))
            .setCertificateSerialNumber(new BigInteger(128,new SecureRandom()).add(BigInteger.ONE))
            .setCertificateNotBefore(new Date(1577836800000L)).setCertificateNotAfter(end.getTime()).build());
        g.generateKeyPair();
    }
    public String pin() throws Exception {ensureTlsKey();return Protocol.hex(Protocol.digest(store.getCertificate(TLS_ALIAS).getEncoded()));}
    public byte[] publicKey() throws Exception {return store.getCertificate(ALIAS).getPublicKey().getEncoded();}
    public byte[] sign(byte[] data) throws Exception {
        Signature s=Signature.getInstance("SHA256withECDSA");s.initSign((PrivateKey)store.getKey(ALIAS,null));s.update(data);return s.sign();
    }
    public SSLServerSocket listen(String address) throws Exception {
        if(!Protocol.privateIpv4(address))throw new IllegalArgumentException("Local network required");
        ensureTlsKey();
        TlsKeyManager manager=new TlsKeyManager(TLS_ALIAS,(PrivateKey)store.getKey(TLS_ALIAS,null),new X509Certificate[]{(X509Certificate)store.getCertificate(TLS_ALIAS)});
        manager.preflight();
        SSLContext c=SSLContext.getInstance("TLS");c.init(new KeyManager[]{manager},null,new SecureRandom());
        SSLServerSocket server=(SSLServerSocket)c.getServerSocketFactory().createServerSocket();
        server.setEnabledProtocols(new String[]{"TLSv1.2"});server.bind(new InetSocketAddress(address,0),4);return server;
    }
    public static SSLSocket connect(Protocol.Offer offer) throws Exception {
        X509TrustManager pins=new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth) throws java.security.cert.CertificateException {throw new java.security.cert.CertificateException("Client certificate not used");}
            public void checkServerTrusted(X509Certificate[] chain,String auth) throws java.security.cert.CertificateException {
                try {if(chain.length!=1 || !MessageDigest.isEqual(Protocol.digest(chain[0].getEncoded()),Protocol.unhex(offer.pin)))
                    throw new java.security.cert.CertificateException("Ключ приставки изменился. Повторите QR-сопряжение.");}
                catch(GeneralSecurityException e){throw new java.security.cert.CertificateException(e);}
            }
        };
        SSLContext c=SSLContext.getInstance("TLS");c.init(null,new TrustManager[]{pins},new SecureRandom());
        SSLSocket socket=(SSLSocket)c.getSocketFactory().createSocket();
        try {socket.setEnabledProtocols(new String[]{"TLSv1.2"});socket.setTcpNoDelay(true);socket.setSoTimeout(45000);
            socket.connect(new InetSocketAddress(offer.host,offer.port),7000);socket.startHandshake();return socket;}
        catch(Exception e){try{socket.close();}catch(Exception ignored){}throw e;}
    }
    public static byte[] nonce(){byte[] n=new byte[32];new SecureRandom().nextBytes(n);return n;}
}

