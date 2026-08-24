package de.maulmann;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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
        @JsonProperty("cardNumber") @JsonAlias({"card_number", "cardNo", "number"}) String cardNumber,
        @JsonProperty("serialNumber") @JsonAlias({"serial_number", "serialNo", "serial"}) String serialNumber,
        @JsonProperty("printRun") @JsonAlias({"print_run", "printrun"}) Integer printRun,
        @JsonProperty("gradingCompany") @JsonAlias({"grading_company", "gradingCo", "grader", "grading_company_name"}) String gradingCompany,
        @JsonProperty("grade") @JsonAlias({"cardGrade", "gradingGrade", "card_grade"}) String grade,
        @JsonProperty("certNumber") @JsonAlias({"gradingCertNumber", "grading_cert_number", "gradingCert", "cert_number", "psaNumber", "psaCertNumber", "certificateNumber", "cert", "gradingCertificate"}) String certNumber,
        String collection,
        String notes,
        @JsonProperty("isAutograph") boolean isAutograph,
        @JsonProperty("isPatch") boolean isPatch,
        @JsonProperty("isRookie") boolean isRookie,
        @JsonProperty("estimatedValue") Double estimatedValue,
        @JsonProperty("lastSoldPrice") Double lastSoldPrice,
        @JsonProperty("lastSoldDate") String lastSoldDate,
        @JsonProperty("purchasePrice") Double purchasePrice,
        @JsonProperty("priceHistory") List<PricePoint> priceHistory,
        @JsonProperty("popReport") PopReport popReport,
        @JsonProperty("popTotal") Integer popTotal,
        @JsonProperty("popHigher") Integer popHigher
) {

    public CardJson(
            String id,
            String player,
            String season,
            String team,
            String company,
            String brand,
            String theme,
            String variant,
            String cardNumber,
            String serialNumber,
            Integer printRun,
            String gradingCompany,
            String grade,
            String collection,
            String notes,
            boolean isAutograph,
            boolean isPatch,
            boolean isRookie
    ) {
        this(
                id, player, season, team, company, brand, theme, variant,
                cardNumber, serialNumber, printRun, gradingCompany, grade, null,
                collection, notes, isAutograph, isPatch, isRookie,
                null, null, null, null, null, null, null, null
        );
    }

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
            case "cert number", "certnumber", "psa number" -> certNumber != null ? certNumber : (popReport != null ? popReport.certNumber() : null);
            case "print run", "printrun" -> printRun != null ? String.valueOf(printRun) : null;
            case "notes" -> notes;
            case "collection" -> collection;
            case "autograph", "auto", "isautograph" -> isAutograph ? "Yes" : "No";
            case "memorabilia", "game used", "patch", "ispatch", "mem / patch" -> isPatch ? "Yes" : "No";
            case "rookie", "rookie card", "isrookie" -> isRookie ? "Yes" : "No";
            default -> null;
        };
    }

    public CardJson enrichWith(MarketDataEntry entry) {
        if (entry == null) return this;
        Builder b = builder()
                .id(this.id)
                .player(this.player)
                .season(this.season)
                .team(this.team)
                .company(this.company)
                .brand(this.brand)
                .theme(this.theme)
                .variant(this.variant)
                .cardNumber(this.cardNumber)
                .serialNumber(this.serialNumber)
                .printRun(this.printRun)
                .gradingCompany(this.gradingCompany != null ? this.gradingCompany : (entry.popReport() != null ? entry.popReport().gradingCompany() : null))
                .grade(this.grade != null ? this.grade : (entry.popReport() != null ? entry.popReport().grade() : null))
                .certNumber(this.certNumber != null ? this.certNumber : entry.certNumber())
                .collection(this.collection)
                .notes(this.notes)
                .isAutograph(this.isAutograph)
                .isPatch(this.isPatch)
                .isRookie(this.isRookie)
                .estimatedValue(this.estimatedValue != null ? this.estimatedValue : entry.estimatedValue())
                .lastSoldPrice(this.lastSoldPrice != null ? this.lastSoldPrice : entry.lastSoldPrice())
                .lastSoldDate(this.lastSoldDate != null ? this.lastSoldDate : entry.lastSoldDate())
                .purchasePrice(this.purchasePrice != null ? this.purchasePrice : entry.purchasePrice())
                .priceHistory(this.priceHistory != null && !this.priceHistory.isEmpty() ? this.priceHistory : entry.priceHistory())
                .popReport(this.popReport != null ? this.popReport : entry.popReport())
                .popTotal(this.popTotal != null ? this.popTotal : (entry.popReport() != null ? entry.popReport().totalGraded() : null))
                .popHigher(this.popHigher != null ? this.popHigher : (entry.popReport() != null ? entry.popReport().popHigher() : null));
        return b.build();
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
        private String certNumber;
        private String collection;
        private String notes;
        private boolean isAutograph;
        private boolean isPatch;
        private boolean isRookie;
        private Double estimatedValue;
        private Double lastSoldPrice;
        private String lastSoldDate;
        private Double purchasePrice;
        private List<PricePoint> priceHistory;
        private PopReport popReport;
        private Integer popTotal;
        private Integer popHigher;

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
        public Builder certNumber(String certNumber) { this.certNumber = certNumber; return this; }
        public Builder collection(String collection) { this.collection = collection; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }
        public Builder isAutograph(boolean isAutograph) { this.isAutograph = isAutograph; return this; }
        public Builder isPatch(boolean isPatch) { this.isPatch = isPatch; return this; }
        public Builder isRookie(boolean isRookie) { this.isRookie = isRookie; return this; }
        public Builder estimatedValue(Double estimatedValue) { this.estimatedValue = estimatedValue; return this; }
        public Builder lastSoldPrice(Double lastSoldPrice) { this.lastSoldPrice = lastSoldPrice; return this; }
        public Builder lastSoldDate(String lastSoldDate) { this.lastSoldDate = lastSoldDate; return this; }
        public Builder purchasePrice(Double purchasePrice) { this.purchasePrice = purchasePrice; return this; }
        public Builder priceHistory(List<PricePoint> priceHistory) { this.priceHistory = priceHistory; return this; }
        public Builder popReport(PopReport popReport) { this.popReport = popReport; return this; }
        public Builder popTotal(Integer popTotal) { this.popTotal = popTotal; return this; }
        public Builder popHigher(Integer popHigher) { this.popHigher = popHigher; return this; }

        public CardJson build() {
            return new CardJson(
                    id, player, season, team, company, brand, theme, variant,
                    cardNumber, serialNumber, printRun, gradingCompany, grade, certNumber,
                    collection, notes, isAutograph, isPatch, isRookie,
                    estimatedValue, lastSoldPrice, lastSoldDate, purchasePrice,
                    priceHistory, popReport, popTotal, popHigher
            );
        }
    }
}
