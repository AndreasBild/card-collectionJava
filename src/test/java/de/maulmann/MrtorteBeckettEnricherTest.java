package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MrTorte Beckett Enricher Tests")
class MrtorteBeckettEnricherTest {

    @Test
    @DisplayName("Should parse hits table rows accurately")
    void testParseHits() {
        String mockHtml = """
            <table class="table">
                <tr><td>1994 Classic Comic SP Printer's Proof #103</td><td>one</td><td>975</td><td></td><td>$8,00</td></tr>
                <tr class="tablebackground"><td>1994 Classic Four Sport Autographs #5A</td><td>691</td><td>955</td><td>Michigan Wolverines</td><td>$12,00</td></tr>
                <tr><td>1994 Signature Rookies 24 Karat Gold Signatures #JUHO</td><td>1139</td><td>5000</td><td></td><td>n/a</td></tr>
            </table>
            """;

        MrtorteBeckettEnricher enricher = new MrtorteBeckettEnricher();
        List<MrtorteBeckettEnricher.ScrapedCard> cards = enricher.parseHits(mockHtml);

        assertEquals(3, cards.size());
        assertEquals("1994 Classic Comic SP Printer's Proof #103", cards.get(0).cardName());
        assertEquals(8.0, cards.get(0).beckettValue());
        assertNull(cards.get(0).serialNumber());
        assertEquals("975", cards.get(0).printRun());

        assertEquals("1994 Classic Four Sport Autographs #5A", cards.get(1).cardName());
        assertEquals(12.0, cards.get(1).beckettValue());
        assertEquals("691", cards.get(1).serialNumber());
        assertEquals("955", cards.get(1).printRun());
        assertEquals("Michigan Wolverines", cards.get(1).team());

        assertEquals("1994 Signature Rookies 24 Karat Gold Signatures #JUHO", cards.get(2).cardName());
        assertNull(cards.get(2).beckettValue());
    }

    @Test
    @DisplayName("Should parse commons table rows accurately")
    void testParseCommons() {
        String mockHtml = """
            <table class="table">
                <tr><td>1992-93 Michigan #3</td><td>$3,00</td><td>common</td></tr>
                <tr class="tablebackground"><td>1994 Classic #5</td><td>$0,50</td><td>common</td></tr>
            </table>
            """;

        MrtorteBeckettEnricher enricher = new MrtorteBeckettEnricher();
        List<MrtorteBeckettEnricher.ScrapedCard> cards = enricher.parseCommons(mockHtml);

        assertEquals(2, cards.size());
        assertEquals("1992-93 Michigan #3", cards.get(0).cardName());
        assertEquals(3.0, cards.get(0).beckettValue());
        assertEquals("1994 Classic #5", cards.get(1).cardName());
        assertEquals(0.5, cards.get(1).beckettValue());
    }

    @Test
    @DisplayName("Should match scraped card against CardData entity")
    void testFindBestMatch() {
        MrtorteBeckettEnricher enricher = new MrtorteBeckettEnricher();

        CardJson cardJson = new CardJson.Builder()
                .id("1994-95-collectors-choice-278")
                .season("1994-95")
                .brand("Collector's Choice")
                .cardNumber("278")
                .variant("Base")
                .build();
        CardData localCard = new CardData(cardJson);

        List<MrtorteBeckettEnricher.ScrapedCard> candidates = List.of(
                new MrtorteBeckettEnricher.ScrapedCard("1994-95 Collector's Choice #278 RC", 1.25, null, null, null, "Commons/Inserts"),
                new MrtorteBeckettEnricher.ScrapedCard("1994-95 Hoops #378", 0.60, null, null, null, "Commons/Inserts")
        );

        Optional<MrtorteBeckettEnricher.ScrapedCard> match = enricher.findBestMatch(localCard, candidates);
        assertTrue(match.isPresent());
        assertEquals("1994-95 Collector's Choice #278 RC", match.get().cardName());
        assertEquals(1.25, match.get().beckettValue());
    }

    @Test
    @DisplayName("Should prevent cross-matching between high-value parallels and base or cheap inserts")
    void testParallelVsBaseMatching() {
        MrtorteBeckettEnricher enricher = new MrtorteBeckettEnricher();

        // 1. PMG Red (#33 PMG) must NOT match "Total O #3"
        CardData pmgCard = new CardData(new CardJson.Builder()
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .cardNumber("33 PMG")
                .variant("Precious Metal Gems Red")
                .printRun(100)
                .build());

        List<MrtorteBeckettEnricher.ScrapedCard> pmgCandidates = List.of(
                new MrtorteBeckettEnricher.ScrapedCard("1997-98 Fleer Total O #3", 2.0, null, null, null, "Commons/Inserts"),
                new MrtorteBeckettEnricher.ScrapedCard("1997-98 Fleer Metal Universe #33", 1.0, null, null, null, "Commons/Inserts")
        );

        Optional<MrtorteBeckettEnricher.ScrapedCard> pmgMatch = enricher.findBestMatch(pmgCard, pmgCandidates);
        assertTrue(pmgMatch.isEmpty(), "PMG Red should not match cheap base or Total O insert");

        // 2. Base card must NOT match Legacy Collection parallel
        CardData baseCard = new CardData(new CardJson.Builder()
                .season("1997-98")
                .brand("Flair Showcase")
                .cardNumber("ROW 2 SEAT 64")
                .theme("Style")
                .variant("Base")
                .build());

        List<MrtorteBeckettEnricher.ScrapedCard> baseCandidates = List.of(
                new MrtorteBeckettEnricher.ScrapedCard("1997-98 Flair Showcase Legacy Collection Row 0 #64", 40.0, "77", "100", null, "Numbered/Hits"),
                new MrtorteBeckettEnricher.ScrapedCard("1997-98 Flair Showcase Legacy Collection Row 2 #64", 30.0, "86", "100", null, "Numbered/Hits"),
                new MrtorteBeckettEnricher.ScrapedCard("1997-98 Flair Showcase Row 2 #64", 0.75, null, null, null, "Commons/Inserts")
        );

        Optional<MrtorteBeckettEnricher.ScrapedCard> baseMatch = enricher.findBestMatch(baseCard, baseCandidates);
        assertTrue(baseMatch.isPresent());
        assertEquals("1997-98 Flair Showcase Row 2 #64", baseMatch.get().cardName());
        assertEquals(0.75, baseMatch.get().beckettValue());
    }
}
