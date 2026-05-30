package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HTMLMinifierTest {

    @TempDir
    Path tempDir;

    @Test
    void testMinifyHTMLToBytes() throws IOException {
        Path sourceFile = tempDir.resolve("test.html");
        String htmlContent = "<!DOCTYPE html><html><head><title>Test</title></head><body><h1>Hello</h1></body></html>";
        Files.write(sourceFile, htmlContent.getBytes());

        byte[] minifiedBytes = HTMLMinifier.minifyHTMLToBytes(new File(sourceFile.toString()));
        String minifiedContent = new String(minifiedBytes);

        assertTrue(minifiedContent.contains("<h1>Hello</h1>"));
    }
}
