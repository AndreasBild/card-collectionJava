package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PopReport & Cert Verification Tests")
class PopReportTest {

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
}
