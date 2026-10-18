package br.com.poolaim.overlay;

/**
 * Hides the projection while the table image is changing (normally a shot in
 * progress). It compares table pixels between consecutive captures while
 * masking the native guide and the previous overlay geometry so that moving
 * the aiming stick does not look like a moving ball.
 */
public final class BallMotionGate {
    private static final int SAMPLE_STEP = 2;
    private static final int REQUIRED_STILL_FRAMES = 7;
    private int[] previousPixels;
    private int previousWidth, previousHeight;
    private AimDetector.Result lastGeometry;
    private boolean moving;
    private int stillFrames;
    private int consecutiveGuideFrames;

    public synchronized boolean update(int[] pixels, int w, int h, AimDetector.Result current) {
        if (pixels == null || w < 2 || h < 2 || pixels.length < w*h) return moving;
        // A native aiming line is strong evidence that the player is aiming,
        // not shooting. In 0.6, moving the cue looked like ball motion, which
        // latched this gate on and hid every projection indefinitely.
        if(current!=null) {
            consecutiveGuideFrames++;
            lastGeometry=current;
            previousPixels=pixels.clone();
            previousWidth=w;previousHeight=h;
            if(consecutiveGuideFrames>=2) { moving=false; stillFrames=0; }
            return moving;
        }
        consecutiveGuideFrames=0;
        AimDetector.Result priorGeometry = lastGeometry;
        if (current != null) lastGeometry = current;
        AimDetector.Result geometry = current != null ? current : lastGeometry;
        if (previousPixels == null || previousWidth != w || previousHeight != h) {
            previousPixels = pixels.clone(); previousWidth = w; previousHeight = h;
            stillFrames = 0;
            return moving;
        }

        boolean[] oldMask = makeLineMask(w, h, priorGeometry != null ? priorGeometry : lastGeometry);
        boolean[] newMask = makeLineMask(w, h, current != null ? current : lastGeometry);
        int left = geometry == null ? Math.round(w*.13f) : clamp(Math.round(geometry.left), 0, w-1);
        int right = geometry == null ? Math.round(w*.87f) : clamp(Math.round(geometry.right), 0, w-1);
        int top = geometry == null ? Math.round(h*.20f) : clamp(Math.round(geometry.top), 0, h-1);
        int bottom = geometry == null ? Math.round(h*.90f) : clamp(Math.round(geometry.bottom), 0, h-1);
        int changed = 0, sampled = 0;
        for (int y=top+2; y<bottom-2; y+=SAMPLE_STEP) {
            int row=y*w;
            for (int x=left+2; x<right-2; x+=SAMPLE_STEP) {
                int index=row+x;
                if (oldMask[index] || newMask[index]) continue;
                sampled++;
                int a=previousPixels[index], b=pixels[index];
                int delta=Math.abs(((a>>>16)&255)-((b>>>16)&255))
                        +Math.abs(((a>>>8)&255)-((b>>>8)&255))
                        +Math.abs((a&255)-(b&255));
                if (delta >= 72) changed++;
            }
        }
        int threshold=Math.max(9, (int)(sampled*.00035f));
        if (changed > threshold) {
            moving=true;
            stillFrames=0;
        } else if (moving) {
            stillFrames++;
            if (stillFrames >= REQUIRED_STILL_FRAMES) {
                moving=false;
                stillFrames=0;
            }
        }
        previousPixels = pixels.clone();
        return moving;
    }

    public synchronized void reset() {
        previousPixels=null; previousWidth=0; previousHeight=0;
        lastGeometry=null; moving=false; stillFrames=0; consecutiveGuideFrames=0;
    }

    public synchronized boolean isMoving() { return moving; }

    private static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }

    private static boolean[] makeLineMask(int w,int h,AimDetector.Result r) {
        boolean[] mask=new boolean[w*h];
        if(r==null) return mask;
        float l=r.left,t=r.top,rr=r.right,b=r.bottom;
        if(r.hasCollision) {
            markSegment(mask,w,h,r.startX+r.dx*5f,r.startY+r.dy*5f,r.collisionX,r.collisionY,1);
            for(AimDetector.Branch branch:r.branches) {
                markRay(mask,w,h,l,t,rr,b,branch.x+branch.dx*2f,branch.y+branch.dy*2f,
                        branch.dx,branch.dy,3);
            }
        } else {
            markRay(mask,w,h,l,t,rr,b,r.startX+r.dx*5f,r.startY+r.dy*5f,r.dx,r.dy,3);
        }
        return mask;
    }

    private static void markRay(boolean[] mask,int w,int h,float left,float top,float right,float bottom,
                                float x,float y,float dx,float dy,int segments) {
        for(int i=0;i<segments;i++) {
            float tx=Float.POSITIVE_INFINITY,ty=Float.POSITIVE_INFINITY;
            if(dx>.0001f) tx=(right-x)/dx; else if(dx<-.0001f) tx=(left-x)/dx;
            if(dy>.0001f) ty=(bottom-y)/dy; else if(dy<-.0001f) ty=(top-y)/dy;
            float dt=Math.min(tx,ty);
            if(!Float.isFinite(dt)||dt<-.1f) return;
            float x2=x+dx*Math.max(0,dt),y2=y+dy*Math.max(0,dt);
            markSegment(mask,w,h,x,y,x2,y2,1);
            boolean bx=tx<=ty+.01f,by=ty<=tx+.01f;
            if(bx) dx=-dx; if(by) dy=-dy;
            x=x2+dx*.7f; y=y2+dy*.7f;
        }
    }

    private static void markSegment(boolean[] mask,int w,int h,float x1,float y1,float x2,float y2,int radius) {
        int steps=Math.max(1,(int)Math.ceil(Math.hypot(x2-x1,y2-y1)));
        for(int i=0;i<=steps;i++) {
            int x=Math.round(x1+(x2-x1)*i/steps),y=Math.round(y1+(y2-y1)*i/steps);
            for(int oy=-radius;oy<=radius;oy++) for(int ox=-radius;ox<=radius;ox++) {
                if(ox*ox+oy*oy>radius*radius+1) continue;
                int px=x+ox,py=y+oy;
                if(px>=0&&px<w&&py>=0&&py<h) mask[py*w+px]=true;
            }
        }
    }
}
