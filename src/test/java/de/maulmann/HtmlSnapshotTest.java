package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HTML Snapshot & Golden Master Structural Tests")
class HtmlSnapshotTest {

    private static final Path OUTPUT_DIR = Paths.get("output");
    @BeforeAll
    static void ensureOutputGenerated() {
        FileGenerator.generate();
        List<CardData> cards = CardPageGenerator.run();
        SitemapGenerator.generate(cards);
    }

    @Test
    @DisplayName("Golden Master: Core static pages must maintain valid semantic HTML5 structure")
    void testCorePagesSemanticStructure() throws Exception {
        List<String> corePages = List.of(
                "index.html",
                "Juwan-Howard-Collection.html",
                "rainbows.html",
                "binder.html",
                "Flawless.html",
                "Panini.html",
                "Baseball.html",
                "Wantlist.html"
        );

        for (String pageName : corePages) {
            Path pagePath = OUTPUT_DIR.resolve(pageName);
            if (!Files.exists(pagePath)) continue;

            String html = Files.readString(pagePath);
            Document doc = Jsoup.parse(html);

            // Invariant 1: Basic Document Structure
            assertNotNull(doc.selectFirst("html"), pageName + " must have <html> tag");
            assertNotNull(doc.selectFirst("head"), pageName + " must have <head> tag");
            assertNotNull(doc.selectFirst("body"), pageName + " must have <body> tag");

            // Invariant 2: Semantic HTML5 Elements
            assertNotNull(doc.selectFirst("header"), pageName + " must have <header>");
            assertNotNull(doc.selectFirst("main"), pageName + " must have <main>");
            assertNotNull(doc.selectFirst("footer"), pageName + " must have <footer>");
            assertNotNull(doc.selectFirst("nav"), pageName + " must have <nav>");

            // Invariant 3: SEO and Social Meta Tags
            Element title = doc.selectFirst("title");
            assertNotNull(title, pageName + " must have <title>");
            assertFalse(title.text().isBlank(), pageName + " title cannot be blank");

            Element desc = doc.selectFirst("meta[name=description]");
            assertNotNull(desc, pageName + " must have meta description");
            assertFalse(desc.attr("content").isBlank(), pageName + " description cannot be blank");

            Element ogImage = doc.selectFirst("meta[property=og:image]");
            assertNotNull(ogImage, pageName + " must have og:image");
            assertTrue(ogImage.attr("content").startsWith("https://www.maulmann.de/"), pageName + " og:image must be absolute HTTPS URL");

            Element twitterCard = doc.selectFirst("meta[name=twitter:card]");
            assertNotNull(twitterCard, pageName + " must have twitter:card");
            assertEquals("summary_large_image", twitterCard.attr("content"), pageName + " twitter:card must be summary_large_image");

            // Invariant 4: No unrendered Freemarker or template placeholders
            assertFalse(html.contains("${"), pageName + " must not contain unrendered Freemarker tags");
            assertFalse(html.contains("[[STABLE_TIME]]"), pageName + " must not contain unresolved [[STABLE_TIME]]");
        }
    }

    @Test
    @DisplayName("Golden Master: Card detail pages must contain valid Schema.org JSON-LD and FAQ blocks")
    void testCardDetailPageJsonLdSnapshot() throws Exception {
        Path cardsDir = OUTPUT_DIR.resolve("cards");
        if (!Files.exists(cardsDir)) return;

        File[] seasonDirs = cardsDir.toFile().listFiles(File::isDirectory);
        if (seasonDirs == null || seasonDirs.length == 0) return;

        File firstSeason = seasonDirs[0];
        File[] cardFiles = firstSeason.listFiles((dir, name) -> name.endsWith(".html"));
        if (cardFiles == null || cardFiles.length == 0) return;

        File sampleCard = cardFiles[0];
        String html = Files.readString(sampleCard.toPath());
        Document doc = Jsoup.parse(html);

        // Verify Schema.org JSON-LD
        Elements jsonLdScripts = doc.select("script[type=application/ld+json]");
        assertFalse(jsonLdScripts.isEmpty(), "Card detail page must contain Schema.org JSON-LD script");

        String jsonLdContent = jsonLdScripts.first().data();
        assertTrue(jsonLdContent.contains("\"@context\": \"https://schema.org\""), "JSON-LD must have schema.org context");
        assertTrue(jsonLdContent.contains("\"@type\": \"Product\"") || jsonLdContent.contains("\"@type\": \"ItemPage\""), "JSON-LD must have Product/ItemPage type");
        assertTrue(jsonLdContent.contains("\"name\":"), "JSON-LD must have name property");

        // Verify OpenGraph tags on card detail page
        Element ogWidth = doc.selectFirst("meta[property=og:image:width]");
        assertNotNull(ogWidth, "Card detail page must have og:image:width");
        Element ogHeight = doc.selectFirst("meta[property=og:image:height]");
        assertNotNull(ogHeight, "Card detail page must have og:image:height");

        // Verify Loupe & Flip Interactive Elements
        Element loupeEl = doc.selectFirst("#cardLoupe");
        assertNotNull(loupeEl, "Card detail page must contain #cardLoupe element");
        assertTrue(loupeEl.hasClass("card-grading-loupe"), "#cardLoupe must have class card-grading-loupe");

        Element flipBtn = doc.selectFirst("#flipActionBtn");
        assertNotNull(flipBtn, "Card detail page must contain #flipActionBtn");

        Element loupeBtn = doc.selectFirst("#loupeBtn");
        assertNotNull(loupeBtn, "Card detail page must contain #loupeBtn");

        Element popoutBtn = doc.selectFirst("#popoutBtn");
        assertNull(popoutBtn, "Card detail page must not contain removed #popoutBtn");

        Element flipContainer = doc.selectFirst(".flip-container");
        assertNotNull(flipContainer, "Card detail page must contain .flip-container");
        assertNotNull(flipContainer.selectFirst(".flip-face-front"), "Flip container must have front face");
        assertNotNull(flipContainer.selectFirst(".flip-face-back"), "Flip container must have back face");
    }
}
