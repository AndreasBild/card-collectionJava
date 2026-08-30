package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TCDB Parser & Entity Matcher Tests")
class TcdbParserTest {

    @Test
    @DisplayName("Should parse TCDB JSON export and extract sid, cid, card numbers")
    void testParseJsonLiveExportIfPresent() throws Exception {
        Path path = Paths.get("content/raw/tcdb-juwan-howard-all.json");
        if (java.nio.file.Files.exists(path)) {
            TcdbParser parser = new TcdbParser();
            List<TcdbParser.TcdbCardItem> items = parser.parseJson(path);

            assertNotNull(items);
            assertFalse(items.isEmpty(), "Should parse items from TCDB JSON");

            TcdbParser.TcdbCardItem item = items.get(0);
            assertNotNull(item.sid(), "sid must be present");
            assertNotNull(item.cid(), "cid must be present");
            assertNotNull(item.url(), "url must be present");
            assertTrue(item.url().contains("ViewCard.cfm"));
        }
    }

    @Test
    @DisplayName("Should match TCDB item against CardData catalog entity")
    void testFindMatchingCard() {
        TcdbParser.TcdbCardItem item = new TcdbParser.TcdbCardItem(
                "59823",
                "5051549",
                "1994 Classic Draft #103 Juwan Howard SP",
                "103",
                "1994",
                "1994 Classic Draft",
                null,
                null,
                "https://www.tcdb.com/ViewCard.cfm/sid/59823/cid/5051549/1994-Classic-Draft-103-Juwan-Howard"
        );

        CardJson cardJson = new CardJson.Builder()
                .id("1994-classic-draft-103")
                .player("Juwan Howard")
                .season("1994")
                .brand("Classic Draft")
                .cardNumber("103")
                .build();
        CardData card = new CardData(cardJson);

        TcdbParser parser = new TcdbParser();
        var match = parser.findMatchingCard(item, List.of(card));

        assertTrue(match.isPresent(), "Must match Classic Draft #103");
        assertEquals("1994-classic-draft-103", match.get().id);
    }
}
