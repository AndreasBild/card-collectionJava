package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("RainbowPageGenerator Tests")
class RainbowPageGeneratorTest {

    @Test
    @DisplayName("isVariantMatch should properly match variants and aliases including credentials")
    void testIsVariantMatch() {
        assertTrue(RainbowPageGenerator.isVariantMatch("Base", "base"));
        assertTrue(RainbowPageGenerator.isVariantMatch("Base Set", "Base"));
        assertTrue(RainbowPageGenerator.isVariantMatch("Single", "Single Diamond"));
        assertTrue(RainbowPageGenerator.isVariantMatch("Credentials Now", "Essential Credentials Now"));
        assertTrue(RainbowPageGenerator.isVariantMatch("Credentials Future", "Essential Credentials Future"));
        assertFalse(RainbowPageGenerator.isVariantMatch("Refractor", "Atomic Refractor"));
    }

    @Test
    @DisplayName("normalizeCardNumber should strip trailing labels and handle nulls")
    void testNormalizeCardNumber() {
        assertEquals("33", RainbowPageGenerator.normalizeCardNumber("33 PMG"));
        assertEquals("LC-JWH", RainbowPageGenerator.normalizeCardNumber("LC-JWH Refractor"));
        assertEquals("N/A", RainbowPageGenerator.normalizeCardNumber(null));
    }

    @Test
    @DisplayName("RainbowPageGenerator should generate rainbows.html with expanded master and dynamic sets")
    void testRainbowPageGenerator(@TempDir Path tempDir) throws IOException {
        String testCardsPath = "content/json/cards.json";
        File cardsFile = new File(testCardsPath);
        if (!cardsFile.exists()) {
            return; // Skip if dataset not available in local test environment
        }

        List<CardJson> allCards = FileGenerator.getCachedCards();
        assertNotNull(allCards);
        assertFalse(allCards.isEmpty());

        String outputDir = tempDir.toString() + File.separator;
        TimestampTracker tracker = new TimestampTracker(tempDir.resolve("timestamps.properties").toString());
        RainbowPageGenerator.buildRainbowsPage(allCards, tracker, outputDir);

        File outputFile = new File(outputDir + "rainbows.html");
        assertTrue(outputFile.exists(), "rainbows.html must be generated");

        String html = Files.readString(outputFile.toPath());
        assertFalse(html.isBlank());

        // Verify featured master sets are present
        assertTrue(html.contains("1999-00 Topps Gold Label #27 Master Rainbow"), "Gold Label master rainbow must be present");
        assertTrue(html.contains("1998-99 Fleer Flair Showcase #42 Master Rainbow"), "Flair Showcase master rainbow must be present");
        assertTrue(html.contains("1997-98 SkyBox E-X2001 #32 Essential Credentials Rainbow"), "E-X2001 credentials rainbow must be present");
        assertTrue(html.contains("1998-99 SkyBox E-X Century #34 Essential Credentials Rainbow"), "E-X Century credentials rainbow must be present");
        assertTrue(html.contains("1999-00 Fleer E-X #15 Essential Credentials Rainbow"), "E-X 1999-00 credentials rainbow must be present");
        assertTrue(html.contains("2003-04 Fleer E-X #25 Essential Credentials Rainbow"), "E-X 2003-04 credentials rainbow must be present");
        assertTrue(html.contains("1996-97 Topps Bowman&#39;s Best #33 Rainbow") || html.contains("Bowman"), "Bowman's Best rainbow must be present");
        assertTrue(html.contains("2018-19 Panini Contenders Optic Legendary Contenders Autographs #LC-JWH Rainbow"), "LC-JWH 1/1 rainbow must be present");

        // Verify newly added dynamic sets are present
        assertTrue(html.contains("2023-24 Topps Chrome Base Set #101 Rainbow"), "Topps Chrome #101 rainbow must be present");
        assertTrue(html.contains("HI-JH2"), "Leaf Metal Helloween #HI-JH2 must be present");
        assertTrue(html.contains("IA-JUW"), "Panini Spectra #IA-JUW must be present");

        // Verify checklist pills with seeking state support
        assertTrue(html.contains("checklist-pill"), "Checklist pills must be rendered");
    }
}
