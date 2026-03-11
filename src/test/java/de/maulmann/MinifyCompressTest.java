package de.maulmann;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinifyCompressTest {

    @TempDir
    Path tempDir;

    @Test
    void testMinifyHTML() throws IOException {
        File inputFile = tempDir.resolve("test.html").toFile();
        File outputFile = tempDir.resolve("test.min.html").toFile();
        Files.writeString(inputFile.toPath(), "<html>  <body> <h1> Test </h1> </body> </html>");

        MinifyCompress.minifyHTML(inputFile, outputFile);

        assertTrue(outputFile.exists());
    }
}
