package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TimestampTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    void testTimestampEntryRecordParsing() {
        TimestampTracker.TimestampEntry entry = TimestampTracker.TimestampEntry.parse("abcd1234hash:01.01.2026 12:00:00");
        assertNotNull(entry);
        assertEquals("abcd1234hash", entry.hash());
        assertEquals("01.01.2026 12:00:00", entry.timestamp());
        assertEquals("abcd1234hash:01.01.2026 12:00:00", entry.toRaw());

        assertNull(TimestampTracker.TimestampEntry.parse(null));
        assertNull(TimestampTracker.TimestampEntry.parse("invalidformat"));
    }

    @Test
    void testGetStableTimestampAndSave() throws Exception {
        Path storeFile = tempDir.resolve("timestamps.properties");
        TimestampTracker tracker = new TimestampTracker(storeFile.toString());

        String identifier = "cards/1994-95/test-card.html";
        String content1 = "<html><body>Test Content [[STABLE_TIME]]</body></html>";

        String time1 = tracker.getStableTimestamp(identifier, content1);
        assertNotNull(time1);

        // Second call with same content should return identical timestamp
        String time2 = tracker.getStableTimestamp(identifier, content1);
        assertEquals(time1, time2, "Timestamp should remain stable for identical content.");

        // Call save to write to disk
        tracker.save();
        assertTrue(Files.exists(storeFile), "Timestamp tracker store file must exist after save.");

        // Load new tracker instance from disk
        TimestampTracker newTracker = new TimestampTracker(storeFile.toString());
        String time3 = newTracker.getStableTimestamp(identifier, content1);
        assertEquals(time1, time3, "Timestamp loaded from store file must match original.");
    }

    @Test
    void testGetIsoDate() throws Exception {
        Path storeFile = tempDir.resolve("timestamps.properties");
        TimestampTracker tracker = new TimestampTracker(storeFile.toString());

        String identifier = "index.html";
        String content = "<html><body>Hello [[STABLE_TIME]]</body></html>";
        tracker.getStableTimestamp(identifier, content);

        String isoDate = tracker.getIsoDate(identifier);
        assertNotNull(isoDate);
        assertTrue(isoDate.matches("\\d{4}-\\d{2}-\\d{2}"), "ISO date must be in YYYY-MM-DD format, got: " + isoDate);
    }
}
