package br.com.poolaim.overlay;

/**
 * Experimental screen-image detector for the native pool aiming guide.
 * It first locates a bright, mostly solid cue-ball candidate, then looks for
 * a low-saturation bright line radiating from that candidate inside the felt.
 * This avoids selecting unrelated long white lines elsewhere on the table.
 */
public final class AimDetector {
    public static final class Branch {
        public final float x, y, dx, dy;
        public final int length;
        Branch(float x, float y, float dx, float dy, int length) {
            this.x=x; this.y=y; this.dx=dx; this.dy=dy; this.length=length;
        }
    }

    public static final class Result {
        public final float left, top, right, bottom, startX, startY, dx, dy;
        public final float collisionX, collisionY;
        public final int votes;
        public final Branch[] branches;
        public final boolean hasCollision;

        Result(float l, float t, float r, float b, float x, float y,
               float dx, float dy, int votes) {
            this(l,t,r,b,x,y,dx,dy,votes,new Branch[0],Float.NaN,Float.NaN);
        }
        Result(float l, float t, float r, float b, float x, float y,
               float dx, float dy, int votes, Branch[] branches, float collisionX, float collisionY) {
            left=l; top=t; right=r; bottom=b; startX=x; startY=y;
            this.dx=dx; this.dy=dy; this.votes=votes;
            this.branches=branches==null?new Branch[0]:branches;
            this.collisionX=collisionX; this.collisionY=collisionY;
            this.hasCollision=this.branches.length>0 && Float.isFinite(collisionX) && Float.isFinite(collisionY);
        }
    }

    private static final class Bounds {
        final int left, top, right, bottom;
        Bounds(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
    }

    private static final class Ray {
        final float dx, dy;
        final int support, longest;
        final float alternativeScore;
        Ray(float dx, float dy, int support, int longest, float alternativeScore) {
            this.dx=dx; this.dy=dy; this.support=support; this.longest=longest;
            this.alternativeScore=alternativeScore;
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

    /** A slightly softer mask is used only for faint post-collision branches. */
    private static boolean branchGuidePixel(int p) {
        int r=red(p), g=green(p), b=blue(p);
        int min=Math.min(r, Math.min(g,b)), max=Math.max(r, Math.max(g,b));
        return min >= 75 && max-min <= 120;
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

    /**
     * Score a round white ball, not a straight stroke or UI text. A real cue
     * ball has bright pixels distributed around several angular sectors even
     * when its centre contains a red spin marker.
     */
    private static int ballScore(int[] pixels, int w, int h, int x, int y) {
        int outer=countBallWhite(pixels,w,h,x,y,2);
        int center=countBallWhite(pixels,w,h,x,y,1);
        boolean[] sectors=new boolean[8];
        int ring=0;
        for(int yy=y-3;yy<=y+3;yy++) {
            if(yy<0||yy>=h) continue;
            for(int xx=x-3;xx<=x+3;xx++) {
                if(xx<0||xx>=w||!ballWhite(pixels[yy*w+xx])) continue;
                float dx=xx-x,dy=yy-y,dist=(float)Math.hypot(dx,dy);
                if(dist<1.0f||dist>3.2f) continue;
                ring++;
                int sector=(int)Math.floor((Math.atan2(dy,dx)+Math.PI)*(8.0/(2.0*Math.PI)));
                sectors[Math.max(0,Math.min(7,sector))]=true;
            }
        }
        int coverage=0;for(boolean occupied:sectors)if(occupied)coverage++;
        if(coverage<4||ring<5||center<3) return 0;
        return outer*2+center+coverage*5+ring;
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
        float[] scores=new float[360]; int[] supports=new int[360],longests=new int[360];
        java.util.Arrays.fill(scores,-1f);
        int bestDegree=-1; float bestScore=-1f;
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
                boolean hit=guideAt(pixels,w,h,x+dx*d,y+dy*d);
                if (hit) { support++; run++; gaps=0; }
                else { gaps++; if (gaps>1) { if(run>longest)longest=run; run=0; gaps=0; } }
            }
            if (run>longest) longest=run;
            float score=support+longest*.7f;
            scores[degrees]=score; supports[degrees]=support; longests[degrees]=longest;
            if(score>bestScore){bestScore=score;bestDegree=degrees;}
        }
        if(bestDegree<0)return null;
        float bestDx=(float)Math.cos(Math.toRadians(bestDegree));
        float bestDy=(float)Math.sin(Math.toRadians(bestDegree));
        float alternative=0f;
        // A target/contact marker often has several guide rays leaving it.
        // Penalize those candidates so the filled cue ball remains the origin.
        for(int degree=0;degree<360;degree++) {
            if(scores[degree]<0)continue;
            float dx=(float)Math.cos(Math.toRadians(degree)),dy=(float)Math.sin(Math.toRadians(degree));
            if(Math.abs(dx*bestDx+dy*bestDy)>.94f)continue; // same line in either direction
            if(scores[degree]>alternative)alternative=scores[degree];
        }
        return new Ray(bestDx,bestDy,supports[bestDegree],longests[bestDegree],alternative);
    }

    private static final class Segment {
        final float x1,y1,x2,y2;
        final int length;
        Segment(float x1,float y1,float x2,float y2,int length) {
            this.x1=x1; this.y1=y1; this.x2=x2; this.y2=y2; this.length=length;
        }
    }

    private static final class BranchSet {
        final Branch[] branches; final float x,y;
        BranchSet(Branch[] branches,float x,float y){this.branches=branches;this.x=x;this.y=y;}
    }

    /**
     * Extract neutral, thin guide segments with a small Hough accumulator. The
     * app's own saturated projection colors are intentionally rejected by the
     * neutral-line mask, so they cannot become input to the next frame.
     */
    private static Segment[] findBrightSegments(int[] pixels,int w,int h,Bounds b,int cueX,int cueY) {
        int cap=(b.right-b.left+1)*(b.bottom-b.top+1);
        int[] xs=new int[cap], ys=new int[cap]; int n=0;
        for(int y=b.top+1;y<b.bottom;y++) for(int x=b.left+1;x<b.right;x++) {
            if(Math.abs(x-cueX)<=3 && Math.abs(y-cueY)<=3) continue;
            if(branchGuidePixel(pixels[y*w+x])) { xs[n]=x; ys[n]=y; n++; }
        }
        if(n<12) return new Segment[0];
        int maxRho=(int)Math.ceil(Math.hypot(w,h));
        int rhoCount=maxRho*2+1;
        int[][] acc=new int[180][rhoCount];
        double[] cos=new double[180],sin=new double[180];
        for(int a=0;a<180;a++){double rad=Math.toRadians(a);cos[a]=Math.cos(rad);sin[a]=Math.sin(rad);}
        for(int i=0;i<n;i++) for(int a=0;a<180;a++) {
            int rho=(int)Math.round(xs[i]*cos[a]+ys[i]*sin[a])+maxRho;
            if(rho>=0&&rho<rhoCount) acc[a][rho]++;
        }
        int minVotes=Math.max(6,(int)Math.round(Math.min(b.right-b.left,b.bottom-b.top)*.045f));
        java.util.ArrayList<Segment> segments=new java.util.ArrayList<>();
        for(int peak=0;peak<22;peak++) {
            int best=0,ba=-1,br=-1;
            for(int a=0;a<180;a++) for(int r=1;r<rhoCount-1;r++) {
                if(acc[a][r]>best){best=acc[a][r];ba=a;br=r;}
            }
            if(best<minVotes||ba<0) break;
            final double c=cos[ba], sn=sin[ba]; final int rho=br-maxRho;
            float[] ts=new float[n]; int count=0;
            for(int i=0;i<n;i++) {
                double dist=Math.abs(xs[i]*c+ys[i]*sn-rho);
                if(dist<=1.35) ts[count++]=(float)(-xs[i]*sn+ys[i]*c);
            }
            java.util.Arrays.sort(ts,0,count);
            int begin=0;
            for(int i=1;i<=count;i++) {
                if(i==count || ts[i]-ts[i-1]>3.5f) {
                    if(i>begin) {
                        float len=ts[i-1]-ts[begin];
                        if(len>=8f && i-begin>=6) {
                            // Reconstruct endpoints on the detected infinite line.
                            float x1=(float)(rho*c-ts[begin]*sn), y1=(float)(rho*sn+ts[begin]*c);
                            float x2=(float)(rho*c-ts[i-1]*sn), y2=(float)(rho*sn+ts[i-1]*c);
                            boolean duplicate=false;
                            for(Segment old:segments) {
                                float ax=old.x2-old.x1, ay=old.y2-old.y1;
                                float bx=x2-x1, by=y2-y1;
                                float al=(float)Math.hypot(ax,ay), bl=(float)Math.hypot(bx,by);
                                if(al<1||bl<1) continue;
                                float parallel=Math.abs((ax*bx+ay*by)/(al*bl));
                                float d1=pointLineDistance(x1,y1,old.x1,old.y1,old.x2,old.y2);
                                float d2=pointLineDistance(x2,y2,old.x1,old.y1,old.x2,old.y2);
                                if(parallel>.985f && Math.min(d1,d2)<2.2f) {duplicate=true;break;}
                            }
                            if(!duplicate) segments.add(new Segment(x1,y1,x2,y2,(int)len));
                        }
                    }
                    begin=i;
                }
            }
            // Suppress only the selected peak and its immediate neighbours.
            for(int da=-2;da<=2;da++) {
                int a=ba+da; if(a<0||a>=180) continue;
                for(int dr=-2;dr<=2;dr++) {int r=br+dr;if(r>=0&&r<rhoCount)acc[a][r]=0;}
            }
        }
        return segments.toArray(new Segment[0]);
    }

    private static float pointLineDistance(float px,float py,float x1,float y1,float x2,float y2) {
        float dx=x2-x1,dy=y2-y1,den=(float)Math.hypot(dx,dy);
        return den<.001f?(float)Math.hypot(px-x1,py-y1):Math.abs(dy*px-dx*py+x2*y1-y2*x1)/den;
    }

    /** Require the branch junction to have the small bright contact ring used by the game. */
    private static boolean hasCollisionMarker(int[] pixels,int w,int h,float cx,float cy,Ray primary) {
        boolean[] sectors=new boolean[8]; int count=0;
        for(int y=(int)cy-5;y<=(int)cy+5;y++) {
            if(y<0||y>=h) continue;
            for(int x=(int)cx-5;x<=(int)cx+5;x++) {
                if(x<0||x>=w||!guidePixel(pixels[y*w+x])) continue;
                float ox=x-cx,oy=y-cy,dist=(float)Math.hypot(ox,oy);
                if(dist<2.0f||dist>5.2f) continue;
                // Exclude pixels belonging only to the incoming guide itself.
                float perpendicular=Math.abs(ox*primary.dy-oy*primary.dx);
                if(perpendicular<1.35f) continue;
                int sector=(int)Math.floor((Math.atan2(oy,ox)+Math.PI)*(8.0/(2.0*Math.PI)));
                sectors[Math.max(0,Math.min(7,sector))]=true; count++;
            }
        }
        int coverage=0;for(boolean occupied:sectors)if(occupied)coverage++;
        return count>=5 && coverage>=4;
    }

    /**
     * Finds guide branches whose endpoint touches the detected cue-to-target
     * line at a collision ring. This rejects table text and unrelated lines.
     */
    private static BranchSet findBranches(int[] pixels,int w,int h,Bounds table,int cueX,int cueY,Ray primary) {
        Segment[] segments=findBrightSegments(pixels,w,h,table,cueX,cueY);
        java.util.ArrayList<Branch> found=new java.util.ArrayList<>();
        float collisionX=Float.NaN,collisionY=Float.NaN;
        for(Segment s:segments) {
            float sx=s.x2-s.x1, sy=s.y2-s.y1;
            float sl=(float)Math.hypot(sx,sy); if(sl<8f) continue;
            float dot=Math.abs((sx*primary.dx+sy*primary.dy)/sl);
            if(dot>.94f) continue; // the cue-to-target line itself, not a branch
            float qx=s.x1-cueX,qy=s.y1-cueY;
            float den=primary.dx*sy-primary.dy*sx;
            if(Math.abs(den)<.001f) continue;
            float t=(qx*sy-qy*sx)/den;
            float u=(qx*primary.dy-qy*primary.dx)/den;
            if(t<10f || t>Math.hypot(table.right-table.left,table.bottom-table.top) || u<-.10f || u>1.10f) continue;
            float ix=cueX+primary.dx*t, iy=cueY+primary.dy*t;
            if(ix<table.left+2||ix>table.right-2||iy<table.top+2||iy>table.bottom-2) continue;
            if(!hasCollisionMarker(pixels,w,h,ix,iy,primary)) continue;
            float d1=(float)Math.hypot(s.x1-ix,s.y1-iy), d2=(float)Math.hypot(s.x2-ix,s.y2-iy);
            if(Math.min(d1,d2)>9.0f) continue;
            float fx=d1<d2?s.x2:s.x1, fy=d1<d2?s.y2:s.y1;
            float vx=fx-ix,vy=fy-iy,vl=(float)Math.hypot(vx,vy);
            if(vl<7.0f) continue;
            Branch candidate=new Branch(ix,iy,vx/vl,vy/vl,s.length);
            boolean duplicate=false;
            for(Branch old:found) {
                float bd=Math.abs(old.dx*candidate.dx+old.dy*candidate.dy);
                if(Math.hypot(old.x-candidate.x,old.y-candidate.y)<8f && bd>.90f) {duplicate=true;break;}
            }
            if(!duplicate) {
                found.add(candidate);
                if(found.size()==1){collisionX=ix;collisionY=iy;}
            }
        }
        // Sort branches by proximity to the first detected collision point, then cap noise.
        if(found.size()>3) found.subList(3,found.size()).clear();
        return new BranchSet(found.toArray(new Branch[0]),collisionX,collisionY);
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
            // A guide endpoint or text stroke can look bright but is not a
            // filled cue ball. Require a compact round white region first.
            if (cx<0 || maxScore<60) break;
            Ray ray=findRay(pixels,w,h,table,cx,cy);
            if (ray!=null && ray.support>=6 && ray.longest>=5) {
                // Prefer a long, uninterrupted native guide over a bright
                // but unrelated UI word, rail highlight, or table graphic.
                float total=maxScore*1.2f+ray.support*.5f+ray.longest*2.8f-ray.alternativeScore*1.4f;
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
        BranchSet branchSet=findBranches(pixels,w,h,table,bestX,bestY,bestRay);
        return new Result(table.left,table.top,table.right,table.bottom,
                bestX,bestY,bestRay.dx,bestRay.dy,
                (int)(bestRay.support+bestRay.longest*.7f),branchSet.branches,branchSet.x,branchSet.y);
    }
}
