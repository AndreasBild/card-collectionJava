package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ValuationOverridesLoader Tests")
class ValuationOverridesLoaderTest {

    @Test
    @DisplayName("Should load default curated valuation overrides from content/json/valuation-overrides.json")
    void testLoadDefault() {
        ValuationOverridesLoader loader = ValuationOverridesLoader.loadDefault();
        assertNotNull(loader);
        assertTrue(loader.size() >= 3, "Should contain at least 3 curated overrides");

        Optional<ValuationOverride> pmgRed = loader.getOverride("1997-98-fleer-metal-universe-precious-metal-gems-red-33-pmg-sn47");
        assertTrue(pmgRed.isPresent());
        assertEquals(1250.0, pmgRed.get().estimatedValue());
        assertEquals(1249.99, pmgRed.get().lastSoldPrice());
        assertEquals("Raw", pmgRed.get().grade());
        assertTrue(pmgRed.get().verified());

        Optional<ValuationOverride> pmgGreen = loader.getOverride("1997-98-fleer-metal-universe-precious-metal-gems-green-33-pmg-sn7");
        assertTrue(pmgGreen.isPresent());
        assertEquals(8500.0, pmgGreen.get().estimatedValue());
        assertEquals("BGS 8.5", pmgGreen.get().grade());

        Optional<ValuationOverride> legacyRow0 = loader.getOverride("1997-98-flair-showcase-showcase-legacy-collection-row-0-seat-64-sn69");
        assertTrue(legacyRow0.isPresent());
        assertEquals(960.0, legacyRow0.get().estimatedValue());
        assertEquals("PSA 10", legacyRow0.get().grade());
    }

    @Test
    @DisplayName("Should handle case insensitivity and whitespace in card ID lookups")
    void testCaseInsensitivity() {
        ValuationOverridesLoader loader = ValuationOverridesLoader.loadDefault();
        assertTrue(loader.hasOverride("  1997-98-FLEER-METAL-UNIVERSE-PRECIOUS-METAL-GEMS-RED-33-PMG-SN47  "));
        assertTrue(loader.getOverride("1997-98-FLEER-metal-universe-PRECIOUS-metal-gems-RED-33-pmg-sn47").isPresent());
    }

    @Test
    @DisplayName("Should load custom overrides and handle missing files gracefully")
    void testLoadCustomOverrides(@TempDir Path tempDir) throws IOException {
        Path customPath = tempDir.resolve("custom-overrides.json");
        String json = """
                {
                  "test-card-1": {
                    "estimatedValue": 450.0,
                    "lastSoldPrice": 420.0,
                    "lastSoldDate": "2026-01-10",
                    "grade": "PSA 9",
                    "source": "Heritage Auctions",
                    "verified": true
                  }
                }
                """;
        Files.writeString(customPath, json);

        ValuationOverridesLoader customLoader = ValuationOverridesLoader.load(customPath);
        assertEquals(1, customLoader.size());
        Optional<ValuationOverride> opt = customLoader.getOverride("test-card-1");
        assertTrue(opt.isPresent());
        assertEquals(450.0, opt.get().estimatedValue());
        assertEquals("PSA 9", opt.get().grade());

        // Test non-existent file
        ValuationOverridesLoader missingLoader = ValuationOverridesLoader.load(tempDir.resolve("non-existent.json"));
        assertNotNull(missingLoader);
        assertEquals(0, missingLoader.size());
        assertFalse(missingLoader.hasOverride("test-card-1"));
    }
}
