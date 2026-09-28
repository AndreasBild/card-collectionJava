package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StaticPageGenerator Tests")
class StaticPageGeneratorTest {

    @Test
    @DisplayName("generateCollectionSummaryHtml should return empty string for empty cards or null stats")
    void testEmptyCardsOrNullStats() {
        assertEquals("", StaticPageGenerator.generateCollectionSummaryHtml("Flawless", Collections.emptyList(), new HashMap<>()));
        assertEquals("", StaticPageGenerator.generateCollectionSummaryHtml("Flawless", List.of(CardJson.builder().player("Test").build()), null));
    }

    @Test
    @DisplayName("generateCollectionSummaryHtml should format Flawless summary with valuation and badges")
    void testFlawlessSummaryGeneration() {
        CardJson c = CardJson.builder()
                .player("Michael Jordan")
                .season("2008")
                .brand("Exquisite Collection")
                .isAutograph(true)
                .serialNumber("23")
                .printRun(23)
                .estimatedValue(10000.0)
                .build();
        List<CardJson> cards = List.of(c);
        Map<String, Object> stats = CardStatsService.computeCollectionStats(cards);

        String html = StaticPageGenerator.generateCollectionSummaryHtml("Flawless", cards, stats);

        assertNotNull(html);
        assertTrue(html.contains("class=\"analytics-accordion context-card full-width\""));
        assertTrue(html.contains("Vault Analytics"));
        assertTrue(html.contains("class=\"vault-valuation-badge\""));
        assertTrue(html.contains("$10,000"));
        assertTrue(html.contains("<strong>1</strong> Cards"));
        assertTrue(html.contains("<strong>1</strong> Autos"));
        assertTrue(html.contains("Estimated Total Value"));
        assertTrue(html.contains("Average Card Value"));
        assertTrue(html.contains("Pricing Coverage"));
        assertTrue(html.contains("Rarity Tier Breakdown"));
    }

    @Test
    @DisplayName("generateCollectionSummaryHtml should format Panini summary with ultra sp and patches")
    void testPaniniSummaryGeneration() {
        CardJson c1 = CardJson.builder()
                .player("Julius Erving")
                .season("2012-13")
                .brand("Panini Flawless")
                .isAutograph(true)
                .isPatch(true)
                .serialNumber("1/5")
                .printRun(5)
                .estimatedValue(750.0)
                .build();
        List<CardJson> cards = List.of(c1);
        Map<String, Object> stats = CardStatsService.computeCollectionStats(cards);

        String html = StaticPageGenerator.generateCollectionSummaryHtml("Panini", cards, stats);

        assertNotNull(html);
        assertTrue(html.contains("$750"));
        assertTrue(html.contains("Ultra SP (&le; 10)"));
        assertTrue(html.contains("Game-Used Patch / Mem"));
    }

    @Test
    @DisplayName("generateCollectionSummaryHtml should omit valuation badge if totalEstimatedValue is 0")
    void testZeroValuationSummary() {
        CardJson c = CardJson.builder()
                .player("Unknown")
                .season("2000")
                .build();
        List<CardJson> cards = List.of(c);
        Map<String, Object> stats = CardStatsService.computeCollectionStats(cards);

        String html = StaticPageGenerator.generateCollectionSummaryHtml("Flawless", cards, stats);
        assertNotNull(html);
        assertFalse(html.contains("class=\"vault-valuation-badge\""));
        assertTrue(html.contains("<strong>1</strong> Cards"));
    }

    @Test
    @DisplayName("generateCollectionSummaryHtml should return empty for non-target collection with 0 valuation")
    void testNonTargetZeroValuation() {
        CardJson c = CardJson.builder()
                .player("Test")
                .build();
        List<CardJson> cards = List.of(c);
        Map<String, Object> stats = CardStatsService.computeCollectionStats(cards);

        String html = StaticPageGenerator.generateCollectionSummaryHtml("RandomColl", cards, stats);
        assertEquals("", html);
    }

    @Test
    @DisplayName("buildOtherCollections should generate Flawless, Panini, and Baseball with valuation summary above table")
    void testBuildOtherCollectionsIntegration(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        String pathOutput = tempDir.toString() + "/";
        TimestampTracker tracker = new TimestampTracker(tempDir.resolve("timestamps.properties").toString());

        StaticPageGenerator.buildOtherCollections("content/", pathOutput, tracker);

        for (String coll : List.of("Flawless", "Panini", "Baseball")) {
            java.nio.file.Path pagePath = tempDir.resolve(coll + ".html");
            assertTrue(java.nio.file.Files.exists(pagePath), coll + ".html should be generated");
            String content = java.nio.file.Files.readString(pagePath);

            assertTrue(content.contains("class=\"analytics-accordion context-card full-width\""),
                    coll + ".html should contain analytics-accordion");
            assertTrue(content.contains("class=\"vault-valuation-badge\""),
                    coll + ".html should contain vault-valuation-badge");
            assertTrue(content.contains("Estimated Total Value"),
                    coll + ".html should contain Estimated Total Value");
            assertTrue(content.contains("Average Card Value"),
                    coll + ".html should contain Average Card Value");
            assertTrue(content.contains("Pricing Coverage"),
                    coll + ".html should contain Pricing Coverage");

            int accordionIdx = content.indexOf("analytics-accordion");
            int tableIdx = content.indexOf("<table");
            assertTrue(accordionIdx > 0 && tableIdx > 0, "Both accordion and table should exist");
            assertTrue(accordionIdx < tableIdx, "Analytics accordion must appear BEFORE the card table");
        }
    }
}
