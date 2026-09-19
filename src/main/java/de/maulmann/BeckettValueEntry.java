package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable cached Beckett Book Value (BV) entry for a trading card.
 * Sourced from MrTorte Beckett checklist archives.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BeckettValueEntry(
        @JsonProperty("cardName") String cardName,
        @JsonProperty("beckettValue") Double beckettValue,
        @JsonProperty("serialNumber") String serialNumber,
        @JsonProperty("printRun") String printRun,
        @JsonProperty("team") String team,
        @JsonProperty("category") String category,
        @JsonProperty("scrapedAt") String scrapedAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String cardName;
        private Double beckettValue;
        private String serialNumber;
        private String printRun;
        private String team;
        private String category;
        private String scrapedAt;

        public Builder cardName(String cardName) {
            this.cardName = cardName;
            return this;
        }

        public Builder beckettValue(Double beckettValue) {
            this.beckettValue = beckettValue;
            return this;
        }

        public Builder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder printRun(String printRun) {
            this.printRun = printRun;
            return this;
        }

        public Builder team(String team) {
            this.team = team;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder scrapedAt(String scrapedAt) {
            this.scrapedAt = scrapedAt;
            return this;
        }

        public BeckettValueEntry build() {
            return new BeckettValueEntry(cardName, beckettValue, serialNumber, printRun, team, category, scrapedAt);
        }
    }
}
