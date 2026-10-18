package br.com.poolaim.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectionModeTest {
    @Test public void missingNativeBranchesMustFallbackToPrimary() {
        AimDetector.Result r=new AimDetector.Result(10,10,110,70,30,40,1,0,20,
            new AimDetector.Branch[0],70,40,20);
        assertTrue(r.hasCollision);
        assertTrue(ProjectionMode.drawPrimary(r));
        assertFalse(ProjectionMode.drawBranches(r));
    }
    @Test public void realBranchesTakePriority() {
        AimDetector.Branch b=new AimDetector.Branch(70,40,0,1,15);
        AimDetector.Result r=new AimDetector.Result(10,10,110,70,30,40,1,0,20,
            new AimDetector.Branch[]{b},70,40,20);
        assertFalse(ProjectionMode.drawPrimary(r));
        assertTrue(ProjectionMode.drawBranches(r));
    }
    @Test public void noDetectorMeansNoRenderedLines() {
        assertFalse(ProjectionMode.drawPrimary(null));
    }
}
