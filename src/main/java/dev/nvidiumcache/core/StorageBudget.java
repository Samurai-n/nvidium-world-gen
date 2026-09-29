package dev.nvidiumcache.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Checked on the I/O worker, never on the render/server thread. No automatic deletion. */
public final class StorageBudget {
    private final Path root;
    private final Path worldSave;
    private record Limits(long limit, long reserve) {}
    private volatile Limits limits;
    private long checked, used;
    private volatile String reason = "checking";
    public StorageBudget(Path root, long limit, long reserve) {
        this(root, limit, reserve, null);
    }
    public StorageBudget(Path root, long limit, long reserve, Path worldSave) {
        this.root = root; this.limits = new Limits(limit, reserve); this.worldSave = worldSave;
    }
    public void update(long limit, long reserve) { limits = new Limits(limit, reserve); }
    public String reason() { return reason; }
    public boolean allowed() { return reason.isEmpty(); }
    public boolean check() {
        long now = System.nanoTime();
        if (now - checked < 1_000_000_000L) return allowed();
        checked = now;
        Limits current = limits;
        long limit = current.limit(), reserve = current.reserve();
        try {
            Files.createDirectories(root);
            used = 0;
            try (var paths = Files.walk(root)) {
                for (var path : paths.filter(Files::isRegularFile).toList()) {
                    String name = path.getFileName().toString();
                    if (name.equals("terrain.sqlite") || name.equals("terrain.sqlite-wal") || name.equals("terrain.sqlite-shm")) used += Files.size(path);
                }
            }
            // Headroom for bounded writes between checks. Limits are cooperative, not filesystem quotas.
            if (used + (64L << 20) >= limit) reason = "cache limit";
            else if (Files.getFileStore(root).getUsableSpace() <= reserve + (64L << 20)) reason = "free space reserve";
            else if (worldSave != null && Files.getFileStore(worldSave).getUsableSpace() <= reserve + (64L << 20)) reason = "world save free space reserve";
            else reason = "";
        } catch (IOException e) { reason = "storage check failed"; }
        return allowed();
    }
    public long usedBytes() { return used; }
}
