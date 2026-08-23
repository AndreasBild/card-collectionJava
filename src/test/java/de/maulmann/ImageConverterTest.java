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
}
