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
    @DisplayName("Should accurately parse Beckett BGS certificate HTML into MarketDataEntry")
    void testParseBgsHtml() {
        String mockHtml = """
            <div class="cert-row">
              <table>
                <tr><td class="label">Set Name</td><td class="value">1997-98 Fleer Metal Universe PMG Red</td></tr>
                <tr><td class="label">Final Grade</td><td class="value">9.5</td></tr>
                <tr><td class="label">Centering</td><td class="value">9.5</td></tr>
                <tr><td class="label">Corners</td><td class="value">9.0</td></tr>
                <tr><td class="label">Edges</td><td class="value">9.5</td></tr>
                <tr><td class="label">Surface</td><td class="value">10.0</td></tr>
                <tr><td class="label">Total Graded</td><td class="value">8</td></tr>
              </table>
            </div>
            """;

        PsaCertScraper scraper = new PsaCertScraper();
        Optional<MarketDataEntry> entryOpt = scraper.parseBgsHtml("0012345678", mockHtml);

        assertTrue(entryOpt.isPresent());
        MarketDataEntry entry = entryOpt.get();
        assertEquals("0012345678", entry.certNumber());

        PopReport pop = entry.popReport();
        assertNotNull(pop);
        assertEquals("BGS", pop.gradingCompany());
        assertEquals("9.5", pop.grade());
        assertEquals(8, pop.totalGraded());
        assertEquals("https://www.beckett.com/grading/cert-verification?cert_num=0012345678", pop.registryUrl());
        assertEquals("9.5", entry.metadata().get("centering"));
    }

    @Test
    @DisplayName("Should accurately parse SGC certificate HTML into MarketDataEntry")
    void testParseSgcHtml() {
        String mockHtml = """
            <div class="cert-detail">
              <dl>
                <dt class="label">Card Title</dt><dd class="value">1994-95 Finest Refractor Juwan Howard</dd>
                <dt class="label">SGC Grade</dt><dd class="value">10</dd>
                <dt class="label">Total Graded</dt><dd class="value">22</dd>
              </dl>
            </div>
            """;

        PsaCertScraper scraper = new PsaCertScraper();
        Optional<MarketDataEntry> entryOpt = scraper.parseSgcHtml("9876543", mockHtml);

        assertTrue(entryOpt.isPresent());
        PopReport pop = entryOpt.get().popReport();
        assertNotNull(pop);
        assertEquals("SGC", pop.gradingCompany());
        assertEquals("10", pop.grade());
        assertEquals(22, pop.totalGraded());
        assertEquals("https://gosgc.com/cert-verification/9876543", pop.registryUrl());
    }

    @Test
    @DisplayName("Should accurately parse CGC Cards certificate HTML into MarketDataEntry")
    void testParseCgcHtml() {
        String mockHtml = """
            <div class="cert-row">
              <table>
                <tr><th>Card Description</th><td>2018-19 Contenders Optic Gold Vinyl</td></tr>
                <tr><th>Grade</th><td>Pristine 10</td></tr>
                <tr><th>Total Population</th><td>1</td></tr>
              </table>
            </div>
            """;

        PsaCertScraper scraper = new PsaCertScraper();
        Optional<MarketDataEntry> entryOpt = scraper.parseCgcHtml("44332211", mockHtml);

        assertTrue(entryOpt.isPresent());
        PopReport pop = entryOpt.get().popReport();
        assertNotNull(pop);
        assertEquals("CGC", pop.gradingCompany());
        assertEquals("10", pop.grade());
        assertEquals(1, pop.totalGraded());
        assertEquals("https://www.cgccards.com/certlookup/44332211", pop.registryUrl());
    }

    @Test
    @DisplayName("Should return empty optional when HTML is blank or null")
    void testParseBlankHtml() {
        PsaCertScraper scraper = new PsaCertScraper();
        assertTrue(scraper.parsePsaHtml("12345", "").isEmpty());
        assertTrue(scraper.parsePsaHtml("12345", null).isEmpty());
        assertTrue(scraper.parseBgsHtml("12345", "").isEmpty());
        assertTrue(scraper.parseSgcHtml("12345", "").isEmpty());
        assertTrue(scraper.parseCgcHtml("12345", "").isEmpty());
        assertTrue(scraper.fetchCertData(null, "12345").isEmpty());
    }
}
