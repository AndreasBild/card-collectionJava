package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("130point Sales Comps Client & HTML Parser Tests")
class Point130ClientTest {

    @Test
    @DisplayName("Should build accurate search query for raw and graded cards")
    void testBuildSearchQuery() {
        CardJson rawCardJson = new CardJson.Builder()
                .id("card-1")
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Collectors Choice")
                .cardNumber("278")
                .variant("Silver")
                .build();
        CardData rawCard = new CardData(rawCardJson);

        String query = Point130Client.buildSearchQuery(rawCard);
        assertTrue(query.contains("1994"));
        assertTrue(query.contains("Juwan Howard"));
        assertTrue(query.contains("Collectors Choice"));
        assertTrue(query.contains("#278"));
        assertTrue(query.contains("Silver"));

        CardJson gradedCardJson = new CardJson.Builder()
                .id("card-2")
                .player("Juwan Howard")
                .season("1995-96")
                .brand("Topps Finest")
                .cardNumber("M20")
                .gradingCompany("PSA")
                .grade("10")
                .build();
        CardData gradedCard = new CardData(gradedCardJson);

        String gradedQuery = Point130Client.buildSearchQuery(gradedCard);
        assertTrue(gradedQuery.contains("PSA 10"));

        CardJson serialCardJson = new CardJson.Builder()
                .id("card-3")
                .player("Juwan Howard")
                .season("1996-97")
                .brand("SkyBox E-2000")
                .cardNumber("79")
                .variant("Credentials")
                .printRun(499)
                .build();
        CardData serialCard = new CardData(serialCardJson);
        String serialQuery = Point130Client.buildSearchQuery(serialCard);
        assertTrue(serialQuery.contains("1996"));
        assertTrue(serialQuery.contains("SkyBox E-2000"));
        assertTrue(serialQuery.contains("#79"));
        assertTrue(serialQuery.contains("Credentials"));
        assertTrue(serialQuery.contains("/499"));
    }

    @Test
    @DisplayName("Should parse 130point sales HTML and extract comps and FMV")
    void testParseSalesHtml() {
        String mockHtml = """
            <table id="salesDataTable-0" class="display compact resultsData salesTable">
                <tbody>
                    <tr id="dRow" data-price="12.50" data-rowId="0-1" data-currency="USD">
                        <td id="dCol">
                            <span id="titleText"><a href="https://ebay.com/itm/123">1994-95 Collectors Choice #278 Juwan Howard RC</a></span>
                            <span id="auctionLabel">Auction</span>
                            <span id="dateText"><b>Date:</b> Mon 10 Jan 2026 12:00:00 GMT</span>
                        </td>
                    </tr>
                    <tr id="dRow" data-price="15.00" data-rowId="0-2" data-currency="USD">
                        <td id="dCol">
                            <span id="titleText"><a href="https://ebay.com/itm/456">1994 Collectors Choice Juwan Howard #278 Rookie PSA 9</a></span>
                            <span id="auctionLabel">Best Offer Accepted</span>
                            <span id="dateText"><b>Date:</b> Fri 20 Feb 2026 15:30:00 GMT</span>
                        </td>
                    </tr>
                </tbody>
            </table>
            """;

        CardJson refJson = new CardJson.Builder()
                .id("1994-collectors-choice-278")
                .player("Juwan Howard")
                .cardNumber("278")
                .build();
        CardData refCard = new CardData(refJson);

        Point130Client client = new Point130Client();
        Point130Client.CardCompResult result = client.parseSalesHtml(mockHtml, refCard);

        assertNotNull(result);
        assertEquals(2, result.comps().size());

        PricePoint comp1 = result.comps().get(0);
        assertEquals(12.50, comp1.price());
        assertEquals("2026-01-10", comp1.date());
        assertTrue(comp1.source().contains("Auction"));

        PricePoint comp2 = result.comps().get(1);
        assertEquals(15.00, comp2.price());
        assertEquals("2026-02-20", comp2.date());
        assertEquals("PSA 9", comp2.grade());

        assertEquals(15.00, result.lastSoldPrice());
        assertEquals("2026-02-20", result.lastSoldDate());
        assertEquals(13.75, result.estimatedValue(), 0.01);
    }

    @Test
    @DisplayName("Should filter out irrelevant comps with non-matching card numbers or players")
    void testRelevanceFiltering() {
        Point130Client client = new Point130Client();

        CardJson targetCard = new CardJson.Builder()
                .player("Juwan Howard")
                .cardNumber("278")
                .build();
        CardData card = new CardData(targetCard);

        assertTrue(client.isRelevantMatch("1994 Collectors Choice #278 Juwan Howard RC", card));
        assertTrue(client.isRelevantMatch("1994-95 Upper Deck Collectors Choice Juwan Howard Card 278", card));
        assertFalse(client.isRelevantMatch("1994 Collectors Choice #100 Chris Webber", card));
        assertFalse(client.isRelevantMatch("1994 Collectors Choice #15 Juwan Howard", card));
    }

    @Test
    @DisplayName("Should handle empty HTML gracefully without exceptions")
    void testEmptyHtmlHandling() {
        Point130Client client = new Point130Client();
        Point130Client.CardCompResult result = client.parseSalesHtml("", null);

        assertNotNull(result);
        assertTrue(result.comps().isEmpty());
        assertNull(result.estimatedValue());
        assertNull(result.lastSoldPrice());
    }

    @Test
    @DisplayName("Should correctly filter extreme outliers using IQR in calculateTrimmedFmv")
    void testIqrOutlierFiltering() {
        // Series with normal sales around $50-$60 and one massive outlier ($500)
        java.util.List<PricePoint> points = java.util.List.of(
                new PricePoint("2026-01-01", 50.0, "eBay", "Raw"),
                new PricePoint("2026-01-02", 52.0, "eBay", "Raw"),
                new PricePoint("2026-01-03", 55.0, "eBay", "Raw"),
                new PricePoint("2026-01-04", 58.0, "eBay", "Raw"),
                new PricePoint("2026-01-05", 500.0, "eBay Fake/Lot", "Raw")
        );

        Double fmv = Point130Client.calculateTrimmedFmv(points);
        assertNotNull(fmv);
        // The outlier $500 should be rejected by IQR filtering, resulting in a median around 53.5
        assertTrue(fmv < 100.0, "FMV should reject $500 outlier, was " + fmv);
        assertEquals(53.5, fmv, 2.0);
    }

    @Test
    @DisplayName("Should parse multi-currency price strings and symbols cleanly")
    void testMultiCurrencyParsing() {
        String mockHtml = """
            <table class="salesTable">
                <tbody>
                    <tr id="dRow" data-price="£24.99">
                        <td><span id="titleText">1994 Collectors Choice #278 Juwan Howard</span><span id="dateText">10 Jan 2026</span></td>
                    </tr>
                    <tr id="dRow" data-price="€30,50">
                        <td><span id="titleText">1994 Collectors Choice #278 Juwan Howard</span><span id="dateText">15 Jan 2026</span></td>
                    </tr>
                </tbody>
            </table>
            """;

        CardJson refJson = new CardJson.Builder()
                .id("card-currency")
                .player("Juwan Howard")
                .cardNumber("278")
                .build();
        CardData refCard = new CardData(refJson);

        Point130Client client = new Point130Client();
        Point130Client.CardCompResult result = client.parseSalesHtml(mockHtml, refCard);

        assertNotNull(result);
        assertEquals(2, result.comps().size());
        assertEquals(24.99, result.comps().get(0).price(), 0.01);
    }
}
