package app.gptcast;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TlsKeyManagerTest {
    private static KeyStore generatedFixture;
    private static synchronized KeyStore fixture() throws Exception {
        if(generatedFixture!=null)return generatedFixture;
        Path directory=Files.createTempDirectory("soracast-tls-test-"),file=directory.resolve("ephemeral.p12");
        String tool=Paths.get(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase().contains("win")?"keytool.exe":"keytool").toString();
        try{
            for(String alias:new String[]{"tls-v2","legacy"}){
                Process p=new ProcessBuilder(tool,"-genkeypair","-alias",alias,"-keyalg","EC","-groupname","secp256r1","-dname","CN=SoraCast ephemeral test","-validity","2","-storetype","PKCS12","-keystore",file.toString(),"-storepass","test-only","-noprompt")
                    .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
                if(!p.waitFor(30,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Test key generation timed out");}
                if(p.exitValue()!=0)throw new IOException("Cannot generate ephemeral TLS test keys");
            }
            KeyStore k=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(file)){k.load(in,"test-only".toCharArray());}generatedFixture=k;return k;
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(directory.resolve("keytool.log"));Files.deleteIfExists(directory);}
    }
    private TlsKeyManager manager(KeyStore k) throws Exception {
        return new TlsKeyManager("tls-v2",(PrivateKey)k.getKey("tls-v2","test-only".toCharArray()),new X509Certificate[]{(X509Certificate)k.getCertificate("tls-v2")});
    }
    @Test public void rawEcdsaOperationAndExplicitAlias() throws Exception {
        KeyStore k=fixture();TlsKeyManager m=manager(k);
        m.preflight();assertEquals("tls-v2",m.chooseServerAlias("EC",null,null));assertNull(m.chooseServerAlias("RSA",null,null));
        assertNull(m.getPrivateKey("legacy"));assertNull(m.getCertificateChain("legacy"));assertNull(m.chooseClientAlias(new String[]{"EC"},null,null));
        X509Certificate[] copy=m.getCertificateChain("tls-v2");copy[0]=null;assertNotNull(m.getCertificateChain("tls-v2")[0]);
    }
    @Test public void mismatchedKeyFailsBeforeListening() throws Exception {
        KeyStore k=fixture();TlsKeyManager bad=new TlsKeyManager("tls-v2",(PrivateKey)k.getKey("legacy","test-only".toCharArray()),new X509Certificate[]{(X509Certificate)k.getCertificate("tls-v2")});
        try{bad.preflight();fail("Mismatched TLS material must be rejected");}catch(GeneralSecurityException expected){}
    }
    @Test public void realTls12ExchangeUsesPinnedCertificate() throws Exception {
        KeyStore k=fixture();TlsKeyManager km=manager(k);km.preflight();
        SSLContext server=SSLContext.getInstance("TLS");server.init(new KeyManager[]{km},null,new SecureRandom());
        byte[] pin=MessageDigest.getInstance("SHA-256").digest(k.getCertificate("tls-v2").getEncoded());
        X509TrustManager trust=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException();}
            public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{try{if(!MessageDigest.isEqual(pin,MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded())))throw new java.security.cert.CertificateException("pin mismatch");}catch(GeneralSecurityException e){throw new java.security.cert.CertificateException(e);}}};
        SSLContext client=SSLContext.getInstance("TLS");client.init(null,new TrustManager[]{trust},new SecureRandom());
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(SSLServerSocket listener=(SSLServerSocket)server.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress())){
            listener.setEnabledProtocols(new String[]{"TLSv1.2"});listener.setSoTimeout(5000);
            Future<Integer> got=worker.submit(()->{try(SSLSocket s=(SSLSocket)listener.accept()){s.setSoTimeout(5000);s.startHandshake();int value=s.getInputStream().read();s.getOutputStream().write(value+1);return value;}});
            try(SSLSocket s=(SSLSocket)client.getSocketFactory().createSocket(InetAddress.getLoopbackAddress(),listener.getLocalPort())){
                s.setSoTimeout(5000);s.setEnabledProtocols(new String[]{"TLSv1.2"});s.startHandshake();s.getOutputStream().write(41);assertEquals(42,s.getInputStream().read());assertEquals(Integer.valueOf(41),got.get(5,TimeUnit.SECONDS));
            }
        }finally{worker.shutdownNow();}
    }
}
