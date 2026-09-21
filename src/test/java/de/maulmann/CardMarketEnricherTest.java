package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CardMarketEnricher Tests")
class CardMarketEnricherTest {

    @Test
    @DisplayName("Should merge, deduplicate, and sort price history chronologically")
    void testMergePriceHistory() {
        PricePoint p1 = new PricePoint("2025-01-15", 100.0, "130point", "Raw");
        PricePoint p2 = new PricePoint("2025-03-20", 120.0, "eBay", "Raw");
        PricePoint p3Duplicate = new PricePoint("2025-01-15", 100.0, "130point", "Raw");
        PricePoint p4Newer = new PricePoint("2025-06-01", 150.0, "PWCC", "Raw");
        PricePoint p0Older = new PricePoint("2024-11-10", 90.0, "eBay", "Raw");

        List<PricePoint> existing = List.of(p1, p2);
        List<PricePoint> incoming = List.of(p0Older, p3Duplicate, p4Newer);

        List<PricePoint> merged = CardMarketEnricher.mergePriceHistory(existing, incoming);

        assertNotNull(merged);
        assertEquals(4, merged.size(), "Should have 4 unique points after deduplication");

        // Verify sorted chronologically
        assertEquals("2024-11-10", merged.get(0).date());
        assertEquals(90.0, merged.get(0).price());

        assertEquals("2025-01-15", merged.get(1).date());
        assertEquals(100.0, merged.get(1).price());

        assertEquals("2025-03-20", merged.get(2).date());
        assertEquals(120.0, merged.get(2).price());

        assertEquals("2025-06-01", merged.get(3).date());
        assertEquals(150.0, merged.get(3).price());
    }

    @Test
    @DisplayName("Should handle empty and null collections in mergePriceHistory")
    void testMergePriceHistoryEmptyAndNull() {
        PricePoint p1 = new PricePoint("2026-01-01", 50.0, "130point", "Raw");

        assertEquals(List.of(p1), CardMarketEnricher.mergePriceHistory(List.of(p1), null));
        assertEquals(List.of(p1), CardMarketEnricher.mergePriceHistory(null, List.of(p1)));
        assertEquals(List.of(p1), CardMarketEnricher.mergePriceHistory(List.of(p1), List.of()));
        assertTrue(CardMarketEnricher.mergePriceHistory(null, null).isEmpty());
        assertTrue(CardMarketEnricher.mergePriceHistory(List.of(), List.of()).isEmpty());
    }
}
