package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SportsCardsPro Parser & Valuation Matcher Tests")
class SportsCardsProParserTest {

    @Test
    @DisplayName("Should parse SportsCardsPro HTML table rows accurately")
    void testParseHtml() {
        String mockHtml = """
            <table id="games_table">
                <tbody>
                    <tr id="product-6304181" data-product="6304181">
                        <td class="title">
                            <a href="https://www.sportscardspro.com/game/123">Juwan Howard #103</a>
                        </td>
                        <td class="console">
                            <a href="/console/123">1994 Classic Draft (Basketball)</a>
                        </td>
                        <td class="price numeric used_price">
                            <span class="js-price">$1.42</span>
                        </td>
                        <td class="price numeric cib_price">
                            <span class="js-price">$11.50</span>
                        </td>
                        <td class="price numeric new_price">
                            <span class="js-price">$45.00</span>
                        </td>
                    </tr>
                </tbody>
            </table>
            """;

        SportsCardsProParser parser = new SportsCardsProParser();
        List<SportsCardsProParser.SportsCardsProItem> items = parser.parseHtml(mockHtml);

        assertNotNull(items);
        assertEquals(1, items.size());

        SportsCardsProParser.SportsCardsProItem item = items.get(0);
        assertEquals("6304181", item.productId());
        assertEquals("Juwan Howard #103", item.title());
        assertEquals("103", item.cardNumber());
        assertEquals("1994", item.year());
        assertEquals(1.42, item.ungradedPrice());
        assertEquals(11.50, item.grade9Price());
        assertEquals(45.00, item.psa10Price());
    }

    @Test
    @DisplayName("Should match SportsCardsPro item against CardData entity")
    void testFindMatchingCard() {
        SportsCardsProParser.SportsCardsProItem item = new SportsCardsProParser.SportsCardsProItem(
                "5670019",
                "Juwan Howard #378",
                "378",
                "1994 Hoops (Basketball)",
                "1994",
                1.48,
                9.70,
                19.76,
                "https://www.sportscardspro.com/game/5670019"
        );

        CardJson cardJson = new CardJson.Builder()
                .id("1994-hoops-378")
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Hoops")
                .cardNumber("378")
                .variant("Base")
                .gradingCompany("PSA")
                .grade("10")
                .build();
        CardData card = new CardData(cardJson);

        SportsCardsProParser parser = new SportsCardsProParser();
        var match = parser.findMatchingCard(item, List.of(card));

        assertTrue(match.isPresent());
        assertEquals("1994-hoops-378", match.get().id);

        Double price = parser.resolvePriceForCard(item, card);
        assertEquals(19.76, price, "PSA 10 card should resolve to PSA 10 price $19.76");
    }

    @Test
    @DisplayName("Should parse content/raw/sportscardspro-juwan-howard.html if present")
    void testParseLiveExportIfPresent() throws Exception {
        Path path = Paths.get("content/raw/sportscardspro-juwan-howard.html");
        if (java.nio.file.Files.exists(path)) {
            SportsCardsProParser parser = new SportsCardsProParser();
            List<SportsCardsProParser.SportsCardsProItem> items = parser.parseFile(path);
            assertFalse(items.isEmpty());
            assertEquals(100, items.size());
        }
    }
}
