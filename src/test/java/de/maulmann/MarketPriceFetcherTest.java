package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarketPriceFetcher Query & Scarcity Valuation Tests")
class MarketPriceFetcherTest {

    @Test
    @DisplayName("Should build clean, readable search queries for cards")
    void testBuildSearchQuery() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1995-96")
                .company("Topps")
                .brand("Topps Finest")
                .theme("Mystery Bordered Test")
                .variant("Refractor")
                .cardNumber("M20")
                .gradingCompany("PSA")
                .grade("10")
                .build();

        CardData cd = new CardData(c, "m20-test");
        String query = MarketPriceFetcher.buildSearchQuery(cd);

        assertTrue(query.contains("Juwan Howard"));
        assertTrue(query.contains("1995-96"));
        assertTrue(query.contains("Topps Finest"));
        assertTrue(query.contains("Refractor"));
        assertTrue(query.contains("#M20"));
        assertTrue(query.contains("PSA 10"));
    }

    @Test
    @DisplayName("Should assign higher valuations to 1-of-1 and low serial numbered cards")
    void testValuationScarcityTiers() {
        CardJson baseCard = CardJson.builder()
                .player("Juwan Howard")
                .season("1995-96")
                .company("Topps")
                .brand("Topps")
                .cardNumber("100")
                .build();
        MarketDataEntry baseEntry = MarketPriceFetcher.estimateMarketData(new CardData(baseCard, null));

        CardJson lowSerialCard = CardJson.builder()
                .player("Juwan Howard")
                .season("2023-24")
                .brand("Topps Chrome")
                .serialNumber("3/5")
                .printRun(5)
                .build();
        MarketDataEntry lowSerialEntry = MarketPriceFetcher.estimateMarketData(new CardData(lowSerialCard, null));

        CardJson oneOfOneCard = CardJson.builder()
                .player("Juwan Howard")
                .season("2023-24")
                .brand("Topps Chrome")
                .variant("Superfractor")
                .serialNumber("1/1")
                .printRun(1)
                .gradingCompany("PSA")
                .grade("10")
                .build();
        MarketDataEntry oneOfOneEntry = MarketPriceFetcher.estimateMarketData(new CardData(oneOfOneCard, null));

        assertNotNull(baseEntry);
        assertNotNull(lowSerialEntry);
        assertNotNull(oneOfOneEntry);

        assertTrue(lowSerialEntry.estimatedValue() > baseEntry.estimatedValue(),
                "Low serial numbered card should have significantly higher FMV than base");
        assertTrue(oneOfOneEntry.estimatedValue() > lowSerialEntry.estimatedValue(),
                "PSA 10 1-of-1 Superfractor should have higher FMV than /5 serial");

        // Verify comps trajectory
        assertFalse(oneOfOneEntry.priceHistory().isEmpty());
        assertEquals(3, oneOfOneEntry.priceHistory().size());
        assertTrue(oneOfOneEntry.lastSoldPrice() > 0);
        assertNotNull(oneOfOneEntry.lastSoldDate());
    }

    @Test
    @DisplayName("Should apply autograph and patch memorabilia multipliers")
    void testAutographAndPatchMultipliers() {
        CardJson autoPatchCard = CardJson.builder()
                .player("Juwan Howard")
                .season("2012-13")
                .brand("Panini National Treasures")
                .isAutograph(true)
                .isPatch(true)
                .printRun(25)
                .build();

        MarketDataEntry entry = MarketPriceFetcher.estimateMarketData(new CardData(autoPatchCard, null));
        assertNotNull(entry);
        assertTrue(entry.estimatedValue() >= 100.0, "Autographed patch card should command premium FMV");
    }
}
