# Alpha 13 — independent World Gen presets

World Cache and World Gen now have separate Stable, Fast and Ultra presets in both Sodium/Reese's options and Mod Menu. Cache loading speed changes only cache restoration. World Gen presets change request interval and concurrent generation requests without restarting the current generation task.

The adaptive scheduler now respects the chosen minimum interval on a healthy server and waits longer when server ticks slow down. Previously it always requested work every tick while healthy, so changing to Stable could leave World Gen at the same pace as Ultra. Stable uses 8 ticks / 2 concurrent requests; Fast 2 ticks / 8 requests; Ultra 1 tick / 16 requests. Existing custom settings remain unchanged until a preset is selected.

Build and unit tests passed with JDK 25. The JAR was installed in Fabulously Optimized after confirming Minecraft was closed; alpha 12 was retained as `.jar.disabled`, and the configuration was not changed. Visual performance still needs an in-game test; this release changes pacing controls, not the underlying vanilla chunk generation engine. The comparison video was inspected through six sampled frames, so it does not provide a reliable chunks-per-second benchmark.
