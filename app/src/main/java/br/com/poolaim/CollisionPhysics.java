package br.com.poolaim.overlay;

/**
 * Independent fallback for a one-ball impact.  No access to game data and no
 * reliance on proprietary code.  The prediction is geometrical (no spin,
 * friction, momentum transfer coefficients or secret in-game parameters).
 */
public final class CollisionPhysics {
    private CollisionPhysics() { }
    private static final float EPS = .0001f;

    public static final class Contact {
        public final float cueX, cueY, objectX, objectY, normalX, normalY;
        public final float cueDx, cueDy, objectDx, objectDy;
        public final float forwardSpeedFraction, cueSpeedFraction;
        public final float diameter, confidence;
        Contact(float cx, float cy, float ox, float oy, float nx, float ny,
                float qx, float qy, float frac, float cueFrac, float diameter, float confidence) {
            this.cueX=cx; this.cueY=cy; this.objectX=ox; this.objectY=oy;
            this.normalX=nx; this.normalY=ny;
            this.cueDx=qx; this.cueDy=qy; this.objectDx=nx; this.objectDy=ny;
            this.forwardSpeedFraction=frac; this.cueSpeedFraction=cueFrac;
            this.diameter=diameter; this.confidence=confidence;
        }
        public AimDetector.Branch[] branches() {
            AimDetector.Branch target=new AimDetector.Branch(objectX,objectY,objectDx,objectDy,0);
            // Head-on contact: the ideal no-spin white stops, so do not invent
            // an arbitrary second outgoing ray.
            if(cueSpeedFraction < .12f) return new AimDetector.Branch[]{target};
            return new AimDetector.Branch[]{target,
                new AimDetector.Branch(cueX,cueY,cueDx,cueDy,0)};
        }
    }

    /** Given a white-ball centre, aim direction and object centre in pixels. */
    public static Contact predict(float whiteX, float whiteY, float ux, float uy,
                                  float targetX, float targetY, float radius, float confidence) {
        if(!Float.isFinite(radius)||radius<1f)return null;
        float d=(float)Math.hypot(ux,uy);
        if(d<EPS)return null;
        ux/=d;uy/=d;
        float vx=targetX-whiteX, vy=targetY-whiteY;
        float forward=vx*ux+vy*uy;
        float lateral=vx*uy-vy*ux;
        float diameter=radius*2f;
        if(forward < diameter*.65f || Math.abs(lateral) > diameter*.985f)return null;
        float t=forward-(float)Math.sqrt(Math.max(0f,diameter*diameter-lateral*lateral));
        if(t<=radius*.25f)return null;
        float hitX=whiteX+ux*t,hitY=whiteY+uy*t;
        float nx=(targetX-hitX)/diameter,ny=(targetY-hitY)/diameter;
        float normalMag=(float)Math.hypot(nx,ny);
        if(normalMag<EPS)return null;
        nx/=normalMag;ny/=normalMag;
        float objectFrac=Math.max(0f,ux*nx+uy*ny);
        float tx=ux-objectFrac*nx,ty=uy-objectFrac*ny;
        float cueFrac=(float)Math.hypot(tx,ty);
        if(cueFrac>EPS){tx/=cueFrac;ty/=cueFrac;}
        return new Contact(hitX,hitY,targetX,targetY,nx,ny,tx,ty,
                objectFrac,cueFrac,diameter,confidence);
    }

    private static int r(int c) { return (c>>>16)&255; }
    private static int g(int c) { return (c>>>8)&255; }
    private static int b(int c) { return c&255; }
    private static int delta(int a,int c) {
        return Math.abs(r(a)-r(c))+Math.abs(g(a)-g(c))+Math.abs(b(a)-b(c));
    }
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static int sample(int[] pixels,int w,int h,float x,float y){
        int xx=clamp(Math.round(x),0,w-1),yy=clamp(Math.round(y),0,h-1);
        return pixels[yy*w+xx];
    }

    /**
     * Circular region colour difference against the local cloth. This tests
     * nearly the whole circumference rather than a single colored pixel, so
     * an aiming ring, a letter, or a long straight line gets a weak score.
     */
    static float ballCircleScore(int[] pixels,int w,int h,float cx,float cy,float radius){
        float outer=radius*1.9f;
        if(cx-outer<1 || cy-outer<1 || cx+outer>=w-1 || cy+outer>=h-1)return 0;
        int altered=0, stableOutside=0, strong=0; float sum=0;
        int[] backgrounds=new int[16];
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8;
            float co=(float)Math.cos(a),si=(float)Math.sin(a);
            backgrounds[i]=sample(pixels,w,h,cx+co*outer,cy+si*outer);
        }
        for(int i=0;i<16;i++){
            double a=i*Math.PI/8;
            float co=(float)Math.cos(a),si=(float)Math.sin(a);
            int inner=sample(pixels,w,h,cx+co*radius*.63f,cy+si*radius*.63f);
            int middle=sample(pixels,w,h,cx+co*radius*.95f,cy+si*radius*.95f);
            int backdrop=backgrounds[i];
            int contrast=Math.max(delta(inner,backdrop),delta(middle,backdrop));
            if(contrast>70){altered++;sum+=Math.min(255,contrast);}
            if(contrast>125)strong++;
            if(delta(backdrop,backgrounds[(i+1)%16])<75)stableOutside++;
        }
        if(altered<11 || strong<6 || stableOutside<10)return 0f;
        int central=sample(pixels,w,h,cx,cy);
        int centreContrast=0;
        for(int i=0;i<16;i+=4) centreContrast+=delta(central,backgrounds[i]);
        if(centreContrast/4<95)return 0f;
        return altered*2f + strong + sum/130f + stableOutside*.25f;
    }

    /**
     * Find the *first* approximately circular ball within two ball radii of
     * the visible cue direction. Candidate search is constrained to a narrow
     * strip in front of the white ball, not the entire UI or rail graphics.
     */
    public static Contact detect(AimDetector.Result guide,int[] rgb,int w,int h){
        if(guide==null||rgb==null||rgb.length<w*h || w<100||h<100)return null;
        final float radius=Math.max(2.5f,Math.min(8.5f,(guide.right-guide.left)*.014f));
        final float diameter=2f*radius;
        float norm=(float)Math.hypot(guide.dx,guide.dy);
        if(norm<EPS)return null;
        float ux=guide.dx/norm,uy=guide.dy/norm;
        float boundary=Float.POSITIVE_INFINITY;
        if(ux>EPS)boundary=Math.min(boundary,(guide.right-guide.startX)/ux);
        else if(ux<-EPS)boundary=Math.min(boundary,(guide.left-guide.startX)/ux);
        if(uy>EPS)boundary=Math.min(boundary,(guide.bottom-guide.startY)/uy);
        else if(uy<-EPS)boundary=Math.min(boundary,(guide.top-guide.startY)/uy);
        if(!Float.isFinite(boundary)||boundary<diameter*3)return null;
        // Only inside active felt. The ROI runs the full route until the rail;
        // a game's native guideline can stop *before* the physical ball.
        final float maxT=Math.min(boundary-diameter,Math.max(guide.guideLength+diameter*2,diameter*4));
        float bestT=Float.POSITIVE_INFINITY,bestScore=Float.NEGATIVE_INFINITY;
        Contact best=null;
        // First contact wins; strong evidence of a farther ball cannot override
        // a nearer, valid ball along the very same line.
        // The game ends its incoming guide at the aiming-contact marker.
        // Restrict candidates to that endpoint region: otherwise a large
        // logo letter crossed by the guide can masquerade as a ball.
        float minT=Math.max(diameter*2.4f, guide.guideLength-diameter*2.4f);
        for(float t=minT;t<=maxT;t+=1.5f){
            float x=guide.startX+ux*t,y=guide.startY+uy*t;
            for(float offset=-diameter*.92f;offset<=diameter*.92f;offset+=1.5f){
                float cx=x-uy*offset,cy=y+ux*offset;
                // A collision marker beside the rail UI often looks circular.
                // Avoid predicting from such border decorations. Balls pinned
                // tightly to a rail remain a known limitation of this filter.
                if(cx<guide.left+radius*4.5f||cx>guide.right-radius*4.5f||
                        cy<guide.top+radius*4.5f||cy>guide.bottom-radius*4.5f)continue;
                // Early rejection. Real balls differ markedly from the felt;
                // local artwork and straight lines usually fail later circle test.
                int c=sample(rgb,w,h,cx,cy);
                int outside=sample(rgb,w,h,cx-uy*radius*2.2f,cy+ux*radius*2.2f);
                if(delta(c,outside)<42)continue;
                float quality=ballCircleScore(rgb,w,h,cx,cy,radius);
                if(quality<65f)continue;
                Contact result=predict(guide.startX,guide.startY,ux,uy,cx,cy,radius,quality);
                if(result==null)continue;
                // Native incoming guide normally terminates at its impact marker.
                // Prefer a round object close to that endpoint over a logo or
                // cursor outline encountered earlier along the same line.
                float endX=guide.startX+ux*guide.guideLength;
                float endY=guide.startY+uy*guide.guideLength;
                float closeness=(float)Math.hypot(cx-endX,cy-endY);
                float qualityAdjusted=quality - closeness*1.85f;
                if(qualityAdjusted>bestScore){
                    bestT=t;bestScore=qualityAdjusted;best=result;
                }
            }
        }
        return best;
    }

    /** Attach measured collision branches if dependable, else model the impact. */
    public static AimDetector.Result augment(AimDetector.Result raw,int[] pixels,int w,int h){
        if(raw==null)return null;
        Contact hit=detect(raw,pixels,w,h);
        if(hit==null){
            // The original detector sometimes mistakes logo strokes or the
            // cue's cursor for two outgoing paths. Without a detected object
            // ball, it is safer to display only the original incoming guide.
            return new AimDetector.Result(raw.left,raw.top,raw.right,raw.bottom,
                    raw.startX,raw.startY,raw.dx,raw.dy,raw.votes,
                    new AimDetector.Branch[0],Float.NaN,Float.NaN,raw.guideLength);
        }
        // The visual junction and predicted white-ball centre should agree.
        // Preserve the native outgoing rays ONLY if both originate from a
        // genuine collision and roughly agree with the geometric expectation.
        AimDetector.Branch[] model=hit.branches();
        boolean nativeReliable=raw.branches.length>=2 && raw.hasCollision
                && Math.hypot(raw.collisionX-hit.cueX,raw.collisionY-hit.cueY)<hit.diameter*2.0f;
        if(nativeReliable) {
            int aligned=0;
            for(AimDetector.Branch nativeBranch:raw.branches){
                float bestDot=-1;
                for(AimDetector.Branch simulated:model)
                    bestDot=Math.max(bestDot,nativeBranch.dx*simulated.dx+nativeBranch.dy*simulated.dy);
                if(bestDot>.67f)aligned++;
            }
            nativeReliable=aligned>=2;
        }
        return new AimDetector.Result(raw.left,raw.top,raw.right,raw.bottom,
                raw.startX,raw.startY,raw.dx,raw.dy,raw.votes,
                nativeReliable ? raw.branches : model,
                nativeReliable ? raw.collisionX : hit.cueX,
                nativeReliable ? raw.collisionY : hit.cueY, raw.guideLength,
                !nativeReliable,hit.diameter*.5f);
    }
}
