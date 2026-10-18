package br.com.poolaim.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class AimDetectorTest {
    private static void setWhite(int[] p, int w, int h, int x, int y, int radius) {
        for (int yy=y-radius;yy<=y+radius;yy++) {
            for (int xx=x-radius;xx<=x+radius;xx++) {
                if (xx>=0 && xx<w && yy>=0 && yy<h) p[yy*w+xx]=0xffffffff;
            }
        }
    }

    private static int[] redTable(int w, int h) {
        int[] p=new int[w*h];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) p[y*w+x]=0xff101a24;
        for (int y=40;y<190;y++) for (int x=20;x<335;x++) p[y*w+x]=0xffa51e19;
        return p;
    }

    @Test public void followsLineFromCueBallInsteadOfOtherTableLines() {
        int w=360,h=210;
        int[] p=redTable(w,h);
        // A thin native guideline starts at a filled cue-ball blob.
        for (int x=70;x<255;x++) {
            int y=72+(x-70)/3;
            if (y>=0 && y<h) {
                p[y*w+x]=0xffffffff;
                if (y+1<h) p[(y+1)*w+x]=0xfff0f0f0;
            }
        }
        setWhite(p,w,h,70,72,2);
        // A longer unrelated line elsewhere should not win over the cue-ball line.
        for (int x=45;x<230;x++) {
            int y=165-(x-45)/2;
            if (y>=0 && y<h) p[y*w+x]=0xffffffff;
        }
        AimDetector.Result result=AimDetector.detect(p,w,h);
        assertNotNull("should find guide line radiating from cue ball",result);
        assertTrue(result.right-result.left>240);
        assertTrue(result.bottom-result.top>110);
        assertTrue(Math.abs(result.startX-70)<5);
        assertTrue(Math.abs(result.startY-72)<5);
        assertTrue("guide direction should point down and right",result.dx>0 && result.dy>0);
        assertTrue(result.votes>10);
    }

    @Test public void blankHasNoResult() {
        int[] p=new int[360*210];
        assertNull(AimDetector.detect(p,360,210));
    }

    @Test public void cueBallWithoutVisibleGuideDoesNotCreateFalseLine() {
        int w=360,h=210;
        int[] p=redTable(w,h);
        setWhite(p,w,h,170,110,3);
        assertNull(AimDetector.detect(p,w,h));
    }
}
