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
}
