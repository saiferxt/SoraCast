package app.gptcast.updates;

import android.content.*;
import android.content.pm.PackageInstaller;

/** Explicit non-exported PendingIntent target: only this app and the installer hold the capability. */
public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!((context.getPackageName()+".INSTALL_RESULT").equals(intent.getAction())))return;
        SharedPreferences prefs=context.getSharedPreferences("updates",Context.MODE_PRIVATE);
        int id=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1);
        if(id<0||id!=prefs.getInt("session",-2))return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        String result;
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null)try{context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}catch(RuntimeException ignored){}
            result="Android не открыл подтверждение. Вернитесь в обновления и нажмите «Установить» ещё раз.";
        }else if(status==PackageInstaller.STATUS_SUCCESS)result="Обновление установлено";
        else if(status==PackageInstaller.STATUS_FAILURE_ABORTED)result="Установка отменена. Текущая версия сохранена.";
        else result="Android не установил обновление. Текущая версия сохранена; можно повторить попытку.";
        prefs.edit().putString("installResult",result).remove("session").apply();
        context.sendBroadcast(new Intent(context.getPackageName()+".UPDATE_STATUS").setPackage(context.getPackageName()));
    }
}
