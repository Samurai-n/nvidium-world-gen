package dev.nvidiumcache.core;

import java.util.ArrayList;
import java.util.List;

/** Nearest configurable tiles first, clipped to the configured square. Constant cursor memory. */
public final class SpatialGroupCursor {
    private final int side;
    private final int centerX, centerZ, radius;
    private final SpatialCursor tiles;
    private int emitted;
    public SpatialGroupCursor(int x, int z, int radius, int side) {
        if (side != 1 && side != 2 && side != 4 && side != 8 && side != 16) throw new IllegalArgumentException("Invalid group side");
        this.side = side;
        if (radius < 0 || radius > 128) throw new IllegalArgumentException("Invalid radius");
        this.centerX = x; this.centerZ = z; this.radius = radius;
        int tx = Math.floorDiv(x, side), tz = Math.floorDiv(z, side);
        int reach = Math.max(Math.max(tx - Math.floorDiv(x - radius, side), Math.floorDiv(x + radius, side) - tx),
            Math.max(tz - Math.floorDiv(z - radius, side), Math.floorDiv(z + radius, side) - tz));
        tiles = new SpatialCursor(tx, tz, reach);
    }
    public List<ChunkKey> nextGroup() {
        while (true) {
            var tile = tiles.next();
            var group = new ArrayList<ChunkKey>(side * side);
            for (int z = 0; z < side; z++) for (int x = 0; x < side; x++) {
                int cx = tile.x() * side + x, cz = tile.z() * side + z;
                if (Math.abs((long) cx - centerX) <= radius && Math.abs((long) cz - centerZ) <= radius)
                    group.add(new ChunkKey(cx, cz));
            }
            if (!group.isEmpty()) return group;
        }
    }
    public List<ChunkKey> nextInPass() {
        if (emitted >= (2 * radius + 1) * (2 * radius + 1)) return null;
        var group = nextGroup();
        emitted += group.size();
        return group;
    }
}
