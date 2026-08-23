package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable Java 26 Record representation of a Trading Card dataset entry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardJson(
        String id,
        String player,
        String season,
        String team,
        String company,
        String brand,
        String theme,
        String variant,
        @JsonProperty("cardNumber") String cardNumber,
        @JsonProperty("serialNumber") String serialNumber,
        @JsonProperty("printRun") Integer printRun,
        @JsonProperty("gradingCompany") String gradingCompany,
        String grade,
        String collection,
        String notes,
        @JsonProperty("isAutograph") boolean isAutograph,
        @JsonProperty("isPatch") boolean isPatch,
        @JsonProperty("isRookie") boolean isRookie
) {

    public String get(String key) {
        if (key == null) return null;
        return switch (key.toLowerCase()) {
            case "player" -> player;
            case "season" -> season;
            case "team" -> team;
            case "company" -> company;
            case "brand" -> brand;
            case "theme" -> theme;
            case "variant" -> variant;
            case "number", "cardnumber" -> cardNumber;
            case "serial", "serialnumber" -> serialNumber;
            case "grading co.", "gradingcompany" -> gradingCompany;
            case "grade" -> grade;
            case "print run", "printrun" -> printRun != null ? String.valueOf(printRun) : null;
            case "notes" -> notes;
            case "collection" -> collection;
            case "autograph", "auto", "isautograph" -> isAutograph ? "Yes" : "No";
            case "memorabilia", "game used", "patch", "ispatch", "mem / patch" -> isPatch ? "Yes" : "No";
            case "rookie", "rookie card", "isrookie" -> isRookie ? "Yes" : "No";
            default -> null;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String player;
        private String season;
        private String team;
        private String company;
        private String brand;
        private String theme;
        private String variant;
        private String cardNumber;
        private String serialNumber;
        private Integer printRun;
        private String gradingCompany;
        private String grade;
        private String collection;
        private String notes;
        private boolean isAutograph;
        private boolean isPatch;
        private boolean isRookie;

        public Builder id(String id) { this.id = id; return this; }
        public Builder player(String player) { this.player = player; return this; }
        public Builder season(String season) { this.season = season; return this; }
        public Builder team(String team) { this.team = team; return this; }
        public Builder company(String company) { this.company = company; return this; }
        public Builder brand(String brand) { this.brand = brand; return this; }
        public Builder theme(String theme) { this.theme = theme; return this; }
        public Builder variant(String variant) { this.variant = variant; return this; }
        public Builder cardNumber(String cardNumber) { this.cardNumber = cardNumber; return this; }
        public Builder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }
        public Builder printRun(Integer printRun) { this.printRun = printRun; return this; }
        public Builder gradingCompany(String gradingCompany) { this.gradingCompany = gradingCompany; return this; }
        public Builder grade(String grade) { this.grade = grade; return this; }
        public Builder collection(String collection) { this.collection = collection; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }
        public Builder isAutograph(boolean isAutograph) { this.isAutograph = isAutograph; return this; }
        public Builder isPatch(boolean isPatch) { this.isPatch = isPatch; return this; }
        public Builder isRookie(boolean isRookie) { this.isRookie = isRookie; return this; }

        public CardJson build() {
            return new CardJson(
                    id, player, season, team, company, brand, theme, variant,
                    cardNumber, serialNumber, printRun, gradingCompany, grade,
                    collection, notes, isAutograph, isPatch, isRookie
            );
        }
    }
}
