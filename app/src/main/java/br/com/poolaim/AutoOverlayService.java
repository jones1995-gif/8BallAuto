package br.com.poolaim.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class AutoOverlayService extends Service {
    public static final String ACTION_START="pool.START";
    private static final String CHANNEL="pool_aim_beta",STOP="pool.STOP";
    private final Handler main=new Handler(Looper.getMainLooper());
    private HandlerThread workThread;
    private Handler worker;
    private WindowManager windowManager;
    private WindowManager.LayoutParams bubbleParams,menuParams,drawParams;
    private ImageView bubble;
    private LinearLayout menu;
    private TrajectoryView drawing;
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private int captureWidth,captureHeight,density;
    private long lastFrame=0;
    private int processedFrames=0, detectedFrames=0;
    private boolean rails=true;
    private boolean hasDetection=false;
    private final AimStabilizer stabilizer = new AimStabilizer(0);
    private final BallMotionGate motionGate = new BallMotionGate();
    // Worker-thread-owned: used to subtract only our previous extension from capture frames.
    private AimDetector.Result lastDisplayedGeometry;
    private TextView status;
    private boolean isStopping=false;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    @Override public void onCreate(){
        super.onCreate();windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
        workThread=new HandlerThread("pool-image-detector");workThread.start();worker=new Handler(workThread.getLooper());
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Pool Aim Auto",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && STOP.equals(intent.getAction())) {stopSelf();return START_NOT_STICKY;}
        if(intent==null||!ACTION_START.equals(intent.getAction()))return START_NOT_STICKY;
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note=new Notification.Builder(this,CHANNEL).setContentTitle("Pool Aim Auto Beta")
                .setContentText("Captura autorizada e mira experimental ativas")
                .setSmallIcon(android.R.drawable.ic_menu_camera).setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(17,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(17,note);
        if(bubble==null)buildOverlay();
        if(projection!=null)closeProjection();
        motionGate.reset();stabilizer.reset();lastDisplayedGeometry=null;
        processedFrames=0;detectedFrames=0;lastFrame=0;
        int result=intent.getIntExtra("resultCode",0);
        Intent data;
        if(Build.VERSION.SDK_INT>=33)data=intent.getParcelableExtra("resultData",Intent.class);
        else data=(Intent)intent.getParcelableExtra("resultData");
        if(result==0||data==null){statusText("Captura não autorizada");return START_NOT_STICKY;}
        try{
            MediaProjectionManager mp=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mp.getMediaProjection(result,data);
            if(projection==null){statusText("Erro: captura não disponível");return START_NOT_STICKY;}
            projectionCallback=new MediaProjection.Callback(){
                @Override public void onStop(){main.post(()->{statusText("Captura encerrada. Reabra o aplicativo.");stopSelf();});}
                @Override public void onCapturedContentResize(int width,int height){
                    if(width>100&&height>100)worker.post(()->resizeCapture(width,height));
                }
            };
            projection.registerCallback(projectionCallback,worker);
            DisplayMetrics metrics=new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            density=metrics.densityDpi;
            createCapture(metrics.widthPixels,metrics.heightPixels);
            statusText("Captura ativa • procurando linha...");
        }catch(Exception ex){statusText("Falha de captura: "+ex.getClass().getSimpleName());}
        return START_NOT_STICKY;
    }
    private void createCapture(int w,int h){
        captureWidth=w;captureHeight=h;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->processImage(r),worker);
        virtualDisplay=projection.createVirtualDisplay("PoolAimCapture",w,h,density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
    }
    private void resizeCapture(int w,int h){
        if(projection==null||virtualDisplay==null||w==captureWidth&&h==captureHeight)return;
        try{
            ImageReader old=reader;
            if(old!=null)old.setOnImageAvailableListener(null,null);
            // Keep the original MediaProjection and VirtualDisplay token; resize instead of recreating it.
            captureWidth=w;captureHeight=h;
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            // Reuse callback logic by passing frames through a shared listener.
            reader.setOnImageAvailableListener(r->processImage(r),worker);
            virtualDisplay.resize(w,h,density);
            virtualDisplay.setSurface(reader.getSurface());
            if(old!=null)old.close();
        }catch(Exception ex){main.post(()->statusText("Gire a tela e reinicie a captura"));}
    }
    private void processImage(ImageReader src){
        Image img=null;
        try{
            img=src.acquireLatestImage();if(img==null)return;
            long now=android.os.SystemClock.elapsedRealtime(); if(now-lastFrame<120)return;lastFrame=now;
            Image.Plane p=img.getPlanes()[0];ByteBuffer bb=p.getBuffer();bb.rewind();
            Bitmap bmp=Bitmap.createBitmap(p.getRowStride()/p.getPixelStride(),img.getHeight(),Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(bb);
            // 480px retains short post-collision guide segments that disappeared
            // at 360px while keeping CPU use reasonable for live screen analysis.
            int fw=480,fh=Math.max(120,Math.round(480f*img.getHeight()/img.getWidth()));
            Bitmap small=Bitmap.createScaledBitmap(bmp,fw,fh,false);int[] rgb=new int[fw*fh];
            small.getPixels(rgb,0,fw,0,0,fw,fh);small.recycle();bmp.recycle();
            // White extensions are visible in some screen-capture pipelines. Mask only
            // the extension we drew last frame, leaving the game's native guide intact.
            int[] detectorPixels=rgb.clone();
            AimDetector.maskOverlayExtensions(detectorPixels,fw,fh,lastDisplayedGeometry);
            AimDetector.Result raw=AimDetector.detect(detectorPixels,fw,fh);
            processedFrames++;
            if(raw!=null)detectedFrames++;
            // The native guide is only present in the aiming phase. Moving the
            // cue also changes many pixels, so it must NOT be treated as a shot.
            // Once the native guide disappears, the motion gate is allowed to
            // hide predictions until the table is stable again.
            boolean ballsMoving=motionGate.update(rgb,fw,fh,raw);
            AimDetector.Result res=null;
            if(ballsMoving) {
                stabilizer.reset();
                lastDisplayedGeometry=null;
            } else if(raw!=null) {
                res=stabilizer.update(raw,now);
                lastDisplayedGeometry=res;
            } else {
                // Do not leave a stale projection visible after the native guide disappears.
                stabilizer.reset();
                lastDisplayedGeometry=null;
            }
            final boolean movingNow=ballsMoving;
            final int total=processedFrames;
            final int found=detectedFrames;
            final AimDetector.Result shown=res;
            main.post(()->{
                if(drawing!=null)drawing.update(shown,fw,fh);
                hasDetection=shown!=null;
                String diagnostic="\nImagens: "+total+" | linhas: "+found+" | captura: "+captureWidth+"x"+captureHeight;
                if(movingNow) statusText("Bolas em movimento • projeção oculta"+diagnostic);
                else if(shown==null) statusText("Guia não encontrada • confira captura de tela inteira"+diagnostic);
                else if(shown.hasCollision && shown.branches.length>0) statusText("Colisão • "+shown.branches.length+" saída(s)"+diagnostic);
                else if(shown.hasCollision) statusText("Contato sem saída: prolongamento provisório"+diagnostic);
                else statusText("Mira detectada • linha estendida"+diagnostic);
            });
        }catch(Exception ex){main.post(()->statusText("Falha ao ler imagem"));}
        finally{if(img!=null)img.close();}
    }
    private void buildOverlay(){
        final int type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // Draw layer: intentionally NOT_TOUCHABLE and translucent, preventing Android 12+ touch blocking.
        drawing=new TrajectoryView(this);
        drawParams=new WindowManager.LayoutParams(-1,-1,type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        // Keep below Android's usual obscuring-opacity threshold so touches can pass through.
        drawParams.gravity=Gravity.TOP|Gravity.START;drawParams.alpha=.74f;
        windowManager.addView(drawing,drawParams);
        bubble=new ImageView(this);bubble.setImageResource(R.drawable.bola_8);
        bubble.setBackgroundColor(Color.TRANSPARENT);bubble.setPadding(dp(3),dp(3),dp(3),dp(3));
        bubbleParams=new WindowManager.LayoutParams(dp(58),dp(58),type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.START;
        DisplayMetrics m=getResources().getDisplayMetrics();bubbleParams.x=m.widthPixels-dp(75);bubbleParams.y=dp(130);
        windowManager.addView(bubble,bubbleParams);
        final float[] start=new float[2];final int[] initial=new int[2];final boolean[] moved={false};
        bubble.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    start[0]=e.getRawX();start[1]=e.getRawY();initial[0]=bubbleParams.x;initial[1]=bubbleParams.y;moved[0]=false;return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-start[0],dy=e.getRawY()-start[1];
                    if(Math.hypot(dx,dy)>dp(7))moved[0]=true;
                    if(moved[0]){bubbleParams.x=initial[0]+(int)dx;bubbleParams.y=initial[1]+(int)dy;
                        windowManager.updateViewLayout(bubble,bubbleParams);if(menu!=null)positionMenu();}
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved[0])toggleMenu();return true;
            }return true;
        });
    }
    private void toggleMenu(){if(menu!=null){windowManager.removeView(menu);menu=null;status=null;return;}
        menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(13),dp(10),dp(13),dp(10));menu.setBackgroundColor(0xee111e2b);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(12);status.setText(hasDetection?"Mira encontrada":"Procurando a mira do jogo...");
        menu.addView(status);
        TextView visualTest=menuItem("Teste da linha branca (6 s)");
        visualTest.setOnClickListener(v->{
            // This checks that overlay drawing is visible independently of
            // screen capture and computer vision; it never selects any ball.
            if(drawing!=null)drawing.setTestVisible(true);
            statusText("Linha de teste ativa por 6 segundos");
            main.postDelayed(()->{if(drawing!=null)drawing.setTestVisible(false);},6000);
        });
        menu.addView(visualTest);
        TextView railsButton=menuItem("Tabelas: ATIVADAS");
        railsButton.setOnClickListener(v->{rails=!rails;drawing.setShowRails(rails);railsButton.setText(rails?"Tabelas: ATIVADAS":"Tabelas: DESLIGADAS");});
        menu.addView(railsButton);
        TextView collapse=menuItem("Minimizar ◉");collapse.setOnClickListener(v->toggleMenu());menu.addView(collapse);
        TextView stop=menuItem("Parar captura");stop.setOnClickListener(v->stopSelf());menu.addView(stop);
        menuParams=new WindowManager.LayoutParams(dp(190),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);menuParams.gravity=Gravity.TOP|Gravity.START;positionMenu();
        windowManager.addView(menu,menuParams);
    }
    private TextView menuItem(String content){TextView t=new TextView(this);t.setText(content);t.setTextSize(15);t.setTextColor(Color.WHITE);t.setPadding(0,dp(12),0,dp(10));return t;}
    private void positionMenu(){if(menuParams==null)return;
        int w=getResources().getDisplayMetrics().widthPixels;
        menuParams.x=bubbleParams.x+dp(62);
        if(menuParams.x+dp(190)>w)menuParams.x=Math.max(0,bubbleParams.x-dp(197));
        menuParams.y=Math.max(0,bubbleParams.y);
        if(menu!=null&&menu.getParent()!=null)windowManager.updateViewLayout(menu,menuParams);
    }
    private void statusText(String text){if(status!=null)status.setText(text);}
    private void closeProjection(){
        if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}
        if(reader!=null){reader.close();reader=null;}
        if(projection!=null){MediaProjection p=projection;projection=null;
            if(projectionCallback!=null){p.unregisterCallback(projectionCallback);projectionCallback=null;}
            p.stop();}
    }
    @Override public void onDestroy(){super.onDestroy();if(isStopping)return;isStopping=true;
        try{closeProjection();}catch(Exception ignored){}
        motionGate.reset();stabilizer.reset();lastDisplayedGeometry=null;
        if(menu!=null){windowManager.removeView(menu);menu=null;}
        if(bubble!=null){windowManager.removeView(bubble);bubble=null;}
        if(drawing!=null){windowManager.removeView(drawing);drawing=null;}
        if(workThread!=null){workThread.quitSafely();workThread=null;}
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
