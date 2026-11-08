package app.gptcast.updates;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.os.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.lang.ref.WeakReference;
import app.nebo.core.UpdateRelease;

public final class UpdateUi {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static WeakReference<AlertDialog> prompt=new WeakReference<>(null);
    private static WeakReference<Activity> promptOwner=new WeakReference<>(null);
    public static void dismiss(Activity activity){AlertDialog d=prompt.get();if(promptOwner.get()==activity){if(d!=null)d.dismiss();prompt.clear();promptOwner.clear();}}
    public static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "неизвестна";}}
    public static void open(Activity activity){activity.startActivity(new Intent(activity,UpdateActivity.class));}
    public static void automatic(Activity activity,BooleanSupplier allowed){
        SharedPreferences p=activity.getSharedPreferences("updates",Context.MODE_PRIVATE);long now=System.currentTimeMillis(),last=p.getLong("lastAttempt",0);
        if(now>=last&&now-last<24*60*60*1000L)return;
        p.edit().putLong("lastAttempt",now).apply();
        WORKER.execute(()->{try(UpdateHttp http=new UpdateHttp()){
            UpdateRelease release=http.check(activity.getPackageName());PackageInfo own=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            if(!release.available(UpdateInstaller.version(own),Build.VERSION.SDK_INT))return;
            new Handler(Looper.getMainLooper()).post(()->{if(activity.isFinishing()||activity.isDestroyed()||!allowed.getAsBoolean())return;
                AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Доступна SoraCast "+release.versionName).setMessage("Открыть страницу обновления?")
                    .setNegativeButton("Позже",null).setPositiveButton("Обновления",(d,w)->{if(!activity.isFinishing()&&!activity.isDestroyed()&&allowed.getAsBoolean())open(activity);}).create();prompt=new WeakReference<>(dialog);promptOwner=new WeakReference<>(activity);dialog.show();});
        }catch(Exception ignored){/* Offline launches stay quiet; the manual page explains errors. */}});
    }
}
