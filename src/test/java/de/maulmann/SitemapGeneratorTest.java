package de.maulmann;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        Files.writeString(indexPage, "<!doctype html><html><head><title>Home Page</title><meta name=\"description\" content=\"Home\"><meta property=\"og:image\" content=\"https://www.maulmann.de/default.avif\"><meta name=\"twitter:card\" content=\"summary_large_image\"></head><body><header><h1>Welcome</h1></header><main></main><nav></nav><footer></footer></body></html>");

        // Create mock highlight card page (1/1) with responsive image
        Path cardDir = outputDir.resolve("cards/1994-95");
        Files.createDirectories(cardDir);
        Path highlightCard = cardDir.resolve("rare-card-1.html");
        String highlightHtml = "<html><head><title>Rare Juwan Howard 1/1</title></head><body>" +
                "<h1>Juwan Howard 1/1</h1>" +
                "<figure><picture>" +
                "<source type=\"image/avif\" srcset=\"rare-front-400w.avif 400w, rare-front-600w.avif 600w, rare-front.avif 1200w\">" +
                "<img src=\"rare-front-400w.avif\" alt=\"Rare Front Scan\" title=\"Rare Juwan Howard 1/1 Front\">" +
                "</picture><figcaption>Rare Front Scan</figcaption></figure>" +
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

        // Check sitemap-highlights.xml for Image SEO namespace and tags
        Path sitemapHighlightsFile = outputDir.resolve("sitemap-highlights.xml");
        assertTrue(Files.exists(sitemapHighlightsFile), "sitemap-highlights.xml must exist");
        String highlightsContent = Files.readString(sitemapHighlightsFile);
        assertTrue(highlightsContent.contains("<priority>0.9</priority>"), "Highlights sitemap must have priority 0.9");
        assertTrue(highlightsContent.contains("rare-card-1.html"), "Highlights sitemap must contain rare card");
        assertTrue(highlightsContent.contains("xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\""), "Highlights sitemap must include image namespace");
        assertTrue(highlightsContent.contains("<image:image>"), "Highlights sitemap must contain <image:image> block");
        assertTrue(highlightsContent.contains("rare-front.avif"), "Image loc must pick highest resolution candidate (rare-front.avif), not 400w thumbnail");
        assertFalse(highlightsContent.contains("rare-front-400w.avif"), "Image loc must NOT use thumbnail");
        assertTrue(highlightsContent.contains("<image:title>Rare Juwan Howard 1/1 Front</image:title>"), "Image title must be set");
        assertTrue(highlightsContent.contains("<image:caption>Rare Front Scan</image:caption>"), "Image caption must be set");

        // Check sitemap-cards-1994-95.xml
        Path sitemapCardsFile = outputDir.resolve("sitemap-cards-1994-95.xml");
        assertTrue(Files.exists(sitemapCardsFile), "sitemap-cards-1994-95.xml must exist");
        String cardsContent = Files.readString(sitemapCardsFile);
        assertTrue(cardsContent.contains("<priority>0.5</priority>"), "Cards sitemap must have priority 0.5");
        assertTrue(cardsContent.contains("base-card-2.html"), "Cards sitemap must contain base card");
    }

    @Test
    void testExtractHighestResCandidate() {
        String srcset = "img-400w.avif 400w, img-600w.avif 600w, img-900w.avif 900w, img-1200w.avif 1200w";
        String result = SitemapGenerator.extractHighestResCandidate(srcset);
        assertEquals("img-1200w.avif", result, "Must select highest width candidate");

        String srcsetWithOriginal = "img-400w.avif 400w, img.avif";
        String resultOriginal = SitemapGenerator.extractHighestResCandidate(srcsetWithOriginal);
        assertEquals("img.avif", resultOriginal, "Must select candidate without thumbnail suffix when unweighted");
    }

    @Test
    void testGenerateLlmsTxtWithDynamicMetrics() throws Exception {
        CardJson c1 = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("Precious Metal Gems Green")
                .cardNumber("33")
                .serialNumber("1/1")
                .printRun(1)
                .isAutograph(true)
                .isPatch(true)
                .isRookie(false)
                .gradingCompany("PSA")
                .grade("10")
                .build();

        CardJson c2 = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Topps Finest")
                .variant("Refractor")
                .cardNumber("220")
                .serialNumber("5/10")
                .printRun(10)
                .isAutograph(false)
                .isPatch(false)
                .isRookie(true)
                .gradingCompany("BGS")
                .grade("9.5")
                .build();

        CardData card1 = new CardData(c1, "pmg-1");
        CardData card2 = new CardData(c2, "ref-2");

        SitemapGenerator.generateLlmsTxt(java.util.List.of(card1, card2));

        Path llmsPath = outputDir.resolve("llms.txt");
        assertTrue(Files.exists(llmsPath), "llms.txt must exist");
        String content = Files.readString(llmsPath);

        assertTrue(content.contains("Total Unique Cards: 2 indexed cards"), "Must include total card count");
        assertTrue(content.contains("1/1 Masterpieces & Grails: 1 true 1/1 cards"), "Must count 1/1 cards");
        assertTrue(content.contains("Ultra Short Prints (≤ 10): 2 cards"), "Must count ultra short prints");
        assertTrue(content.contains("Certified Autographs: 1 cards"), "Must count autographs");
        assertTrue(content.contains("Game-Used Patches & Memorabilia: 1 cards"), "Must count patches");
        assertTrue(content.contains("Official Rookie Cards (RC): 1 cards"), "Must count rookies");
        assertTrue(content.contains("Gem Mint Graded (PSA 10 / BGS 9.5): 2 cards"), "Must count gem mint graded cards");
        assertTrue(content.contains("https://www.maulmann.de/binder.html"), "Must contain binder link");
        assertTrue(content.contains("https://www.maulmann.de/rainbows.html"), "Must contain rainbows link");
    }
}
