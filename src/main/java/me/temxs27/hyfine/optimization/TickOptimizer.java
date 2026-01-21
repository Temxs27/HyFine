package me.temxs27.hyfine.optimization;

public class TickOptimizer {
    private static double smoothedTps = 20.0;
    private static final double ALPHA = 0.15;

    public static void update(long duration) {
        double instantTps = (duration <= 0) ? 20.0 : Math.min(20.0, 1000.0 / duration);

        smoothedTps = (instantTps * ALPHA) + (smoothedTps * (1.0 - ALPHA));
    }

    public static boolean shouldRun() {
        return smoothedTps < 19.7;
    }

    public static double getTPS() {
        return Math.round(smoothedTps * 100.0) / 100.0;
    }
}