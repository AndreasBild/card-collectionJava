package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable record representing a verified valuation or auction comp override.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValuationOverride(
        Double estimatedValue,
        Double lastSoldPrice,
        String lastSoldDate,
        String grade,
        String source,
        boolean verified,
        String url,
        String notes
) {
    public ValuationOverride {
        if (grade == null || grade.isBlank()) {
            grade = "Raw";
        }
    }
}
