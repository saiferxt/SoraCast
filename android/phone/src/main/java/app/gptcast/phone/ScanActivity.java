package app.gptcast.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.gptcast.*;
import app.nebo.core.Protocol;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;

@SuppressWarnings("deprecation")
public final class ScanActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private SurfaceView preview;
    private TextView status;
    private long last;
    private boolean done;
    private final MultiFormatReader reader=new MultiFormatReader();
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=Ui.column(this);Ui.background(root);Ui.insets(root);setContentView(root);
        root.addView(Ui.text(this,"QR на телевизоре",26));root.addView(Ui.text(this,"Поместите QR-код в рамку",17));
        FrameLayout cameraArea=new FrameLayout(this);root.addView(cameraArea,new LinearLayout.LayoutParams(-1,0,1));
        preview=new SurfaceView(this);cameraArea.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        cameraArea.addView(new ScanGuide(this),new FrameLayout.LayoutParams(-1,-1));preview.getHolder().addCallback(this);
        status=Ui.text(this,"Камера используется только для QR",16);root.addView(status);
        root.addView(Ui.button(this,"Отмена",this::finish));
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,true);reader.setHints(hints);
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},10);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)open();else status.setText("Камера не разрешена. Можно вставить QR-ссылку на главном экране.");}
    @Override protected void onResume(){super.onResume();if(preview.getHolder().getSurface().isValid())open();}
    private void open(){if(camera!=null||done||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||!preview.getHolder().getSurface().isValid())return;
        try{camera=Camera.open();Camera.Parameters p=camera.getParameters();
            List<Camera.Size> sizes=p.getSupportedPreviewSizes();Camera.Size chosen=sizes.get(sizes.size()-1);
            for(Camera.Size size:sizes)if(size.width<=1280&&size.height<=720&&size.width>chosen.width)chosen=size;
            p.setPreviewSize(chosen.width,chosen.height);p.setPreviewFormat(android.graphics.ImageFormat.NV21);
            if(p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);camera.setDisplayOrientation(90);camera.setPreviewDisplay(preview.getHolder());
            camera.setPreviewCallback((data,cam)->{if(done||SystemClock.elapsedRealtime()-last<300)return;last=SystemClock.elapsedRealtime();
                Camera.Size size=cam.getParameters().getPreviewSize();
                try{Result result=reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(data,size.width,size.height,0,0,size.width,size.height,false))));
                    Protocol.Offer offer=Protocol.parseOffer(result.getText());done=true;setResult(RESULT_OK,new Intent().putExtra("offer",offer.uri()));finish();
                }catch(NotFoundException ignored){}catch(Exception invalid){status.setText("Нужен QR из приложения SoraCast на телевизоре");}finally{reader.reset();}
            });camera.startPreview();
        }catch(Exception e){release();status.setText("Камера недоступна. Используйте импорт QR-ссылки.");}}
    private void release(){if(camera!=null){camera.setPreviewCallback(null);camera.stopPreview();camera.release();camera=null;}}
    @Override protected void onPause(){release();super.onPause();}
    public void surfaceCreated(SurfaceHolder holder){open();}public void surfaceChanged(SurfaceHolder h,int f,int w,int he){}public void surfaceDestroyed(SurfaceHolder h){release();}
    private static final class ScanGuide extends View {
        private final Paint shade=new Paint(),border=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box=new RectF();
        ScanGuide(Context c){super(c);shade.setColor(0x88000000);border.setStyle(Paint.Style.STROKE);border.setStrokeCap(Paint.Cap.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){float side=Math.min(w,h)*.74f;box.set((w-side)/2,(h-side)/2,(w+side)/2,(h+side)/2);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);
            c.drawRect(0,0,getWidth(),box.top,shade);c.drawRect(0,box.bottom,getWidth(),getHeight(),shade);
            c.drawRect(0,box.top,box.left,box.bottom,shade);c.drawRect(box.right,box.top,getWidth(),box.bottom,shade);
            border.setColor(0xDDEBF9FF);border.setStrokeWidth(Ui.dp(getContext(),1));c.drawRoundRect(box,Ui.dp(getContext(),8),Ui.dp(getContext(),8),border);
            float n=Math.min(Ui.dp(getContext(),26),box.width()/5);border.setColor(0xFF77DDFF);border.setStrokeWidth(Ui.dp(getContext(),4));
            Path corners=new Path();corners.moveTo(box.left,box.top+n);corners.lineTo(box.left,box.top);corners.lineTo(box.left+n,box.top);
            corners.moveTo(box.right-n,box.top);corners.lineTo(box.right,box.top);corners.lineTo(box.right,box.top+n);
            corners.moveTo(box.right,box.bottom-n);corners.lineTo(box.right,box.bottom);corners.lineTo(box.right-n,box.bottom);
            corners.moveTo(box.left+n,box.bottom);corners.lineTo(box.left,box.bottom);corners.lineTo(box.left,box.bottom-n);c.drawPath(corners,border);
        }
    }
}

