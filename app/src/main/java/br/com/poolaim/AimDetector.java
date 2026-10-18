package br.com.poolaim.overlay;

/** Experimental color/edge-based detector: no game modification or input automation. */
public final class AimDetector {
    public static final class Result {
        public final float left, top, right, bottom, startX, startY, dx, dy;
        public final int votes;
        Result(float l, float t, float r, float b, float x, float y, float dx, float dy, int votes) {
            left=l; top=t; right=r; bottom=b; startX=x; startY=y;
            this.dx=dx; this.dy=dy; this.votes=votes;
        }
    }
    private static int rd(int p){return (p>>>16)&255;}
    private static int gr(int p){return (p>>>8)&255;}
    private static int bl(int p){return p&255;}
    private static boolean white(int p) {
        int r=rd(p),g=gr(p),b=bl(p);
        int min=Math.min(r,Math.min(g,b)), max=Math.max(r,Math.max(g,b));
        return min>=172 && max-min<=49;
    }
    private static int hueBin(int p) {
        float r=rd(p)/255f,g=gr(p)/255f,b=bl(p)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        float d=max-min;
        if (max<.27f || d<.19f || d/max<.29f) return -1;
        float h;
        if(max==r) h=((g-b)/d)%6f;
        else if(max==g) h=(b-r)/d+2f;
        else h=(r-g)/d+4f;
        if(h<0)h+=6f;
        return Math.min(35,(int)(h*6f));
    }
    private static boolean felt(int p,int wanted) {
        int hue=hueBin(p);
        if(hue<0)return false;
        int diff=Math.abs(hue-wanted); diff=Math.min(diff,36-diff);
        return diff<=3;
    }
    /** pixels are packed ARGB, w*h, sampled to 360px or smaller for speed. */
    public static Result detect(int[] pixels, int w, int h) {
        if(pixels==null || w<100 || h<100 || pixels.length<w*h)return null;
        int[] histogram=new int[36];
        for(int y=h*26/100;y<h*78/100;y+=3) for(int x=w*20/100;x<w*80/100;x+=3){
            int c=hueBin(pixels[y*w+x]); if(c>=0) histogram[c]++;
        }
        int wanted=0, best=0;
        for(int k=0;k<36;k++) {
            int n=histogram[(k+35)%36]+histogram[k]+histogram[(k+1)%36];
            if(n>best){best=n;wanted=k;}
        }
        if(best<45)return null;
        int xLo=w*5/100,xHi=w*95/100,yLo=h*12/100,yHi=h*94/100;
        int firstY=h,lastY=0,firstX=w,lastX=0;
        for(int y=yLo;y<yHi;y+=2){
            int count=0;
            for(int x=xLo;x<xHi;x+=3) if(felt(pixels[y*w+x],wanted))count++;
            if(count>(xHi-xLo)/3*.34f){firstY=Math.min(firstY,y);lastY=Math.max(lastY,y);}
        }
        if(lastY-firstY<h*.20f)return null;
        for(int x=xLo;x<xHi;x+=2){
            int count=0;
            for(int y=firstY;y<=lastY;y+=3)if(felt(pixels[y*w+x],wanted))count++;
            if(count>(lastY-firstY)/3*.35f){firstX=Math.min(firstX,x);lastX=Math.max(lastX,x);}
        }
        if(lastX-firstX<w*.35f)return null;
        int l=Math.max(0,firstX+3),r=Math.min(w-1,lastX-3);
        int t=Math.max(0,firstY+3),b=Math.min(h-1,lastY-3);
        // Collect near-white pixels only inside the playing field, not HUD or cushion.
        int maxPts=14000, n=0;
        int[] px=new int[maxPts],py=new int[maxPts];
        for(int y=t+3;y<b-3;y+=1) for(int x=l+3;x<r-3;x+=1){
            int color=pixels[y*w+x];
            if(white(color) && n<maxPts){px[n]=x;py[n]=y;n++;}
        }
        if(n<15)return null;
        // Hough vote for thin straight white segments. Bins: 3 degrees, 2 px distance.
        final int angles=60; int rhoBins=(int)Math.ceil(Math.hypot(w,h));
        int[][] votes=new int[angles][rhoBins+2];
        double[] sn=new double[angles],cs=new double[angles];
        for(int a=0;a<angles;a++){double rad=Math.PI*a/angles;sn[a]=Math.sin(rad);cs[a]=Math.cos(rad);}
        for(int k=0;k<n;k++) for(int a=0;a<angles;a++){
            int rho=(int)Math.round((px[k]*cs[a]+py[k]*sn[a]+rhoBins)/2.0);
            if(rho>=0&&rho<votes[a].length)votes[a][rho]++;
        }
        int bestAngle=-1,bestRho=-1,bestScore=0;
        float bestSpan=0;
        for(int a=0;a<angles;a++)for(int q=0;q<votes[a].length;q++){
            if(votes[a][q]<12)continue;
            double ux=-sn[a],uy=cs[a];
            float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;
            for(int k=0;k<n;k++){
                double delta=Math.abs((px[k]*cs[a]+py[k]*sn[a]+rhoBins)/2.0-q);
                if(delta<=.8){float p=(float)(px[k]*ux+py[k]*uy);lo=Math.min(lo,p);hi=Math.max(hi,p);}
            }
            float span=hi-lo;
            if(span<Math.min(w,h)*.17f)continue;
            // Favors long sparse guide lines over isolated ball disks.
            int score=(int)(votes[a][q]*Math.min(2.0,span/35f));
            if(score>bestScore){bestScore=score;bestAngle=a;bestRho=q;bestSpan=span;}
        }
        if(bestAngle<0)return null;
        double ux=-sn[bestAngle],uy=cs[bestAngle];
        float pMin=Float.MAX_VALUE,pMax=-Float.MAX_VALUE;
        for(int k=0;k<n;k++){
            double delta=Math.abs((px[k]*cs[bestAngle]+py[k]*sn[bestAngle]+rhoBins)/2.0-bestRho);
            if(delta<=.8){float pos=(float)(px[k]*ux+py[k]*uy);pMin=Math.min(pos,pMin);pMax=Math.max(pos,pMax);}
        }
        if(pMax<=pMin)return null;
        float center=(float)(bestRho*2-rhoBins);
        float ax=(float)(center*cs[bestAngle]+pMin*ux),ay=(float)(center*sn[bestAngle]+pMin*uy);
        float bx=(float)(center*cs[bestAngle]+pMax*ux),by=(float)(center*sn[bestAngle]+pMax*uy);
        // Heuristic: cue ball is more likely at the endpoint surrounded by white pixels.
        int countA=whiteAround(pixels,w,h,ax,ay),countB=whiteAround(pixels,w,h,bx,by);
        if(countB>countA){float tx=ax; ax=bx;bx=tx;float ty=ay;ay=by;by=ty;}
        float vx=bx-ax,vy=by-ay,len=(float)Math.hypot(vx,vy);
        if(len==0)return null;
        return new Result(l,t,r,b,ax,ay,vx/len,vy/len,Math.max(bestScore,(int)bestSpan));
    }
    private static int whiteAround(int[]p,int w,int h,float fx,float fy){
        int n=0,cx=(int)fx,cy=(int)fy;
        for(int dy=-5;dy<=5;dy++)for(int dx=-5;dx<=5;dx++){
            int x=cx+dx,y=cy+dy; if(x>=0&&x<w&&y>=0&&y<h&&white(p[y*w+x]))n++;
        }
        return n;
    }
}
