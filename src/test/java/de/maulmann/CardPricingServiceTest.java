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

    @Test
    @DisplayName("Should use beckettValue as fallback in getEffectiveValue when estimatedValue is absent")
    void testEffectiveValueWithBeckettFallback() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .cardNumber("278")
                .beckettValue(1.25)
                .build();
        CardData cd = new CardData(c, "c-bv");

        assertEquals(1.25, CardPricingService.getEffectiveValue(cd));

        // When estimatedValue is present, it takes precedence
        CardJson cWithEst = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .cardNumber("278")
                .estimatedValue(5.0)
                .beckettValue(1.25)
                .build();
        CardData cdWithEst = new CardData(cWithEst, "c-est");
        assertEquals(5.0, CardPricingService.getEffectiveValue(cdWithEst));
    }

    @Test
    @DisplayName("Should give curated overrides precedence over standard market values in getEffectiveValue")
    void testEffectiveValueWithOverridePrecedence() {
        CardJson pmgCard = CardJson.builder()
                .id("1997-98-fleer-metal-universe-precious-metal-gems-red-33-pmg-sn47")
                .player("Juwan Howard")
                .season("1997-98")
                .estimatedValue(50.0) // Lower unverified estimate in json
                .build();
        CardData cdPmg = new CardData(pmgCard, "pmg-override-test");

        Double effective = CardPricingService.getEffectiveValue(cdPmg);
        assertNotNull(effective);
        assertEquals(1250.0, effective, 0.01, "Curated override ($1,250.00) must take precedence over $50.00");

        CardPricingService.ValuationDetails details = CardPricingService.getValuationDetails(cdPmg);
        assertNotNull(details);
        assertTrue(details.isOverride());
        assertEquals(1250.0, details.effectiveValue(), 0.01);
        assertEquals("eBay Historical / PWCC", details.source());
        assertEquals("Raw", details.grade());
    }

    @Test
    @DisplayName("Should calculate valuation details correctly across all fallback tiers")
    void testValuationDetailsFallbacks() {
        // Fallback to Market FMV
        CardJson cFmv = CardJson.builder().id("regular-card").estimatedValue(45.0).grade("PSA 9").build();
        CardData cdFmv = new CardData(cFmv, "cd-fmv");
        CardPricingService.ValuationDetails dFmv = CardPricingService.getValuationDetails(cdFmv);
        assertFalse(dFmv.isOverride());
        assertEquals(45.0, dFmv.effectiveValue());
        assertEquals("Market FMV", dFmv.source());
        assertEquals("PSA 9", dFmv.grade());

        // Fallback to Acquisition Cost
        CardJson cCost = CardJson.builder().id("cost-card").purchasePrice(12.50).build();
        CardData cdCost = new CardData(cCost, "cd-cost");
        CardPricingService.ValuationDetails dCost = CardPricingService.getValuationDetails(cdCost);
        assertEquals(12.50, dCost.effectiveValue());
        assertEquals("Acquisition Cost", dCost.source());
    }
}
