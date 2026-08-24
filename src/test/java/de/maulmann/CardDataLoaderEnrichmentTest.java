package de.maulmann;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardDataLoader Market Data Overlay Tests")
class CardDataLoaderEnrichmentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Should transparently overlay market cache onto raw exported cards without modifying base JSON")
    void testTransparentMarketOverlay(@TempDir Path tempDir) throws IOException {
        // 1. Raw cards exported from database (without pop report or pricing)
        String rawCardsJson = """
            [
              {
                "id": "1995-topps-finest-mystery-m20-card830",
                "player": "Juwan Howard",
                "season": "1995-96",
                "team": "Washington Bullets",
                "company": "Topps",
                "brand": "Topps Finest",
                "cardNumber": "M20",
                "gradingCompany": "PSA",
                "grade": "10",
                "certNumber": "26215655"
              }
            ]
            """;

        Path cardsFile = tempDir.resolve("cards.json");
        Files.writeString(cardsFile, rawCardsJson);

        // 2. Populate market data cache
        MarketDataCache cache = new MarketDataCache();
        PopReport pop = new PopReport("PSA", "10", 14, 0, "26215655", "https://www.psacard.com/cert/26215655");
        MarketDataEntry entry = MarketDataEntry.builder()
                .certNumber("26215655")
                .lastQueried("2026-08-24T10:00:00Z")
                .popReport(pop)
                .estimatedValue(185.00)
                .lastSoldPrice(175.00)
                .lastSoldDate("2025-11")
                .purchasePrice(120.00)
                .priceHistory(List.of(
                        new PricePoint("2024-06", 150.00, "eBay Sold", "PSA 10"),
                        new PricePoint("2025-11", 175.00, "eBay Sold", "PSA 10")
                ))
                .build();

        cache.put("1995-topps-finest-mystery-m20-card830", entry);

        // 3. Load via CardDataLoader with market cache overlay
        List<CardJson> enrichedList = CardDataLoader.loadCardsFromJson(cardsFile.toString(), cache);
        assertEquals(1, enrichedList.size());

        CardJson card = enrichedList.get(0);
        assertEquals("Juwan Howard", card.player());
        assertEquals("26215655", card.certNumber());
        assertEquals(185.00, card.estimatedValue());
        assertEquals(175.00, card.lastSoldPrice());
        assertEquals("2025-11", card.lastSoldDate());
        assertEquals(120.00, card.purchasePrice());
        assertEquals(2, card.priceHistory().size());
        assertNotNull(card.popReport());
        assertEquals(14, card.popReport().totalGraded());
        assertEquals(0, card.popReport().popHigher());

        // 4. Construct CardData instance and verify derived properties
        CardData cardData = new CardData(card, "m20-test");
        assertEquals(185.00, cardData.estimatedValue);
        assertEquals(175.00, cardData.lastSoldPrice);
        assertEquals("2025-11", cardData.lastSoldDate);
        assertEquals(120.00, cardData.purchasePrice);
        assertEquals(2, cardData.priceHistory.size());
        assertEquals(14, cardData.popTotal);
        assertEquals(0, cardData.popHigher);
        assertEquals("https://www.psacard.com/cert/26215655", cardData.getVerificationUrl());
    }
}
