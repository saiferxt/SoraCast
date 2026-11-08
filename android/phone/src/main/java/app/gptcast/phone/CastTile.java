package app.gptcast.phone;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.*;

public final class CastTile extends TileService {
    @Override public void onStartListening(){Tile tile=getQsTile();if(tile!=null){tile.setState(ProjectionService.active==null?Tile.STATE_INACTIVE:Tile.STATE_ACTIVE);tile.updateTile();}}
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Android 26–33 lacks the PendingIntent overload; the legacy call never runs on 34+.
    @Override public void onClick(){super.onClick();if(ProjectionService.active!=null){stopService(new Intent(this,ProjectionService.class));onStartListening();return;}
        Intent intent=new Intent(this,PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));else startActivityAndCollapse(intent);
    }
}
