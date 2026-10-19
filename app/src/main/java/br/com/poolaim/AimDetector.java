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
        /** Approximate visible native branch length, measured from the collision marker. */
        public final int length;
        Branch(float x, float y, float dx, float dy, int length) {
            this.x=x; this.y=y; this.dx=dx; this.dy=dy; this.length=Math.max(0,length);
        }
    }

    public static final class Result {
        public final float left, top, right, bottom, startX, startY, dx, dy;
        public final float collisionX, collisionY;
        /** Length of the native, visible cue guide before the overlay extension begins. */
        public final float guideLength;
        public final int votes;
        public final Branch[] branches;
        public final boolean hasCollision;

        Result(float l, float t, float r, float b, float x, float y,
               float dx, float dy, int votes) {
            this(l,t,r,b,x,y,dx,dy,votes,new Branch[0],Float.NaN,Float.NaN,0f);
        }
        Result(float l, float t, float r, float b, float x, float y,
               float dx, float dy, int votes, Branch[] branches, float collisionX, float collisionY) {
            this(l,t,r,b,x,y,dx,dy,votes,branches,collisionX,collisionY,0f);
        }
        Result(float l, float t, float r, float b, float x, float y,
               float dx, float dy, int votes, Branch[] branches, float collisionX, float collisionY,
               float guideLength) {
            left=l; top=t; right=r; bottom=b; startX=x; startY=y;
            this.dx=dx; this.dy=dy; this.votes=votes;
            this.branches=branches==null?new Branch[0]:branches;
            this.collisionX=collisionX; this.collisionY=collisionY;
            this.guideLength=Math.max(0f,guideLength);
            this.hasCollision=Float.isFinite(collisionX) && Float.isFinite(collisionY);
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
        final float visibleLength;
        Ray(float dx, float dy, int support, int longest, float alternativeScore, float visibleLength) {
            this.dx=dx; this.dy=dy; this.support=support; this.longest=longest;
            this.alternativeScore=alternativeScore; this.visibleLength=Math.max(0f,visibleLength);
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
        if (best<45) return fallbackTable(w,h);

        int xLo=w*5/100, xHi=w*95/100, yLo=h*12/100, yHi=h*94/100;
        int firstY=h, lastY=0, firstX=w, lastX=0;
        int rowThreshold=(int)(((xHi-xLo)/3)*.34f);
        for (int y=yLo;y<yHi;y+=2) {
            int count=0;
            for (int x=xLo;x<xHi;x+=3) if (felt(pixels[y*w+x],wanted)) count++;
            if (count>rowThreshold) { firstY=Math.min(firstY,y); lastY=Math.max(lastY,y); }
        }
        if (lastY-firstY<h*.20f) return fallbackTable(w,h);
        int colThreshold=(int)(((lastY-firstY)/3)*.35f);
        for (int x=xLo;x<xHi;x+=2) {
            int count=0;
            for (int y=firstY;y<=lastY;y+=3) if (felt(pixels[y*w+x],wanted)) count++;
            if (count>colThreshold) { firstX=Math.min(firstX,x); lastX=Math.max(lastX,x); }
        }
        if (lastX-firstX<w*.35f) return fallbackTable(w,h);
        int l=Math.max(0,firstX+3), r=Math.min(w-1,lastX-3);
        int t=Math.max(0,firstY+3), b=Math.min(h-1,lastY-3);
        if (r-l<w*.30f || b-t<h*.18f) return fallbackTable(w,h);
        return new Bounds(l,t,r,b);
    }

    /** Fallback for dark/neutral felt themes where hue-based felt detection fails. */
    private static Bounds fallbackTable(int w,int h) {
        int l=Math.round(w*.13f), r=Math.round(w*.87f);
        int t=Math.round(h*.20f), b=Math.round(h*.90f);
        if(r-l<w*.30f || b-t<h*.18f) return null;
        return new Bounds(Math.max(0,l),Math.max(0,t),Math.min(w-1,r),Math.min(h-1,b));
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
        float[] visibleEnds=new float[360];
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
            float currentEnd=0f, longestEnd=0f;
            for (float d=3; d<maxDistance; d+=1f) {
                boolean hit=guideAt(pixels,w,h,x+dx*d,y+dy*d);
                if (hit) {
                    support++; run++; gaps=0; currentEnd=d;
                    if(run>longest){longest=run;longestEnd=currentEnd;}
                } else {
                    gaps++;
                    if(gaps>2){run=0;gaps=0;}
                }
            }
            float score=support+longest*.7f;
            scores[degrees]=score; supports[degrees]=support; longests[degrees]=longest;
            visibleEnds[degrees]=longestEnd;
            if(score>bestScore){bestScore=score;bestDegree=degrees;}
        }
        if(bestDegree<0)return null;
        float bestDx=(float)Math.cos(Math.toRadians(bestDegree));
        float bestDy=(float)Math.sin(Math.toRadians(bestDegree));
        float alternative=0f;
        for(int degree=0;degree<360;degree++) {
            if(scores[degree]<0)continue;
            float dx=(float)Math.cos(Math.toRadians(degree)),dy=(float)Math.sin(Math.toRadians(degree));
            if(Math.abs(dx*bestDx+dy*bestDy)>.94f)continue;
            if(scores[degree]>alternative)alternative=scores[degree];
        }
        // Leave a small margin so the overlay begins just after the native guide.
        float visibleLength=Math.max(0f,visibleEnds[bestDegree]-1.5f);
        return new Ray(bestDx,bestDy,supports[bestDegree],longests[bestDegree],alternative,visibleLength);
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
        int minVotes=Math.max(5,(int)Math.round(Math.min(b.right-b.left,b.bottom-b.top)*.035f));
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
                        if(len>=6f && i-begin>=4) {
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
    private static int collisionMarkerScore(int[] pixels,int w,int h,float cx,float cy,Ray primary) {
        boolean[] sectors=new boolean[8]; int count=0,positive=0,negative=0;
        for(int y=(int)cy-5;y<=(int)cy+5;y++) {
            if(y<0||y>=h) continue;
            for(int x=(int)cx-5;x<=(int)cx+5;x++) {
                if(x<0||x>=w||!guidePixel(pixels[y*w+x])) continue;
                float ox=x-cx,oy=y-cy,dist=(float)Math.hypot(ox,oy);
                if(dist<2.0f||dist>5.2f) continue;
                // Exclude pixels belonging only to the incoming guide itself.
                float perpendicular=Math.abs(ox*primary.dy-oy*primary.dx);
                if(perpendicular<1.75f) continue;
                float signed=ox*primary.dy-oy*primary.dx;
                if(signed>=0) positive++; else negative++;
                int sector=(int)Math.floor((Math.atan2(oy,ox)+Math.PI)*(8.0/(2.0*Math.PI)));
                sectors[Math.max(0,Math.min(7,sector))]=true; count++;
            }
        }
        int coverage=0;for(boolean occupied:sectors)if(occupied)coverage++;
        return count>=5 && coverage>=4 && positive>=2 && negative>=2
                ? count+coverage*5 : 0;
    }

    private static boolean hasCollisionMarker(int[] pixels,int w,int h,float cx,float cy,Ray primary) {
        return collisionMarkerScore(pixels,w,h,cx,cy,primary)>0;
    }

    /**
     * Finds guide branches whose endpoint touches the detected cue-to-target
     * line at a collision ring. This rejects table text and unrelated lines.
     */
    private static BranchSet findBranches(int[] pixels,int w,int h,Bounds table,int cueX,int cueY,Ray primary) {
        Segment[] segments=findBrightSegments(pixels,w,h,table,cueX,cueY);
        java.util.ArrayList<Branch> found=new java.util.ArrayList<>();
        float collisionX=Float.NaN,collisionY=Float.NaN;
        float maxT=(float)Math.hypot(table.right-table.left,table.bottom-table.top);
        for(Segment s:segments) {
            float sx=s.x2-s.x1, sy=s.y2-s.y1;
            float sl=(float)Math.hypot(sx,sy); if(sl<6f) continue;
            // The incoming line itself touches the cue ball; outgoing lines do not.
            if(pointSegmentDistance(cueX,cueY,s.x1,s.y1,s.x2,s.y2)<8f) continue;
            float qx=s.x1-cueX,qy=s.y1-cueY;
            float den=primary.dx*sy-primary.dy*sx;
            float ix,iy,t;
            if(Math.abs(den)<.025f) {
                // A straight-on hit makes the target-ball line collinear with
                // the incoming guide. Do not discard it just because it is parallel.
                float perp=Math.abs(qx*primary.dy-qy*primary.dx);
                if(perp>3.5f) continue;
                float t1=(s.x1-cueX)*primary.dx+(s.y1-cueY)*primary.dy;
                float t2=(s.x2-cueX)*primary.dx+(s.y2-cueY)*primary.dy;
                t=Math.min(t1,t2);
                if(t<10f||t>maxT) continue;
                ix=cueX+primary.dx*t; iy=cueY+primary.dy*t;
                if(pointSegmentDistance(ix,iy,s.x1,s.y1,s.x2,s.y2)>9.0f) continue;
            } else {
                t=(qx*sy-qy*sx)/den;
                float u=(qx*primary.dy-qy*primary.dx)/den;
                if(t<10f||t>maxT||u<-.10f||u>1.10f) continue;
                ix=cueX+primary.dx*t; iy=cueY+primary.dy*t;
                if(pointSegmentDistance(ix,iy,s.x1,s.y1,s.x2,s.y2)>9.0f) continue;
            }
            if(ix<table.left+2||ix>table.right-2||iy<table.top+2||iy>table.bottom-2) continue;
            if(!hasCollisionMarker(pixels,w,h,ix,iy,primary)) continue;
            float d1=(float)Math.hypot(s.x1-ix,s.y1-iy), d2=(float)Math.hypot(s.x2-ix,s.y2-iy);
            if(Math.min(d1,d2)>9.0f) continue;
            float fx=d1<d2?s.x2:s.x1, fy=d1<d2?s.y2:s.y1;
            float vx=fx-ix,vy=fy-iy,vl=(float)Math.hypot(vx,vy);
            if(vl<6.0f) continue;
            Branch candidate=new Branch(ix,iy,vx/vl,vy/vl,Math.round(vl));
            boolean duplicate=false;
            for(Branch old:found) {
                float bd=Math.abs(old.dx*candidate.dx+old.dy*candidate.dy);
                if(Math.hypot(old.x-candidate.x,old.y-candidate.y)<10f && bd>.86f) {duplicate=true;break;}
            }
            if(!duplicate) {
                found.add(candidate);
                if(!Float.isFinite(collisionX)){collisionX=ix;collisionY=iy;}
            }
        }
        // Keep at most two outgoing branches, one for the cue ball and one for the target ball.
        found.sort((a,b)->Integer.compare(b.length,a.length));
        if(found.size()>2) found.subList(2,found.size()).clear();
        return new BranchSet(found.toArray(new Branch[0]),collisionX,collisionY);
    }

    private static float pointSegmentDistance(float px,float py,float x1,float y1,float x2,float y2) {
        float vx=x2-x1,vy=y2-y1,den=vx*vx+vy*vy;
        if(den<.001f) return (float)Math.hypot(px-x1,py-y1);
        float t=((px-x1)*vx+(py-y1)*vy)/den;
        t=Math.max(0f,Math.min(1f,t));
        return (float)Math.hypot(px-(x1+t*vx),py-(y1+t*vy));
    }

    /** A thin screen-space ray traced from a *known* impact marker. */
    private static final class LocalRay {
        final float dx,dy,score;
        final int length, hits, longest;
        LocalRay(float dx,float dy,float score,int length,int hits,int longest) {
            this.dx=dx;this.dy=dy;this.score=score;
            this.length=length;this.hits=hits;this.longest=longest;
        }
    }

    /**
     * Measure a short native guideline starting just outside the contact ring.
     * This does not confuse the rail decorations with branches: pixels must be
     * near the proposed impact point AND form a contiguous thin, bright ray.
     */
    private static LocalRay traceLocalRay(int[] pixels,int w,int h,Bounds table,
                                          float cx,float cy,float dx,float dy) {
        int hits=0,consecutive=0,longest=0,gaps=0;
        int furthest=0;
        // Use 2px samples: all distances are in the downscaled screenshot.
        for(int r=7;r<=48;r+=2) {
            float x=cx+dx*r,y=cy+dy*r;
            if(x<=table.left+2||x>=table.right-2||y<=table.top+2||y>=table.bottom-2) break;
            boolean bright=branchAt(pixels,w,h,x,y,dx,dy);
            if(bright) {
                hits++;consecutive++;gaps=0;
                furthest=r;
                longest=Math.max(longest,consecutive);
            } else {
                gaps++;
                if(gaps>=2) consecutive=0;
            }
        }
        float score=hits+longest*1.3f;
        return new LocalRay(dx,dy,score,furthest,hits,longest);
    }

    /**
     * Require a locally brighter, thinner pixel stripe rather than just a
     * bright pixel. The old low-threshold mask accepted the BLUE FELT itself
     * as a collision ray, creating two fake 48px branches everywhere.
     */
    private static boolean branchAt(int[] pixels,int w,int h,float x,float y,
                                    float dx,float dy) {
        for(int oy=-1;oy<=1;oy++) for(int ox=-1;ox<=1;ox++) {
            int xx=Math.round(x)+ox,yy=Math.round(y)+oy;
            float px=-dy*4f,py=dx*4f;
            int ax=Math.round(xx+px),ay=Math.round(yy+py);
            int bx=Math.round(xx-px),by=Math.round(yy-py);
            if(xx<0||xx>=w||yy<0||yy>=h||ax<0||ax>=w||ay<0||ay>=h
                    ||bx<0||bx>=w||by<0||by>=h) continue;
            int c=pixels[yy*w+xx];
            int sideA=pixels[ay*w+ax],sideB=pixels[by*w+bx];
            int dr=red(c)-(red(sideA)+red(sideB))/2;
            int dg=green(c)-(green(sideA)+green(sideB))/2;
            int db=blue(c)-(blue(sideA)+blue(sideB))/2;
            int brightChannels=(dr>10?1:0)+(dg>10?1:0)+(db>10?1:0);
            if(brightChannels>=2 && dr+dg+db>=55 &&
                    Math.min(red(c),Math.min(green(c),blue(c)))>=65) return true;
        }
        return false;
    }

    /**
     * An aim-ring on the end of a cue stick is NOT a ball collision. A real
     * target must be a compact, roughly circular blob near the junction.
     * Compare the middle against its surrounding felt along 12 radial spokes,
     * so this also handles solid-black or striped target balls.
     */
    private static boolean objectBallNear(int[] pixels,int w,int h,Bounds bounds,
                                          float collisionX,float collisionY) {
        int radius=Math.max(3,Math.min(6,Math.round((bounds.right-bounds.left)*.013f)));
        int search=radius*3;
        for(int yy=Math.round(collisionY)-search;yy<=collisionY+search;yy+=2) {
            for(int xx=Math.round(collisionX)-search;xx<=collisionX+search;xx+=2) {
                if(Math.hypot(xx-collisionX,yy-collisionY)>search)continue;
                if(xx<bounds.left+radius*3||xx>bounds.right-radius*3
                        ||yy<bounds.top+radius*3||yy>bounds.bottom-radius*3) continue;
                if(compactBlob(pixels,w,h,xx,yy,radius))return true;
            }
        }
        return false;
    }

    private static int rgbDistance(int a,int b) {
        return Math.abs(red(a)-red(b))+Math.abs(green(a)-green(b))
                +Math.abs(blue(a)-blue(b));
    }

    private static boolean compactBlob(int[] pixels,int w,int h,int cx,int cy,int radius) {
        int outerRadius=radius*2+2;
        if(cx-outerRadius<0||cx+outerRadius>=w||cy-outerRadius<0||cy+outerRadius>=h)return false;
        int center=pixels[cy*w+cx];
        int contrasting=0,innerUniform=0;
        for(int i=0;i<12;i++) {
            double angle=2*Math.PI*i/12.0;
            float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            int ix=Math.round(cx+dx*(radius*.55f)),iy=Math.round(cy+dy*(radius*.55f));
            int ox=Math.round(cx+dx*outerRadius),oy=Math.round(cy+dy*outerRadius);
            int inner=pixels[iy*w+ix],outer=pixels[oy*w+ox];
            if(rgbDistance(inner,outer)>=90) contrasting++;
            if(rgbDistance(center,inner)<=190) innerUniform++;
        }
        return contrasting>=9&&innerUniform>=7;
    }

    /**
     * Find the *junction* first, then follow the two short outgoing native rays.
     * The older Hough search across the entire cloth rejected many real impacts:
     * it required a complete high-contrast circle and long branches, although
     * in the game they can be short, faint, and only partially visible.
     *
     * We accept either (a) a ring + an outgoing ray, or (b) two clear rays
     * splitting from a single point. A plain uninterrupted guide stays a
     * no-collision result; no invented post-impact directions are produced.
     */
    private static BranchSet findLocalBranches(int[] pixels,int w,int h,Bounds b,
                                               int cueX,int cueY,Ray primary) {
        final float maxLength=(float)Math.hypot(b.right-b.left,b.bottom-b.top);
        final float far=Math.min(maxLength,Math.max(12f,primary.visibleLength+10f));
        BranchSet best=null;
        float bestScore=0;
        // Scan *along the incoming guide*, not unrelated white features elsewhere.
        // The small normal offsets tolerate 1-degree quantization of findRay().
        for(float d=12f;d<=far;d+=3f) {
            for(int lateral=-3;lateral<=3;lateral+=3) {
                float cx=cueX+primary.dx*d-primary.dy*lateral;
                float cy=cueY+primary.dy*d+primary.dx*lateral;
                if(cx<b.left+7||cx>b.right-7||cy<b.top+7||cy>b.bottom-7) continue;
                int ring=collisionMarkerScore(pixels,w,h,cx,cy,primary);
                // Rank directions by *local continuation*, rather than votes
                // contributed by distant white artwork and extra objects.
                java.util.ArrayList<LocalRay> available=new java.util.ArrayList<>();
                for(int angle=0;angle<360;angle+=6) {
                    float rad=(float)Math.toRadians(angle);
                    float dx=(float)Math.cos(rad),dy=(float)Math.sin(rad);
                    // Reject a line that simply goes backward to the cue ball.
                    if(dx*primary.dx+dy*primary.dy < -.83f) continue;
                    LocalRay sample=traceLocalRay(pixels,w,h,b,cx,cy,dx,dy);
                    if(sample.hits>=5&&sample.longest>=4&&sample.length>=15
                            &&sample.score>=9f) available.add(sample);
                }
                if(available.isEmpty())continue;
                available.sort((a,z)->Float.compare(z.score,a.score));
                java.util.ArrayList<LocalRay> selected=new java.util.ArrayList<>();
                for(LocalRay candidate:available) {
                    boolean similar=false;
                    for(LocalRay other:selected) {
                        if(candidate.dx*other.dx+candidate.dy*other.dy>.91f) {similar=true;break;}
                    }
                    if(!similar) selected.add(candidate);
                    if(selected.size()>=2)break;
                }
                if(selected.isEmpty()) continue;
                if(selected.size()>=2) {
                    // Normal pool-ball contact produces nearly perpendicular
                    // outgoing paths. Discard two arbitrary, nearly parallel
                    // (or opposite) artwork/cue-stick strokes at a cursor ring.
                    float alignment=selected.get(0).dx*selected.get(1).dx
                            +selected.get(0).dy*selected.get(1).dy;
                    if(Math.abs(alignment)>.64f) continue;
                }
                // Ringless branching must have two independently supported rays,
                // one sufficiently different from the incoming direction.
                if(ring==0) {
                    if(selected.size()<2) continue;
                    boolean divergent=false;
                    for(LocalRay candidate:selected)
                        if(candidate.dx*primary.dx+candidate.dy*primary.dy<.88f) divergent=true;
                    float branchDot=selected.get(0).dx*selected.get(1).dx
                            +selected.get(0).dy*selected.get(1).dy;
                    // A one-pixel straight guideline can produce two almost
                    // parallel scored angles. Those are not outgoing balls.
                    if(!divergent || branchDot>.78f) continue;
                }
                // The contact ring must also belong to a *ball*, not just the
                // aiming cursor at the tip of the cue. Do not invent impacts.
                if(!objectBallNear(pixels,w,h,b,cx,cy))continue;
                float quality=(ring>0?14f+Math.min(20f,ring*.35f):0f);
                for(LocalRay selectedRay:selected) quality+=Math.min(21f,selectedRay.score);
                if(selected.size()>1) quality+=8f;
                quality-=Math.abs(d-primary.visibleLength)*.12f;
                if(quality<=bestScore)continue;
                java.util.ArrayList<Branch> outgoing=new java.util.ArrayList<>();
                for(LocalRay ray:selected) outgoing.add(new Branch(cx,cy,ray.dx,ray.dy,ray.length));
                best=new BranchSet(outgoing.toArray(new Branch[0]),cx,cy);
                bestScore=quality;
            }
        }
        return best==null?new BranchSet(new Branch[0],Float.NaN,Float.NaN):best;
    }

    private static BranchSet includeCollinearTargetPath(int[] pixels,int w,int h,Bounds table,
                                                          int cueX,int cueY,Ray primary,BranchSet found) {
        float maxT=(float)Math.hypot(table.right-table.left,table.bottom-table.top);
        float scanEnd=Math.min(maxT,Math.max(12f,primary.visibleLength+6f));
        float bestT=Float.NaN; int bestScore=0;
        for(float d=10f;d<=scanEnd;d+=1f) {
            float x=cueX+primary.dx*d,y=cueY+primary.dy*d;
            if(x<table.left+2||x>table.right-2||y<table.top+2||y>table.bottom-2) break;
            int score=collisionMarkerScore(pixels,w,h,x,y,primary);
            if(score>bestScore){bestScore=score;bestT=d;}
        }
        if(!Float.isFinite(bestT)) return found;
        float cx=cueX+primary.dx*bestT,cy=cueY+primary.dy*bestT;
        if(!objectBallNear(pixels,w,h,table,cx,cy)) return found;
        java.util.ArrayList<Branch> branches=new java.util.ArrayList<>();
        for(Branch b:found.branches) branches.add(b);
        boolean hasParallel=false;
        for(Branch b:branches) if(Math.abs(b.dx*primary.dx+b.dy*primary.dy)>.94f) hasParallel=true;
        float remaining=primary.visibleLength-bestT;
        // If the native guide continues beyond the contact ring along the same
        // direction, that is the target-ball path for a straight-on collision.
        if(!hasParallel && remaining>=7f && branches.size()<2
                && guideAt(pixels,w,h,cx+primary.dx*6f,cy+primary.dy*6f)) {
            branches.add(new Branch(cx,cy,primary.dx,primary.dy,Math.round(remaining)));
        }
        if(branches.size()>2) branches.subList(2,branches.size()).clear();
        float outX=Float.isFinite(found.x)?found.x:cx;
        float outY=Float.isFinite(found.y)?found.y:cy;
        if(!Float.isFinite(found.x)) {
            // Rebase branches found on the same incoming collision point so the
            // renderer and stabilizer share one collision origin.
            java.util.ArrayList<Branch> rebased=new java.util.ArrayList<>();
            for(Branch b:branches) {
                if(Math.hypot(b.x-cx,b.y-cy)<10f) rebased.add(new Branch(cx,cy,b.dx,b.dy,b.length));
                else rebased.add(b);
            }
            branches=rebased;
        }
        return new BranchSet(branches.toArray(new Branch[0]),outX,outY);
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
        BranchSet branchSet=findLocalBranches(pixels,w,h,table,bestX,bestY,bestRay);
        if(branchSet.branches.length==0) {
            // Collinear target trajectories are only accepted if a complete
            // multi-sector contact ring is visible; a plain cue line is not one.
            branchSet=includeCollinearTargetPath(pixels,w,h,table,bestX,bestY,bestRay,branchSet);
        }
        return new Result(table.left,table.top,table.right,table.bottom,
                bestX,bestY,bestRay.dx,bestRay.dy,
                (int)(bestRay.support+bestRay.longest*.7f),branchSet.branches,branchSet.x,branchSet.y,bestRay.visibleLength);
    }

    /**
     * Remove only the overlay-generated extension from a captured frame before
     * detection. The game's original, short white guide is preserved.
     */
    public static void maskOverlayExtensions(int[] pixels,int w,int h,Result previous) {
        if(pixels==null||previous==null||pixels.length<w*h) return;
        Bounds b=new Bounds(Math.max(0,Math.round(previous.left)),Math.max(0,Math.round(previous.top)),
                Math.min(w-1,Math.round(previous.right)),Math.min(h-1,Math.round(previous.bottom)));
        if(previous.hasCollision && previous.branches.length>0) {
            for(Branch branch:previous.branches) {
                float sx=branch.x+branch.dx*Math.max(0,branch.length);
                float sy=branch.y+branch.dy*Math.max(0,branch.length);
                paintRayMask(pixels,w,h,b,sx,sy,branch.dx,branch.dy,3);
            }
        } else {
            float sx=previous.startX+previous.dx*Math.max(0f,previous.guideLength);
            float sy=previous.startY+previous.dy*Math.max(0f,previous.guideLength);
            paintRayMask(pixels,w,h,b,sx,sy,previous.dx,previous.dy,3);
        }
    }

    /** Mark a narrow path mask for overlay extensions, including geometric rail bounces. */
    private static void paintRayMask(int[] pixels,int w,int h,Bounds b,float x,float y,float dx,float dy,int segments) {
        for(int i=0;i<segments;i++) {
            float tx=Float.POSITIVE_INFINITY,ty=Float.POSITIVE_INFINITY;
            if(dx>.0001f) tx=(b.right-x)/dx; else if(dx<-.0001f) tx=(b.left-x)/dx;
            if(dy>.0001f) ty=(b.bottom-y)/dy; else if(dy<-.0001f) ty=(b.top-y)/dy;
            float dt=Math.min(tx,ty);
            if(!Float.isFinite(dt)||dt<-.1f) return;
            float x2=x+dx*Math.max(0,dt),y2=y+dy*Math.max(0,dt);
            rasterMaskLine(pixels,w,h,x,y,x2,y2,1);
            boolean bounceX=tx<=ty+.01f,bounceY=ty<=tx+.01f;
            float width=b.right-b.left,height=b.bottom-b.top;
            boolean pocket=false;
            if(bounceY) pocket=x2-b.left<width*.055f || b.right-x2<width*.055f
                    || Math.abs(x2-(b.left+b.right)/2f)<width*.045f;
            if(bounceX) pocket=pocket || y2-b.top<height*.10f || b.bottom-y2<height*.10f
                    || Math.abs(y2-(b.top+b.bottom)/2f)<height*.085f;
            if(pocket) return;
            if(bounceX) dx=-dx;
            if(bounceY) dy=-dy;
            x=x2+dx*.7f; y=y2+dy*.7f;
        }
    }

    private static void rasterMaskLine(int[] pixels,int w,int h,float x1,float y1,float x2,float y2,int radius) {
        int steps=Math.max(1,(int)Math.ceil(Math.hypot(x2-x1,y2-y1)));
        for(int i=0;i<=steps;i++) {
            int x=Math.round(x1+(x2-x1)*i/steps),y=Math.round(y1+(y2-y1)*i/steps);
            for(int oy=-radius;oy<=radius;oy++) for(int ox=-radius;ox<=radius;ox++) {
                if(ox*ox+oy*oy>radius*radius+1) continue;
                int px=x+ox,py=y+oy;
                if(px>=0&&px<w&&py>=0&&py<h) pixels[py*w+px]=0xff000000;
            }
        }
    }

}
