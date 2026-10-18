package br.com.poolaim.overlay;

/**
 * Experimental screen-image detector for the native pool aiming guide.
 * It first locates a bright, mostly solid cue-ball candidate, then looks for
 * a low-saturation bright line radiating from that candidate inside the felt.
 * This avoids selecting unrelated long white lines elsewhere on the table.
 */
public final class AimDetector {
    public static final class Result {
        public final float left, top, right, bottom, startX, startY, dx, dy;
        public final int votes;

        Result(float l, float t, float r, float b, float x, float y,
               float dx, float dy, int votes) {
            left = l; top = t; right = r; bottom = b;
            startX = x; startY = y; this.dx = dx; this.dy = dy; this.votes = votes;
        }
    }

    private static final class Bounds {
        final int left, top, right, bottom;
        Bounds(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
    }

    private static final class Ray {
        final float dx, dy;
        final int support, longest;
        Ray(float dx, float dy, int support, int longest) {
            this.dx=dx; this.dy=dy; this.support=support; this.longest=longest;
        }
        float score() { return support + longest * 0.7f; }
    }

    private static int red(int p) { return (p >>> 16) & 255; }
    private static int green(int p) { return (p >>> 8) & 255; }
    private static int blue(int p) { return p & 255; }

    /** Filled white ball pixels: stricter than the guide-line mask. */
    private static boolean ballWhite(int p) {
        int r=red(p), g=green(p), b=blue(p);
        int min=Math.min(r, Math.min(g,b)), max=Math.max(r, Math.max(g,b));
        return min >= 168 && max-min <= 55;
    }

    /** White/grey pixels in the thin native guide, tolerant of compression. */
    private static boolean guidePixel(int p) {
        int r=red(p), g=green(p), b=blue(p);
        int min=Math.min(r, Math.min(g,b)), max=Math.max(r, Math.max(g,b));
        return min >= 140 && max-min <= 55;
    }

    private static int hueBin(int p) {
        float r=red(p)/255f, g=green(p)/255f, b=blue(p)/255f;
        float max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
        float d=max-min;
        if (max < .27f || d < .19f || d/max < .29f) return -1;
        float h;
        if (max==r) h=((g-b)/d)%6f;
        else if (max==g) h=(b-r)/d+2f;
        else h=(r-g)/d+4f;
        if (h<0) h+=6f;
        return Math.min(35,(int)(h*6f));
    }

    private static boolean felt(int p, int wanted) {
        int hue=hueBin(p);
        if (hue<0) return false;
        int diff=Math.abs(hue-wanted);
        diff=Math.min(diff,36-diff);
        return diff<=3;
    }

    /** Locate the dominant felt rectangle; all returned coordinates use the sampled frame. */
    private static Bounds findTable(int[] pixels, int w, int h) {
        int[] histogram=new int[36];
        for (int y=h*26/100; y<h*78/100; y+=3) {
            for (int x=w*20/100; x<w*80/100; x+=3) {
                int hue=hueBin(pixels[y*w+x]);
                if (hue>=0) histogram[hue]++;
            }
        }
        int wanted=0, best=0;
        for (int k=0;k<36;k++) {
            int n=histogram[(k+35)%36]+histogram[k]+histogram[(k+1)%36];
            if (n>best) { best=n; wanted=k; }
        }
        if (best<45) return null;

        int xLo=w*5/100, xHi=w*95/100, yLo=h*12/100, yHi=h*94/100;
        int firstY=h, lastY=0, firstX=w, lastX=0;
        int rowThreshold=(int)(((xHi-xLo)/3)*.34f);
        for (int y=yLo;y<yHi;y+=2) {
            int count=0;
            for (int x=xLo;x<xHi;x+=3) if (felt(pixels[y*w+x],wanted)) count++;
            if (count>rowThreshold) { firstY=Math.min(firstY,y); lastY=Math.max(lastY,y); }
        }
        if (lastY-firstY<h*.20f) return null;
        int colThreshold=(int)(((lastY-firstY)/3)*.35f);
        for (int x=xLo;x<xHi;x+=2) {
            int count=0;
            for (int y=firstY;y<=lastY;y+=3) if (felt(pixels[y*w+x],wanted)) count++;
            if (count>colThreshold) { firstX=Math.min(firstX,x); lastX=Math.max(lastX,x); }
        }
        if (lastX-firstX<w*.35f) return null;
        int l=Math.max(0,firstX+3), r=Math.min(w-1,lastX-3);
        int t=Math.max(0,firstY+3), b=Math.min(h-1,lastY-3);
        if (r-l<w*.30f || b-t<h*.18f) return null;
        return new Bounds(l,t,r,b);
    }

    private static int countBallWhite(int[] pixels, int w, int h, int cx, int cy, int radius) {
        int count=0;
        for (int y=cy-radius;y<=cy+radius;y++) {
            if (y<0 || y>=h) continue;
            for (int x=cx-radius;x<=cx+radius;x++) {
                if (x>=0 && x<w && ballWhite(pixels[y*w+x])) count++;
            }
        }
        return count;
    }

    /** Score a compact, filled white region more highly than a hollow ring or thin line. */
    private static int ballScore(int[] pixels, int w, int h, int x, int y) {
        int outer=countBallWhite(pixels,w,h,x,y,2);
        int center=countBallWhite(pixels,w,h,x,y,1);
        return outer*2+center;
    }

    private static boolean guideAt(int[] pixels, int w, int h, float x, float y) {
        int cx=Math.round(x), cy=Math.round(y);
        // Sample a 3x3 neighborhood to tolerate anti-aliasing and one-pixel lines.
        for (int oy=-1;oy<=1;oy++) {
            int py=cy+oy;
            if (py<0 || py>=h) continue;
            for (int ox=-1;ox<=1;ox++) {
                int px=cx+ox;
                if (px>=0 && px<w && guidePixel(pixels[py*w+px])) return true;
            }
        }
        return false;
    }

    private static Ray findRay(int[] pixels, int w, int h, Bounds b, int x, int y) {
        Ray bestRay=null;
        float bestScore=-1f;
        for (int degrees=0; degrees<360; degrees++) {
            double angle=Math.toRadians(degrees);
            float dx=(float)Math.cos(angle), dy=(float)Math.sin(angle);
            float tx=Float.POSITIVE_INFINITY, ty=Float.POSITIVE_INFINITY;
            if (dx>.0001f) tx=(b.right-x)/dx;
            else if (dx<-.0001f) tx=(b.left-x)/dx;
            if (dy>.0001f) ty=(b.bottom-y)/dy;
            else if (dy<-.0001f) ty=(b.top-y)/dy;
            float maxDistance=Math.min(tx,ty);
            if (!Float.isFinite(maxDistance) || maxDistance<8) continue;

            int support=0, run=0, gaps=0, longest=0;
            for (float d=4; d<maxDistance; d+=1f) {
                float px=x+dx*d, py=y+dy*d;
                boolean hit=guideAt(pixels,w,h,px,py);
                if (hit) {
                    support++;
                    run++;
                    gaps=0;
                } else {
                    gaps++;
                    if (gaps>1) {
                        if (run>longest) longest=run;
                        run=0;
                        gaps=0;
                    }
                }
            }
            if (run>longest) longest=run;
            float score=support+longest*.7f;
            if (score>bestScore) {
                bestScore=score;
                bestRay=new Ray(dx,dy,support,longest);
            }
        }
        return bestRay;
    }

    /**
     * pixels are packed ARGB from a downscaled screen frame. Returns null when
     * there is no confident bright guide coming out of a filled white ball.
     */
    public static Result detect(int[] pixels, int w, int h) {
        if (pixels==null || w<100 || h<100 || pixels.length<w*h) return null;
        Bounds table=findTable(pixels,w,h);
        if (table==null) return null;

        int[] scores=new int[w*h];
        for (int y=table.top+3;y<table.bottom-3;y++) {
            for (int x=table.left+3;x<table.right-3;x++) {
                scores[y*w+x]=ballScore(pixels,w,h,x,y);
            }
        }

        float bestTotal=-1f;
        int bestX=-1,bestY=-1;
        Ray bestRay=null;
        // Evaluate several solid-white candidates. This lets line continuity
        // distinguish the cue ball from white-striped object balls.
        for (int candidate=0;candidate<12;candidate++) {
            int maxScore=0, cx=-1, cy=-1;
            for (int y=table.top+3;y<table.bottom-3;y++) {
                int offset=y*w;
                for (int x=table.left+3;x<table.right-3;x++) {
                    int score=scores[offset+x];
                    if (score>maxScore) { maxScore=score; cx=x; cy=y; }
                }
            }
            if (cx<0 || maxScore<14) break;
            Ray ray=findRay(pixels,w,h,table,cx,cy);
            if (ray!=null && ray.support>=6 && ray.longest>=5) {
                float total=maxScore*5f+ray.support+ray.longest*.7f;
                if (total>bestTotal) { bestTotal=total; bestX=cx; bestY=cy; bestRay=ray; }
            }
            // Non-maximum suppression: don't evaluate the same ball several times.
            for (int yy=Math.max(table.top,cy-5);yy<=Math.min(table.bottom,cy+5);yy++) {
                for (int xx=Math.max(table.left,cx-5);xx<=Math.min(table.right,cx+5);xx++) {
                    scores[yy*w+xx]=0;
                }
            }
        }
        if (bestRay==null) return null;
        return new Result(table.left,table.top,table.right,table.bottom,
                bestX,bestY,bestRay.dx,bestRay.dy,
                (int)(bestRay.support+bestRay.longest*.7f));
    }
}
