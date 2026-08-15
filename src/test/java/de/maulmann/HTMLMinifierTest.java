package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HTMLMinifierTest {

    @TempDir
    Path tempDir;

    @Test
    void testMinifyHTMLToBytes() throws IOException {
        Path sourceFile = tempDir.resolve("test.html");
        String htmlContent = "<!DOCTYPE html>\n<html>\n<head>\n  <title>Test</title>\n</head>\n<body>\n  <h1>Hello</h1>\n</body>\n</html>";
        Files.writeString(sourceFile, htmlContent);

        byte[] minifiedBytes = HTMLMinifier.minifyHTMLToBytes(new File(sourceFile.toString()));
        String minifiedContent = new String(minifiedBytes);

        assertTrue(minifiedContent.contains("<h1>Hello</h1>"));
        assertFalse(minifiedContent.contains("\n  <h1>"));
    }

    @Test
    void testStripsHtmlComments() {
        String input = "<div><!-- This is a comment --><span>Content</span><!-- Another comment --></div>";
        String minified = HTMLMinifier.minifyHTML(input);

        assertEquals("<div><span>Content</span></div>", minified);
        assertFalse(minified.contains("comment"));
    }

    @Test
    void testPreservesScriptContent() {
        String input = "<div>\n  <script>\n    const x = 10;\n    const y = 20;\n  </script>\n</div>";
        String minified = HTMLMinifier.minifyHTML(input);

        assertTrue(minified.contains("const x = 10;"));
        assertTrue(minified.contains("const y = 20;"));
        assertTrue(minified.startsWith("<div><script>"));
    }

    @Test
    void testCollapseInterTagWhitespace() {
        String input = "<ul>\n   <li>Item 1</li>   \n   <li>Item 2</li>\n</ul>";
        String minified = HTMLMinifier.minifyHTML(input);

        assertEquals("<ul><li>Item 1</li><li>Item 2</li></ul>", minified);
    }
}
