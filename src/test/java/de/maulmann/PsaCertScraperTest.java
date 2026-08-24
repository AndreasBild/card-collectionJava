package de.maulmann;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PsaCertScraper HTML Parser Tests")
class PsaCertScraperTest {

    @Test
    @DisplayName("Should accurately parse PSA certificate table HTML into MarketDataEntry")
    void testParsePsaHtml() {
        String mockHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>PSA Cert Verification #26215655</title></head>
            <body>
              <div class="cert-details">
                <table>
                  <tr><th>Certification Number</th><td>26215655</td></tr>
                  <tr><th>Item Description</th><td>1995 TOPPS FINEST MYSTERY JUWAN HOWARD REFRACTOR-BORDERED TEST</td></tr>
                  <tr><th>Card Grade</th><td>GEM MT 10</td></tr>
                  <tr><th>Total Population</th><td>14</td></tr>
                  <tr><th>Population Higher</th><td>0</td></tr>
                </table>
              </div>
            </body>
            </html>
            """;

        PsaCertScraper scraper = new PsaCertScraper();
        Optional<MarketDataEntry> entryOpt = scraper.parsePsaHtml("26215655", mockHtml);

        assertTrue(entryOpt.isPresent());
        MarketDataEntry entry = entryOpt.get();
        assertEquals("26215655", entry.certNumber());

        PopReport pop = entry.popReport();
        assertNotNull(pop);
        assertEquals("PSA", pop.gradingCompany());
        assertEquals("10", pop.grade());
        assertEquals(14, pop.totalGraded());
        assertEquals(0, pop.popHigher());
        assertEquals("https://www.psacard.com/cert/26215655", pop.registryUrl());
    }

    @Test
    @DisplayName("Should return empty optional when HTML is blank or null")
    void testParseBlankHtml() {
        PsaCertScraper scraper = new PsaCertScraper();
        assertTrue(scraper.parsePsaHtml("12345", "").isEmpty());
        assertTrue(scraper.parsePsaHtml("12345", null).isEmpty());
    }
}
