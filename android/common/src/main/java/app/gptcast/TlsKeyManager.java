package app.gptcast;

import java.net.Socket;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Only the TLS-authorized key is eligible; an old signing-only alias must never be selected. */
public final class TlsKeyManager extends X509ExtendedKeyManager {
    private final String alias;
    private final PrivateKey key;
    private final X509Certificate[] chain;
    public TlsKeyManager(String alias,PrivateKey key,X509Certificate[] chain){this.alias=alias;this.key=key;this.chain=chain.clone();}
    public void preflight() throws GeneralSecurityException {
        byte[] digest=new byte[32];new SecureRandom().nextBytes(digest);
        // Android TLS signs an already calculated digest. This also checks Keystore authorizations.
        Signature signature=Signature.getInstance("NONEwithECDSA");signature.initSign(key);signature.update(digest);byte[] proof=signature.sign();
        Signature verify=Signature.getInstance("NONEwithECDSA");verify.initVerify(chain[0].getPublicKey());verify.update(digest);
        if(!verify.verify(proof))throw new InvalidKeyException("TLS certificate does not match its private key");
    }
    private boolean supported(String type){return type!=null&&(type.equals("EC")||type.startsWith("EC_"));}
    @Override public String[] getServerAliases(String type,Principal[] issuers){return supported(type)?new String[]{alias}:null;}
    @Override public String chooseServerAlias(String type,Principal[] issuers,Socket socket){return supported(type)?alias:null;}
    @Override public String chooseEngineServerAlias(String type,Principal[] issuers,SSLEngine engine){return supported(type)?alias:null;}
    @Override public PrivateKey getPrivateKey(String name){return alias.equals(name)?key:null;}
    @Override public X509Certificate[] getCertificateChain(String name){return alias.equals(name)?chain.clone():null;}
    @Override public String[] getClientAliases(String type,Principal[] issuers){return null;}
    @Override public String chooseClientAlias(String[] types,Principal[] issuers,Socket socket){return null;}
}
