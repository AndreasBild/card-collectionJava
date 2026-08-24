package de.maulmann;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarketDataCache Storage & Lookup Tests")
class MarketDataCacheTest {

    @Test
    @DisplayName("Should store, retrieve, save, and reload market cache entries")
    void testCacheSaveAndLoad(@TempDir Path tempDir) throws IOException {
        Path cacheFile = tempDir.resolve("market-cache.json");

        MarketDataCache cache = new MarketDataCache();
        assertEquals(0, cache.size());

        PopReport pop = new PopReport("PSA", "10", 14, 0, "26215655", "https://www.psacard.com/cert/26215655");
        MarketDataEntry entry = MarketDataEntry.builder()
                .certNumber("26215655")
                .lastQueried("2026-08-24T10:00:00Z")
                .popReport(pop)
                .estimatedValue(185.00)
                .lastSoldPrice(175.00)
                .lastSoldDate("2025-11")
                .priceHistory(List.of(new PricePoint("2025-11", 175.00, "eBay Sold", "PSA 10")))
                .build();

        cache.put("1995-topps-finest-m20-card830", entry);
        assertEquals(1, cache.size());
        assertTrue(cache.contains("1995-topps-finest-m20-card830"));
        assertTrue(cache.containsCert("26215655"));

        // Save to disk
        cache.save(cacheFile);
        assertTrue(Files.exists(cacheFile));

        // Reload from disk
        MarketDataCache reloaded = MarketDataCache.load(cacheFile);
        assertEquals(1, reloaded.size());
        assertTrue(reloaded.get("1995-topps-finest-m20-card830").isPresent());
        assertTrue(reloaded.getByCert("26215655").isPresent());

        MarketDataEntry retrieved = reloaded.get("1995-topps-finest-m20-card830").get();
        assertEquals("26215655", retrieved.certNumber());
        assertEquals(185.00, retrieved.estimatedValue());
        assertEquals(1, retrieved.priceHistory().size());
        assertNotNull(retrieved.popReport());
        assertEquals(14, retrieved.popReport().totalGraded());
        assertEquals(0, retrieved.popReport().popHigher());
    }

    @Test
    @DisplayName("Should handle missing file gracefully when loading")
    void testLoadMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("non-existent-cache.json");
        MarketDataCache cache = MarketDataCache.load(missing);
        assertNotNull(cache);
        assertEquals(0, cache.size());
    }
}
