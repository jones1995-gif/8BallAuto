package br.com.poolaim.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.DashPathEffect;
import android.view.View;

/** A drawing-only (not touchable) overlay. */
public class TrajectoryView extends View {
    private final Paint paint=new Paint(3);
    private AimDetector.Result result;
    private int frameWidth=1,frameHeight=1;
    private boolean showRails=true;
    public TrajectoryView(Context context){super(context);setBackgroundColor(Color.TRANSPARENT);}
    public void update(AimDetector.Result r,int w,int h){result=r;frameWidth=Math.max(1,w);frameHeight=Math.max(1,h);invalidate();}
    public void setShowRails(boolean show){showRails=show;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c); if(result==null)return;
        float sx=getWidth()/(float)frameWidth,sy=getHeight()/(float)frameHeight;
        c.save();c.scale(sx,sy);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff00e9f6);
        paint.setStrokeWidth(Math.max(1.5f,frameWidth/360f*1.6f));
        paint.setPathEffect(new DashPathEffect(new float[]{12,7},0));
        float x=result.startX,y=result.startY,dx=result.dx,dy=result.dy;
        if(x<result.left||x>result.right||y<result.top||y>result.bottom){c.restore();return;}
        int bounces=showRails?3:1;
        for(int i=0;i<bounces;i++){
            float dtX=Float.POSITIVE_INFINITY,dtY=Float.POSITIVE_INFINITY;
            if(dx>.0001f)dtX=(result.right-x)/dx;else if(dx<-.0001f)dtX=(result.left-x)/dx;
            if(dy>.0001f)dtY=(result.bottom-y)/dy;else if(dy<-.0001f)dtY=(result.top-y)/dy;
            float dt=Math.min(dtX,dtY);
            if(Float.isInfinite(dt)||dt<0)break;
            float x2=x+dx*dt,y2=y+dy*dt;
            c.drawLine(x,y,x2,y2,paint);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);c.drawCircle(x2,y2,3.6f,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setPathEffect(new DashPathEffect(new float[]{12,7},0));
            if(dtX<=dtY+.01f)dx=-dx;
            if(dtY<=dtX+.01f)dy=-dy;
            x=x2+dx*.5f; y=y2+dy*.5f;
        }
        paint.setPathEffect(null);paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);
        c.drawCircle(result.startX,result.startY,5,paint);
        c.restore();
    }
}
