package de.maulmann;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FileGeneratorTest {

    @TempDir
    Path tempDir;

    private Path contentDir;
    private Path outputDir;
    private String originalSourcePath;
    private String originalOutputPath;

    @BeforeEach
    void setUp() throws Exception {
        contentDir = Files.createDirectories(tempDir.resolve("content"));
        outputDir = Files.createDirectories(tempDir.resolve("output"));

        originalSourcePath = FileGenerator.pathSource;
        originalOutputPath = FileGenerator.pathOutput;

        FileGenerator.pathSource = contentDir.toString() + "/";
        FileGenerator.pathOutput = outputDir.toString() + "/";
    }

    @AfterEach
    void tearDown() {
        FileGenerator.pathSource = originalSourcePath;
        FileGenerator.pathOutput = originalOutputPath;
    }

    private void createDummyHtmlFile(String fileName, String... lines) throws IOException {
        Files.write(contentDir.resolve(fileName), Arrays.asList(lines));
    }

    @Test
    void testMain() throws Exception {
        createDummyHtmlFile("1994-95.html", "<tr><td>Data</td></tr>");

        assertDoesNotThrow(() -> FileGenerator.main(new String[]{}));

        Path generatedPath = outputDir.resolve("Juwan-Howard-Collection.html");
        assertTrue(Files.exists(generatedPath), "Output file was not generated.");

        String content = Files.readString(generatedPath);

        assertTrue(content.contains("Juwan Howard"), "Should contain Juwan Howard");
        assertTrue(content.contains("1994-95"), "Should contain season 1994-95");
        assertTrue(content.contains("Data"), "Should contain Data from source file");

        // Ensure proper HTML structure
        assertTrue(content.contains("<html"), "Should have opening html tag");
        assertTrue(content.contains("</html>"), "Should have closing html tag");
        assertTrue(content.contains("</body>"), "Should have closing body tag");
        assertTrue(content.contains("</table>"), "Should have closing table tag");
        assertTrue(content.contains("</h2>"), "Should have closing h2 tag");

        // Ensure table is NOT inside h2
        int h2CloseIndex = content.indexOf("</h2>");
        int tableOpenIndex = content.indexOf("<table");
        assertTrue(h2CloseIndex < tableOpenIndex, "Table should be AFTER h2 close tag, not inside it.");

        Path indexPath = outputDir.resolve("index.html");
        assertTrue(Files.exists(indexPath), "Index file was not generated.");

        Path errorPath = outputDir.resolve("error.html");
        assertTrue(Files.exists(errorPath), "Error file was not generated.");
        String errorContent = Files.readString(errorPath);
        assertTrue(errorContent.contains("Error Page"), "Error page should contain the title");
        assertTrue(errorContent.contains("index.html"), "Error page should contain a link back to home");
    }
}
