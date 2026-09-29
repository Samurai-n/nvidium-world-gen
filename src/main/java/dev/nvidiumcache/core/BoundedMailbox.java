package dev.nvidiumcache.core;

import java.util.LinkedHashMap;
import java.util.function.ToLongFunction;

/** Latest value per key. A refused replacement leaves the old value intact. */
public final class BoundedMailbox<K, V> {
    public record Item<K, V>(K key, V value) {}
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>();
    private final int maxEntries;
    private final long maxBytes;
    private final ToLongFunction<V> weight;
    private long bytes;

    public BoundedMailbox(int maxEntries, long maxBytes, ToLongFunction<V> weight) {
        if (maxEntries < 1 || maxBytes < 1) throw new IllegalArgumentException("Invalid limits");
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.weight = weight;
    }

    public synchronized boolean offer(K key, V value) {
        long size = weight.applyAsLong(value);
        if (size < 0) throw new IllegalArgumentException("Negative weight");
        V previous = entries.get(key);
        long oldSize = previous == null ? 0 : weight.applyAsLong(previous);
        if ((previous == null && entries.size() >= maxEntries) || size > maxBytes - bytes + oldSize) return false;
        entries.put(key, value);
        bytes += size - oldSize;
        return true;
    }

    public synchronized Item<K, V> poll() {
        var iterator = entries.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        var entry = iterator.next();
        Item<K, V> result = new Item<>(entry.getKey(), entry.getValue());
        bytes -= weight.applyAsLong(entry.getValue());
        iterator.remove();
        return result;
    }

    public synchronized void remove(K key) {
        V value = entries.remove(key);
        if (value != null) bytes -= weight.applyAsLong(value);
    }
    public synchronized int size() { return entries.size(); }
    public synchronized boolean containsKey(K key) { return entries.containsKey(key); }
    public synchronized long bytes() { return bytes; }
    public synchronized void clear() { entries.clear(); bytes = 0; }
}
