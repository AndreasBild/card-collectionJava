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
    void testMinifyHTML() throws IOException {
        Path sourceFile = tempDir.resolve("test.html");
        Path outputFile = tempDir.resolve("test.min.html");
        String htmlContent = "<!DOCTYPE html><html><head><title>Test</title></head><body><h1>Hello</h1></body></html>";
        Files.write(sourceFile, htmlContent.getBytes());

        HTMLMinifier.minifyHTML(new File(sourceFile.toString()), new File(outputFile.toString()));

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);

        assertTrue(minifiedContent.contains("<h1>Hello</h1>"));
    }
}
