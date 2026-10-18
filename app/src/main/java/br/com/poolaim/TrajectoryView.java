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
        paint.setColor(Color.WHITE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // Solid line: no dash animation, glow, circles or endpoints.
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
        // 1.5 real screen pixels irrespective of phone resolution.
        paint.setStrokeWidth(1.5f / Math.max(.001f, (sx + sy) / 2f));
        float x = result.startX, y = result.startY;
        float dx = result.dx, dy = result.dy;
        if (x < result.left || x > result.right ||
                y < result.top || y > result.bottom) {
            canvas.restore();
            return;
        }
        final int segments = showRails ? 3 : 1;
        for (int i = 0; i < segments; i++) {
            float dtX = Float.POSITIVE_INFINITY, dtY = Float.POSITIVE_INFINITY;
            if (dx > .0001f) dtX = (result.right - x) / dx;
            else if (dx < -.0001f) dtX = (result.left - x) / dx;
            if (dy > .0001f) dtY = (result.bottom - y) / dy;
            else if (dy < -.0001f) dtY = (result.top - y) / dy;
            float dt = Math.min(dtX, dtY);
            if (!Float.isFinite(dt) || dt < 0) break;
            float x2 = x + dx * dt, y2 = y + dy * dt;
            canvas.drawLine(x, y, x2, y2, paint);
            boolean bounceX = dtX <= dtY + .01f;
            boolean bounceY = dtY <= dtX + .01f;
            if (bounceX) dx = -dx;
            if (bounceY) dy = -dy;
            x = x2 + dx * .5f;
            y = y2 + dy * .5f;
        }
        canvas.restore();
    }
}
