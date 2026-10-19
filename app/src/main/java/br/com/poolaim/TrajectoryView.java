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
    private boolean testVisible = false;

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

    /** A controlled visual check; independent of the capture/detector. */
    public void setTestVisible(boolean visible) {
        testVisible=visible;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(testVisible){
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            paint.setStrokeWidth(2.0f);
            float x1=getWidth()*.25f, y1=getHeight()*.58f;
            float x2=getWidth()*.75f, y2=getHeight()*.40f;
            canvas.drawLine(x1,y1,x2,y2,paint);
            canvas.drawCircle(x2,y2,8f,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(28f);
            canvas.drawText("TESTE DE LINHA",x1,y1-18f,paint);
            paint.setStyle(Paint.Style.STROKE);
        }
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

        if (ProjectionMode.drawBranches(result)) {
            // The game already draws the incoming guide and the visible parts
            // of the two outgoing collision lines. Only draw the extensions.
            for (AimDetector.Branch branch : result.branches) {
                float x=branch.x + branch.dx * Math.max(0, branch.length);
                float y=branch.y + branch.dy * Math.max(0, branch.length);
                drawReflectedRay(canvas,x,y,branch.dx,branch.dy,left,top,right,bottom,showRails?3:1,scale);
            }
        } else {
            // A detected contact WITHOUT outgoing segments must not suppress
            // drawing entirely (this was the 0.6 invisible-line regression).
            // Until the branches can be read, show only an approximate extension
            // of the incoming direction rather than fabricating two paths.
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
        float radius=(result==null || result.cueRadius<1f)
                ? Math.max(2.6f,(right-left)*.014f):result.cueRadius;
        java.util.List<RailTrace.Segment> path=RailTrace.trace(x,y,dx,dy,
                left,top,right,bottom,radius,segments);
        for(RailTrace.Segment s:path){
            canvas.drawLine(s.x1,s.y1,s.x2,s.y2,paint);
            if(s.cushion){
                paint.setStrokeWidth(1.0f/scale);
                canvas.drawCircle(s.x2,s.y2,2.1f/scale,paint);
                paint.setStrokeWidth(1.15f/scale);
            }
        }
    }
}
