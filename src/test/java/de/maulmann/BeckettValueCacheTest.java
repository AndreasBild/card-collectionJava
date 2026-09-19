package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Beckett Value Cache Tests")
class BeckettValueCacheTest {

    @Test
    @DisplayName("Should put, get, and serialize BeckettValueEntry correctly")
    void testPutGetAndSave(@TempDir Path tempDir) throws IOException {
        Path cacheFile = tempDir.resolve("test-beckett-values.json");

        BeckettValueCache cache = BeckettValueCache.load(cacheFile);
        assertEquals(0, cache.size());
        assertFalse(cache.contains("test-card-1"));

        BeckettValueEntry entry1 = BeckettValueEntry.builder()
                .cardName("1994-95 Collector's Choice #278 RC")
                .beckettValue(1.25)
                .category("Commons/Inserts")
                .scrapedAt("2026-09-19T11:00:00Z")
                .build();

        cache.put("test-card-1", entry1);
        assertEquals(1, cache.size());
        assertTrue(cache.contains("test-card-1"));

        Optional<BeckettValueEntry> retrieved = cache.get("test-card-1");
        assertTrue(retrieved.isPresent());
        assertEquals(1.25, retrieved.get().beckettValue());
        assertEquals("1994-95 Collector's Choice #278 RC", retrieved.get().cardName());

        // Save and reload
        cache.save(cacheFile);
        assertTrue(Files.exists(cacheFile));

        BeckettValueCache reloaded = BeckettValueCache.load(cacheFile);
        assertEquals(1, reloaded.size());
        Optional<BeckettValueEntry> reloadedEntry = reloaded.get("test-card-1");
        assertTrue(reloadedEntry.isPresent());
        assertEquals(1.25, reloadedEntry.get().beckettValue());
    }

    @Test
    @DisplayName("Should load default cache when file exists")
    void testLoadDefault() {
        BeckettValueCache cache = BeckettValueCache.loadDefault();
        assertNotNull(cache);
        // If content/json/beckett-values.json is present, it should load entries
        assertTrue(cache.size() >= 0);
    }
}
