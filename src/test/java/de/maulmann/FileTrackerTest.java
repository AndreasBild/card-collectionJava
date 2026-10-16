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

    @Test
    @DisplayName("Unchanged size+mtime is treated as unchanged and survives save/reload")
    void testFastPathAfterReload() throws IOException {
        Path propsFile = tempDir.resolve("fast.properties");
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "content-one");

        FileTracker tracker = new FileTracker(propsFile.toString());
        assertTrue(tracker.hasChanged(file), "unknown file counts as changed");
        tracker.updateHash(file);
        assertFalse(tracker.hasChanged(file));
        tracker.save();

        assertFalse(new FileTracker(propsFile.toString()).hasChanged(file));
        assertFalse(Files.exists(tempDir.resolve("fast.properties.tmp")), "no temp file left behind");
    }

    @Test
    @DisplayName("Changed content is detected even when the size stays the same (mtime differs)")
    void testSameSizeContentChangeDetected() throws IOException {
        Path propsFile = tempDir.resolve("same-size.properties");
        Path file = tempDir.resolve("b.txt");
        Files.writeString(file, "aaaa");
        FileTracker tracker = new FileTracker(propsFile.toString());
        tracker.updateHash(file);

        Files.writeString(file, "bbbb");
        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        assertTrue(tracker.hasChanged(file));
    }

    @Test
    @DisplayName("Touched but identical content is not reported as changed")
    void testTouchWithoutContentChange() throws IOException {
        Path propsFile = tempDir.resolve("touch.properties");
        Path file = tempDir.resolve("c.txt");
        Files.writeString(file, "same");
        FileTracker tracker = new FileTracker(propsFile.toString());
        tracker.updateHash(file);

        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        assertFalse(tracker.hasChanged(file));
    }
}
