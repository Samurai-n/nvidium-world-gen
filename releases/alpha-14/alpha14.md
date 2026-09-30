# Alpha 14 — release preparation and test coverage

This alpha adds the MIT license and project links to the mod metadata, and clarifies the mod description. It also updates automated UI tests for the separate World Cache and World Gen presets and keeps the disposable GameTest run focused on the relevant behavior.

The generation engine is unchanged from alpha 13. A one-off disposable-world comparison generated 1,089 new chunks in 38.1 s without C2ME and 15.3 s with C2ME 0.4.2-alpha.0.52+26.2 using 16 concurrent requests. These are single samples, not a general performance claim; C2ME remains optional and external.

The offline Gradle build and full client GameTest suite passed on JDK 25. Tests covered both configuration screens, independent presets, live cache changes, nine generated chunks, and teleport follow/pause/stop. Real-world play and a larger cache stress test remain release gates. This is not yet a public platform release.
