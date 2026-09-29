package dev.nvidiumcache.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingTest {
    @Test void finiteScansStopAndDoNotRestartWhileIdle() {
        for (int side : new int[]{1, 2, 4, 8, 16}) {
            var cursor = new SpatialGroupCursor(-17, 7, 32, side);
            var seen = new java.util.HashSet<ChunkKey>();
            java.util.List<ChunkKey> group;
            while ((group = cursor.nextInPass()) != null) for (var key : group) assertTrue(seen.add(key));
            assertEquals(65 * 65, seen.size());
            for (int i = 0; i < 100; i++) assertNull(cursor.nextInPass());
        }
        var cursor = new SpatialCursor(0, 0, 1);
        for (int i = 0; i < 9; i++) assertNotNull(cursor.nextInPass());
        for (int i = 0; i < 100; i++) assertNull(cursor.nextInPass());
    }
    @Test void burstEditsCoalesceButContinuousChangesCannotStarve() {
        var dirty = new DirtyTracker(2);
        var key = new ChunkKey(1, 1);
        for (long tick = 0; tick < 1000; tick++) assertTrue(dirty.mark(key, tick));
        assertEquals(1, dirty.size());
        assertNull(dirty.due(999, 20, 2000));
        assertEquals(key, dirty.due(999, 20, 200));
        assertEquals(key, dirty.due(1019, 20, 2000));
    }
    @Test void mailboxAccountsReplacementBytesAndRefusesOverloadWithoutDiscardingPrevious() {
        var mailbox = new BoundedMailbox<String, byte[]>(2, 10, a -> a.length);
        assertTrue(mailbox.offer("a", new byte[8]));
        assertFalse(mailbox.offer("b", new byte[3]));
        assertTrue(mailbox.offer("a", new byte[2]));
        assertTrue(mailbox.offer("b", new byte[8]));
        assertFalse(mailbox.offer("a", new byte[3]));
        assertEquals(10, mailbox.bytes());
        assertEquals(2, mailbox.poll().value().length);
        assertEquals(8, mailbox.bytes());
        mailbox.clear(); assertEquals(0, mailbox.bytes()); assertNull(mailbox.poll());
    }
    @Test void limitsBoundMetadataEvenForSmallPayloads() {
        var mailbox = new BoundedMailbox<Integer, byte[]>(4, 100, a -> a.length);
        for (int i = 0; i < 4; i++) assertTrue(mailbox.offer(i, new byte[1]));
        assertFalse(mailbox.offer(5, new byte[1]));
        var dirty = new DirtyTracker(1);
        assertTrue(dirty.mark(new ChunkKey(0, 0), 0));
        assertFalse(dirty.mark(new ChunkKey(1, 0), 0));
        assertTrue(dirty.mark(new ChunkKey(0, 0), 1));
    }
    @Test void signedCoordinatesRoundTrip() {
        for (int x : new int[]{0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE})
            for (int z : new int[]{0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                var key = new ChunkKey(x, z); assertEquals(key, ChunkKey.unpack(key.packed()));
            }
    }
    @Test void spatialCursorCoversWindowInRingsWithConstantState() {
        var cursor = new SpatialCursor(-6, 12, 32);
        var seen = new java.util.HashSet<ChunkKey>();
        int previousRing = 0;
        for (int i = 0; i < 65 * 65; i++) {
            var key = cursor.next();
            int ring = Math.max(Math.abs(key.x() + 6), Math.abs(key.z() - 12));
            assertTrue(ring >= previousRing && ring <= 32);
            assertTrue(seen.add(key)); previousRing = ring;
        }
        assertEquals(new ChunkKey(-6, 12), cursor.next());
    }
    @Test void groupsCoverClippedWindowsWithoutDuplicatesAcrossAllSizes() {
        for (int side : new int[]{1, 2, 4, 8, 16}) for (int radius : new int[]{0, 3, 32, 128}) {
            var cursor = new SpatialGroupCursor(-17, 7, radius, side);
            var seen = new java.util.HashSet<ChunkKey>();
            int target = (2 * radius + 1) * (2 * radius + 1);
            while (seen.size() < target) {
                var group = cursor.nextGroup();
                assertTrue(group.size() <= side * side);
                var first = group.getFirst();
                for (var key : group) {
                    assertTrue(Math.abs(key.x() + 17) <= radius && Math.abs(key.z() - 7) <= radius);
                    assertEquals(Math.floorDiv(first.x(), side), Math.floorDiv(key.x(), side));
                    assertEquals(Math.floorDiv(first.z(), side), Math.floorDiv(key.z(), side));
                    assertTrue(seen.add(key));
                }
            }
            assertTrue(seen.containsAll(cursor.nextGroup()));
        }
        assertThrows(IllegalArgumentException.class, () -> new SpatialGroupCursor(0, 0, 1, 3));
    }
}
