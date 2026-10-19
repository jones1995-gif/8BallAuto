package br.com.poolaim.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class CollisionPhysicsTest {
    private static int[] table(int w,int h){
        int[] p=new int[w*h];java.util.Arrays.fill(p,0xff9e352a);
        return p;
    }
    private static void ball(int[] p,int w,int h,int cx,int cy,int radius,int color){
        for(int y=cy-radius;y<=cy+radius;y++)for(int x=cx-radius;x<=cx+radius;x++)
            if(x>=0&&x<w&&y>=0&&y<h&&(x-cx)*(x-cx)+(y-cy)*(y-cy)<=radius*radius)
                p[y*w+x]=color;
    }
    private static AimDetector.Result guide(int x,int y,float dx,float dy,int nativeLength){
        return new AimDetector.Result(20,40,335,190,x,y,dx,dy,25,
                new AimDetector.Branch[0],Float.NaN,Float.NaN,nativeLength);
    }
    @Test public void headOnCollisionStopsWhiteAndSendsTargetForward(){
        CollisionPhysics.Contact c=CollisionPhysics.predict(70,100,1,0,170,100,4,80);
        assertNotNull(c);
        assertEquals(162f,c.cueX,.001f);
        assertEquals(1f,c.objectDx,.0001f);
        assertEquals(0f,c.cueSpeedFraction,.001f);
        assertEquals(1,c.branches().length);
    }
    @Test public void glancingCollisionSplitsIntoTwoNormalizedPaths(){
        CollisionPhysics.Contact c=CollisionPhysics.predict(70,100,1,0,170,105,4,80);
        assertNotNull(c);
        assertEquals(2,c.branches().length);
        assertTrue(c.objectDy>0);
        assertTrue(c.cueDy<0);
        assertEquals(0f,c.objectDx*c.cueDx+c.objectDy*c.cueDy,.002f);
    }
    @Test public void tooFarFromAimHasNoHit(){
        assertNull(CollisionPhysics.predict(70,100,1,0,170,120,4,90));
        assertNull(CollisionPhysics.predict(70,100,1,0,50,100,4,90));
    }
    @Test public void darkTargetBallAlongEndOfGuideIsFound(){
        int[] p=table(360,210);ball(p,360,210,181,101,5,0xff14151b);
        CollisionPhysics.Contact c=CollisionPhysics.detect(guide(70,100,1,0,108),p,360,210);
        assertNotNull(c);assertEquals(181f,c.objectX,4f);
    }
    @Test public void noObjectDoesNotInventImpact(){
        int[] p=table(360,210);
        assertNull(CollisionPhysics.detect(guide(70,100,1,0,108),p,360,210));
        AimDetector.Result noHit=CollisionPhysics.augment(guide(70,100,1,0,108),p,360,210);
        assertFalse(noHit.hasCollision);assertEquals(0,noHit.branches.length);
    }
    @Test public void wrongPositionAndOffTableObjectsAreIgnored(){
        int[] p=table(360,210);
        ball(p,360,210,181,134,5,0xff101010);
        ball(p,360,210,16,25,5,0xff101010);
        assertNull(CollisionPhysics.detect(guide(70,100,1,0,108),p,360,210));
    }
    @Test public void ignoresThinStripeAcrossAim(){
        int[] p=table(360,210);
        for(int y=65;y<175;y++)p[y*360+178]=0xffffffff;
        assertNull(CollisionPhysics.detect(guide(70,100,1,0,108),p,360,210));
    }
    @Test public void colorTargetCanBeDetected(){
        int[] p=table(360,210);ball(p,360,210,181,103,5,0xfff6d640);
        assertNotNull(CollisionPhysics.detect(guide(70,100,1,0,108),p,360,210));
    }
    @Test public void conservativeModelIsMarkedAsPredicted(){
        int[] p=table(360,210);ball(p,360,210,181,103,5,0xff143393);
        AimDetector.Result r=CollisionPhysics.augment(guide(70,100,1,0,108),p,360,210);
        assertTrue(r.predictedCollision);assertTrue(r.hasCollision);assertEquals(2,r.branches.length);
    }
}
