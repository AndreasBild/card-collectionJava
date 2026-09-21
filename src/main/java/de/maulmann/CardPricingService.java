package de.maulmann;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Service for calculating card valuations, portfolio summaries, and price trends.
 */
public class CardPricingService {

    private static final NumberFormat USD_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    public record ValuableCardEntry(CardData card, double value, int rank) {}

    public record PortfolioSummary(
            double totalEstimatedValue,
            double totalPurchaseCost,
            double totalGainLoss,
            double totalGainLossPct,
            int cardsPricedCount,
            List<ValuableCardEntry> topValuableCards,
            Map<String, Double> eraValuations
    ) {}

    /**
     * Calculates the aggregate portfolio valuation and analytics across all cards.
     */
    public static PortfolioSummary calculatePortfolioSummary(List<CardData> allCards) {
        if (allCards == null || allCards.isEmpty()) {
            return new PortfolioSummary(0.0, 0.0, 0.0, 0.0, 0, List.of(), Map.of());
        }

        double totalValue = 0.0;
        double totalCost = 0.0;
        int pricedCount = 0;

        List<ValuableCardEntry> pricedList = new ArrayList<>();
        Map<String, Double> eraValuations = new TreeMap<>();

        for (CardData c : allCards) {
            Double val = getEffectiveValue(c);
            if (val != null && val > 0.0) {
                totalValue += val;
                pricedCount++;
                pricedList.add(new ValuableCardEntry(c, val, 0));

                String season = c.get("Season");
                String eraKey = (season != null && !season.isBlank()) ? season : "Unknown Era";
                eraValuations.merge(eraKey, val, Double::sum);
            }

            if (c.purchasePrice != null && c.purchasePrice > 0.0) {
                totalCost += c.purchasePrice;
            }
        }

        pricedList.sort(Comparator.comparingDouble(ValuableCardEntry::value).reversed());
        List<ValuableCardEntry> topCards = new ArrayList<>();
        for (int i = 0; i < Math.min(10, pricedList.size()); i++) {
            ValuableCardEntry entry = pricedList.get(i);
            topCards.add(new ValuableCardEntry(entry.card(), entry.value(), i + 1));
        }

        double gainLoss = totalValue - totalCost;
        double gainLossPct = (totalCost > 0.0) ? ((gainLoss / totalCost) * 100.0) : 0.0;

        return new PortfolioSummary(
                totalValue, totalCost, gainLoss, gainLossPct, pricedCount,
                topCards, eraValuations
        );
    }

    private static final SimpleLazyConstant<ValuationOverridesLoader> OVERRIDES_LOADER =
            SimpleLazyConstant.of(ValuationOverridesLoader::loadDefault);

    public record ValuationDetails(
            Double effectiveValue,
            String source,
            String grade,
            Double growthPct,
            boolean isOverride
    ) {}

    /**
     * Determines the most accurate current market value (override, estimated value, last sold price, or history).
     */
    public static Double getEffectiveValue(CardData c) {
        if (c == null) return null;

        String cardId = c.id != null ? c.id : (c.sourceJson != null ? c.sourceJson.id() : null);
        if (cardId != null) {
            Optional<ValuationOverride> voOpt = OVERRIDES_LOADER.get().getOverride(cardId);
            if (voOpt.isPresent()) {
                ValuationOverride vo = voOpt.get();
                if (vo.estimatedValue() != null && vo.estimatedValue() > 0.0) {
                    return vo.estimatedValue();
                }
                if (vo.lastSoldPrice() != null && vo.lastSoldPrice() > 0.0) {
                    return vo.lastSoldPrice();
                }
            }
        }

        if (c.estimatedValue != null && c.estimatedValue > 0.0) {
            return c.estimatedValue;
        }
        if (c.lastSoldPrice != null && c.lastSoldPrice > 0.0) {
            return c.lastSoldPrice;
        }
        if (c.priceHistory != null && !c.priceHistory.isEmpty()) {
            return c.priceHistory.getLast().price();
        }
        if (c.beckettValue != null && c.beckettValue > 0.0) {
            return c.beckettValue;
        }
        if (c.purchasePrice != null && c.purchasePrice > 0.0) {
            return c.purchasePrice;
        }
        return null;
    }

    /**
     * Returns rich valuation metadata including source attribution, grade tier, and override status.
     */
    public static ValuationDetails getValuationDetails(CardData c) {
        if (c == null) {
            return new ValuationDetails(null, "None", "Raw", null, false);
        }

        String cardId = c.id != null ? c.id : (c.sourceJson != null ? c.sourceJson.id() : null);
        if (cardId != null) {
            Optional<ValuationOverride> voOpt = OVERRIDES_LOADER.get().getOverride(cardId);
            if (voOpt.isPresent()) {
                ValuationOverride vo = voOpt.get();
                Double val = vo.estimatedValue() != null && vo.estimatedValue() > 0.0
                        ? vo.estimatedValue()
                        : vo.lastSoldPrice();
                if (val != null && val > 0.0) {
                    return new ValuationDetails(val, vo.source(), vo.grade(), calculateGrowthPct(c), true);
                }
            }
        }

        if (c.estimatedValue != null && c.estimatedValue > 0.0) {
            return new ValuationDetails(c.estimatedValue, "Market FMV", c.get("Grade"), calculateGrowthPct(c), false);
        }
        if (c.lastSoldPrice != null && c.lastSoldPrice > 0.0) {
            return new ValuationDetails(c.lastSoldPrice, "Last Sold Comp", c.get("Grade"), calculateGrowthPct(c), false);
        }
        if (c.priceHistory != null && !c.priceHistory.isEmpty()) {
            PricePoint last = c.priceHistory.getLast();
            return new ValuationDetails(last.price(), last.source(), last.grade(), calculateGrowthPct(c), false);
        }
        if (c.beckettValue != null && c.beckettValue > 0.0) {
            return new ValuationDetails(c.beckettValue, "Beckett Book Value", "Raw", calculateGrowthPct(c), false);
        }
        if (c.purchasePrice != null && c.purchasePrice > 0.0) {
            return new ValuationDetails(c.purchasePrice, "Acquisition Cost", c.get("Grade"), 0.0, false);
        }

        return new ValuationDetails(null, "Unpriced", c.get("Grade"), null, false);
    }

    /**
     * Calculates the percentage return on investment or value growth.
     */
    public static Double calculateGrowthPct(CardData c) {
        if (c == null) return null;
        if (c.priceHistory != null && c.priceHistory.size() >= 2) {
            double start = c.priceHistory.getFirst().price();
            double end = c.priceHistory.getLast().price();
            if (start > 0.0) {
                return ((end - start) / start) * 100.0;
            }
        }
        if (c.purchasePrice != null && c.purchasePrice > 0.0) {
            Double current = getEffectiveValue(c);
            if (current != null && current > 0.0) {
                return ((current - c.purchasePrice) / c.purchasePrice) * 100.0;
            }
        }
        return null;
    }

    public static String formatUsd(Double amount) {
        if (amount == null) return "—";
        synchronized (USD_FORMAT) {
            return USD_FORMAT.format(amount);
        }
    }
}
