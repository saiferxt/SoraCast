package app.nebo.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;

/** Real parser and streamed payload checks, no network or Android dependency. */
public final class UpdateSpec {
    private static int checks;
    private static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    static String feed(){return "schema=1\nphone.packageName=app.soracast.phone\nphone.versionCode=7\nphone.versionName=0.1.5\nphone.minSdk=29\nphone.bytes=3\nphone.sha256="+HASH+"\nphone.url=https://github.com/saiferxt/SoraCast/releases/download/v0.1.5/SoraCast_phone.apk\nphone.notes=Исправления\\nНовые возможности\n";}
    static UpdateRelease parse(String value){return UpdateRelease.parse(value.getBytes(StandardCharsets.UTF_8),"app.soracast.phone");}
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    interface Action {void run() throws Exception;}
    static void reject(Action action,String name){boolean failed=false;try{action.run();}catch(IOException|IllegalArgumentException|SecurityException expected){failed=true;}catch(Exception e){throw new AssertionError(name,e);}check(failed,name);}
    public static void main(String[] args) throws Exception {
        UpdateRelease r=parse(feed());
        check(r.versionCode==7&&r.bytes==3,"valid metadata parsed");
        check(r.notes.contains("Исправления\nНовые"),"UTF8 and escaped notes preserved");
        check(r.available(6,29),"new compatible release offered");
        check(!r.available(7,29)&&!r.available(8,29),"same/older never offered");
        check(!r.available(6,28),"unsupported SDK not offered");
        reject(()->UpdateRelease.parse(feed().getBytes(StandardCharsets.UTF_8),"app.soracast.tv"),"wrong role rejected");
        reject(()->parse(feed().replace("app.soracast.phone","some.other.app")),"different package rejected");
        reject(()->parse(feed().replace("schema=1","schema=2")),"unknown schema rejected");
        reject(()->parse(feed().replace("versionCode=7","versionCode=0")),"nonpositive version rejected");
        reject(()->parse(feed().replace("bytes=3","bytes=999999999")),"oversize APK rejected");
        reject(()->parse(feed().replace("bytes=3","bytes=-1")),"negative size rejected");
        reject(()->parse(feed().replace(HASH,"broken")),"malformed digest rejected");
        reject(()->parse(feed().replace("https://","http://")),"insecure URL rejected");
        reject(()->parse(feed().replace("github.com/saiferxt","github.com.evil/saiferxt")),"host spoof rejected");
        reject(()->parse(feed().replace("github.com/saiferxt","user@github.com/saiferxt")),"userinfo rejected");
        reject(()->parse(feed().replace("github.com/saiferxt","github.com/another-owner")),"wrong repository rejected");
        reject(()->parse("<html>not a manifest</html>"),"HTML error page rejected");
        reject(()->parse(" ".repeat(65537)),"unbounded manifest rejected");
        UpdateRelease.checkDownloadUri(URI.create("https://release-assets.githubusercontent.com/x?signature=test"));
        reject(()->UpdateRelease.checkDownloadUri(URI.create("http://release-assets.githubusercontent.com/x")),"redirect downgrade rejected");
        reject(()->UpdateRelease.checkDownloadUri(URI.create("https://127.0.0.1/x")),"arbitrary redirect host rejected");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        r.copyAndVerify(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),output,n->{});
        check(output.toString("UTF-8").equals("abc"),"complete payload streamed");
        reject(()->r.copyAndVerify(new ByteArrayInputStream(new byte[]{1,2,3}),new ByteArrayOutputStream(),n->{}),"corrupted payload rejected");
        reject(()->r.copyAndVerify(new ByteArrayInputStream(new byte[]{1,2}),new ByteArrayOutputStream(),n->{}),"truncated payload rejected");
        reject(()->r.copyAndVerify(new ByteArrayInputStream(new byte[]{1,2,3,4}),new ByteArrayOutputStream(),n->{}),"oversized response rejected");
        Thread.currentThread().interrupt();
        try{reject(()->r.copyAndVerify(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),new ByteArrayOutputStream(),n->{}),"cancelled transfer rejected");}finally{Thread.interrupted();}
        r.checkArchive("app.soracast.phone",7,new byte[][]{{1,2}},new byte[][]{{1,2}});
        reject(()->r.checkArchive("app.soracast.tv",7,new byte[][]{{1,2}},new byte[][]{{1,2}}),"wrong archive app rejected");
        reject(()->r.checkArchive("app.soracast.phone",8,new byte[][]{{1,2}},new byte[][]{{1,2}}),"metadata/archive version mismatch rejected");
        reject(()->r.checkArchive("app.soracast.phone",7,new byte[][]{{1,2}},new byte[][]{{9,9}}),"different signer rejected");
        reject(()->r.checkArchive("app.soracast.phone",7,new byte[0][],new byte[0][]),"missing signers rejected");
        UpdateCommitGate cancelled=new UpdateCommitGate();
        check(cancelled.cancel(),"cancel before commit accepted");
        check(!cancelled.beginCommit(),"cancelled installation cannot commit");
        UpdateCommitGate committing=new UpdateCommitGate();
        check(committing.beginCommit(),"commit may begin once");
        check(!committing.cancel(),"cancel is not falsely accepted after installer handoff");
        check(!committing.beginCommit(),"duplicate commit refused");
        for(int attempt=0;attempt<16;attempt++){
            UpdateCommitGate race=new UpdateCommitGate();java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);boolean[] won=new boolean[2];
            Thread commit=new Thread(()->{try{start.await();won[0]=race.beginCommit();}catch(InterruptedException e){throw new AssertionError(e);}});
            Thread cancel=new Thread(()->{try{start.await();won[1]=race.cancel();}catch(InterruptedException e){throw new AssertionError(e);}});
            commit.start();cancel.start();start.countDown();commit.join();cancel.join();check(won[0]!=won[1],"cancel and commit cannot both succeed");
        }
        System.out.println("PASS: "+checks+" update checks (manifest, trust, streamed hash, cancellation)");
    }
}
