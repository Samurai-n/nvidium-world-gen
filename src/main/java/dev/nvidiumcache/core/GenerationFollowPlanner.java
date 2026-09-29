package dev.nvidiumcache.core;

/** Recenter after a meaningful move; teleport destinations take priority. */
public final class GenerationFollowPlanner {
    private final int radius, centerX, centerZ;
    private long outsideSince = -1;

    public GenerationFollowPlanner(int centerX, int centerZ, int radius) {
        if (radius < 1 || radius > 128) throw new IllegalArgumentException("Invalid generation radius");
        this.centerX = centerX; this.centerZ = centerZ; this.radius = radius;
    }
    public boolean shouldRecenter(int x, int z, long tick) {
        long distance = Math.max(Math.abs((long) x - centerX), Math.abs((long) z - centerZ));
        if (distance < Math.max(2, radius / 2)) { outsideSince = -1; return false; }
        if (outsideSince < 0) outsideSince = tick;
        // Let the visual cache load at a teleport destination before starting new generation.
        if (distance >= Math.max(12, radius * 2)) return tick - outsideSince >= 10;
        return tick - outsideSince >= 20;
    }
}
