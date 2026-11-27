package dev.buffy.ytlyrics;

/**
 * Signal for the instrumental visualiser.
 *
 * <p>No audio is captured from the player, so the bars are synthesised: a smooth per-track wobble
 * plus a beat envelope at a fixed tempo, with the low columns taller than the high ones. It moves
 * with the song rather than reacting to it, which is all a lyrics mod can honestly do.
 */
public final class Spectrum {
    public static final int BARS = 28;

    /** Assumed tempo for the pulse: 125 BPM. */
    private static final double BEAT_MS = 480.0;

    private final float[] level = new float[BARS];
    private final float[] phase = new float[BARS];
    private long seed = Long.MIN_VALUE;

    /** Gives each song its own pattern, so switching tracks visibly changes the bars. */
    public void track(String key) {
        long next = key.hashCode() * 0x9E3779B97F4A7C15L;
        if (next == seed) return;

        seed = next;
        long state = next;
        for (int i = 0; i < BARS; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            phase[i] = ((state >>> 40) & 0xFFFF) / 65535f * (float) (Math.PI * 2);
            level[i] = 0f;
        }
    }

    public float level(int bar) {
        return level[Math.max(0, Math.min(BARS - 1, bar))];
    }

    /** Eases every column toward the height the current playback position implies. */
    public void update(long positionMs, float dt) {
        double seconds = positionMs / 1000.0;
        double beat = (positionMs % (long) BEAT_MS) / BEAT_MS;
        float pulse = (float) Math.exp(-beat * 4.5);

        for (int i = 0; i < BARS; i++) {
            double wobble = 0.5 + 0.5 * Math.sin(seconds * (0.9 + i * 0.11) + phase[i]);
            double shimmer = 0.12 * (i / (double) BARS) * Math.sin(seconds * 7.3 + phase[i] * 2.0);
            double bass = 1.0 - 0.55 * (i / (double) BARS);
            double goal = 0.14 + bass * (0.28 + 0.40 * wobble + 0.30 * pulse) + shimmer;

            float target = (float) Math.max(0.05, Math.min(1.0, goal));
            float speed = target > level[i] ? 20f : 7f;
            level[i] += (target - level[i]) * Math.min(1f, dt * speed);
        }
    }
}
