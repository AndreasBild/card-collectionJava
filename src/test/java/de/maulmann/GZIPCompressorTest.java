package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.Deflater;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GZIPCompressorTest {

    @TempDir
    Path tempDir;

    private String readGzipFile(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             InputStreamReader isr = new InputStreamReader(gis);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
                // Add newline characters back if they were part of the original content and are significant
                // For this test, assuming simple line-by-line content.
            }
        }
        return content.toString();
    }

    @Test
    void testCompressFile() throws IOException {
        Path sourceFile = tempDir.resolve("testCompress.txt");
        Path outputFile = tempDir.resolve("testCompress.txt.gz");
        String originalContent = "This is a test string for GZIP compression. " +
                                 "It needs to be long enough to demonstrate effective compression. " +
                                 "Repeating some content helps with compression ratios. " +
                                 "Test test test test test.";
        Files.write(sourceFile, originalContent.getBytes());

        // Test with default compression level
        GZIPCompressor.compressFile(sourceFile.toString(), outputFile.toString());

        assertTrue(Files.exists(outputFile), "Compressed file should exist.");
        String decompressedContent = readGzipFile(outputFile);
        assertEquals(originalContent, decompressedContent, "Decompressed content should match original.");
        assertTrue(Files.size(outputFile) < Files.size(sourceFile), "Compressed file should be smaller than original for this content.");

        // Clean up for next test case if needed (or use different file names)
        Files.delete(outputFile);

        // Test with specific compression level (e.g., BEST_COMPRESSION)
        Path outputFileBestCompression = tempDir.resolve("testCompress.txt.gz.best");
        GZIPCompressor.compressFile(sourceFile.toString(), outputFileBestCompression.toString(), Deflater.BEST_COMPRESSION);
        assertTrue(Files.exists(outputFileBestCompression), "Compressed file (best compression) should exist.");
        String decompressedContentBest = readGzipFile(outputFileBestCompression);
        assertEquals(originalContent, decompressedContentBest, "Decompressed content (best compression) should match original.");
        assertTrue(Files.size(outputFileBestCompression) < Files.size(sourceFile), "Compressed file (best compression) should be smaller.");

        // Optionally, compare sizes if content is suitable for noticeable difference
        // long sizeDefault = Files.size(outputFile); // Need to re-compress with default if deleted
        // For simplicity, we're primarily testing functionality and correctness here.
    }
}
