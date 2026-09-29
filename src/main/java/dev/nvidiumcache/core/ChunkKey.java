package dev.nvidiumcache.core;

public record ChunkKey(int x, int z) {
    public long packed() { return (x & 0xffffffffL) | ((long) z << 32); }
    public static ChunkKey unpack(long key) { return new ChunkKey((int) key, (int) (key >>> 32)); }
    public long distanceSquared(int cx, int cz) {
        long dx = (long) x - cx, dz = (long) z - cz;
        return dx * dx + dz * dz;
    }
}
