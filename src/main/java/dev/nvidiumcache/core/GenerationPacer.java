package dev.nvidiumcache.core;

/** Slows requests under server load without permanently starving generation. */
public final class GenerationPacer {
    private long previousNanos;
    private volatile double tickMillis = 50;
    private long ticks;

    public void observe(long nowNanos) {
        if (previousNanos != 0) {
            double elapsed = (nowNanos - previousNanos) / 1e6;
            // Integrated servers stop ticking in pause menus. Do not treat a long pause as lag.
            tickMillis = elapsed > 1000 || elapsed <= 0 ? 50 : tickMillis * 0.9 + elapsed * 0.1;
        }
        previousNanos = nowNanos;
    }
    public int interval(int requestedTicks) {
        if (requestedTicks < 1 || requestedTicks > 200) throw new IllegalArgumentException("Invalid generation interval");
        // Rounding up turns a healthy 50.1 ms tick into two ticks when requestedTicks=1.
        return Math.max(requestedTicks, Math.min(1000, (int) Math.round(requestedTicks * Math.max(1, tickMillis / 50.0))));
    }
    public boolean ready(int requestedTicks) { return ++ticks % interval(requestedTicks) == 0; }
    public int adaptiveInterval(int requestedTicks) { return interval(requestedTicks); }
    public boolean ready(int requestedTicks, boolean adaptive) {
        return ++ticks % (adaptive ? adaptiveInterval(requestedTicks) : requestedTicks) == 0;
    }
    public double tickMillis() { return tickMillis; }
    public boolean noRecentTick(long nowNanos) { return previousNanos != 0 && nowNanos - previousNanos > 1_000_000_000L; }
}
