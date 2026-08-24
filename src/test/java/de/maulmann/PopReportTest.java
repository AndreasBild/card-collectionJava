package de.maulmann;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PopReport & Cert Verification Tests")
class PopReportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Should generate accurate official verification URLs for grading companies")
    void testGetVerificationUrl() {
        assertEquals("https://www.psacard.com/cert/48921054",
                PopReport.getVerificationUrl("PSA", "48921054"));

        assertEquals("https://www.psacard.com/cert/12345678",
                PopReport.getVerificationUrl("Professional Sports Authenticator (PSA)", "12345678"));

        assertEquals("https://www.beckett.com/grading/cert-verification?cert_num=0014295831",
                PopReport.getVerificationUrl("BGS", "0014295831"));

        assertEquals("https://gosgc.com/cert-verification/9482710",
                PopReport.getVerificationUrl("SGC", "9482710"));

        assertEquals("https://www.cgccards.com/certlookup/12345",
                PopReport.getVerificationUrl("CGC", "12345"));

        assertNull(PopReport.getVerificationUrl(null, "12345"));
        assertNull(PopReport.getVerificationUrl("PSA", null));
        assertNull(PopReport.getVerificationUrl("PSA", "   "));
        assertNull(PopReport.getVerificationUrl("UnknownGrader", "12345"));
    }

    @Test
    @DisplayName("Should populate CardData cert verification URL when certNumber is provided")
    void testCardDataVerificationUrl() {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .gradingCompany("PSA")
                .grade("10")
                .certNumber("48921054")
                .build();

        CardData cd = new CardData(c, "psa-10-test");
        assertEquals("48921054", cd.certNumber);
        assertEquals("https://www.psacard.com/cert/48921054", cd.getVerificationUrl());
    }

    @Test
    @DisplayName("Should deserialize different cert number JSON property aliases from exporter")
    void testJsonAliasesForCertNumber() throws Exception {
        String json1 = """
            {"player": "Juwan Howard", "season": "1995-96", "brand": "Topps Finest", "gradingCompany": "PSA", "grade": "10", "gradingCertNumber": "48921054"}
            """;
        CardJson c1 = MAPPER.readValue(json1, CardJson.class);
        assertEquals("48921054", c1.certNumber());

        String json2 = """
            {"player": "Juwan Howard", "season": "1995-96", "brand": "Topps Finest", "grading_company": "BGS", "grade": "9.5", "cert_number": "0014295831"}
            """;
        CardJson c2 = MAPPER.readValue(json2, CardJson.class);
        assertEquals("0014295831", c2.certNumber());
        assertEquals("BGS", c2.gradingCompany());

        String json3 = """
            {"player": "Juwan Howard", "season": "1995-96", "brand": "Topps Finest", "gradingCompany": "PSA", "grade": "9", "psaNumber": "88776655"}
            """;
        CardJson c3 = MAPPER.readValue(json3, CardJson.class);
        assertEquals("88776655", c3.certNumber());
    }

    @Test
    @DisplayName("StableId and URL path must be strictly unaffected by adding a certNumber")
    void testStableIdAndUrlUnaffectedByCertNumber() {
        CardJson baseCard = CardJson.builder()
                .player("Juwan Howard")
                .season("1995-96")
                .company("Topps")
                .brand("Topps Finest")
                .variant("Refractor")
                .cardNumber("M20")
                .gradingCompany("PSA")
                .grade("10")
                .build();

        CardJson certCard = CardJson.builder()
                .player("Juwan Howard")
                .season("1995-96")
                .company("Topps")
                .brand("Topps Finest")
                .variant("Refractor")
                .cardNumber("M20")
                .gradingCompany("PSA")
                .grade("10")
                .certNumber("48921054")
                .build();

        CardData cdBase = new CardData(baseCard, null);
        CardData cdCert = new CardData(certCard, null);

        assertEquals(cdBase.stableId, cdCert.stableId, "Stable ID hash must remain 100% identical");
        assertEquals(cdBase.filenameBase, cdCert.filenameBase, "Filename base must remain 100% identical");
        assertEquals(cdBase.filename, cdCert.filename, "Filename must remain 100% identical");
        assertEquals(cdBase.fullRelativePath, cdCert.fullRelativePath, "URL relative path must remain 100% identical");
    }
}
