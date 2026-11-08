package app.gptcast.updates;

import app.nebo.core.UpdateRelease;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.function.LongConsumer;

/** HTTPS only, bounded bodies, explicit redirect policy, no credentials or background service. */
final class UpdateHttp implements Closeable {
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    static final class Unpublished extends IOException {}
    private InputStream open(URI address) throws IOException {
        for(int redirects=0;redirects<=5;redirects++) {
            if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            UpdateRelease.checkDownloadUri(address);
            HttpURLConnection c=(HttpURLConnection)address.toURL().openConnection();connection=c;
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent","SoraCast-Updater/1");c.setRequestProperty("Accept-Encoding","identity");
            int code=c.getResponseCode();
            if(cancelled){c.disconnect();throw new InterruptedIOException();}
            if(code==200)return c.getInputStream();
            if(code==404){c.disconnect();throw new Unpublished();}
            if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Missing redirect");address=address.resolve(location);continue;}
            c.disconnect();throw new IOException("Update HTTP "+code);
        }
        throw new IOException("Too many update redirects");
    }
    UpdateRelease check(String packageName) throws IOException {
        try(InputStream in=open(URI.create(UpdateRelease.FEED));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>UpdateRelease.MAX_FEED)throw new IOException("Feed too large");out.write(buffer,0,n);}
            return UpdateRelease.parse(out.toByteArray(),packageName);
        }finally{closeConnection();}
    }
    File download(UpdateRelease release,File directory,LongConsumer progress) throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create private cache");
        File part=new File(directory,release.sha256+".part"),target=new File(directory,release.sha256+".apk");
        // Only app-owned update files, never shared storage or user-selected directories.
        File[] previous=directory.listFiles();if(previous!=null)for(File f:previous)if(f.getName().matches("[0-9a-f]{64}\\.(apk|part)")&&!f.equals(part))f.delete();
        try{
            try(InputStream in=open(release.url);FileOutputStream out=new FileOutputStream(part)){
                release.copyAndVerify(in,out,progress);out.getFD().sync();
            }
            if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            return target;
        }finally{part.delete();closeConnection();}
    }
    private void closeConnection(){HttpURLConnection c=connection;connection=null;if(c!=null)c.disconnect();}
    @Override public void close(){cancelled=true;closeConnection();}
}
