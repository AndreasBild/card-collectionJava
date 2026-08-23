package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HtmlToJsonConverter Table Parser Tests")
class HtmlToJsonConverterTest {

    @Test
    @DisplayName("parseTableToCardJson should extract structured CardJson records from HTML table")
    void testParseTableToCardJson() {
        String html = """
            <table>
              <thead>
                <tr>
                  <th>Player</th>
                  <th>Season</th>
                  <th>Team</th>
                  <th>Brand</th>
                  <th>Variant</th>
                  <th>Card Number</th>
                  <th>Serial/Print Run</th>
                  <th>Rookie</th>
                  <th>Autograph</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Juwan Howard</td>
                  <td>1997-98</td>
                  <td>Washington Bullets</td>
                  <td>Metal Universe</td>
                  <td>PMG Red</td>
                  <td>#33</td>
                  <td>47/100</td>
                  <td>No</td>
                  <td>No</td>
                </tr>
                <tr>
                  <td>Juwan Howard</td>
                  <td>1994-95</td>
                  <td>Washington Bullets</td>
                  <td>Finest</td>
                  <td>Refractor</td>
                  <td>220</td>
                  <td>—</td>
                  <td>Yes</td>
                  <td>No</td>
                </tr>
              </tbody>
            </table>
            """;

        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table");
        assertNotNull(table);

        List<CardJson> cards = HtmlToJsonConverter.parseTableToCardJson(table);
        assertEquals(2, cards.size());

        CardJson card1 = cards.get(0);
        assertEquals("Juwan Howard", card1.player());
        assertEquals("1997-98", card1.season());
        assertEquals("Washington Bullets", card1.team());
        assertEquals("Metal Universe", card1.brand());
        assertEquals("PMG Red", card1.variant());
        assertEquals("#33", card1.cardNumber());
        assertEquals("47", card1.serialNumber());
        assertEquals(100, card1.printRun());
        assertFalse(card1.isRookie());
        assertFalse(card1.isAutograph());

        CardJson card2 = cards.get(1);
        assertEquals("1994-95", card2.season());
        assertEquals("Finest", card2.brand());
        assertEquals("Refractor", card2.variant());
        assertEquals("220", card2.cardNumber());
        assertEquals("", card2.serialNumber(), "Em-dash serial number must normalize to empty string");
        assertTrue(card2.isRookie());
    }

    @Test
    @DisplayName("parseTableToCardJson should return empty list for empty or headerless table")
    void testParseEmptyTable() {
        Document doc = Jsoup.parse("<table></table>");
        Element table = doc.selectFirst("table");
        assertNotNull(table);

        List<CardJson> cards = HtmlToJsonConverter.parseTableToCardJson(table);
        assertTrue(cards.isEmpty());
    }
}
