package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardStatsService Tests")
class CardStatsServiceTest {

    @Test
    @DisplayName("computeCollectionStats should accurately calculate counts and percentages")
    void testComputeCollectionStats() {
        CardJson c1 = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("Precious Metal Gems Green")
                .cardNumber("33")
                .serialNumber("1/1")
                .printRun(1)
                .isAutograph(true)
                .isPatch(true)
                .isRookie(false)
                .gradingCompany("BGS")
                .grade("9.5")
                .build();

        CardJson c2 = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Topps Finest")
                .variant("Refractor")
                .cardNumber("220")
                .serialNumber("5/10")
                .printRun(10)
                .isAutograph(false)
                .isPatch(false)
                .isRookie(true)
                .gradingCompany("PSA")
                .grade("10")
                .build();

        CardJson c3 = CardJson.builder()
                .player("Juwan Howard")
                .season("1998-99")
                .brand("UD Black Diamond")
                .variant("Single")
                .cardNumber("89")
                .isRookie(false)
                .build();

        List<CardJson> cards = List.of(c1, c2, c3);
        Map<String, Object> stats = CardStatsService.computeCollectionStats(cards);

        assertEquals(3, stats.get("rawTotalCards"));
        assertEquals("3", stats.get("totalCards"));

        assertEquals(1, stats.get("count1of1"));
        assertEquals(2, stats.get("countUltraSp")); // c1 (/1) + c2 (/10)
        assertEquals(2, stats.get("countSerialized"));
        assertEquals(1, stats.get("countAutographs"));
        assertEquals(1, stats.get("countPatches"));
        assertEquals(1, stats.get("countRookies"));
        assertEquals(2, stats.get("countGradedTotal"));
        assertEquals(2, stats.get("countGemMint")); // BGS 9.5 and PSA 10
    }

    @Test
    @DisplayName("isOneOfOneMasterpiece should identify 1/1 cards and exclude plates/proofs")
    void testIsOneOfOneMasterpiece() {
        CardJson masterpiece = CardJson.builder()
                .variant("Nebula 1/1")
                .printRun(1)
                .build();
        assertTrue(CardStatsService.isOneOfOneMasterpiece(masterpiece));

        CardJson printingPlate = CardJson.builder()
                .variant("Black Printing Plate 1/1")
                .printRun(1)
                .build();
        assertFalse(CardStatsService.isOneOfOneMasterpiece(printingPlate), "Printing plates must be excluded");

        CardJson proof = CardJson.builder()
                .variant("Pre Production Proof 1/1")
                .printRun(1)
                .build();
        assertFalse(CardStatsService.isOneOfOneMasterpiece(proof), "Proof cards must be excluded");
    }

    @Test
    @DisplayName("filterDuplicateJsonCards should keep numbered cards and drop unnumbered duplicates")
    void testFilterDuplicateJsonCards() {
        CardJson dup1 = CardJson.builder()
                .season("1995-96")
                .brand("Topps")
                .variant("Base")
                .cardNumber("100")
                .build();

        CardJson dup2 = CardJson.builder()
                .season("1995-96")
                .brand("Topps")
                .variant("Base")
                .cardNumber("100")
                .build();

        CardJson serialized = CardJson.builder()
                .season("1995-96")
                .brand("Topps")
                .variant("Base")
                .cardNumber("100")
                .serialNumber("25/100")
                .build();

        List<CardJson> filtered = CardStatsService.filterDuplicateJsonCards(List.of(dup1, dup2, serialized));
        assertEquals(2, filtered.size(), "Should drop one unnumbered duplicate and keep serialized card");
    }

    @Test
    @DisplayName("formatSerialAndPrintRun should format correctly with serial and print run combinations")
    void testFormatSerialAndPrintRun() {
        assertEquals("7/10", CardStatsService.formatSerialAndPrintRun("7", 10, "Fallback"));
        assertEquals("7/10", CardStatsService.formatSerialAndPrintRun("7/10", 10, "Fallback"));
        assertEquals("#7", CardStatsService.formatSerialAndPrintRun("7", null, "Fallback"));
        assertEquals("/100", CardStatsService.formatSerialAndPrintRun(null, 100, "Fallback"));
        assertEquals("Custom Fallback", CardStatsService.formatSerialAndPrintRun(null, null, "Custom Fallback"));
        assertEquals("Parallel", CardStatsService.formatSerialAndPrintRun(null, null, null));
    }
}
