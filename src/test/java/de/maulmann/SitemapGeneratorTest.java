package de.maulmann;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SitemapGeneratorTest {

    @TempDir
    Path tempDir;

    private Path outputDir;

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Paths.get("output");
        Files.createDirectories(outputDir);
    }

    @Test
    void testSitemapGenerationHierarchyAndContent() throws Exception {
        // Create mock static page
        Path indexPage = outputDir.resolve("index.html");
        Files.writeString(indexPage, "<html><head><title>Home Page</title></head><body><h1>Welcome</h1></body></html>");

        // Create mock highlight card page (1/1)
        Path cardDir = outputDir.resolve("cards/1994-95");
        Files.createDirectories(cardDir);
        Path highlightCard = cardDir.resolve("rare-card-1.html");
        String highlightHtml = "<html><head><title>Rare Juwan Howard 1/1</title></head><body>" +
                "<h1>Juwan Howard 1/1</h1>" +
                "<table><tr><th class=\"specs-th\">Variant</th><td class=\"specs-td\">1/1 Masterpiece</td></tr>" +
                "<tr><th class=\"specs-th\">Print Run</th><td class=\"specs-td\">1</td></tr></table>" +
                "</body></html>";
        Files.writeString(highlightCard, highlightHtml);

        // Create mock normal base card page
        Path baseCard = cardDir.resolve("base-card-2.html");
        String baseHtml = "<html><head><title>Base Juwan Howard</title></head><body>" +
                "<h1>Juwan Howard Base</h1>" +
                "<table><tr><th class=\"specs-th\">Variant</th><td class=\"specs-td\">Base</td></tr>" +
                "<tr><th class=\"specs-th\">Print Run</th><td class=\"specs-td\">10000</td></tr></table>" +
                "</body></html>";
        Files.writeString(baseCard, baseHtml);

        // Create tracker
        Path trackerFile = tempDir.resolve("timestamps.properties");
        TimestampTracker tracker = new TimestampTracker(trackerFile.toString());
        tracker.getStableTimestamp("index.html", Files.readString(indexPage));
        tracker.getStableTimestamp("cards/1994-95/rare-card-1.html", highlightHtml);
        tracker.getStableTimestamp("cards/1994-95/base-card-2.html", baseHtml);
        tracker.save();

        SitemapGenerator.setTimestampTracker(tracker);
        SitemapGenerator.generate();

        // Check root sitemap.xml (Sitemap Index)
        Path sitemapIndexFile = outputDir.resolve("sitemap.xml");
        assertTrue(Files.exists(sitemapIndexFile), "sitemap.xml must exist");
        String indexContent = Files.readString(sitemapIndexFile);

        assertTrue(indexContent.contains("<sitemapindex"), "sitemap.xml must be a sitemapindex");
        assertTrue(indexContent.contains("sitemap-main.xml"), "sitemapindex must list sitemap-main.xml");
        assertTrue(indexContent.contains("sitemap-highlights.xml"), "sitemapindex must list sitemap-highlights.xml");
        assertTrue(indexContent.contains("sitemap-cards-1994-95.xml"), "sitemapindex must list sitemap-cards-1994-95.xml");

        // Check sitemap-main.xml
        Path sitemapMainFile = outputDir.resolve("sitemap-main.xml");
        assertTrue(Files.exists(sitemapMainFile), "sitemap-main.xml must exist");
        String mainContent = Files.readString(sitemapMainFile);
        assertTrue(mainContent.contains("<priority>1.0</priority>"), "Main sitemap must have priority 1.0");
        assertTrue(mainContent.contains("<changefreq>daily</changefreq>"), "Main sitemap must have changefreq daily");

        // Check sitemap-highlights.xml
        Path sitemapHighlightsFile = outputDir.resolve("sitemap-highlights.xml");
        assertTrue(Files.exists(sitemapHighlightsFile), "sitemap-highlights.xml must exist");
        String highlightsContent = Files.readString(sitemapHighlightsFile);
        assertTrue(highlightsContent.contains("<priority>0.9</priority>"), "Highlights sitemap must have priority 0.9");
        assertTrue(highlightsContent.contains("rare-card-1.html"), "Highlights sitemap must contain rare card");

        // Check sitemap-cards-1994-95.xml
        Path sitemapCardsFile = outputDir.resolve("sitemap-cards-1994-95.xml");
        assertTrue(Files.exists(sitemapCardsFile), "sitemap-cards-1994-95.xml must exist");
        String cardsContent = Files.readString(sitemapCardsFile);
        assertTrue(cardsContent.contains("<priority>0.5</priority>"), "Cards sitemap must have priority 0.5");
        assertTrue(cardsContent.contains("base-card-2.html"), "Cards sitemap must contain base card");
    }
}
