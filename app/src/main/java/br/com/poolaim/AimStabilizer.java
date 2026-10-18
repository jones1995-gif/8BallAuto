package br.com.poolaim.overlay;

/**
 * Rejects single-frame jumps and direction flips. This improves visual stability,
 * but cannot make an incorrect candidate line into a correct prediction.
 * No Android dependencies: can be unit-tested with javac.
 */
public final class AimStabilizer {
    private AimDetector.Result last;
    private AimDetector.Result challenger;
    private int challengerCount;
    private long lastSeenMs;
    private final int maxLostMs;

    public AimStabilizer() { this(350); }
    public AimStabilizer(int maxLostMs) { this.maxLostMs = maxLostMs; }

    public synchronized AimDetector.Result update(AimDetector.Result now, long timestampMs) {
        if (now == null) {
            if (last != null && timestampMs - lastSeenMs < maxLostMs) return last;
            last = null; challenger = null; challengerCount = 0;
            return null;
        }
        if (last == null || timestampMs - lastSeenMs > maxLostMs) {
            last = now; lastSeenMs = timestampMs;
            challenger = null; challengerCount = 0;
            return now;
        }
        if (sameLine(last, now)) {
            // Smooth only modest changes. Updating continuously lets the line track cue rotation.
            last = mix(last, now, .68f);
            lastSeenMs = timestampMs;
            challenger = null; challengerCount = 0;
            return last;
        }
        // A sudden different line must be seen consistently for more than one image.
        if (challenger != null && sameLine(challenger, now)) {
            challengerCount++;
            challenger = mix(challenger, now, .68f);
        } else {
            challenger = now; challengerCount = 1;
        }
        if (challengerCount >= 2) {
            last = challenger;
            lastSeenMs = timestampMs;
            challenger = null; challengerCount = 0;
        }
        return last;
    }

    private static boolean sameLine(AimDetector.Result a, AimDetector.Result b) {
        float dot = a.dx * b.dx + a.dy * b.dy;
        float dist = (float) Math.hypot(a.startX - b.startX, a.startY - b.startY);
        float maxDistance = Math.max(18f, (a.right - a.left) * .12f);
        return dot >= .72f && dist < maxDistance;
    }

    private static AimDetector.Result mix(AimDetector.Result a, AimDetector.Result b, float weight) {
        float x = a.dx * (1 - weight) + b.dx * weight;
        float y = a.dy * (1 - weight) + b.dy * weight;
        float mag = (float) Math.hypot(x, y);
        if (mag < .001f) { x = b.dx; y = b.dy; }
        else { x /= mag; y /= mag; }
        return new AimDetector.Result(
                lerp(a.left, b.left, weight), lerp(a.top, b.top, weight),
                lerp(a.right, b.right, weight), lerp(a.bottom, b.bottom, weight),
                lerp(a.startX, b.startX, weight), lerp(a.startY, b.startY, weight),
                x, y, b.votes);
    }

    private static float lerp(float a, float b, float w) { return a + (b - a) * w; }
}
