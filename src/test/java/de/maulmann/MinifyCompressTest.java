package de.maulmann;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class MinifyCompressTest {

    @TempDir
    Path tempDir;

    private Path outputDir; // Used by testMain to simulate the "output" directory

    @BeforeEach
    void setUp() throws IOException {
        // For testMain, we simulate the structure MinifyCompress.main() expects
        outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
    }

    @AfterEach
    void tearDown() {
        // TempDir will handle cleanup of files created directly under it.
        // OutputDir is inside tempDir, so it's also handled.
    }

    private String readGzipFile(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             InputStreamReader isr = new InputStreamReader(gis);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }

    @Test
    void testMinifyHTML() throws IOException {
        Path sourceFile = tempDir.resolve("test.html");
        Path outputFile = tempDir.resolve("test.min.html");
        String htmlContent = "<!DOCTYPE html>\n<html>\n  <head>\n    <title>Test</title>\n  </head>\n  <body>\n    <h1>Hello World</h1>\n  </body>\n</html>";
        Files.write(sourceFile, htmlContent.getBytes());

        MinifyCompress.minifyHTML(sourceFile.toString(), outputFile.toString());

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);
        // Basic check: minified content should be smaller and contain no newlines (or fewer)
        // A more robust check would involve an HTML parser or comparing against expected minified output
        assertTrue(minifiedContent.length() < htmlContent.length());
        assertFalse(minifiedContent.contains("\n  ")); // Check removal of some indentation/newlines
        assertTrue(minifiedContent.contains("<h1>Hello World</h1>"));
    }

    @Test
    void testMinifyCSS() throws IOException {
        Path sourceFile = tempDir.resolve("test.css");
        Path outputFile = tempDir.resolve("test.min.css");
        String cssContent = "body {\n  color: red;\n  font-size: 12px;\n}\n\na {\n  text-decoration: none;\n}";
        Files.write(sourceFile, cssContent.getBytes());

        MinifyCompress.minifyCSS(sourceFile.toString(), outputFile.toString());

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);
        // Basic check: minified content should be smaller and contain fewer newlines/spaces
        assertTrue(minifiedContent.length() < cssContent.length());
        assertEquals("body{color:red;font-size:12px}a{text-decoration:none}", minifiedContent.replaceAll("\\s", ""));
    }

    @Test
    void testCompressFile() throws IOException {
        Path sourceFile = tempDir.resolve("test.txt");
        Path outputFile = tempDir.resolve("test.txt.gz");
        String originalContent = "This is a test file for GZIP compression. It has some content.";
        Files.write(sourceFile, originalContent.getBytes());

        MinifyCompress.compressFile(sourceFile.toString(), outputFile.toString());

        assertTrue(Files.exists(outputFile));
        // Verify it's a GZIP file by trying to read it
        String decompressedContent = readGzipFile(outputFile);
        assertEquals(originalContent, decompressedContent);
        assertTrue(Files.size(outputFile) < Files.size(sourceFile) || Files.size(sourceFile) == 0); // Compressed usually smaller
    }

    @Test
    void testMain() throws IOException {
        // Create dummy HTML and CSS files in the temporary output/ directory
        Path htmlFile = outputDir.resolve("index.html");
        String htmlContent = "<!DOCTYPE html><html><body><h1>Test</h1></body></html>";
        Files.write(htmlFile, htmlContent.getBytes());

        Path cssFile = outputDir.resolve("style.css");
        String cssContent = "body { color: blue; }";
        Files.write(cssFile, cssContent.getBytes());

        Path subDir = Files.createDirectory(outputDir.resolve("subdir"));
        Path htmlFileInSubDir = subDir.resolve("another.html");
        String htmlContentSubDir = "<!DOCTYPE html><html><body><h2>Test 2</h2></body></html>";
        Files.write(htmlFileInSubDir, htmlContentSubDir.getBytes());


        // Call MinifyCompress.main()
        // MinifyCompress.main uses a hardcoded path "../card-CollectionJava/output"
        // We need to temporarily change this path or mock its behavior.
        // For simplicity, let's assume MinifyCompress is modified to take a path or uses a configurable path.
        // If not, this test would require more complex setup (e.g., PowerMock for static fields/methods, or refactoring MinifyCompress).

        // Let's try to run main and see. If it fails due to path, we'll note it.
        // It's expected to operate on files within the `outputDir` we created if it were configurable.
        // We will save the original path and set it to our temp path.
        java.lang.reflect.Field pathField = null;
        String originalPath = null;
        try {
            pathField = MinifyCompress.class.getDeclaredField("path");
            pathField.setAccessible(true);
            originalPath = (String) pathField.get(null);
            pathField.set(null, outputDir.toString());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not modify path field in MinifyCompress. Test setup needs adjustment or MinifyCompress needs refactoring for testability. " + e.getMessage());
        }

        MinifyCompress.main(new String[]{}); // Call main

        // Assertions
        // Original files should be replaced by .gz versions
        Path minHtmlFile = outputDir.resolve("index.html.gz"); // Original is replaced
        Path minCssFile = outputDir.resolve("style.css.gz");   // Original is replaced
        Path minHtmlFileInSubDir = subDir.resolve("another.html.gz");

        assertTrue(Files.exists(minHtmlFile), "Minified and compressed HTML file (index.html.gz) should exist.");
        assertTrue(Files.exists(minCssFile), "Minified and compressed CSS file (style.css.gz) should exist.");
        assertTrue(Files.exists(minHtmlFileInSubDir), "Minified and compressed HTML file in subdirectory (another.html.gz) should exist.");

        assertFalse(Files.exists(htmlFile), "Original HTML file (index.html) should have been deleted.");
        assertFalse(Files.exists(cssFile), "Original CSS file (style.css) should have been deleted.");
        assertFalse(Files.exists(htmlFileInSubDir), "Original HTML file in subdirectory (another.html) should have been deleted.");

        // Verify content of compressed files
        String decompressedHtml = readGzipFile(minHtmlFile);
        assertTrue(decompressedHtml.length() <= htmlContent.length()); // Minified before compression
        assertTrue(decompressedHtml.contains("<h1>Test</h1>"));

        String decompressedCss = readGzipFile(minCssFile);
        assertEquals("body{color:blue}", decompressedCss.replaceAll("\\s", ""));

        String decompressedHtmlSubDir = readGzipFile(minHtmlFileInSubDir);
        assertTrue(decompressedHtmlSubDir.length() <= htmlContentSubDir.length());
        assertTrue(decompressedHtmlSubDir.contains("<h2>Test 2</h2>"));

        // Restore the original path in MinifyCompress
        if (pathField != null && originalPath != null) {
            try {
                pathField.set(null, originalPath);
            } catch (IllegalAccessException e) {
                // log or handle if necessary
            }
        }
    }
}
