package app.gptcast.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjectionManager;
import android.net.nsd.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.gptcast.*;
import app.gptcast.updates.UpdateUi;
import app.nebo.core.Protocol;
import app.nebo.core.StreamQuality;
import java.util.concurrent.*;
import org.json.*;

public final class PhoneActivity extends Activity {
    private Protocol.Offer offer;
    private volatile Link pending;
    private boolean connecting;
    private TextView target,status;
    private Button cast,qualityButton,metricsButton;
    private StreamQuality selectedQuality;
    private Switch audio;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private NsdManager.DiscoveryListener discovery;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        selectedQuality=StreamQuality.fromId(getSharedPreferences("stream",MODE_PRIVATE).getString("quality",null));
        FrameLayout outer=new FrameLayout(this);Ui.background(outer);Ui.insets(outer);setContentView(outer);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);outer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=Ui.column(this);scroll.addView(root);root.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);icon.setImageResource(app.gptcast.R.drawable.gptcast_icon);icon.setContentDescription("SoraCast");
        LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(Ui.dp(this,88),Ui.dp(this,88));iconSize.gravity=Gravity.CENTER_HORIZONTAL;root.addView(icon,iconSize);
        TextView name=Ui.text(this,"SoraCast",32);name.setTypeface(null,android.graphics.Typeface.BOLD);name.setGravity(Gravity.CENTER);root.addView(name);
        LinearLayout card=Ui.column(this);card.setBackground(Ui.glass(this));root.addView(card,new LinearLayout.LayoutParams(-1,-2));
        target=Ui.text(this,"Подключите телевизор",23);target.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(target);
        status=Ui.text(this,"Откройте SoraCast на телевизоре и отсканируйте QR",16);card.addView(status);
        qualityButton=Ui.button(this,"",this::chooseQuality);qualityButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);card.addView(qualityButton);
        cast=Ui.button(this,"Передать экран",this::cast);card.addView(cast);
        audio=new Switch(this);audio.setText("Передавать звук устройства");audio.setTextSize(16);audio.setTextColor(Ui.INK);audio.setChecked(true);audio.setMinHeight(Ui.dp(this,56));card.addView(audio);
        metricsButton=Ui.button(this,"Показатели передачи",()->{
            String snapshot=ProjectionService.diagnostics;
            new AlertDialog.Builder(this).setTitle("Показатели передачи").setMessage(snapshot).setNegativeButton("Закрыть",null)
                .setPositiveButton("Скопировать",(d,w)->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("SoraCast",snapshot));Toast.makeText(this,"Показатели скопированы",Toast.LENGTH_SHORT).show();}).show();
        });card.addView(metricsButton);
        root.addView(Ui.button(this,"Подключить по QR",()->{if(!connecting&&ProjectionService.active==null)startActivityForResult(new Intent(this,ScanActivity.class),10);}));
        root.addView(Ui.button(this,"Мои телевизоры",this::choose));root.addView(Ui.button(this,"Добавить в шторку",this::tile));
        root.addView(Ui.button(this,"Обновления · "+UpdateUi.version(this),()->{if(connecting||ProjectionService.active!=null)Toast.makeText(this,"Сначала остановите трансляцию",Toast.LENGTH_SHORT).show();else UpdateUi.open(this);}));
        TextView manual=Ui.text(this,"Вставить QR-ссылку",15);manual.setTextColor(Ui.BLUE);manual.setGravity(Gravity.CENTER);manual.setMinHeight(Ui.dp(this,48));manual.setOnClickListener(v->manual());root.addView(manual);
        TextView foot=Ui.text(this,"Только ваша домашняя сеть",13);foot.setGravity(Gravity.CENTER);root.addView(foot);
        String saved=getPreferences(MODE_PRIVATE).getString("last",null);if(saved!=null)try{offer=Protocol.parseOffer(saved);}catch(Exception ignored){}
        refresh();
        UpdateUi.automatic(this,()->!connecting&&ProjectionService.active==null);
    }
    @Override protected void onStart(){super.onStart();ProjectionService.refresh=this::refresh;discover();refresh();}
    @Override protected void onStop(){UpdateUi.dismiss(this);ProjectionService.refresh=null;stopDiscovery();super.onStop();}
    private void refresh(){boolean active=ProjectionService.active!=null;cast.setText(active?"Остановить трансляцию":connecting?"Подключение…":"Передать экран");
        cast.setEnabled(active||offer!=null&&!connecting);audio.setEnabled(!active&&!connecting);
        qualityButton.setText("Качество · "+selectedQuality.title+"  ›");qualityButton.setEnabled(!active&&!connecting);
        metricsButton.setVisibility(active?View.VISIBLE:View.GONE);
        qualityButton.setAlpha(active||connecting?.55f:1f);
        qualityButton.setContentDescription("Качество трансляции: "+selectedQuality.title+". "+selectedQuality.detail);
        target.setText(offer==null?"Подключите телевизор":"Телевизор · "+offer.pin.substring(0,6));
        if(active)status.setText(ProjectionService.state);else if(!connecting)status.setText(offer==null?"Откройте SoraCast на телевизоре и отсканируйте QR":ProjectionService.state);
    }
    private void cast(){
        UpdateUi.dismiss(this);
        if(ProjectionService.active!=null){stopService(new Intent(this,ProjectionService.class));return;}
        if(offer==null||connecting)return;
        if(audio.isChecked()&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}
        connecting=true;refresh();status.setText("Подтвердите подключение на телевизоре…");Protocol.Offer selected=offer;
        worker.execute(()->{try{
            Link link=Handshake.connect(this,selected);pending=link;
            link.start(new Link.Listener(){public void message(JSONObject m){link.close();}public void closed(){main.post(()->{if(pending==link){pending=null;connecting=false;refresh();status.setText("Соединение завершено. Подключитесь снова.");}});}});
            main.post(()->{if(isFinishing()||pending!=link||link.isClosed()){link.close();return;}remember(selected);
                MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
                try{startActivityForResult(manager.createScreenCaptureIntent(),20);}catch(Exception e){cancel();status.setText("Передача экрана не поддерживается системой");}});
        }catch(Exception e){main.post(()->{connecting=false;refresh();status.setText(e.getMessage()==null?"Не удалось подключиться. Проверьте общую сеть и VPN.":e.getMessage());});}});
    }
    private void cancel(){Link l=pending;pending=null;if(l!=null)l.close();connecting=false;refresh();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);
        if(request==11){if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED){audio.setChecked(false);Toast.makeText(this,"Передача будет без звука",Toast.LENGTH_LONG).show();}cast();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==10&&result==RESULT_OK&&data!=null){try{offer=Protocol.parseOffer(data.getStringExtra("offer"));ProjectionService.state="Телевизор выбран · можно подключаться";refresh();discover();}catch(Exception e){status.setText("Неверный QR SoraCast");}}
        if(request==20){Link link=pending;pending=null;connecting=false;
            if(result!=RESULT_OK||data==null||link==null||link.isClosed()){if(link!=null)link.close();refresh();status.setText("Передача экрана отменена. Можно попробовать снова.");return;}
            ProjectionService.handoff(link);
            try{startForegroundService(new Intent(this,ProjectionService.class).putExtra("grant",data).putExtra("audio",audio.isChecked()).putExtra("quality",selectedQuality.name()));}
            catch(Exception e){ProjectionService.handoff(null);status.setText("Не удалось запустить службу передачи");}refresh();
        }
    }
    private void chooseQuality(){
        if(connecting||ProjectionService.active!=null)return;
        Dialog dialog=new Dialog(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout sheet=Ui.column(this);sheet.setBackground(Ui.glass(this));scroll.addView(sheet);
        TextView title=Ui.text(this,"Качество экрана",25);title.setTypeface(null,android.graphics.Typeface.BOLD);sheet.addView(title);
        sheet.addView(Ui.text(this,"Разрешение и частота кадров — верхние пределы. Реальное качество зависит от устройств и сети.",14));
        for(StreamQuality value:StreamQuality.values()){
            boolean selected=value==selectedQuality;
            Button option=Ui.button(this,(selected?"✓  ":"")+value.title+"\n"+value.detail,()->{
                if(connecting||ProjectionService.active!=null){dialog.dismiss();return;}
                selectedQuality=value;getSharedPreferences("stream",MODE_PRIVATE).edit().putString("quality",value.name()).apply();
                refresh();dialog.dismiss();
            });
            option.setTextSize(15);option.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);option.setSelected(selected);
            if(selected){android.graphics.drawable.GradientDrawable glass=Ui.glass(this);glass.setStroke(Ui.dp(this,2),Ui.BLUE);option.setBackground(glass);}
            sheet.addView(option);
        }
        sheet.addView(Ui.text(this,"2K означает до 2560 × 1440 с сохранением пропорций. Высокие режимы сильнее нагружают устройства. Для изменения качества завершите текущую трансляцию.",13));
        sheet.addView(Ui.button(this,"Готово",dialog::dismiss));dialog.setContentView(scroll);dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*.86f));}
    }
    private void remember(Protocol.Offer selected){Protocol.Offer persistent=new Protocol.Offer(selected.host,selected.port,selected.pin,new byte[32]);
        try{SharedPreferences prefs=getPreferences(MODE_PRIVATE);JSONObject saved=new JSONObject(prefs.getString("receivers","{}"));saved.put(selected.pin,persistent.uri());
            prefs.edit().putString("receivers",saved.toString()).putString("last",persistent.uri()).apply();
        }catch(Exception ignored){}
    }
    private void choose(){try{JSONObject saved=new JSONObject(getPreferences(MODE_PRIVATE).getString("receivers","{}"));String[] keys=new String[saved.length()],names=new String[saved.length()];
        java.util.Iterator<String> it=saved.keys();for(int i=0;i<keys.length;i++){keys[i]=it.next();names[i]="Телевизор · "+keys[i].substring(0,6);}
        if(keys.length==0){Toast.makeText(this,"Сначала подключите телевизор по QR",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Мои телевизоры").setItems(names,(d,index)->{try{offer=Protocol.parseOffer(saved.getString(keys[index]));refresh();discover();}catch(Exception e){status.setText("Сохранённый телевизор недоступен");}})
            .setNeutralButton("Забыть все",(d,w)->{getPreferences(MODE_PRIVATE).edit().clear().apply();offer=null;stopDiscovery();refresh();}).show();
    }catch(Exception e){status.setText("Не удалось прочитать список телевизоров");}}
    private void manual(){EditText value=new EditText(this);value.setSingleLine(false);value.setHint("soracast://pair?…");
        new AlertDialog.Builder(this).setTitle("QR-ссылка SoraCast").setMessage("Можно распознать QR другой камерой и вставить его содержимое сюда.").setView(value)
            .setNegativeButton("Отмена",null).setPositiveButton("Использовать",(d,w)->{try{offer=Protocol.parseOffer(value.getText().toString().trim());refresh();discover();}catch(Exception e){status.setText("Нужна полная ссылка из QR SoraCast");}}).show();}
    private void tile(){if(Build.VERSION.SDK_INT>=33){getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this,CastTile.class),"SoraCast",Icon.createWithResource(this,app.gptcast.phone.R.drawable.cast_status),getMainExecutor(),result->{});}
        else new AlertDialog.Builder(this).setTitle("Кнопка в шторке").setMessage("Откройте шторку → изменение кнопок → добавьте SoraCast. Кнопка открывает приложение; во время передачи останавливает её.").setPositiveButton("Понятно",null).show();}
    private void discover(){stopDiscovery();if(offer==null)return;String pin=offer.pin;
        try{discovery=LocalNetwork.discover(this,pin,(host,port)->main.post(()->{if(offer!=null&&offer.pin.equals(pin)&&!connecting&&ProjectionService.active==null){offer=new Protocol.Offer(host,port,pin,offer.token);}}));}catch(Exception ignored){}
    }
    private void stopDiscovery(){if(discovery!=null)try{((NsdManager)getSystemService(NSD_SERVICE)).stopServiceDiscovery(discovery);}catch(Exception ignored){}discovery=null;}
    @Override protected void onDestroy(){cancel();worker.shutdownNow();super.onDestroy();}
}
