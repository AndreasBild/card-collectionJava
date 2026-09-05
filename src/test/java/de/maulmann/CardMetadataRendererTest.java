package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardMetadataRenderer Tests")
class CardMetadataRendererTest {

    @Test
    @DisplayName("generateBrowserTitle, generateH1, and generateH1Html should produce clean SEO strings")
    void testTitleAndHeadingGeneration() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("Precious Metal Gems Red")
                .cardNumber("33")
                .gradingCompany("PSA")
                .grade("8")
                .build();

        CardData cardData = new CardData(c, "pmg-red-33");

        String title = CardMetadataRenderer.generateBrowserTitle(cardData, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red #33 PSA-8 | Juwan Howard Private Collection", title);

        String h1 = CardMetadataRenderer.generateH1(cardData);
        assertEquals("Juwan Howard | 1997-98 Fleer Metal Universe Precious Metal Gems Red #33 PSA-8", h1);

        String h1Html = CardMetadataRenderer.generateH1Html(cardData);
        assertTrue(h1Html.contains("<span class=\"player-name\">Juwan Howard</span>"));
        assertTrue(h1Html.contains("<span class=\"sub-title\">1997-98 Fleer Metal Universe Precious Metal Gems Red #33</span>"));
        assertTrue(h1Html.contains("PSA-8"));
    }

    @Test
    @DisplayName("generateAiSnapshotText should construct rich contextual descriptions")
    void testAiSnapshotText() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1996-97")
                .company("Topps")
                .brand("Topps Chrome")
                .theme("Base Set")
                .variant("Refractor")
                .cardNumber("100")
                .team("Washington Bullets")
                .serialNumber("5")
                .printRun(25)
                .isAutograph(true)
                .isPatch(false)
                .build();

        CardData cardData = new CardData(c, "chrome-ref-100");
        String aiSnapshot = CardMetadataRenderer.generateAiSnapshotText(cardData);

        assertTrue(aiSnapshot.contains("Washington Bullets"));
        assertTrue(aiSnapshot.contains("1996-97 Topps Topps Chrome"));
        assertTrue(aiSnapshot.contains("Refractor parallel variation"));
        assertTrue(aiSnapshot.contains("printrun of 5/25"));
        assertTrue(aiSnapshot.contains("official certified autograph"));
    }

    @Test
    @DisplayName("findRelatedCards should score and rank closest cards by player, season, and brand")
    void testFindRelatedCards() {
        CardJson target = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("PMG Green")
                .cardNumber("33")
                .build();

        CardJson match1 = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("PMG Red")
                .cardNumber("33")
                .build();

        CardJson match2 = CardJson.builder()
                .player("Juwan Howard")
                .season("1996-97")
                .brand("Fleer Metal Universe")
                .variant("Base")
                .cardNumber("50")
                .build();

        CardJson match3 = CardJson.builder()
                .player("Michael Jordan")
                .season("1997-98")
                .brand("Upper Deck")
                .variant("Base")
                .cardNumber("23")
                .build();

        CardData targetData = new CardData(target, "target-id");
        CardData d1 = new CardData(match1, "d1-id");
        CardData d2 = new CardData(match2, "d2-id");
        CardData d3 = new CardData(match3, "d3-id");

        CardIndex index = new CardIndex(List.of(targetData, d1, d2, d3));
        List<Map<String, String>> related = CardMetadataRenderer.findRelatedCards(targetData, index, 2, Set.of("target-id", "d1-id"));

        assertEquals(2, related.size());
        assertEquals("../1997-98/" + d1.filename, related.get(0).get("url"), "Same player, season, brand, rare parallel must rank first");
    }

    @Test
    @DisplayName("getOpenGraph and getHead should emit complete, absolute, orientation-aware social preview tags")
    void testOpenGraphSocialSharePreviews() {
        String ogHtml = SharedTemplates.getOpenGraph("cards/1997-98/juwan-howard-pmg.html", "Juwan Howard PMG #33", "A rare 90s basketball card.", "images/1997-98/juwan-howard-pmg-front.avif", 1200, 1680, "Juwan Howard Front Scan");

        assertTrue(ogHtml.contains("property=\"og:url\" content=\"https://www.maulmann.de/cards/1997-98/juwan-howard-pmg.html\""), "og:url must be absolute HTTPS URL");
        assertTrue(ogHtml.contains("property=\"og:image\" content=\"https://www.maulmann.de/images/1997-98/juwan-howard-pmg-front.avif\""), "og:image must be absolute HTTPS URL");
        assertTrue(ogHtml.contains("property=\"og:image:secure_url\" content=\"https://www.maulmann.de/images/1997-98/juwan-howard-pmg-front.avif\""), "og:image:secure_url must be HTTPS");
        assertTrue(ogHtml.contains("property=\"og:image:width\" content=\"1200\""), "og:image:width must be set");
        assertTrue(ogHtml.contains("property=\"og:image:height\" content=\"1680\""), "og:image:height must be set");
        assertTrue(ogHtml.contains("property=\"og:image:type\" content=\"image/avif\""), "og:image:type must match extension");
        assertTrue(ogHtml.contains("property=\"og:image:alt\" content=\"Juwan Howard Front Scan\""), "og:image:alt must match image alt");
        assertTrue(ogHtml.contains("name=\"twitter:card\" content=\"summary_large_image\""), "twitter:card must be summary_large_image");
        assertTrue(ogHtml.contains("name=\"twitter:image\" content=\"https://www.maulmann.de/images/1997-98/juwan-howard-pmg-front.avif\""), "twitter:image must be absolute URL");
    }

    @Test
    @DisplayName("getSeasonHighlights and getEraContext should produce rich dynamic descriptions when no static trivia matches")
    void testDynamicSeasonHighlightsAndEraContextFallback() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("2008-09")
                .team("Charlotte Bobcats")
                .brand("Topps Signature")
                .company("Topps")
                .isAutograph(true)
                .isPatch(false)
                .printRun(50)
                .build();

        CardData cardData = new CardData(c, "bobcats-auto-50");

        // Pass null or empty manager to trigger dynamic fallback synthesis
        String highlights = CardMetadataRenderer.getSeasonHighlights(cardData, "Juwan-Howard-Collection.html", null);
        assertTrue(highlights.contains("Charlotte Bobcats"), "Highlights fallback must mention team");
        assertTrue(highlights.contains("2008-09"), "Highlights fallback must mention season");
        assertTrue(highlights.contains("official manufacturer-certified autograph"), "Highlights fallback must mention certified autograph");

        String era = CardMetadataRenderer.getEraContext(cardData, "Juwan-Howard-Collection.html", null);
        assertTrue(era.contains("2008-09 Hobby Era"), "Era fallback must mention season era");
        assertTrue(era.contains("Topps Signature"), "Era fallback must mention brand");
        assertTrue(era.contains("serial-numbered chase cards"), "Era fallback must mention serial numbering");
    }
}
