package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable cached market and population census data for a trading card.
 * Enables transparent build-time enrichment without database migrations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketDataEntry(
        @JsonProperty("certNumber") String certNumber,
        @JsonProperty("lastQueried") String lastQueried,
        @JsonProperty("popReport") PopReport popReport,
        @JsonProperty("estimatedValue") Double estimatedValue,
        @JsonProperty("lastSoldPrice") Double lastSoldPrice,
        @JsonProperty("lastSoldDate") String lastSoldDate,
        @JsonProperty("purchasePrice") Double purchasePrice,
        @JsonProperty("priceHistory") List<PricePoint> priceHistory,
        @JsonProperty("metadata") Map<String, String> metadata
) {
    public MarketDataEntry {
        if (priceHistory == null) {
            priceHistory = Collections.emptyList();
        } else {
            priceHistory = Collections.unmodifiableList(priceHistory);
        }
        if (metadata == null) {
            metadata = Collections.emptyMap();
        } else {
            metadata = Collections.unmodifiableMap(metadata);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String certNumber;
        private String lastQueried;
        private PopReport popReport;
        private Double estimatedValue;
        private Double lastSoldPrice;
        private String lastSoldDate;
        private Double purchasePrice;
        private List<PricePoint> priceHistory = Collections.emptyList();
        private Map<String, String> metadata = Collections.emptyMap();

        public Builder certNumber(String certNumber) { this.certNumber = certNumber; return this; }
        public Builder lastQueried(String lastQueried) { this.lastQueried = lastQueried; return this; }
        public Builder popReport(PopReport popReport) { this.popReport = popReport; return this; }
        public Builder estimatedValue(Double estimatedValue) { this.estimatedValue = estimatedValue; return this; }
        public Builder lastSoldPrice(Double lastSoldPrice) { this.lastSoldPrice = lastSoldPrice; return this; }
        public Builder lastSoldDate(String lastSoldDate) { this.lastSoldDate = lastSoldDate; return this; }
        public Builder purchasePrice(Double purchasePrice) { this.purchasePrice = purchasePrice; return this; }
        public Builder priceHistory(List<PricePoint> priceHistory) { this.priceHistory = priceHistory; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public MarketDataEntry build() {
            return new MarketDataEntry(
                    certNumber, lastQueried, popReport, estimatedValue,
                    lastSoldPrice, lastSoldDate, purchasePrice, priceHistory, metadata
            );
        }
    }
}
