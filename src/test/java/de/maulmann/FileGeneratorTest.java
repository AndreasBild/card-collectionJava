package de.maulmann;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        Path jsonDir = Files.createDirectories(contentDir.resolve("json"));
        String jsonContent = "[{\"player\":\"Juwan Howard\",\"season\":\"1994-95\",\"team\":\"Washington Bullets\",\"company\":\"Upper Deck\",\"brand\":\"Collector's Choice\",\"theme\":\"Base\",\"variant\":\"Base\",\"cardNumber\":\"123\",\"serialNumber\":\"0\",\"printRun\":0,\"isAutograph\":false,\"isPatch\":false,\"isRookie\":true}]";
        Files.writeString(jsonDir.resolve("cards.json"), jsonContent);

        assertDoesNotThrow(() -> FileGenerator.main(new String[]{}));

        Path generatedPath = outputDir.resolve("Juwan-Howard-Collection.html");
        assertTrue(Files.exists(generatedPath), "Output file was not generated.");

        String content = Files.readString(generatedPath);

        assertTrue(content.contains("Juwan Howard"), "Should contain Juwan Howard");
        assertTrue(content.contains("1994-95"), "Should contain season 1994-95");

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
        assertTrue(errorContent.contains("Error Page"), "Error page should contain Error Page");
        assertTrue(errorContent.contains("index.html"), "Error page should contain a link back to home");
    }

    @Test
    void testComputeCollectionStats() {
        List<CardJson> cards = FileGenerator.filterDuplicateJsonCards(CardDataLoader.loadCardsFromJson("content/json/cards.json"));
        Map<String, Object> stats = FileGenerator.computeCollectionStats(cards);

        assertNotNull(stats, "Stats map should not be null");
        assertEquals(1376, stats.get("rawTotalCards"));
        assertEquals("1,376", stats.get("totalCards"));
        assertEquals(58, stats.get("count1of1"));
        assertEquals(155, stats.get("countUltraSp"));
        assertEquals(465, stats.get("countSerialized"));
        assertEquals(202, stats.get("countAutographs"));
        assertEquals(107, stats.get("countPatches"));
        assertEquals(16, stats.get("countRookies"));
        assertEquals(12, stats.get("countGradedTotal"));
        assertEquals(4, stats.get("countGemMint"));
    }

    @Test
    void testBuildRainbowsPageOrderingAndDeduplication() throws Exception {
        Path jsonDir = Files.createDirectories(contentDir.resolve("json"));
        // Create 2 rainbow sets: Set A with 4 cards, Set B with 5 cards (including 2 cards with different serial numbers and 1 duplicate)
        String jsonContent = "[" +
                "{\"player\":\"Juwan Howard\",\"season\":\"2020-21\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"Panini Prizm\",\"theme\":\"Base Set\",\"variant\":\"Base\",\"cardNumber\":\"10\",\"serialNumber\":\"0\",\"printRun\":0}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2020-21\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"Panini Prizm\",\"theme\":\"Base Set\",\"variant\":\"Silver\",\"cardNumber\":\"10\",\"serialNumber\":\"0\",\"printRun\":0}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2020-21\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"Panini Prizm\",\"theme\":\"Base Set\",\"variant\":\"Green\",\"cardNumber\":\"10\",\"serialNumber\":\"0\",\"printRun\":0}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2020-21\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"Panini Prizm\",\"theme\":\"Base Set\",\"variant\":\"Gold\",\"cardNumber\":\"10\",\"serialNumber\":\"5\",\"printRun\":10}," +

                "{\"player\":\"Juwan Howard\",\"season\":\"2021-22\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Chrome\",\"theme\":\"Base Set\",\"variant\":\"Base\",\"cardNumber\":\"20\",\"serialNumber\":\"0\",\"printRun\":0}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2021-22\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Chrome\",\"theme\":\"Base Set\",\"variant\":\"Refractor\",\"cardNumber\":\"20\",\"serialNumber\":\"0\",\"printRun\":0}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2021-22\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Chrome\",\"theme\":\"Base Set\",\"variant\":\"Blue Refractor\",\"cardNumber\":\"20\",\"serialNumber\":\"12\",\"printRun\":99}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2021-22\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Chrome\",\"theme\":\"Base Set\",\"variant\":\"Blue Refractor\",\"cardNumber\":\"20\",\"serialNumber\":\"45\",\"printRun\":99}," + // Different serial! Must be distinct!
                "{\"player\":\"Juwan Howard\",\"season\":\"2021-22\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Chrome\",\"theme\":\"Base Set\",\"variant\":\"Gold Refractor\",\"cardNumber\":\"20\",\"serialNumber\":\"5\",\"printRun\":50}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2021-22\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Chrome\",\"theme\":\"Base Set\",\"variant\":\"Gold Refractor\",\"cardNumber\":\"20\",\"serialNumber\":\"5\",\"printRun\":50}" + // Duplicate of previous! Must be deduplicated!
                "]";
        Files.writeString(jsonDir.resolve("cards.json"), jsonContent);

        FileGenerator.buildRainbowsPage();

        Path rainbowHtml = outputDir.resolve("rainbows.html");
        assertTrue(Files.exists(rainbowHtml), "rainbows.html should be generated");

        String html = Files.readString(rainbowHtml);
        assertTrue(html.contains("2021-22 Topps Chrome Base Set #20 Rainbow"), "Should contain 2021-22 set");
        assertTrue(html.contains("2020-21 Panini Prizm Base Set #10 Rainbow"), "Should contain 2020-21 set");

        // Set with 5 cards (2021-22) should appear BEFORE set with 4 cards (2020-21)
        int posSet2021 = html.indexOf("2021-22 Topps Chrome Base Set #20 Rainbow");
        int posSet2020 = html.indexOf("2020-21 Panini Prizm Base Set #10 Rainbow");
        assertTrue(posSet2021 < posSet2020, "Set with more cards should appear first");
    }
}





