package de.maulmann;

import com.aayushatharva.brotli4j.decoder.BrotliInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrotliCompressorTest {

    @TempDir
    Path tempDir;

    private String readBrotliFile(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             BrotliInputStream bis = new BrotliInputStream(fis);
             InputStreamReader isr = new InputStreamReader(bis);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }

    @Test
    void testCompressFile() throws IOException {
        Path sourceFile = tempDir.resolve("testCompress.txt");
        Path outputFile = tempDir.resolve("testCompress.txt.br");
        String originalContent = "This is a test string for Brotli compression. " +
                                 "It needs to be long enough to demonstrate effective compression. " +
                                 "Repeating some content helps with compression ratios. " +
                                 "Test test test test test.";
        Files.write(sourceFile, originalContent.getBytes());

        // Test with default quality
        BrotliCompressor.compressFile(sourceFile.toFile(), outputFile.toFile());

        assertTrue(Files.exists(outputFile), "Compressed file should exist.");
        String decompressedContent = readBrotliFile(outputFile);
        assertEquals(originalContent, decompressedContent, "Decompressed content should match original.");
        assertTrue(Files.size(outputFile) < Files.size(sourceFile), "Compressed file should be smaller than original for this content.");

        Files.delete(outputFile);

        // Test with specific quality (BEST_QUALITY)
        Path outputFileBest = tempDir.resolve("testCompressBest.txt.br");
        BrotliCompressor.compressFile(sourceFile.toFile(), outputFileBest.toFile(), BrotliCompressor.BEST_QUALITY);
        assertTrue(Files.exists(outputFileBest), "Compressed file (best quality) should exist.");
        String decompressedContentBest = readBrotliFile(outputFileBest);
        assertEquals(originalContent, decompressedContentBest, "Decompressed content (best quality) should match original.");
        assertTrue(Files.size(outputFileBest) < Files.size(sourceFile), "Compressed file (best quality) should be smaller.");
    }

    @Test
    void testCompressBytes() throws IOException {
        String originalContent = "Brotli byte compression test content.";
        byte[] inputData = originalContent.getBytes();

        byte[] compressedData = BrotliCompressor.compressBytes(inputData, BrotliCompressor.DEFAULT_QUALITY);

        assertTrue(compressedData.length > 0, "Compressed data should not be empty.");

        try (BrotliInputStream bis = new BrotliInputStream(new ByteArrayInputStream(compressedData));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = bis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            assertEquals(originalContent, baos.toString(), "Decompressed bytes should match original content.");
        }
    }
}
