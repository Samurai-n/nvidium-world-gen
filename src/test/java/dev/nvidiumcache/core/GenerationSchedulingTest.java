package dev.nvidiumcache.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationSchedulingTest {
    @Test void nearFiftyMillisecondTicksKeepOneRequestPerTick() {
        var pacing = new GenerationPacer();
        long time = 10_000_000_000L;
        pacing.observe(time);
        for (int i = 0; i < 30; i++) pacing.observe(time += 51_000_000L);
        assertEquals(1, pacing.interval(1));
        for (int i = 0; i < 20; i++) assertTrue(pacing.ready(1, false));
    }
    @Test void adaptiveModeRespectsChosenMinimumAndSlowsUnderLoad() {
        var automatic = new GenerationPacer();
        var manual = new GenerationPacer();
        int fast = 0, slow = 0;
        for (int i = 0; i < 100; i++) {
            if (automatic.ready(5, true)) fast++;
            if (manual.ready(5, false)) slow++;
        }
        assertEquals(20, fast); assertEquals(20, slow);
        long time = 10_000_000_000L;
        for (int i = 0; i < 30; i++) automatic.observe(time += 100_000_000L);
        assertTrue(automatic.adaptiveInterval(5) > 5);
    }
    @Test void changingPresetPaceTakesEffectWithoutResettingTheScheduler() {
        var pacing = new GenerationPacer();
        int stable = 0, ultra = 0;
        for (int i = 0; i < 80; i++) if (pacing.ready(8, true)) stable++;
        for (int i = 0; i < 80; i++) if (pacing.ready(1, true)) ultra++;
        assertEquals(10, stable);
        assertEquals(80, ultra);
    }
    @Test void sustainedSlowServerReducesRequestsWithoutStarvingThem() {
        var pacing = new GenerationPacer();
        long time = 10_000_000_000L;
        pacing.observe(time);
        for (int i = 0; i < 20; i++) pacing.observe(time += 100_000_000L);
        assertTrue(pacing.interval(5) > 5);
        int ready = 0;
        for (int i = 0; i < 100; i++) if (pacing.ready(5)) ready++;
        assertTrue(ready > 0 && ready < 20);
    }
    @Test void pauseGapDoesNotLeaveServerInFalseOverload() {
        var pacing = new GenerationPacer();
        pacing.observe(10_000_000_000L);
        pacing.observe(10_100_000_000L);
        assertTrue(pacing.interval(5) > 5);
        assertTrue(pacing.noRecentTick(12_100_000_000L));
        pacing.observe(40_100_000_000L);
        assertEquals(5, pacing.interval(5));
        assertFalse(pacing.noRecentTick(40_100_000_000L));
    }
    @Test void smallMovementsDoNotRestartButTravelAndTeleportDo() {
        var planner = new GenerationFollowPlanner(0, 0, 16);
        assertFalse(planner.shouldRecenter(7, 0, 100));
        assertFalse(planner.shouldRecenter(8, 0, 100));
        assertFalse(planner.shouldRecenter(8, 0, 119));
        assertTrue(planner.shouldRecenter(8, 0, 120));
        assertFalse(planner.shouldRecenter(0, 0, 121));
        assertFalse(planner.shouldRecenter(100, 100, 200));
        assertFalse(planner.shouldRecenter(100, 100, 209));
        assertTrue(planner.shouldRecenter(100, 100, 210));
    }
}
