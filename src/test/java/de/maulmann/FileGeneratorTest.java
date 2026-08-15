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
import java.util.Set;

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
    void tearDown() throws Exception {
        FileGenerator.pathSource = originalSourcePath;
        FileGenerator.pathOutput = originalOutputPath;
        
        // Reset the static cache to prevent state leakage between tests
        java.lang.reflect.Field cacheField = FileGenerator.class.getDeclaredField("cachedFilteredCards");
        cacheField.setAccessible(true);
        cacheField.set(null, null);
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
        assertEquals(1385, stats.get("rawTotalCards"));
        assertEquals("1,385", stats.get("totalCards"));
        assertEquals(57, stats.get("count1of1"));
        assertEquals(159, stats.get("countUltraSp"));
        assertEquals(472, stats.get("countSerialized"));
        assertEquals(205, stats.get("countAutographs"));
        assertEquals(108, stats.get("countPatches"));
        assertEquals(16, stats.get("countRookies"));
        assertEquals(14, stats.get("countGradedTotal"));
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
        assertTrue(html.contains("?from=rainbows"), "Card links on rainbows page should include ?from=rainbows parameter");
    }

    @Test
    void testBuildIndexPageNewInFooterLinks() throws Exception {
        Path jsonDir = Files.createDirectories(contentDir.resolve("json"));
        String jsonContent = "[" +
                "{\"player\":\"Juwan Howard\",\"season\":\"2004-05\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"UD Black Diamond\",\"theme\":\"Base Set\",\"variant\":\"Black\",\"cardNumber\":\"10\",\"serialNumber\":\"3\",\"printRun\":5}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2005-06\",\"team\":\"Bullets\",\"company\":\"Topps\",\"brand\":\"Topps Bazooka\",\"theme\":\"Base Set\",\"variant\":\"Blue\",\"cardNumber\":\"20\",\"serialNumber\":\"2\",\"printRun\":5}" +
                "]";
        Files.writeString(jsonDir.resolve("cards.json"), jsonContent);

        FileGenerator.buildStaticPages();

        Path indexPath = outputDir.resolve("index.html");
        assertTrue(Files.exists(indexPath), "index.html should be generated");

        String content = Files.readString(indexPath);
        assertTrue(content.contains("<section class=\"seo-showcase-footer\""), "Should contain SEO showcase footer section");
        assertTrue(content.contains("<h2 class=\"seo-showcase-title\">New In</h2>"), "Should contain New In title");
        assertTrue(content.contains("UD Black Diamond"), "Should contain rare card link");
        assertTrue(content.contains("(/5)"), "Should contain print run in title");
    }

    @Test
    void testBuildIndexPageMasterpiecesFooterLinks() throws Exception {
        Path jsonDir = Files.createDirectories(contentDir.resolve("json"));
        String jsonContent = "[" +
                "{\"player\":\"Juwan Howard\",\"season\":\"2022-23\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"Panini Spectra\",\"theme\":\"Base Set\",\"variant\":\"Nebula\",\"cardNumber\":\"1\",\"serialNumber\":\"1/1\",\"printRun\":1}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2022-23\",\"team\":\"Bullets\",\"company\":\"Panini\",\"brand\":\"Panini Spectra\",\"theme\":\"Base Set\",\"variant\":\"Black Printing Plate Cyan\",\"cardNumber\":\"1\",\"serialNumber\":\"1/1\",\"printRun\":1}," +
                "{\"player\":\"Juwan Howard\",\"season\":\"2024-25\",\"team\":\"Bullets\",\"company\":\"Leaf\",\"brand\":\"Leaf Metal\",\"theme\":\"Base Set\",\"variant\":\"Pre Production Proof Orange\",\"cardNumber\":\"1\",\"serialNumber\":\"1/1\",\"printRun\":1}" +
                "]";
        Files.writeString(jsonDir.resolve("cards.json"), jsonContent);

        FileGenerator.buildStaticPages();

        Path indexPath = outputDir.resolve("index.html");
        assertTrue(Files.exists(indexPath), "index.html should be generated");

        String content = Files.readString(indexPath);
        assertTrue(content.contains("<section class=\"seo-showcase-footer\""), "Should contain Masterpieces footer section");
        assertTrue(content.contains("<h2 class=\"seo-showcase-title\">Masterpieces (1/1)</h2>"), "Should contain Masterpieces title");
        assertTrue(content.contains("Nebula"), "Should contain 1/1 Masterpiece Nebula link");
        assertFalse(content.contains("Black Printing Plate Cyan"), "Should EXCLUDE printing plates from Masterpieces section");
        assertFalse(content.contains("Pre Production Proof Orange"), "Should EXCLUDE proof cards from Masterpieces section");
    }

    @Test
    void testSingleCardPageShowcaseFooter() throws Exception {
        CardJson c1 = createTestCardJson("Juwan Howard", "1997-98", "Wizards", "Fleer", "Fleer Metal Universe", "Base Set", "Rubies", "1", "1/50", 50);
        CardJson c2 = createTestCardJson("Juwan Howard", "1997-98", "Wizards", "Fleer", "Fleer Metal Universe", "Base Set", "Precious Metal Gems", "1", "1/100", 100);
        CardJson c3 = createTestCardJson("Juwan Howard", "1997-98", "Wizards", "Fleer", "Fleer Ultra", "Base Set", "Gold Medallion", "5", null, null);

        CardPageGenerator.CardData card1 = new CardPageGenerator.CardData(c1, "id1");
        CardPageGenerator.CardData card2 = new CardPageGenerator.CardData(c2, "id2");
        CardPageGenerator.CardData card3 = new CardPageGenerator.CardData(c3, "id3");

        List<CardPageGenerator.CardData> allCards = List.of(card1, card2, card3);

        List<Map<String, String>> brandCards = CardPageGenerator.findSameBrandCards(card1, allCards, 6);
        assertEquals(1, brandCards.size());
        assertEquals("Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems (/100)", brandCards.get(0).get("title"));

        Set<String> brandIds = Set.of(card2.stableId);
        List<Map<String, String>> companyCards = CardPageGenerator.findSameCompanyCards(card1, allCards, brandIds, 6);
        assertEquals(1, companyCards.size());
        assertEquals("Juwan Howard 1997-98 Fleer Ultra Gold Medallion", companyCards.get(0).get("title"));
    }

    private CardJson createTestCardJson(String player, String season, String team, String company, String brand, String theme, String variant, String number, String serialNumber, Integer printRun) {
        CardJson c = new CardJson();
        c.player = player;
        c.season = season;
        c.team = team;
        c.company = company;
        c.brand = brand;
        c.theme = theme;
        c.variant = variant;
        c.cardNumber = number;
        c.serialNumber = serialNumber;
        c.printRun = printRun;
        return c;
    }
}





