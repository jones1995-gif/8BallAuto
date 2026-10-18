package br.com.poolaim.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class AimStabilizerTest {
    private AimDetector.Result line(float startX, float dx, float dy) {
        return new AimDetector.Result(10, 10, 340, 190, startX, 100, dx, dy, 100);
    }
    @Test public void smoothsNearbyDetections() {
        AimStabilizer stable = new AimStabilizer();
        assertNotNull(stable.update(line(100, 1, 0), 100));
        AimDetector.Result result = stable.update(line(106, 1, 0), 400);
        assertTrue(result.startX > 100 && result.startX < 106);
    }
    @Test public void rejectsSingleSpuriousFlip() {
        AimStabilizer stable = new AimStabilizer();
        stable.update(line(100, 1, 0), 100);
        AimDetector.Result result = stable.update(line(200, -1, 0), 400);
        assertTrue(result.dx > 0);
    }
    @Test public void acceptsConsistentDirectionChange() {
        AimStabilizer stable = new AimStabilizer();
        stable.update(line(100, 1, 0), 100);
        stable.update(line(200, -1, 0), 400);
        AimDetector.Result result = stable.update(line(200, -1, 0), 700);
        assertTrue(result.dx < 0);
    }
    @Test public void dropsStaleDetection() {
        AimStabilizer stable = new AimStabilizer();
        stable.update(line(100, 1, 0), 100);
        assertNotNull(stable.update(null, 400));
        assertNull(stable.update(null, 1900));
    }
}
