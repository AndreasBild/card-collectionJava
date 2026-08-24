package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Graded population census report and certificate verification data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PopReport(
        @JsonProperty("gradingCompany") String gradingCompany,
        @JsonProperty("grade") String grade,
        @JsonProperty("totalGraded") Integer totalGraded,
        @JsonProperty("popHigher") Integer popHigher,
        @JsonProperty("gradePop") Integer gradePop,
        @JsonProperty("certNumber") String certNumber,
        @JsonProperty("registryUrl") String registryUrl
) {
    public PopReport(Integer totalGraded, Integer popHigher, Integer gradePop, String certNumber) {
        this(null, null, totalGraded, popHigher, gradePop, certNumber, null);
    }

    public PopReport(String gradingCompany, String grade, Integer totalGraded, Integer popHigher, String certNumber, String registryUrl) {
        this(gradingCompany, grade, totalGraded, popHigher, null, certNumber, registryUrl);
    }

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
