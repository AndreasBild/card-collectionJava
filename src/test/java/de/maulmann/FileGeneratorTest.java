package de.maulmann;

// import org.junit.jupiter.api.Disabled; // Removed as it's no longer used
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

// @Disabled annotation removed from the class level
class FileGeneratorTest {

    @TempDir
    Path tempDir;

    private Path contentDir;
    private Path outputDir;
    private Path defaultSourcePath;
    private Path defaultOutputPath;

    @BeforeEach
    void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
        // Create temporary directories for content and output
        contentDir = Files.createDirectories(tempDir.resolve("content"));
        outputDir = Files.createDirectories(tempDir.resolve("output"));

        // Store original paths
        Field pathSourceField = FileGenerator.class.getDeclaredField("pathSource");
        pathSourceField.setAccessible(true);
        defaultSourcePath = Path.of((String) pathSourceField.get(null));

        Field pathOutputField = FileGenerator.class.getDeclaredField("pathOutput");
        pathOutputField.setAccessible(true);
        defaultOutputPath = Path.of((String) pathOutputField.get(null));

        // Override static paths in FileGenerator to use temp directories
        pathSourceField.set(null, contentDir.toString() + "/");
        pathOutputField.set(null, outputDir.toString() + "/");


        // Set system property for user.dir to use tempDir as the base for relative paths in FileGenerator
        // This is needed because getFileNamesFromDirectory uses new File("./") which resolves against user.dir
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        // Clean up: Reset user.dir and clear the static field nameOfInputFile in FileGenerator
        System.clearProperty("user.dir");
        try {
            Field nameOfInputFileField = FileGenerator.class.getDeclaredField("nameOfInputFile");
            nameOfInputFileField.setAccessible(true);
            nameOfInputFileField.set(null, new String[]{}); // Set to empty array
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Log or handle the exception if necessary
            e.printStackTrace();
        }

        // Restore original paths
        Field pathSourceField = FileGenerator.class.getDeclaredField("pathSource");
        pathSourceField.setAccessible(true);
        pathSourceField.set(null, defaultSourcePath.toString() + "/");

        Field pathOutputField = FileGenerator.class.getDeclaredField("pathOutput");
        pathOutputField.setAccessible(true);
        pathOutputField.set(null, defaultOutputPath.toString() + "/");

    }

    private void createDummyHtmlFile(String fileName, String... lines) throws IOException {
        Files.write(contentDir.resolve(fileName), Arrays.asList(lines));
    }

    @Test
    void testCreateAnchorList() throws Exception {
        // Create dummy HTML files
        createDummyHtmlFile("file1.html", "content1");
        createDummyHtmlFile("file2.html", "content2");
        createDummyHtmlFile("another.html", "content3");

        // Make getFileNamesFromDirectory accessible and invoke it to populate nameOfInputFile
        Method getFileNamesFromDirectoryMethod = FileGenerator.class.getDeclaredMethod("getFileNamesFromDirectory");
        getFileNamesFromDirectoryMethod.setAccessible(true);
        String[] fileNames = (String[]) getFileNamesFromDirectoryMethod.invoke(null);

        // Ensure nameOfInputFile is populated
        Field nameOfInputFileField = FileGenerator.class.getDeclaredField("nameOfInputFile");
        nameOfInputFileField.setAccessible(true);
        nameOfInputFileField.set(null, fileNames); // Set the static field

        // Access the private static method createAnchorList
        Method createAnchorListMethod = FileGenerator.class.getDeclaredMethod("createAnchorList");
        createAnchorListMethod.setAccessible(true);
        String anchorList = (String) createAnchorListMethod.invoke(null);

        // Assertions
        assertNotNull(anchorList);
        assertTrue(anchorList.contains("<a class='modern-button' href=#file1 title='Juwan Howard Trading Cards from Season: file1'>file1</a>"));
        assertTrue(anchorList.contains("<a class='modern-button' href=#file2 title='Juwan Howard Trading Cards from Season: file2'>file2</a>"));
        assertTrue(anchorList.contains("<a class='modern-button' href=#another title='Juwan Howard Trading Cards from Season: another'>another</a>"));
        assertEquals(3, Arrays.stream(fileNames).filter(Objects::nonNull).count());
    }


    @Test
    void testAppendFileContent() throws Exception {
        // Create a dummy source HTML file
        String sourceFileName = "source";
        Path sourceFile = contentDir.resolve(sourceFileName + ".html");
        // Content with <tr> elements to be counted
        Files.write(sourceFile, Arrays.asList("<table>", "<tr><td>Data 1</td></tr>", "<tr><td>Data 2</td></tr>", "</table>"));

        // Create a dummy target file (index.html in outputDir, as per FileGenerator logic)
        // FileGenerator.generatedFileLocation uses pathOutput + "index.html"
        Path targetFile = outputDir.resolve("index.html");
        // Ensure targetFile is created by createTargetFile method or manually,
        // as appendFileContent appends to it.
        // For this test, we'll let appendFileContent be called by main or a similar wrapper,
        // or ensure the file exists and is writable.
        // Let's create it to simulate the flow where addTemplateComponent would have created it.
        Files.createFile(targetFile);


        // Access the private static method appendFileContent
        Method appendFileContentMethod = FileGenerator.class.getDeclaredMethod("appendFileContent", String.class, String.class, int.class);
        appendFileContentMethod.setAccessible(true);

        int initialCounter = 0;
        // Invoke appendFileContent. It appends to FileGenerator.generatedFileLocation
        int finalCounter = (int) appendFileContentMethod.invoke(null, sourceFile.toString(), sourceFileName, initialCounter);

        // Assertions
        List<String> lines = Files.readAllLines(targetFile);
        String content = String.join("\n", lines);

        assertTrue(content.contains("<h2 title='Juwan Howard Trading Cards for Season " + sourceFileName + "' id='" + sourceFileName + "'>" + sourceFileName + " [This Season: 2 | Total: 2]</h2>"));
        assertTrue(content.contains("<table>"));
        assertTrue(content.contains("<tr><td>Data 1</td></tr>"));
        assertTrue(content.contains("<tr><td>Data 2</td></tr>"));
        assertTrue(content.contains("</table>"));
        assertEquals(2, finalCounter); // Two <tr> rows were processed
    }


    @Test
    void testFormatFileContent() throws Exception {
        // Create a dummy source HTML file
        Path sourceContentFile = contentDir.resolve("testFormat.html");
        Files.write(sourceContentFile, Arrays.asList(
                "  <table>  ", // Extra spaces to be trimmed
                "<tr><td>  Item 1  </td></tr>", // Extra spaces
                "  </table>  ",
                "  <h2>An H2 Title</h2>  " // This h2 will be modified
        ));

        // Define a target file path for the formatted content
        Path formattedFile = outputDir.resolve("formattedTestFormat.html");
        // formatFileContent creates/overwrites the target file

        // Access the private static method formatFileContent
        Method formatFileContentMethod = FileGenerator.class.getDeclaredMethod("formatFileContent", String.class, String.class);
        formatFileContentMethod.setAccessible(true);

        // Invoke formatFileContent
        formatFileContentMethod.invoke(null, sourceContentFile.toString(), formattedFile.toString());

        // Assertions
        List<String> lines = Files.readAllLines(formattedFile);
        // Expected content after trimming and processing
        assertEquals("<table>", lines.get(0).trim()); // Check trimmed content
        assertEquals("<tr><td>Item 1</td></tr>", lines.get(1).trim());
        assertEquals("</table>", lines.get(2).trim());
        // The h2 is modified to include "[Total: X]". The counter logic in formatFileContent counts <tr> elements.
        // In this case, there is one "<tr>" element in the input.
        assertTrue(lines.stream().anyMatch(line -> line.contains("<h2>An H2 Title [Total: 1]</h2>")),
                "Formatted H2 title with total count not found or incorrect. Lines: " + lines);
    }


    @Test
    void testMain() throws Exception {
        // Create dummy HTML files in the temporary content directory
        createDummyHtmlFile("mainTest1.html", "<table><tr><td>Main Test Content 1</td></tr></table>");
        createDummyHtmlFile("mainTest2.html", "<table><tr><td>Main Test Content 2</td></tr><tr><td>More Data</td></tr></table>");

        // Call FileGenerator.main()
        // It should use the overridden pathSource and pathOutput
        assertDoesNotThrow(() -> FileGenerator.main(new String[]{}));

        // Assert that the index.html file is generated in the output directory
        Path indexPath = outputDir.resolve("index.html");
        assertTrue(Files.exists(indexPath), "index.html was not generated.");

        // Assert its content is as expected
        List<String> lines = Files.readAllLines(indexPath);
        String content = String.join("\n", lines);

        // Check for template parts
        assertTrue(content.startsWith("<!DOCTYPE html>"), "Generated content does not start with DOCTYPE.");
        // Check if the footer (part of templateEnd) is present
        String footerContent = FileGenerator.footer;
        assertTrue(content.contains(footerContent), "Generated content does not include the footer.");
        // Check if the file ends correctly (part of templateEnd)
        assertTrue(content.contains("</body></html>"), "Generated content does not end correctly.");

        // Check for anchor list
        assertTrue(content.contains("<a class='modern-button' href=#mainTest1 title='Juwan Howard Trading Cards from Season: mainTest1'>mainTest1</a>"), "Anchor for mainTest1 not found.");
        assertTrue(content.contains("<a class='modern-button' href=#mainTest2 title='Juwan Howard Trading Cards from Season: mainTest2'>mainTest2</a>"), "Anchor for mainTest2 not found.");

        // Check for appended content from mainTest1.html
        assertTrue(content.contains("<h2 title='Juwan Howard Trading Cards for Season mainTest1' id='mainTest1'>mainTest1 [This Season: 1 | Total: 1]</h2>"), "Header for mainTest1 not found or incorrect.");
        assertTrue(content.contains("<table><tr><td>Main Test Content 1</td></tr></table>"), "Content from mainTest1 not found.");

        // Check for appended content from mainTest2.html
        // Total should be 1 (from mainTest1) + 2 (from mainTest2) = 3
        assertTrue(content.contains("<h2 title='Juwan Howard Trading Cards for Season mainTest2' id='mainTest2'>mainTest2 [This Season: 2 | Total: 3]</h2>"), "Header for mainTest2 not found or incorrect.");
        assertTrue(content.contains("<table><tr><td>Main Test Content 2</td></tr><tr><td>More Data</td></tr></table>"), "Content from mainTest2 not found.");
    }
}
