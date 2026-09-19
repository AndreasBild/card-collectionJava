package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardDataLoader Service Tests")
class CardDataLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("loadCardsFromJson should successfully parse valid JSON dataset from disk")
    void testLoadCardsFromJsonSuccess() throws IOException {
        Path jsonFile = tempDir.resolve("cards_test.json");
        String jsonContent = """
            [
              {
                "player": "Juwan Howard",
                "season": "1997-98",
                "team": "Washington Bullets",
                "company": "Fleer",
                "brand": "Metal Universe",
                "variant": "Precious Metal Gems Red",
                "cardNumber": "33",
                "serialNumber": "47",
                "printRun": 100,
                "isRookie": false,
                "isAutograph": false,
                "isPatch": false
              }
            ]
            """;
        Files.writeString(jsonFile, jsonContent);

        List<CardJson> cards = CardDataLoader.loadCardsFromJson(jsonFile.toString());
        assertNotNull(cards);
        assertEquals(1, cards.size());

        CardJson card = cards.getFirst();
        assertEquals("Juwan Howard", card.player());
        assertEquals("1997-98", card.season());
        assertEquals("Washington Bullets", card.team());
        assertEquals("Fleer", card.company());
        assertEquals("Metal Universe", card.brand());
        assertEquals("Precious Metal Gems Red", card.variant());
        assertEquals("33", card.cardNumber());
        assertEquals("47", card.serialNumber());
        assertEquals(100, card.printRun());
        assertFalse(card.isRookie());
        assertFalse(card.isAutograph());
        assertFalse(card.isPatch());
    }

    @Test
    @DisplayName("loadCardsFromJson should return empty list when file does not exist")
    void testLoadCardsFromJsonNonExistent() {
        Path nonExistent = tempDir.resolve("does_not_exist.json");
        List<CardJson> cards = CardDataLoader.loadCardsFromJson(nonExistent.toString());
        assertNotNull(cards);
        assertTrue(cards.isEmpty(), "Non-existent path must return empty list");
    }

    @Test
    @DisplayName("loadCardsFromJson should return empty list on malformed JSON")
    void testLoadCardsFromJsonMalformed() throws IOException {
        Path malformedFile = tempDir.resolve("malformed.json");
        Files.writeString(malformedFile, "{ invalid json structure ");

        List<CardJson> cards = CardDataLoader.loadCardsFromJson(malformedFile.toString());
        assertNotNull(cards);
        assertTrue(cards.isEmpty(), "Malformed JSON must return empty list without throwing");
    }

    @Test
    @DisplayName("CardData helper methods getRawImageBase and getPrimaryPlayer should work as expected")
    void testCardDataHelpers() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard, Calbert Cheaney")
                .season("1997-98")
                .company("Fleer")
                .brand("SkyBox")
                .variant("Base")
                .cardNumber("10")
                .build();
        CardData cardData = new CardData(c, "unique123");

        assertEquals("Juwan Howard", cardData.getPrimaryPlayer());
        assertEquals(cardData.filenameBase.substring(0, cardData.filenameBase.lastIndexOf("-")), cardData.getRawImageBase());
    }

    @Test
    @DisplayName("loadCardsFromJson should enrich cards with BeckettValueCache")
    void testLoadCardsFromJsonWithBeckettCache() throws IOException {
        Path jsonFile = tempDir.resolve("cards_bv_test.json");
        String jsonContent = """
            [
              {
                "id": "1994-95-collectors-choice-278",
                "player": "Juwan Howard",
                "season": "1994-95",
                "brand": "Collector's Choice",
                "cardNumber": "278"
              }
            ]
            """;
        Files.writeString(jsonFile, jsonContent);

        BeckettValueCache bvCache = new BeckettValueCache();
        bvCache.put("1994-95-collectors-choice-278", BeckettValueEntry.builder()
                .cardName("1994-95 Collector's Choice #278 RC")
                .beckettValue(1.25)
                .category("Commons/Inserts")
                .build());

        List<CardJson> cards = CardDataLoader.loadCardsFromJson(jsonFile.toString(), new MarketDataCache(), bvCache);
        assertNotNull(cards);
        assertEquals(1, cards.size());
        CardJson enriched = cards.getFirst();
        assertEquals(1.25, enriched.beckettValue());
        assertEquals(1.25, enriched.estimatedValue(), "Estimated value should fallback to beckettValue when not set");

        CardData cardData = new CardData(enriched);
        assertEquals(1.25, cardData.beckettValue);
        assertEquals("$1.25", cardData.attributes.get("Beckett Value"));
        assertEquals(1.25, CardPricingService.getEffectiveValue(cardData));
    }
}
