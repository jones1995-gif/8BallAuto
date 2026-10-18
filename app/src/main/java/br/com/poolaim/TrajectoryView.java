package br.com.poolaim.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Transparent, drawing-only overlay. The game receives all touches. */
public class TrajectoryView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AimDetector.Result result;
    private int frameWidth = 1, frameHeight = 1;
    private boolean showRails = true;

    public TrajectoryView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(null);
        paint.setColor(Color.WHITE);
    }

    public void update(AimDetector.Result r, int w, int h) {
        result = r;
        frameWidth = Math.max(1, w);
        frameHeight = Math.max(1, h);
        invalidate();
    }

    public void setShowRails(boolean show) {
        showRails = show;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (result == null) return;
        float sx = getWidth() / (float) frameWidth;
        float sy = getHeight() / (float) frameHeight;
        float scale = Math.max(.001f, (sx + sy) / 2f);
        canvas.save();
        canvas.scale(sx, sy);
        // A thin, bright white line matching the native game's guide.
        paint.setColor(Color.WHITE);
        paint.setAlpha(238);
        paint.setStrokeWidth(1.15f / scale);

        float left=result.left, top=result.top, right=result.right, bottom=result.bottom;
        if (result.startX < left || result.startX > right || result.startY < top || result.startY > bottom) {
            canvas.restore();
            return;
        }

        if (result.hasCollision) {
            // The game already draws the incoming guide and the visible parts
            // of the two outgoing collision lines. Only draw the extensions.
            for (AimDetector.Branch branch : result.branches) {
                float x=branch.x + branch.dx * Math.max(0, branch.length);
                float y=branch.y + branch.dy * Math.max(0, branch.length);
                drawReflectedRay(canvas,x,y,branch.dx,branch.dy,left,top,right,bottom,showRails?3:1,scale);
            }
        } else {
            // Preserve the native line and begin just beyond its visible endpoint.
            float nativeLength=Math.max(5f,result.guideLength);
            float x=result.startX + result.dx * (nativeLength + 1.0f);
            float y=result.startY + result.dy * (nativeLength + 1.0f);
            drawReflectedRay(canvas,x,y,result.dx,result.dy,left,top,right,bottom,showRails?3:1,scale);
        }
        canvas.restore();
    }

    private void drawReflectedRay(Canvas canvas,float x,float y,float dx,float dy,
                                  float left,float top,float right,float bottom,int segments,float scale) {
        float width=right-left,height=bottom-top;
        float cornerGapX=width*.055f, cornerGapY=height*.10f;
        float sidePocketGap=height*.085f, middlePocketGap=width*.045f;
        for(int i=0;i<segments;i++) {
            float tx=Float.POSITIVE_INFINITY,ty=Float.POSITIVE_INFINITY;
            if(dx>.0001f) tx=(right-x)/dx; else if(dx<-.0001f) tx=(left-x)/dx;
            if(dy>.0001f) ty=(bottom-y)/dy; else if(dy<-.0001f) ty=(top-y)/dy;
            float dt=Math.min(tx,ty);
            if(!Float.isFinite(dt)||dt<-.1f) return;
            dt=Math.max(0f,dt);
            float x2=x+dx*dt,y2=y+dy*dt;
            canvas.drawLine(x,y,x2,y2,paint);
            boolean bounceX=tx<=ty+.01f;
            boolean bounceY=ty<=tx+.01f;
            boolean pocket=false;
            if(bounceY) {
                pocket = x2-left<cornerGapX || right-x2<cornerGapX
                        || Math.abs(x2-(left+right)/2f)<middlePocketGap;
            }
            if(bounceX) {
                pocket = pocket || y2-top<cornerGapY || bottom-y2<cornerGapY
                        || Math.abs(y2-(top+bottom)/2f)<sidePocketGap;
            }
            if(pocket || (!bounceX && !bounceY)) return;
            // Small white contact ring at each predicted rail rebound.
            paint.setStrokeWidth(1.0f/scale);
            canvas.drawCircle(x2,y2,2.1f/scale,paint);
            paint.setStrokeWidth(1.15f/scale);
            if(bounceX) dx=-dx;
            if(bounceY) dy=-dy;
            x=x2+dx*.7f;
            y=y2+dy*.7f;
        }
    }
}
