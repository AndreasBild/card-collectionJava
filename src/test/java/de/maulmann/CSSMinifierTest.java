package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CSSMinifierTest {

    @TempDir
    Path tempDir;

    @Test
    void testMinifyCSS() throws IOException {
        Path sourceFile = tempDir.resolve("test.css");
        Path outputFile = tempDir.resolve("test.min.css");
        String cssContent = "body {\n  color: red;\n  font-size: 12px;\n}\n\na {\n  text-decoration: none;\n}";
        Files.write(sourceFile, cssContent.getBytes());

        CSSMinifier.minifyCSS(new File(sourceFile.toString()), new File(outputFile.toString()));

        assertTrue(Files.exists(outputFile));
        String minifiedContent = Files.readString(outputFile);
        // Normalize whitespace for comparison, as different minifiers might have slightly different outputs
        // regarding spacing, but the core declarations should be the same.
        String expectedMinified = "body{color:red;font-size:12px}a{text-decoration:none}";
        assertEquals(expectedMinified, minifiedContent.replaceAll("\\s+", "").replaceAll(";}", "}"));
        assertTrue(minifiedContent.length() < cssContent.length());
    }
}
