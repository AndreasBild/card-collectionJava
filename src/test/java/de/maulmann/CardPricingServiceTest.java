package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardPricingService Tests")
class CardPricingServiceTest {

    @Test
    @DisplayName("Should compute portfolio summary correctly across multiple cards")
    void testCalculatePortfolioSummary() {
        CardJson c1 = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Topps Finest")
                .estimatedValue(200.0)
                .purchasePrice(150.0)
                .build();

        CardJson c2 = CardJson.builder()
                .player("Juwan Howard")
                .season("1995-96")
                .brand("Fleer Metal")
                .lastSoldPrice(300.0)
                .purchasePrice(200.0)
                .build();

        CardJson c3 = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .brand("SkyBox")
                .build();

        CardData cd1 = new CardData(c1, "c1");
        CardData cd2 = new CardData(c2, "c2");
        CardData cd3 = new CardData(c3, "c3");

        CardPricingService.PortfolioSummary summary = CardPricingService.calculatePortfolioSummary(List.of(cd1, cd2, cd3));

        assertNotNull(summary);
        assertEquals(500.0, summary.totalEstimatedValue(), 0.01);
        assertEquals(350.0, summary.totalPurchaseCost(), 0.01);
        assertEquals(150.0, summary.totalGainLoss(), 0.01);
        assertEquals(42.857, summary.totalGainLossPct(), 0.01);
        assertEquals(2, summary.cardsPricedCount());
        assertEquals(2, summary.topValuableCards().size());
        assertEquals("c2", summary.topValuableCards().getFirst().card().stableId);
        assertEquals(300.0, summary.topValuableCards().getFirst().value());
    }

    @Test
    @DisplayName("Should calculate growth percentage from price history points")
    void testCalculateGrowthPct() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1996-97")
                .priceHistory(List.of(
                        new PricePoint("2023-01", 100.0, "eBay", "PSA 9"),
                        new PricePoint("2025-01", 150.0, "eBay", "PSA 9")
                ))
                .build();

        CardData cd = new CardData(c, "growth-test");
        Double growth = CardPricingService.calculateGrowthPct(cd);

        assertNotNull(growth);
        assertEquals(50.0, growth, 0.01);
    }

    @Test
    @DisplayName("Should detect collector features on CardData correctly")
    void testCollectorFeatures() {
        CardJson jerseyCard = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .serialNumber("#05")
                .printRun(50)
                .build();
        CardData cdJersey = new CardData(jerseyCard, "jersey-5");
        assertTrue(cdJersey.isJerseyNumberMatch(), "Juwan Howard #05 must match jersey number");

        CardJson oneOfOne = CardJson.builder()
                .player("Juwan Howard")
                .variant("1/1 Masterpiece")
                .build();
        CardData cd1of1 = new CardData(oneOfOne, "1of1");
        assertTrue(cd1of1.isOneOfOne(), "Must identify 1/1 Masterpiece variant");

        CardJson bookend = CardJson.builder()
                .serialNumber("1")
                .printRun(100)
                .build();
        CardData cdBookend = new CardData(bookend, "bookend");
        assertTrue(cdBookend.isBookendSerial(), "Must identify #1/100 as bookend serial");

        CardJson refractor = CardJson.builder()
                .variant("Atomic Refractor")
                .build();
        CardData cdRefractor = new CardData(refractor, "refractor");
        assertTrue(cdRefractor.isRefractorOrFoil(), "Must identify Atomic Refractor as foil/refractor");
    }
}
