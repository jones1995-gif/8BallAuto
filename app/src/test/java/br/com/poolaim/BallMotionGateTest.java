package br.com.poolaim.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class BallMotionGateTest {
    private static void circle(int[] p,int w,int h,int cx,int cy,int radius,int color) {
        for(int y=cy-radius;y<=cy+radius;y++) for(int x=cx-radius;x<=cx+radius;x++) {
            if(x>=0&&x<w&&y>=0&&y<h&&(x-cx)*(x-cx)+(y-cy)*(y-cy)<=radius*radius)
                p[y*w+x]=color;
        }
    }
    private static int[] felt(int w,int h) {
        int[] p=new int[w*h]; java.util.Arrays.fill(p,0xff174a72); return p;
    }
    private static AimDetector.Result geometry() {
        return new AimDetector.Result(10,10,110,70,30,40,1,0,20,
                new AimDetector.Branch[0],Float.NaN,Float.NaN,20);
    }

    @Test public void movingBallHidesProjectionUntilSeveralStillFrames() {
        int w=120,h=80; BallMotionGate gate=new BallMotionGate();
        int[] first=felt(w,h); circle(first,w,h,30,40,5,0xffffffff);
        assertFalse(gate.update(first,w,h,geometry()));
        int[] moved=felt(w,h); circle(moved,w,h,50,40,5,0xffffffff);
        assertTrue("a ball moving along the aiming direction should be detected",gate.update(moved,w,h,geometry()));
        for(int i=0;i<6;i++) assertTrue("projection remains hidden while waiting for a stable table",gate.update(moved,w,h,geometry()));
        assertFalse("projection may return only after the stable-frame window",gate.update(moved,w,h,geometry()));
    }
}
