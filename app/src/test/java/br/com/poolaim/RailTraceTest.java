package br.com.poolaim.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
public class RailTraceTest{
  @Test public void horizontalShotReflectsBackFromRealBallCentrePlane(){
    List<RailTrace.Segment> p=RailTrace.trace(100,100,1,0,20,40,335,190,4,2);
    assertEquals(2,p.size());
    assertEquals(331f,p.get(0).x2,.1f);
    assertEquals(24f,p.get(1).x2,.1f);
    assertFalse(p.get(0).pocket);
  }
  @Test public void verticalShotCanEnterMiddlePocket(){
    List<RailTrace.Segment> p=RailTrace.trace(177.5f,120,0,-1,20,40,335,190,4,3);
    assertEquals(1,p.size());assertTrue(p.get(0).pocket);
  }
  @Test public void cornerPocketStopsInsteadOfReflecting(){
    List<RailTrace.Segment> p=RailTrace.trace(60,75,-1,-1,20,40,335,190,4,3);
    assertEquals(1,p.size());assertTrue(p.get(0).pocket);
  }
  @Test public void invalidOrZeroDirectionNoSegments(){
    assertEquals(0,RailTrace.trace(100,100,0,0,20,40,335,190,4,3).size());
    assertEquals(0,RailTrace.trace(100,100,1,0,20,40,335,190,4,0).size());
  }
  @Test public void diagonalReflectionPreservesAngleComponents(){
    List<RailTrace.Segment> p=RailTrace.trace(150,110,1,1,20,40,335,190,4,2);
    assertTrue(p.size()>=1);
    assertTrue(p.get(0).x2>=p.get(0).x1);
    assertTrue(p.get(0).y2>=p.get(0).y1);
  }
}
