package app.nebo.core;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.LongConsumer;

/** Bounded update metadata and payload verification; Android performs final APK verification. */
public final class UpdateRelease {
    public static final String REPOSITORY="saiferxt/SoraCast";
    public static final String FEED="https://github.com/"+REPOSITORY+"/releases/latest/download/updates.properties";
    public static final int MAX_FEED=65536;
    public static final long MAX_APK=200L*1024*1024;
    public final String packageName,versionName,sha256,notes;
    public final int versionCode,minSdk;
    public final long bytes;
    public final URI url;
    private UpdateRelease(Properties p,String role,String expected) {
        packageName=required(p,role+".packageName",80);
        if(!expected.equals(packageName))throw new IllegalArgumentException("Wrong update package");
        versionName=required(p,role+".versionName",80);
        notes=p.getProperty(role+".notes","");if(notes.length()>4000)throw new IllegalArgumentException("Release notes too long");
        try {
            versionCode=Integer.parseInt(required(p,role+".versionCode",10));
            minSdk=Integer.parseInt(required(p,role+".minSdk",3));
            bytes=Long.parseLong(required(p,role+".bytes",12));
            url=URI.create(required(p,role+".url",4096));
        }catch(RuntimeException bad){throw new IllegalArgumentException("Invalid update metadata",bad);}
        if(versionCode<1||minSdk<1||minSdk>1000||bytes<1||bytes>MAX_APK)throw new IllegalArgumentException("Update metadata out of range");
        sha256=required(p,role+".sha256",64).toLowerCase(Locale.ROOT);
        if(!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid update digest");
        checkDownloadUri(url);
        if(!"github.com".equalsIgnoreCase(url.getHost())||!url.getRawPath().startsWith("/"+REPOSITORY+"/releases/download/"))throw new IllegalArgumentException("Wrong update repository");
    }
    private static String required(Properties p,String key,int limit) {
        String value=p.getProperty(key);
        if(value==null||value.trim().isEmpty()||value.length()>limit)throw new IllegalArgumentException("Missing or invalid "+key);
        return value.trim();
    }
    public static UpdateRelease parse(byte[] data,String expectedPackage) {
        if(data==null||data.length==0||data.length>MAX_FEED)throw new IllegalArgumentException("Invalid update feed size");
        String role;
        if("app.soracast.phone".equals(expectedPackage))role="phone";
        else if("app.soracast.tv".equals(expectedPackage))role="tv";
        else throw new IllegalArgumentException("Unsupported app");
        Properties p=new Properties();
        try{p.load(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8));}
        catch(IOException bad){throw new IllegalArgumentException("Invalid update feed",bad);}
        if(!"1".equals(p.getProperty("schema")))throw new IllegalArgumentException("Unsupported update feed");
        return new UpdateRelease(p,role,expectedPackage);
    }
    public boolean available(long installedVersion,int sdk){return versionCode>installedVersion&&minSdk<=sdk;}
    public static void checkDownloadUri(URI uri) {
        if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException("HTTPS update address required");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        if(!Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","raw.githubusercontent.com").contains(host))throw new IllegalArgumentException("Untrusted update host");
    }
    public void copyAndVerify(InputStream input,OutputStream output,LongConsumer progress) throws IOException {
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        byte[] buffer=new byte[65536];long total=0;int read;
        while(true){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");
            read=input.read(buffer);if(read<0)break;if(read==0)continue;
            total+=read;if(total>bytes)throw new IOException("Update exceeds declared size");
            digest.update(buffer,0,read);output.write(buffer,0,read);progress.accept(total);
        }
        if(total!=bytes)throw new IOException("Incomplete update");
        StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!MessageDigest.isEqual(actual.toString().getBytes(StandardCharsets.US_ASCII),sha256.getBytes(StandardCharsets.US_ASCII)))throw new IOException("Update checksum mismatch");
    }
    public void checkArchive(String actualPackage,long actualVersion,byte[][] installedSigners,byte[][] candidateSigners) {
        if(!packageName.equals(actualPackage)||actualVersion!=versionCode)throw new SecurityException("Wrong APK identity/version");
        // This channel uses the existing single signing key; rotation requires an explicit migration.
        if(installedSigners==null||candidateSigners==null||installedSigners.length!=1||candidateSigners.length!=1||installedSigners[0]==null||candidateSigners[0]==null||installedSigners[0].length==0||!MessageDigest.isEqual(installedSigners[0],candidateSigners[0]))throw new SecurityException("APK signer does not match installed app");
    }
}
