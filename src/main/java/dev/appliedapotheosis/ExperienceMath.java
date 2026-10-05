package dev.appliedapotheosis;

/** Vanilla level thresholds. Fluid amounts are converted to POINTS before using these helpers. */
public final class ExperienceMath {
    private ExperienceMath() {}
    public static long totalAtLevel(int level) {
        long n = Math.max(0, level);
        if (n <= 16) return n * n + 6 * n;
        if (n <= 31) return (5 * n * n - 81 * n + 720) / 2;
        return (9 * n * n - 325 * n + 4440) / 2;
    }
    public static int level(long points) {
        int low = 0, high = 1000000;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (totalAtLevel(mid) <= points) low = mid; else high = mid - 1;
        }
        return low;
    }
    public static float progress(long points) {
        int level = level(points);
        long base = totalAtLevel(level);
        return Math.clamp((float) (points - base) / (totalAtLevel(level + 1) - base), 0, 1);
    }
}
