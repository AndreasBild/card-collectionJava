package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/**
 * Historical transaction or price observation for a card entity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PricePoint(
        @JsonProperty("date") String date,
        @JsonProperty("price") double price,
        @JsonProperty("source") String source,
        @JsonProperty("grade") String grade
) {
    public PricePoint {
        if (source == null || source.isBlank()) {
            source = "Market Comp";
        }
    }

    public String formattedPrice() {
        return String.format(Locale.US, "$%.2f", price);
    }
}
