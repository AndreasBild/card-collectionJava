package de.maulmann;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CardJson {
    public String id;
    public String player;
    public String season;
    public String team;
    public String company;
    public String brand;
    public String theme;
    public String variant;

    @JsonProperty("cardNumber")
    public String cardNumber;

    @JsonProperty("serialNumber")
    public String serialNumber;

    @JsonProperty("printRun")
    public Integer printRun;

    @JsonProperty("gradingCompany")
    public String gradingCompany;

    public String grade;
    public String collection;
    public String notes;

    @JsonProperty("isAutograph")
    public boolean isAutograph;

    @JsonProperty("isPatch")
    public boolean isPatch;

    @JsonProperty("isRookie")
    public boolean isRookie;

    public String get(String key) {
        if (key == null) return null;
        switch (key.toLowerCase()) {
            case "player": return player;
            case "season": return season;
            case "team": return team;
            case "company": return company;
            case "brand": return brand;
            case "theme": return theme;
            case "variant": return variant;
            case "number":
            case "cardnumber": return cardNumber;
            case "serial":
            case "serialnumber": return serialNumber;
            case "grading co.":
            case "gradingcompany": return gradingCompany;
            case "grade": return grade;
            case "notes": return notes;
            case "collection": return collection;
            default: return null;
        }
    }
}
