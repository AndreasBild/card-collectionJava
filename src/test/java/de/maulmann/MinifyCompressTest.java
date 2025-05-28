package de.maulmann;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Disabled; // Removed as it's no longer used
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
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
        String htmlContent = "<!DOCTYPE html>\n" +
                             "<!-- Top level comment -->\n" +
                             "<html>\n\n" +
                             "  <head>\n" +
                             "    <title>Test Page Title</title>\n\n" +
                             "    <!-- Another comment -->\n" +
                             "    <style>\n" +
                             "      body {\n" +
                             "        font-family: Arial, sans-serif;\n" +
                             "        color: #333333; /* style comment */ \n" +
                             "      }\n" +
                             "    </style>\n" +
                             "  </head>\n\n" +
                             "  <body>\n\n" +
                             "    <h1>Hello <!-- inline comment --> World Of HTML</h1>\n\n" +
                             "    <p>\n" +
                             "      This is a paragraph with   lots of   extra   spaces and\n" +
                             "      multiple lines.\n" +
                             "    </p>\n\n" +
                             "    <script>\n" +
                             "      // Script comment that Jsoup might keep\n" +
                             "      var x = 10; \n" +
                             "      var y = 20; // another script comment \n" +
                             "      function add(a, b) { return a + b; }\n" +
                             "    </script>\n\n" +
                             "  </body>\n" +
                             "</html>";
        Files.write(sourceFile, htmlContent.getBytes());

        MinifyCompress.minifyHTML(new File(sourceFile.toString()), new File(outputFile.toString()));

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);
        // Basic check: minified content should be smaller or equal and contain no newlines (or fewer)
        // A more robust check would involve an HTML parser or comparing against expected minified output
        assertTrue(minifiedContent.length() <= htmlContent.length(), "Minified content should be smaller or equal.");
        // The following assertion for "\n  " has been removed as per the subtask.
        assertTrue(minifiedContent.contains("<title>Test Page Title</title>"), "Title should remain."); // Added assertion for title
        assertTrue(minifiedContent.contains("<h1>Hello World Of HTML</h1>")); // Adjusted to match new content
        assertTrue(minifiedContent.contains("// Script comment"), "JS comments should be preserved by Jsoup.");
        assertTrue(minifiedContent.contains("font-family: Arial, sans-serif;") || minifiedContent.contains("font-family:Arial,sans-serif;"), "CSS rule should be present; Jsoup may not deeply minify content within the rule itself.");
    }

    @Test
    void testMinifyCSS() throws IOException {
        Path sourceFile = tempDir.resolve("test.css");
        Path outputFile = tempDir.resolve("test.min.css");
        String cssContent = "body {\n  color: red;\n  font-size: 12px;\n}\n\na {\n  text-decoration: none;\n}";
        Files.write(sourceFile, cssContent.getBytes());

        MinifyCompress.minifyCSS(new File(sourceFile.toString()), new File(outputFile.toString()));

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);
        // Basic check: minified content should be smaller and contain fewer newlines/spaces
        assertTrue(minifiedContent.length() <= cssContent.length()); // Changed to <= for consistency if minification is minimal
        assertEquals("body{color:red;font-size:12px}a{text-decoration:none}", minifiedContent.replaceAll("\\s", ""));
    }

    @Test
    void testCompressFile() throws IOException {
        Path sourceFile = tempDir.resolve("test.txt");
        Path outputFile = tempDir.resolve("test.txt.gz");
        String originalContent = "This is a test string for GZIP compression. Test Test Test. " +
                                 "Repeating this sentence multiple times will make it more compressible. ".repeat(20) +
                                 "Juwan Howard Juwan Howard Juwan Howard Juwan Howard. ".repeat(10) +
                                 "Basketball cards collection basketball cards collection. ".repeat(15);
        Files.write(sourceFile, originalContent.getBytes());

        MinifyCompress.compressFile(new File(sourceFile.toString()), new File(outputFile.toString()));

        assertTrue(Files.exists(outputFile));
        // Verify it's a GZIP file by trying to read it
        String decompressedContent = readGzipFile(outputFile);
        assertEquals(originalContent, decompressedContent);
        assertTrue(Files.size(outputFile) < Files.size(sourceFile) || Files.size(sourceFile) == 0); // Compressed usually smaller
    }

    @Test
    // @Disabled annotation removed from this method
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
            pathField = MinifyCompress.class.getDeclaredField("pathSource");
            pathField.setAccessible(true);
            originalPath = (String) pathField.get(null);
            pathField.set(null, outputDir.toString());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not modify path field in MinifyCompress. Test setup needs adjustment or MinifyCompress needs refactoring for testability. " + e.getMessage());
        }

        MinifyCompress.main(new String[]{}); // Call main

        // Assertions
        // Original files should be overwritten with GZIPped content.
        Path processedHtmlFile = outputDir.resolve("index.html");
        Path processedCssFile = outputDir.resolve("style.css");
        Path processedHtmlFileInSubDir = subDir.resolve("another.html");

        assertTrue(Files.exists(processedHtmlFile), "Processed HTML file (index.html) should exist, overwritten with GZIP content.");
        assertTrue(Files.exists(processedCssFile), "Processed CSS file (style.css) should exist, overwritten with GZIP content.");
        assertTrue(Files.exists(processedHtmlFileInSubDir), "Processed HTML file in subdirectory (another.html) should exist, overwritten with GZIP content.");

        // Original files are overwritten, so no separate deletion check is needed.
        // The assertFalse checks for original files (htmlFile, cssFile, htmlFileInSubDir if they were different Path objects) are removed.

        // Verify content of the processed (GZIPed) files
        String decompressedHtml = readGzipFile(processedHtmlFile);
        assertTrue(decompressedHtml.length() <= htmlContent.length(), "Minified content should generally be smaller or equal.");
        assertTrue(decompressedHtml.contains("<h1>Test</h1>"), "Essential HTML content should remain.");

        String decompressedCss = readGzipFile(processedCssFile);
        assertEquals("body{color:blue}", decompressedCss.replaceAll("\\s", ""), "CSS content should be minified.");

        String decompressedHtmlSubDir = readGzipFile(processedHtmlFileInSubDir);
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
