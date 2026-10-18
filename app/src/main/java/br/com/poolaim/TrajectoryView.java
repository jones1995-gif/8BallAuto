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
        canvas.save();
        canvas.scale(sx, sy);
        paint.setStrokeWidth(1.6f / Math.max(.001f, (sx + sy) / 2f));

        float x = result.startX, y = result.startY;
        if (x < result.left || x > result.right || y < result.top || y > result.bottom) {
            canvas.restore();
            return;
        }

        if (result.hasCollision) {
            // The native cue-to-object line ends at the collision point. Do not
            // pretend the cue ball continues straight through the object ball.
            paint.setColor(Color.rgb(0, 255, 70));
            canvas.drawLine(x, y, result.collisionX, result.collisionY, paint);
            for (int i = 0; i < result.branches.length; i++) {
                AimDetector.Branch branch = result.branches[i];
                // Separate colors make the two post-collision paths easier to compare.
                paint.setColor(i == 0 ? Color.rgb(255, 0, 210) : Color.rgb(35, 120, 255));
                drawReflectedRay(canvas, branch.x, branch.y, branch.dx, branch.dy,
                        result.left, result.top, result.right, result.bottom,
                        showRails ? 3 : 1);
            }
        } else {
            paint.setColor(Color.rgb(0, 255, 70));
            drawReflectedRay(canvas, x, y, result.dx, result.dy,
                    result.left, result.top, result.right, result.bottom,
                    showRails ? 3 : 1);
        }
        canvas.restore();
    }

    private void drawReflectedRay(Canvas canvas, float x, float y, float dx, float dy,
                                  float left, float top, float right, float bottom, int segments) {
        for (int i = 0; i < segments; i++) {
            float tx = Float.POSITIVE_INFINITY, ty = Float.POSITIVE_INFINITY;
            if (dx > .0001f) tx = (right - x) / dx;
            else if (dx < -.0001f) tx = (left - x) / dx;
            if (dy > .0001f) ty = (bottom - y) / dy;
            else if (dy < -.0001f) ty = (top - y) / dy;
            float dt = Math.min(tx, ty);
            if (!Float.isFinite(dt) || dt < 0) return;
            float x2 = x + dx * dt, y2 = y + dy * dt;
            canvas.drawLine(x, y, x2, y2, paint);
            boolean bounceX = tx <= ty + .01f;
            boolean bounceY = ty <= tx + .01f;
            if (bounceX) dx = -dx;
            if (bounceY) dy = -dy;
            x = x2 + dx * .6f;
            y = y2 + dy * .6f;
        }
    }
}
