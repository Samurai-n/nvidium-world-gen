package dev.nvidiumcache.core;

import java.util.LinkedHashMap;

/** Main-thread metadata only. Repeated edits debounce, but continuous edits cannot starve saving. */
public final class DirtyTracker {
    public record Entry(long firstTick, long lastTick) {}
    private final LinkedHashMap<ChunkKey, Entry> dirty = new LinkedHashMap<>();
    private final int capacity;
    public DirtyTracker(int capacity) { this.capacity = capacity; }
    public boolean mark(ChunkKey key, long tick) {
        Entry entry = dirty.get(key);
        if (entry == null && dirty.size() >= capacity) return false;
        dirty.put(key, new Entry(entry == null ? tick : entry.firstTick, tick));
        return true;
    }
    public ChunkKey due(long tick, long debounce, long maxAge) {
        for (var e : dirty.entrySet()) {
            if (tick - e.getValue().lastTick >= debounce || tick - e.getValue().firstTick >= maxAge) return e.getKey();
        }
        return null;
    }
    public void remove(ChunkKey key) { dirty.remove(key); }
    public int size() { return dirty.size(); }
}
