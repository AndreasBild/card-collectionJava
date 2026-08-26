package de.maulmann;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Service for Card Market Pricing, Confirmed Sales Validation & Deep Auction Lookup Links.
 * Strictly adheres to verified confirmed sales from authorized sources (eBay, PSA, Fanatics, Self-Purchases)
 * for exact card and variant matches. Avoids synthetic price guesses or unverified estimates.
 */
public class MarketPriceFetcher {

    /**
     * Authorized channels for confirmed card sales comps.
     */
    public static final Set<String> CONFIRMED_SALES_SOURCES = Set.of(
            "ebay",
            "ebay sold",
            "psa",
            "psa apr",
            "psa auction prices realized",
            "fanatics",
            "fanatics collect",
            "pwcc",
            "goldin",
            "self purchase",
            "personal purchase",
            "manual",
            "collector purchase"
    );

    /**
     * Validates whether a given sales comp source is an authorized confirmed sales channel.
     */
    public static boolean isConfirmedSalesSource(String source) {
        if (source == null || source.isBlank()) return false;
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        return CONFIRMED_SALES_SOURCES.stream().anyMatch(normalized::contains);
    }

    /**
     * Verifies that a transaction comp represents an exact match for the player, brand/set, and variant.
     */
    public static boolean isExactMatch(CardData card, String candidatePlayer, String candidateBrand, String candidateVariant) {
        if (card == null) return false;

        String cardPlayer = normalizeToken(card.get("Player"));
        String candPlayer = normalizeToken(candidatePlayer);
        if (!cardPlayer.isBlank() && !candPlayer.isBlank() && !cardPlayer.equalsIgnoreCase(candPlayer)) {
            return false;
        }

        String cardBrand = normalizeToken(card.get("Brand"));
        String candBrand = normalizeToken(candidateBrand);
        if (!cardBrand.isBlank() && !candBrand.isBlank() && !cardBrand.equalsIgnoreCase(candBrand)) {
            return false;
        }

        String cardVariant = normalizeToken(card.get("Variant"));
        String candVariant = normalizeToken(candidateVariant);
        if (!cardVariant.isBlank() && !candVariant.isBlank() && !cardVariant.equalsIgnoreCase(candVariant)) {
            return false;
        }

        return true;
    }

    /**
     * Builds an optimized search query string for auction lookups (e.g. eBay, 130point, Fanatics).
     */
    public static String buildSearchQuery(CardData card) {
        if (card == null) return "";
        StringBuilder sb = new StringBuilder();

        String player = card.get("Player");
        if (player != null && !player.isBlank()) {
            sb.append(cleanQueryToken(player)).append(" ");
        }

        String season = card.get("Season");
        if (season != null && !season.isBlank()) {
            sb.append(cleanQueryToken(season)).append(" ");
        }

        String brand = card.get("Brand");
        String company = card.get("Company");
        if (brand != null && !brand.isBlank()) {
            sb.append(cleanQueryToken(brand)).append(" ");
        } else if (company != null && !company.isBlank()) {
            sb.append(cleanQueryToken(company)).append(" ");
        }

        String theme = card.get("Theme");
        if (theme != null && !theme.isBlank() && !theme.equalsIgnoreCase("Base") && !theme.equalsIgnoreCase("Base Set")) {
            sb.append(cleanQueryToken(theme)).append(" ");
        }

        String variant = card.get("Variant");
        if (variant != null && !variant.isBlank() && !variant.equalsIgnoreCase("Base")) {
            sb.append(cleanQueryToken(variant)).append(" ");
        }

        String number = card.get("Number");
        if (number != null && !number.isBlank() && !number.equals("—")) {
            sb.append("#").append(number).append(" ");
        }

        String grade = card.get("Grade");
        String grader = card.get("Grading Co.");
        if (grader != null && !grader.isBlank() && grade != null && !grade.isBlank() && !grade.equals("-") && !grader.equalsIgnoreCase("No")) {
            sb.append(cleanQueryToken(grader)).append(" ").append(cleanQueryToken(grade)).append(" ");
        }

        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * Constructs a 1-click deep search URL for 130point.com (cleared eBay Best Offer sales).
     */
    public static String build130PointUrl(CardData card) {
        String query = buildSearchQuery(card);
        return "https://130point.com/sales/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    /**
     * Constructs a 1-click deep search URL for eBay Sold & Completed listings.
     */
    public static String buildEbaySoldUrl(CardData card) {
        String query = buildSearchQuery(card);
        return "https://www.ebay.com/sch/i.html?_nkw=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&LH_Sold=1&LH_Complete=1";
    }

    /**
     * Constructs a 1-click search URL for PSA Auction Prices Realized (APR).
     */
    public static String buildPsaAprUrl(CardData card) {
        String query = buildSearchQuery(card);
        return "https://www.psacard.com/auctionprices#search=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    /**
     * Constructs a 1-click search URL for Fanatics Collect completed auctions.
     */
    public static String buildFanaticsCollectUrl(CardData card) {
        String query = buildSearchQuery(card);
        return "https://www.fanaticscollect.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    /**
     * Filters candidate price points to retain only verified confirmed sales matching authorized sources.
     */
    public static List<PricePoint> filterConfirmedPricePoints(List<PricePoint> points) {
        if (points == null || points.isEmpty()) return Collections.emptyList();
        return points.stream()
                .filter(p -> p != null && p.price() > 0.0 && isConfirmedSalesSource(p.source()))
                .toList();
    }

    private static String cleanQueryToken(String token) {
        if (token == null) return "";
        return token.replaceAll("[^a-zA-Z0-9\\-#/]", " ").trim();
    }

    private static String normalizeToken(String token) {
        if (token == null) return "";
        return token.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
