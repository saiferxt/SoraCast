package app.gptcast.updates;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import app.nebo.core.UpdateRelease;
import app.nebo.core.UpdateCommitGate;
import java.io.*;

final class UpdateInstaller {
    static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    @SuppressWarnings("deprecation")
    private static byte[][] signers(PackageInfo p){
        Signature[] signatures=Build.VERSION.SDK_INT>=28&&p.signingInfo!=null?p.signingInfo.getApkContentsSigners():p.signatures;
        if(signatures==null)return new byte[0][];
        byte[][] result=new byte[signatures.length][];for(int i=0;i<result.length;i++)result[i]=signatures[i].toByteArray();return result;
    }
    @SuppressWarnings("deprecation")
    static void verify(Context c,File file,UpdateRelease release) throws Exception {
        PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo own=pm.getPackageInfo(c.getPackageName(),flags),candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(candidate==null||candidate.applicationInfo==null)throw new SecurityException("Not an APK");
        if(!release.available(version(own),Build.VERSION.SDK_INT)||candidate.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new SecurityException("Incompatible update");
        release.checkArchive(candidate.packageName,version(candidate),signers(own),signers(candidate));
        try(InputStream in=new FileInputStream(file)){
            release.copyAndVerify(in,new OutputStream(){public void write(int b){}public void write(byte[] b,int off,int len){}},n->{});
        }
    }
    static void install(Context c,File file,UpdateRelease release,UpdateCommitGate gate) throws Exception {
        if(!c.getPackageManager().canRequestPackageInstalls())throw new SecurityException("Install approval required");
        verify(c,file,release);
        PackageInstaller installer=c.getPackageManager().getPackageInstaller();
        SharedPreferences prefs=c.getSharedPreferences("updates",Context.MODE_PRIVATE);
        int previous=prefs.getInt("session",-1);
        if(previous!=-1)try{installer.abandonSession(previous);}catch(RuntimeException ignored){}
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());params.setSize(file.length());params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int id=installer.createSession(params);
        boolean committed=false;
        try(PackageInstaller.Session session=installer.openSession(id)){
            try(InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,file.length())){
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.write(buffer,0,n);}session.fsync(out);
            }
            if(Thread.currentThread().isInterrupted()||!gate.beginCommit())throw new InterruptedIOException("Installation cancelled before handoff");
            prefs.edit().putInt("session",id).putString("installResult","Ожидается подтверждение установки Android").apply();
            Intent callback=new Intent(c,InstallResultReceiver.class).setAction(c.getPackageName()+".INSTALL_RESULT");
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent status=PendingIntent.getBroadcast(c,id,callback,flags);
            session.commit(status.getIntentSender());committed=true;
        }finally{if(!committed){try{installer.abandonSession(id);}catch(RuntimeException ignored){}prefs.edit().remove("session").apply();}}
    }
}
