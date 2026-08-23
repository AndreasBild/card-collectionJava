package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageConverter Tests")
class ImageConverterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("getBaseName should extract clean file base names without extensions")
    void testGetBaseName() {
        assertEquals("card-front", ImageConverter.getBaseName("card-front.jpg"));
        assertEquals("card-back", ImageConverter.getBaseName("card-back.png"));
        assertEquals("juwan.howard.pmg", ImageConverter.getBaseName("juwan.howard.pmg.avif"));
        assertEquals("noextension", ImageConverter.getBaseName("noextension"));
    }

    @Test
    @DisplayName("processImages should handle empty directory gracefully")
    void testProcessEmptyDirectory() throws Exception {
        Path emptySource = tempDir.resolve("empty-images");
        Path outDir = tempDir.resolve("out-images");
        Files.createDirectories(emptySource);
        Files.createDirectories(outDir);

        assertDoesNotThrow(() -> ImageConverter.processImages(emptySource, outDir));
    }

    @Test
    @DisplayName("cleanOrphanedAvifImages should delete AVIF files when source scan was removed")
    void testCleanOrphanedAvifImages() throws Exception {
        Path sourceDir = tempDir.resolve("src-images");
        Path avifOutDir = tempDir.resolve("out-images");
        Files.createDirectories(sourceDir.resolve("1994-95"));
        Files.createDirectories(avifOutDir.resolve("1994-95"));

        // Valid source image and corresponding AVIF files
        Files.writeString(sourceDir.resolve("1994-95/valid-front.jpg"), "fake-image");
        Files.writeString(avifOutDir.resolve("1994-95/valid-front.avif"), "fake-avif");
        Files.writeString(avifOutDir.resolve("1994-95/valid-front-400w.avif"), "fake-avif");

        // Orphaned AVIF file (source deleted)
        Files.writeString(avifOutDir.resolve("1994-95/orphaned-front.avif"), "fake-orphan-avif");
        Files.writeString(avifOutDir.resolve("1994-95/orphaned-front-200w.avif"), "fake-orphan-avif");

        int cleaned = ImageConverter.cleanOrphanedAvifImages(sourceDir, avifOutDir);

        assertEquals(2, cleaned, "Should clean exactly 2 orphaned AVIF files");
        assertTrue(Files.exists(avifOutDir.resolve("1994-95/valid-front.avif")));
        assertTrue(Files.exists(avifOutDir.resolve("1994-95/valid-front-400w.avif")));
        assertFalse(Files.exists(avifOutDir.resolve("1994-95/orphaned-front.avif")));
        assertFalse(Files.exists(avifOutDir.resolve("1994-95/orphaned-front-200w.avif")));
    }
}
