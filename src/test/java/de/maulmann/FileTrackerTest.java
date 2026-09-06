package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FileTracker Cross-Platform Tests")
class FileTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("normalizePath should convert Windows backslashes to forward slashes")
    void testNormalizePath() {
        Path winPath = Path.of("output", "images", "1994", "card.avif");
        String normalized = FileTracker.normalizePath(winPath);
        assertFalse(normalized.contains("\\"), "Normalized path should not contain backslashes");
        assertTrue(normalized.contains("output/images/1994/card.avif") || normalized.endsWith("output/images/1994/card.avif"));
    }

    @Test
    @DisplayName("FileTracker should store and retrieve hashes with cross-platform key format")
    void testCrossPlatformKeyRetrieval() throws IOException {
        Path propsFile = tempDir.resolve("test-hashes.properties");
        FileTracker tracker = new FileTracker(propsFile.toString());

        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Antigravity Cross-Platform Test");

        String hash = tracker.getHash(testFile);
        assertNotNull(hash);

        // Store hash
        tracker.updateHash(testFile, hash);
        assertEquals(hash, tracker.getStoredHash(testFile));
        assertFalse(tracker.hasChanged(testFile));

        // Verify save and reload
        tracker.save();

        FileTracker reloadedTracker = new FileTracker(propsFile.toString());
        assertEquals(hash, reloadedTracker.getStoredHash(testFile));
        assertFalse(reloadedTracker.hasChanged(testFile));
    }
}
