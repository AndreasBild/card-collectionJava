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
        assertEquals("Juwan Howard", card.player);
        assertEquals("1997-98", card.season);
        assertEquals("Washington Bullets", card.team);
        assertEquals("Fleer", card.company);
        assertEquals("Metal Universe", card.brand);
        assertEquals("Precious Metal Gems Red", card.variant);
        assertEquals("33", card.cardNumber);
        assertEquals("47", card.serialNumber);
        assertEquals(100, card.printRun);
        assertFalse(card.isRookie);
        assertFalse(card.isAutograph);
        assertFalse(card.isPatch);
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
}
