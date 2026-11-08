package app.gptcast.phone;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import app.gptcast.*;
import app.nebo.core.StreamQuality;

public final class ProjectionService extends Service {
    static volatile ProjectionService active;
    static volatile String state="Готов к подключению";
    static volatile String diagnostics="Показатели появятся после начала передачи";
    static volatile Runnable refresh;
    private static Link handoff;
    private MediaEngine engine;
    private String endingReason="Трансляция завершена";
    private PowerManager.WakeLock wake;
    private DisplayManager.DisplayListener displayListener;
    static synchronized void handoff(Link link){if(handoff!=null)handoff.close();handoff=link;}
    private static synchronized Link take(){Link l=handoff;handoff=null;return l;}
    static void update(String text){state=text;Runnable r=refresh;if(r!=null)new Handler(Looper.getMainLooper()).post(r);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(active!=null&&active.engine!=null){Link extra=take();if(extra!=null)extra.close();return START_NOT_STICKY;}
        Link link=take();if(link==null||link.isClosed()){if(link!=null)link.close();stopSelf();return START_NOT_STICKY;}
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("cast","Трансляция экрана",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ProjectionService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,PhoneActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=new Notification.Builder(this,"cast").setSmallIcon(app.gptcast.phone.R.drawable.cast_status).setContentTitle("SoraCast · передача экрана")
                .setContentText("Нажмите «Остановить», чтобы завершить").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Остановить",stop).build()).build();
            startForeground(10,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);active=this;diagnostics="Показатели появятся после начала передачи";update("Подключение медиапотока…");
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SoraCast:session");wake.acquire(2*60*60*1000L);
            Intent grant=intent.getParcelableExtra("grant");if(grant==null)throw new IllegalArgumentException("Missing permission");
            engine=new MediaEngine(this,link,null,grant,intent.getBooleanExtra("audio",false),StreamQuality.fromId(intent.getStringExtra("quality")),new MediaEngine.Events(){public void status(String s){if(active==ProjectionService.this)update(s);}public void diagnostics(String text){if(active==ProjectionService.this)diagnostics=text;}public void ended(String reason){new Handler(Looper.getMainLooper()).post(()->{if(active==ProjectionService.this){endingReason=reason;stopSelf();}});}});
            if(Build.VERSION.SDK_INT<34){displayListener=new DisplayManager.DisplayListener(){
                public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}
                public void onDisplayChanged(int id){DisplayMetrics d=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(d);if(engine!=null)engine.displayChanged(d.widthPixels,d.heightPixels);}
            };getSystemService(DisplayManager.class).registerDisplayListener(displayListener,new Handler(Looper.getMainLooper()));}
        }catch(Exception e){link.close();endingReason="Не удалось начать захват. Разрешите передачу экрана и попробуйте снова.";update(endingReason);stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){if(displayListener!=null)getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        if(engine!=null)engine.close();engine=null;if(wake!=null&&wake.isHeld())wake.release();if(active==this)active=null;
        update(endingReason);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

