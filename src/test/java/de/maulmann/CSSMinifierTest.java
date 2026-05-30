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
    void testMinifyCSSToBytes() throws IOException {
        Path sourceFile = tempDir.resolve("test.css");
        String cssContent = "body {\n  color: red;\n  font-size: 12px;\n}\n\na {\n  text-decoration: none;\n}";
        Files.write(sourceFile, cssContent.getBytes());

        byte[] minifiedBytes = CSSMinifier.minifyCSSToBytes(new File(sourceFile.toString()));
        String minifiedContent = new String(minifiedBytes);

        // Normalize whitespace for comparison, as different minifiers might have slightly different outputs
        // regarding spacing, but the core declarations should be the same.
        String expectedMinified = "body{color:red;font-size:12px}a{text-decoration:none}";
        assertEquals(expectedMinified, minifiedContent.replaceAll("\\s+", "").replaceAll(";}", "}"));
        assertTrue(minifiedContent.length() < cssContent.length());
    }
}
