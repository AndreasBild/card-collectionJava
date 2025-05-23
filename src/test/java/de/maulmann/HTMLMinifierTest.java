package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HTMLMinifierTest {

    @TempDir
    Path tempDir;

    @Test
    void testMinifyHTML() throws IOException {
        Path sourceFile = tempDir.resolve("test.html");
        Path outputFile = tempDir.resolve("test.min.html");
        String htmlContent = "<!DOCTYPE html>\n" +
                             "<html>\n" +
                             "  <head>\n" +
                             "    <title>Test Page</title>\n" +
                             "    <!-- This is a comment -->\n" +
                             "    <style>\n" +
                             "      body {\n" +
                             "        font-family: Arial, sans-serif;\n" +
                             "      }\n" +
                             "    </style>\n" +
                             "  </head>\n" +
                             "  <body>\n" +
                             "    <h1>Hello <!-- another comment --> World</h1>\n" +
                             "    <p>This is a paragraph with   extra   spaces.</p>\n" +
                             "    <script>\n" +
                             "      // Script comment\n" +
                             "      var x = 10;\n" +
                             "    </script>\n" +
                             "  </body>\n" +
                             "</html>";
        Files.write(sourceFile, htmlContent.getBytes());

        HTMLMinifier.minifyHTML(new File(sourceFile.toString()), new File(outputFile.toString()));

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);

        // Basic checks for minification
        assertTrue(minifiedContent.length() < htmlContent.length(), "Minified content should be smaller.");
        assertFalse(minifiedContent.contains("<!-- This is a comment -->"), "HTML comments should be removed.");
        assertFalse(minifiedContent.contains("\n  "), "Excess whitespace and newlines should be reduced.");
        assertTrue(minifiedContent.contains("<title>Test Page</title>"), "Title should remain.");
        assertTrue(minifiedContent.contains("<h1>Hello World</h1>"), "Content should largely remain, comments within tags might be handled differently by minifiers.");
        assertTrue(minifiedContent.contains("<p>This is a paragraph with extra spaces.</p>"), "Spaces within tags are often collapsed.");
        // Check if style and script tags are present, their content minification is more complex
        assertTrue(minifiedContent.contains("<style>"), "Style tag should be present.");
        assertTrue(minifiedContent.contains("font-family:Arial,sans-serif"), "CSS within style tag should be minified.");
        assertTrue(minifiedContent.contains("<script>"), "Script tag should be present.");
        assertFalse(minifiedContent.contains("// Script comment"), "JS comments should be removed if minifier handles script tags.");
        assertTrue(minifiedContent.contains("var x=10"), "JS whitespace should be reduced.");
    }
}
