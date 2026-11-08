package app.gptcast.updates;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import app.gptcast.Ui;
import app.nebo.core.UpdateRelease;
import app.nebo.core.UpdateCommitGate;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.*;

/** One foreground update screen shared by phone/TV; never silently installs an APK. */
public final class UpdateActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UpdateHttp http;
    private Future<?> task;
    private UpdateRelease release;
    private File apk;
    private boolean busy,awaitingPermission,installing;
    private UpdateCommitGate installGate;
    private int generation;
    private TextView status,notes;
    private ProgressBar progress;
    private Button check,download,install,cancel;
    private final BroadcastReceiver installStatus=new BroadcastReceiver(){public void onReceive(Context c,Intent i){showInstallResult();}};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        FrameLayout outer=new FrameLayout(this);Ui.background(outer);Ui.insets(outer);setContentView(outer);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);outer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=Ui.column(this);scroll.addView(root);root.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=Ui.text(this,"Обновления SoraCast",29);title.setTypeface(null,android.graphics.Typeface.BOLD);root.addView(title);
        root.addView(Ui.text(this,"Установлена версия "+UpdateUi.version(this)+("app.soracast.tv".equals(getPackageName())?" · телевизор":" · телефон"),16));
        LinearLayout card=Ui.column(this);card.setBackground(Ui.glass(this));root.addView(card,new LinearLayout.LayoutParams(-1,-2));
        status=Ui.text(this,"Проверка версии…",18);card.addView(status);
        notes=Ui.text(this,"",15);card.addView(notes);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);card.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,12)));
        check=Ui.button(this,"Проверить обновления",this::check);card.addView(check);
        download=Ui.button(this,"Скачать обновление",this::download);card.addView(download);
        install=Ui.button(this,"Установить обновление",this::install);card.addView(install);
        cancel=Ui.button(this,"Отменить загрузку",this::cancel);card.addView(cancel);
        root.addView(Ui.text(this,"Файлы загружаются с GitHub. Установку подтверждает Android; настройки SoraCast сохраняются.",13));
        root.addView(Ui.button(this,"Назад",()->finish()));
        render();check();
    }
    @Override protected void onStart(){super.onStart();IntentFilter filter=new IntentFilter(getPackageName()+".UPDATE_STATUS");if(Build.VERSION.SDK_INT>=33)registerReceiver(installStatus,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(installStatus,filter);}
    @Override protected void onStop(){unregisterReceiver(installStatus);super.onStop();}
    @Override protected void onResume(){super.onResume();
        if(awaitingPermission){awaitingPermission=false;if(getPackageManager().canRequestPackageInstalls())install();else status.setText("Установка пока не разрешена. Разрешите её для SoraCast и нажмите «Установить».");}
        else if(installing)showInstallResult();
    }
    private void render(){
        check.setEnabled(!busy);download.setVisibility(release!=null&&apk==null?View.VISIBLE:View.GONE);download.setEnabled(!busy);
        install.setVisibility(apk!=null?View.VISIBLE:View.GONE);install.setEnabled(!busy);
        cancel.setVisibility(busy?View.VISIBLE:View.GONE);progress.setVisibility(busy?View.VISIBLE:View.GONE);
    }
    private void post(int token,Runnable work){main.post(()->{if(token==generation&&!isFinishing()&&!isDestroyed())work.run();});}
    private void check(){if(busy)return;release=null;apk=null;installing=false;installGate=null;busy=true;render();notes.setText("");status.setText("Проверяем новую версию…");progress.setIndeterminate(true);
        int token=++generation;UpdateHttp request=new UpdateHttp();http=request;
        task=worker.submit(()->{try(request){
            UpdateRelease found=request.check(getPackageName());PackageInfo own=getPackageManager().getPackageInfo(getPackageName(),0);
            post(token,()->{busy=false;
                if(found.available(UpdateInstaller.version(own),Build.VERSION.SDK_INT)){release=found;status.setText("Доступна версия "+found.versionName);notes.setText(found.notes+String.format(Locale.ROOT,"\n\nРазмер: %.1f МБ",found.bytes/1048576.0));}
                else if(found.versionCode>UpdateInstaller.version(own)){status.setText("Новая версия требует Android API "+found.minSdk+" или новее");}
                else status.setText("У вас актуальная версия");
                getSharedPreferences("updates",MODE_PRIVATE).edit().putLong("lastAttempt",System.currentTimeMillis()).apply();render();
            });
        }catch(Exception failure){post(token,()->{busy=false;status.setText(error(failure));render();});}});
    }
    private void download(){if(busy||release==null)return;
        UpdateRelease selected=release;busy=true;render();status.setText("Скачиваем "+selected.versionName+"…");progress.setIndeterminate(false);progress.setProgress(0);
        int token=++generation;UpdateHttp request=new UpdateHttp();http=request;
        task=worker.submit(()->{try(request){
            long[] last={0};File file=request.download(selected,new File(getCacheDir(),"updates"),count->{
                long now=SystemClock.elapsedRealtime();if(now-last[0]>=150||count==selected.bytes){last[0]=now;post(token,()->progress.setProgress((int)(count*100/selected.bytes)));}
            });
            UpdateInstaller.verify(this,file,selected);
            post(token,()->{apk=file;busy=false;status.setText("APK проверен и готов к установке");render();});
        }catch(Exception failure){post(token,()->{busy=false;status.setText(error(failure));render();});}});
    }
    private void install(){if(busy||apk==null||release==null)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            try{awaitingPermission=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}
            catch(RuntimeException unavailable){awaitingPermission=false;status.setText("В настройках Android разрешите установку из SoraCast, затем вернитесь сюда.");}return;
        }
        busy=true;installing=true;installGate=new UpdateCommitGate();UpdateCommitGate gate=installGate;render();status.setText("Готовим системное подтверждение…");progress.setIndeterminate(true);
        File selectedFile=apk;UpdateRelease selected=release;int token=++generation;
        task=worker.submit(()->{try{
            UpdateInstaller.install(this,selectedFile,selected,gate);
            post(token,()->{busy=false;status.setText("Подтвердите установку в окне Android. Затем снова откройте SoraCast.");render();});
        }catch(Exception failure){post(token,()->{busy=false;installing=false;status.setText(error(failure));render();});}});
    }
    private void showInstallResult(){String result=getSharedPreferences("updates",MODE_PRIVATE).getString("installResult",null);if(result!=null){status.setText(result);if(!getSharedPreferences("updates",MODE_PRIVATE).contains("session")){++generation;busy=false;installing=false;installGate=null;render();}}}
    private void cancel(){if(!busy)return;if(installGate!=null&&!installGate.cancel()){status.setText("Запрос уже передан Android. Подтвердите или отмените установку в системном окне.");return;}++generation;if(task!=null)task.cancel(true);if(http!=null)http.close();busy=false;installing=false;status.setText("Операция отменена. Текущая версия сохранена.");render();}
    private static String error(Exception failure){
        if(failure instanceof UpdateHttp.Unpublished)return "Выпуск пока не найден на GitHub. Попробуйте проверить позже.";
        if(failure instanceof SecurityException)return "APK не подходит этому приложению или его подпись отличается. Установка остановлена.";
        if(failure instanceof IllegalArgumentException)return "Лента обновлений имеет неподдерживаемый формат. Текущая версия сохранена.";
        return "Не удалось завершить обновление. Проверьте интернет, VPN и свободное место, затем повторите попытку.";
    }
    @Override protected void onDestroy(){++generation;boolean cancellable=installGate==null||installGate.cancel();if(cancellable&&task!=null)task.cancel(true);if(http!=null)http.close();if(cancellable)worker.shutdownNow();else worker.shutdown();super.onDestroy();}
}
