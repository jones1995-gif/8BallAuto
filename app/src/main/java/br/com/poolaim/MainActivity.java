package br.com.poolaim.overlay;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST=11;
    private MediaProjectionManager projectionManager;
    private LinearLayout layout;
    @Override public void onCreate(Bundle state){super.onCreate(state);projectionManager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);showUi();}
    private void showUi(){
        layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(40,45,40,30);
        layout.setBackgroundColor(Color.rgb(13,21,29));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.bola_8);
        layout.addView(icon,new LinearLayout.LayoutParams(-1,140));
        TextView title=text("POOL AIM AUTO · BETA 0.2",24,Color.WHITE);title.setTypeface(null,Typeface.BOLD);layout.addView(title);
        layout.addView(text("Bolinha flutuante, detecção visual experimental da linha de mira e projeção geométrica nas tabelas.",16,0xffd1dae3));
        layout.addView(text("A captura de tela precisa de sua autorização. Não seleciona bolas manualmente nem controla o jogo. Resultados podem estar incorretos.",14,0xffd1dae3));
        Button overlay=button("1. Permitir sobreposição");overlay.setOnClickListener(v->{
            if(!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            else Toast.makeText(this,"Sobreposição já autorizada",Toast.LENGTH_SHORT).show();
        });layout.addView(overlay);
        Button capture=button("2. Iniciar reconhecimento automático");capture.setOnClickListener(v->{
            if(!Settings.canDrawOverlays(this)) {Toast.makeText(this,"Autorize a sobreposição primeiro",Toast.LENGTH_LONG).show();return;}
            startActivityForResult(projectionManager.createScreenCaptureIntent(),CAPTURE_REQUEST);
        });layout.addView(capture);
        Button stop=button("Parar e remover a bolinha");stop.setOnClickListener(v->stopService(new Intent(this,AutoOverlayService.class)));layout.addView(stop);
        setContentView(layout);
    }
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,8,0,18);return t;}
    private Button button(String label){Button b=new Button(this);b.setAllCaps(false);b.setText(label);return b;}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==CAPTURE_REQUEST){
            if(result==RESULT_OK && data!=null){
                Intent i=new Intent(this,AutoOverlayService.class).setAction(AutoOverlayService.ACTION_START);
                i.putExtra("resultCode",result);i.putExtra("resultData",data);
                if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                Toast.makeText(this,"Capture ativada. Abra o jogo e toque na bolinha 8.",Toast.LENGTH_LONG).show();
            } else Toast.makeText(this,"Captura não autorizada",Toast.LENGTH_SHORT).show();
        }
    }
}
