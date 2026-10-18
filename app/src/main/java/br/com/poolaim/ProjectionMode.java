package br.com.poolaim.overlay;

/** Rendering choice does not hide the whole overlay on a marker-only collision. */
public final class ProjectionMode {
    private ProjectionMode() {}
    public static boolean drawBranches(AimDetector.Result r) {
        return r != null && r.hasCollision && r.branches.length > 0;
    }
    public static boolean drawPrimary(AimDetector.Result r) {
        return r != null && !drawBranches(r);
    }
}
