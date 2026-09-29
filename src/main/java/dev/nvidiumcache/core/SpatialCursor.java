package dev.nvidiumcache.core;

/** O(1) state, nearest rings first. Opening a world never enumerates its disk cache. */
public final class SpatialCursor {
    private final int centerX, centerZ, radius;
    private int x, z, dx, dz = -1, emitted;
    public SpatialCursor(int centerX, int centerZ, int radius) {
        if (radius < 0 || radius > 128) throw new IllegalArgumentException("Invalid radius");
        this.centerX = centerX; this.centerZ = centerZ; this.radius = radius;
    }
    public ChunkKey next() {
        if (emitted == (2 * radius + 1) * (2 * radius + 1)) { x = z = dx = emitted = 0; dz = -1; }
        ChunkKey result = new ChunkKey(centerX + x, centerZ + z);
        if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) { int old = dx; dx = -dz; dz = old; }
        x += dx; z += dz; emitted++;
        return result;
    }
    /** Finite traversal for consumers which should sleep once their area is covered. */
    public ChunkKey nextInPass() {
        return emitted == (2 * radius + 1) * (2 * radius + 1) ? null : next();
    }
}
