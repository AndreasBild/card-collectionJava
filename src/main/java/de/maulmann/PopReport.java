package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Graded population census report and certificate verification data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PopReport(
        @JsonProperty("totalGraded") Integer totalGraded,
        @JsonProperty("popHigher") Integer popHigher,
        @JsonProperty("gradePop") Integer gradePop,
        @JsonProperty("certNumber") String certNumber
) {

    /**
     * Resolves the official third-party grader verification URL.
     */
    public static String getVerificationUrl(String gradingCompany, String certNumber) {
        if (gradingCompany == null || certNumber == null || certNumber.isBlank()) {
            return null;
        }
        String co = gradingCompany.trim().toUpperCase();
        String cert = certNumber.trim();
        if (co.contains("PSA")) {
            return "https://www.psacard.com/cert/" + cert;
        } else if (co.contains("BGS") || co.contains("BECKETT")) {
            return "https://www.beckett.com/grading/cert-verification?cert_num=" + cert;
        } else if (co.contains("SGC")) {
            return "https://gosgc.com/cert-verification/" + cert;
        } else if (co.contains("CGC")) {
            return "https://www.cgccards.com/certlookup/" + cert;
        }
        return null;
    }
}
