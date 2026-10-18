package br.com.poolaim.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class AimDetectorTest {
    @Test public void findsDiagonalOnRedFelt(){
        int w=360,h=210; int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)p[y*w+x]=0xff101a24;
        for(int y=40;y<190;y++)for(int x=20;x<335;x++)p[y*w+x]=0xffa51e19;
        for(int x=65;x<255;x++){int y=70+(x-65)/3; if(y>0&&y<h){p[y*w+x]=0xffffffff;p[(y+1)*w+x]=0xffeeeeee;}}
        AimDetector.Result result=AimDetector.detect(p,w,h);
        assertNotNull("should find white guideline",result);
        assertTrue(result.right-result.left>240);
        assertTrue(result.bottom-result.top>110);
        assertTrue(result.votes>10);
    }
    @Test public void blankHasNoResult(){int[] p=new int[360*210];assertNull(AimDetector.detect(p,360,210));}
}
