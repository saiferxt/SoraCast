package app.gptcast.tv;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import app.gptcast.*;
import app.gptcast.updates.UpdateUi;
import app.nebo.core.Protocol;
import app.nebo.core.StreamQuality;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.webrtc.*;

public final class TvActivity extends Activity implements ReceiverServer.Events {
    private LinearLayout welcome;
    private FrameLayout root;
    private ImageView qr;
    private TextView status;
    private TextView quality;
    private String dimensions="Разрешение потока пока неизвестно";
    private ReceiverServer server;
    private SurfaceViewRenderer renderer;
    private volatile MediaEngine engine;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean visible;
    private volatile boolean updateAllowed=true;
    private AlertDialog confirmation;
    private final AtomicInteger generation=new AtomicInteger();
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        root=new FrameLayout(this);Ui.background(root);setContentView(root);
        renderer=new SurfaceViewRenderer(this);renderer.init(MediaEngine.eglContext(),new RendererCommon.RendererEvents(){
            public void onFirstFrameRendered(){}
            public void onFrameResolutionChanged(int width,int height,int rotation){main.post(()->{dimensions="Поток: "+(rotation%180==0?width:height)+" × "+(rotation%180==0?height:width);if(quality!=null)quality.setText(dimensions);});}
        });renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        // Fixed-size Surface crops the buffer to TV aspect before FIT letterboxing, losing portrait detail.
        renderer.setEnableHardwareScaler(false);renderer.setVisibility(View.GONE);
        root.addView(renderer,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        welcome=new LinearLayout(this);welcome.setGravity(Gravity.CENTER_VERTICAL);welcome.setPadding(Ui.dp(this,44),Ui.dp(this,24),Ui.dp(this,44),Ui.dp(this,24));
        root.addView(welcome,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout left=Ui.column(this);left.setGravity(Gravity.CENTER_VERTICAL);welcome.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        TextView brand=Ui.text(this,"SoraCast",18);brand.setLetterSpacing(.12f);left.addView(brand);
        TextView title=Ui.text(this,"Ваш экран — здесь",34);title.setTypeface(null,android.graphics.Typeface.BOLD);left.addView(title);
        left.addView(Ui.text(this,"1. Откройте SoraCast на телефоне\n2. Отсканируйте QR и подтвердите подключение",19));
        status=Ui.text(this,"Подготовка приёмника…",15);left.addView(status);
        left.addView(Ui.button(this,"Обновить QR",()->{if(server!=null)server.rotate();}));
        left.addView(Ui.button(this,"Обновления · "+UpdateUi.version(this),()->{if(updateAllowed)UpdateUi.open(this);}));
        left.addView(Ui.button(this,"Забыть телефоны",()->new AlertDialog.Builder(this).setTitle("Удалить все сопряжения?").setMessage("Каждому телефону потребуется снова отсканировать QR.").setNegativeButton("Отмена",null).setPositiveButton("Забыть",(d,w)->server.forget()).show()));
        qr=new ImageView(this);qr.setContentDescription("QR для подключения SoraCast");qr.setBackground(Ui.glass(this));qr.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
        int side=Ui.dp(this,248);welcome.addView(qr,new LinearLayout.LayoutParams(side,side));
        quality=Ui.text(this,"Разрешение потока пока неизвестно",14);quality.setTextColor(Color.WHITE);quality.setBackgroundColor(0xBB000000);
        quality.setPadding(Ui.dp(this,12),Ui.dp(this,6),Ui.dp(this,12),Ui.dp(this,6));quality.setVisibility(View.GONE);
        FrameLayout.LayoutParams qualityPosition=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);qualityPosition.bottomMargin=Ui.dp(this,12);root.addView(quality,qualityPosition);
        UpdateUi.automatic(this,()->updateAllowed&&welcome.getVisibility()==View.VISIBLE&&(confirmation==null||!confirmation.isShowing()));
    }
    @Override protected void onStart(){super.onStart();visible=true;updateAllowed=true;quality.setVisibility(View.GONE);renderer.clearImage();renderer.setVisibility(View.GONE);welcome.setVisibility(View.VISIBLE);Ui.background(root);server=new ReceiverServer(this,this);server.start();}
    @Override protected void onStop(){UpdateUi.dismiss(this);visible=false;generation.incrementAndGet();if(confirmation!=null)confirmation.dismiss();if(server!=null)server.close();MediaEngine m=engine;if(m!=null)m.close();super.onStop();}
    @Override protected void onDestroy(){renderer.release();super.onDestroy();}
    public void offer(Protocol.Offer o){main.post(()->{if(!visible)return;try{qr.setImageBitmap(Ui.qr(o.uri(),432));status.setText("Одна домашняя сеть · QR обновляется каждые 2 минуты");}catch(Exception e){status.setText("Не удалось создать QR");}});}
    public boolean approve(String name,boolean switching){CountDownLatch latch=new CountDownLatch(1);AtomicBoolean accepted=new AtomicBoolean();
        main.post(()->{if(!visible||isFinishing()){latch.countDown();return;}
            UpdateUi.dismiss(this);
            if(confirmation!=null&&confirmation.isShowing()){latch.countDown();return;}
            confirmation=new AlertDialog.Builder(this).setTitle(switching?"Переключить телефон?":"Разрешить подключение?")
                .setMessage(name+(switching?" хочет заменить текущую трансляцию.":" хочет передавать экран. Подтверждайте только знакомый телефон."))
                .setNegativeButton("Отмена",(d,w)->latch.countDown()).setPositiveButton("Разрешить",(d,w)->{accepted.set(true);latch.countDown();})
                .setOnCancelListener(d->latch.countDown()).create();confirmation.setOnDismissListener(d->latch.countDown());confirmation.show();
            main.postDelayed(()->{if(latch.getCount()>0&&confirmation!=null)confirmation.dismiss();},30000);
        });try{return latch.await(32,TimeUnit.SECONDS)&&accepted.get()&&visible;}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}
    public void connected(Link link,String name){int id=generation.incrementAndGet();
        updateAllowed=false;main.post(()->UpdateUi.dismiss(this));
        MediaEngine old=engine;if(old!=null)old.close();
        engine=new MediaEngine(this,link,renderer,null,false,StreamQuality.AUTO,new MediaEngine.Events(){
            public void status(String text){main.post(()->{if(id!=generation.get()||!visible)return;status.setText(text);if(text.startsWith("Приём"))quality.setText(dimensions+"\n"+text);if("Экран подключён".equals(text)){renderer.setVisibility(View.VISIBLE);welcome.setVisibility(View.GONE);root.setBackgroundColor(Color.BLACK);}});}
            public void ended(String reason){main.post(()->{if(id!=generation.get()||!visible)return;updateAllowed=true;quality.setVisibility(View.GONE);renderer.clearImage();renderer.setVisibility(View.GONE);welcome.setVisibility(View.VISIBLE);Ui.background(root);status.setText(reason);});}
        });}
    public void error(String text){main.post(()->{if(visible)status.setText(text);});}
    @Override public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_CENTER&&welcome.getVisibility()!=View.VISIBLE){quality.setVisibility(quality.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);return true;}return super.onKeyUp(key,event);}
    @Override public void onBackPressed(){if(engine!=null&&welcome.getVisibility()!=View.VISIBLE)new AlertDialog.Builder(this).setTitle("Остановить трансляцию?").setNegativeButton("Продолжить",null).setPositiveButton("Остановить",(d,w)->server.stopSession()).show();else super.onBackPressed();}
}

