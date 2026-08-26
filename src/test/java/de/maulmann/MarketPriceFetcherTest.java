package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarketPriceFetcher Confirmed Sales & Exact Match Tests")
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
    @DisplayName("Should construct deep search lookup URLs for eBay, 130point, PSA APR, and Fanatics")
    void testLookupUrls() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("2023-24")
                .brand("Topps Chrome")
                .variant("Superfractor")
                .build();
        CardData cd = new CardData(c, "superfractor-test");

        String url130 = MarketPriceFetcher.build130PointUrl(cd);
        String ebayUrl = MarketPriceFetcher.buildEbaySoldUrl(cd);
        String psaAprUrl = MarketPriceFetcher.buildPsaAprUrl(cd);
        String fanaticsUrl = MarketPriceFetcher.buildFanaticsCollectUrl(cd);

        assertTrue(url130.startsWith("https://130point.com/sales/?q="));
        assertTrue(ebayUrl.contains("ebay.com/sch/i.html"));
        assertTrue(ebayUrl.contains("LH_Sold=1"));
        assertTrue(psaAprUrl.contains("psacard.com/auctionprices"));
        assertTrue(fanaticsUrl.contains("fanaticscollect.com/search"));
    }

    @Test
    @DisplayName("Should validate confirmed sales sources strictly (eBay, PSA, Fanatics, Self Purchase)")
    void testConfirmedSalesSourceValidation() {
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("eBay Sold"));
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("eBay"));
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("PSA APR"));
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("PSA Auction Prices Realized"));
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("Fanatics Collect"));
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("Self Purchase"));
        assertTrue(MarketPriceFetcher.isConfirmedSalesSource("Personal Purchase"));

        assertFalse(MarketPriceFetcher.isConfirmedSalesSource(null));
        assertFalse(MarketPriceFetcher.isConfirmedSalesSource(""));
        assertFalse(MarketPriceFetcher.isConfirmedSalesSource("Random Blog Forum"));
        assertFalse(MarketPriceFetcher.isConfirmedSalesSource("Synthetic Estimate"));
    }

    @Test
    @DisplayName("Should verify exact matches of player, brand, and card variant")
    void testExactMatchValidation() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .brand("Topps Finest")
                .variant("Gold Refractor")
                .build();
        CardData cd = new CardData(c, "finest-gold");

        assertTrue(MarketPriceFetcher.isExactMatch(cd, "Juwan Howard", "Topps Finest", "Gold Refractor"));
        assertFalse(MarketPriceFetcher.isExactMatch(cd, "Michael Jordan", "Topps Finest", "Gold Refractor"));
        assertFalse(MarketPriceFetcher.isExactMatch(cd, "Juwan Howard", "Fleer Metal", "Gold Refractor"));
        assertFalse(MarketPriceFetcher.isExactMatch(cd, "Juwan Howard", "Topps Finest", "Base"));
    }

    @Test
    @DisplayName("Should filter price history points to only keep confirmed sales")
    void testFilterConfirmedPricePoints() {
        List<PricePoint> points = List.of(
                new PricePoint("2024-01", 120.0, "eBay Sold", "PSA 10"),
                new PricePoint("2024-03", 135.0, "Fanatics Collect", "PSA 10"),
                new PricePoint("2024-05", -10.0, "eBay Sold", "PSA 10"),
                new PricePoint("2024-06", 140.0, "Unverified Source", "PSA 10")
        );

        List<PricePoint> filtered = MarketPriceFetcher.filterConfirmedPricePoints(points);
        assertEquals(2, filtered.size());
        assertEquals("eBay Sold", filtered.get(0).source());
        assertEquals("Fanatics Collect", filtered.get(1).source());
    }
}
