package app.gptcast;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.*;

public final class Ui {
    public static final int INK=Color.rgb(20,48,72),BLUE=Color.rgb(18,114,174);
    public static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static GradientDrawable glass(Context c){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xF4FFFFFF,0xBBDFF4FF});
        d.setCornerRadius(dp(c,28));d.setStroke(dp(c,1),0xFFFFFFFF);return d;}
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(c,24),dp(c,24),dp(c,24),dp(c,24));return v;}
    public static TextView text(Context c,String value,int sp){TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(INK);t.setPadding(0,dp(c,7),0,dp(c,7));return t;}
    public static Button button(Context c,String value,Runnable action){Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(17);b.setTextColor(INK);
        b.setMinHeight(dp(c,52));b.setPadding(dp(c,20),dp(c,8),dp(c,20),dp(c,8));b.setBackground(glass(c));b.setElevation(dp(c,2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,10),0,dp(c,5));b.setLayoutParams(p);b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focus)->{GradientDrawable d=glass(c);d.setStroke(dp(c,focus?3:1),focus?BLUE:Color.WHITE);v.setBackground(d);});return b;}
    public static void background(View v){v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFF0FAFF,0xFFC8E9FA,0xFFEAF8FF}));}
    public static Bitmap qr(String value,int size) throws WriterException {
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,3);hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
        BitMatrix matrix=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size,hints);
        int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?0xFF123047:Color.WHITE;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    public static void insets(View root){root.setOnApplyWindowInsetsListener((v,i)->{
        if(Build.VERSION.SDK_INT>=30){Insets p=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(p.left,p.top,p.right,p.bottom);}
        else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});}
}
