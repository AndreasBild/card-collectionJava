package de.maulmann;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline HTML Parser and Valuation Matcher for SportsCardsPro / PriceCharting search exports.
 * Extracts condition-tiered pricing (Ungraded, Grade 9, PSA 10) and enriches the market cache.
 */
public class SportsCardsProParser {

    private static final Logger logger = LoggerFactory.getLogger(SportsCardsProParser.class);
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("#([A-Za-z0-9\\-]+)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b");

    public record SportsCardsProItem(
            String productId,
            String title,
            String cardNumber,
            String setName,
            String year,
            Double ungradedPrice,
            Double grade9Price,
            Double psa10Price,
            String url
    ) {}

    /**
     * Parses raw HTML string from a saved SportsCardsPro search page.
     */
    public List<SportsCardsProItem> parseHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("tr[id^=product-]");
        List<SportsCardsProItem> items = new ArrayList<>();

        for (Element row : rows) {
            String productId = row.attr("data-product");

            Element titleEl = row.selectFirst("td.title a");
            String title = (titleEl != null) ? titleEl.text().trim() : "";
            String url = (titleEl != null) ? titleEl.attr("href") : "";

            Element setEl = row.selectFirst("td.console a");
            String setName = (setEl != null) ? setEl.text().trim() : "";

            String cardNumber = extractCardNumber(title);
            String year = extractYear(setName);

            Double ungraded = parsePrice(row.selectFirst("td.used_price span.js-price"));
            Double grade9 = parsePrice(row.selectFirst("td.cib_price span.js-price"));
            Double psa10 = parsePrice(row.selectFirst("td.new_price span.js-price"));

            items.add(new SportsCardsProItem(
                    productId,
                    title,
                    cardNumber,
                    setName,
                    year,
                    ungraded,
                    grade9,
                    psa10,
                    url
            ));
        }

        return Collections.unmodifiableList(items);
    }

    /**
     * Parses SportsCardsPro HTML file from disk.
     */
    public List<SportsCardsProItem> parseFile(Path htmlPath) throws IOException {
        if (htmlPath == null || !Files.exists(htmlPath)) {
            logger.warn("SportsCardsPro HTML file not found at {}", htmlPath);
            return List.of();
        }
        String html = Files.readString(htmlPath);
        return parseHtml(html);
    }

    /**
     * Finds the best matching CardData entity for a SportsCardsPro item.
     */
    public Optional<CardData> findMatchingCard(SportsCardsProItem item, List<CardData> cards) {
        if (item == null || cards == null || cards.isEmpty()) {
            return Optional.empty();
        }

        String itemNum = item.cardNumber();
        String itemYear = item.year();
        String itemSet = item.setName().toLowerCase(Locale.ROOT);
        String itemTitle = item.title().toLowerCase(Locale.ROOT);

        for (CardData card : cards) {
            String cardNum = card.get("Number");
            if (cardNum == null || cardNum.isBlank()) continue;

            // Check card number match
            if (itemNum != null && !itemNum.isBlank()) {
                String cleanItemNum = itemNum.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                String cleanCardNum = cardNum.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                if (!cleanItemNum.equals(cleanCardNum)) {
                    continue;
                }
            } else {
                continue;
            }

            // Check year match
            String season = card.get("Season");
            if (itemYear != null && season != null && !season.contains(itemYear)) {
                continue;
            }

            // Check brand / set tokens
            String brand = card.get("Brand");
            String theme = card.get("Theme");
            String variant = card.get("Variant");

            boolean setMatch = false;
            if (brand != null && !brand.isBlank()) {
                String brandLower = brand.toLowerCase(Locale.ROOT);
                if (itemSet.contains(brandLower) || itemTitle.contains(brandLower)) {
                    setMatch = true;
                }
            }
            if (theme != null && !theme.isBlank() && !"Base Set".equalsIgnoreCase(theme)) {
                String themeLower = theme.toLowerCase(Locale.ROOT);
                if (itemSet.contains(themeLower) || itemTitle.contains(themeLower)) {
                    setMatch = true;
                }
            }
            if (!setMatch && itemSet.contains("classic") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("classic"))) {
                setMatch = true;
            }
            if (!setMatch && itemSet.contains("hoops") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("hoops"))) {
                setMatch = true;
            }
            if (!setMatch && itemSet.contains("ultra") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("ultra"))) {
                setMatch = true;
            }
            if (!setMatch && itemSet.contains("finest") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("finest"))) {
                setMatch = true;
            }
            if (!setMatch && itemSet.contains("skybox") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("skybox"))) {
                setMatch = true;
            }
            if (!setMatch && itemSet.contains("sp") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("sp"))) {
                setMatch = true;
            }

            // Check parallel / variant alignment
            boolean isParallelItem = itemTitle.contains("[") || itemTitle.contains("refractor") || itemTitle.contains("gold") || itemTitle.contains("silver");
            boolean isParallelCard = (variant != null && !variant.isBlank() && !"Base".equalsIgnoreCase(variant))
                    || (theme != null && (theme.toLowerCase(Locale.ROOT).contains("refractor") || theme.toLowerCase(Locale.ROOT).contains("gold")));

            if (isParallelItem != isParallelCard) {
                // Skip mismatch between base and parallel
                continue;
            }

            if (setMatch) {
                return Optional.of(card);
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves the appropriate price from a SportsCardsPro item based on card grading.
     */
    public Double resolvePriceForCard(SportsCardsProItem item, CardData card) {
        if (item == null) return null;
        if (card == null) return item.ungradedPrice();

        String grade = card.get("Grade");
        if (grade != null && !grade.isBlank()) {
            String cleanGrade = grade.toLowerCase(Locale.ROOT);
            if (cleanGrade.contains("10") || cleanGrade.contains("gem mint") || cleanGrade.contains("pristine")) {
                if (item.psa10Price() != null) return item.psa10Price();
                if (item.grade9Price() != null) return item.grade9Price();
            } else if (cleanGrade.contains("9") || cleanGrade.contains("mint")) {
                if (item.grade9Price() != null) return item.grade9Price();
                if (item.ungradedPrice() != null) return item.ungradedPrice();
            }
        }

        return item.ungradedPrice();
    }

    /**
     * Enriches the MarketDataCache using items parsed from a SportsCardsPro HTML export.
     */
    public int enrichCache(Path htmlPath, List<CardData> cards, MarketDataCache cache) throws IOException {
        List<SportsCardsProItem> items = parseFile(htmlPath);
        if (items.isEmpty()) {
            logger.warn("No items parsed from SportsCardsPro HTML at {}", htmlPath);
            return 0;
        }

        logger.info("Parsed {} items from SportsCardsPro export ({})", items.size(), htmlPath);
        int matchedCount = 0;
        String todayIso = LocalDate.now().toString();

        for (SportsCardsProItem item : items) {
            Optional<CardData> matchOpt = findMatchingCard(item, cards);
            if (matchOpt.isEmpty()) continue;

            CardData card = matchOpt.get();
            String cardId = card.id != null ? card.id : (card.sourceJson != null ? card.sourceJson.id() : null);
            if (cardId == null || cardId.isBlank()) continue;

            Double price = resolvePriceForCard(item, card);
            if (price == null || price <= 0.0) continue;

            MarketDataEntry existing = cache.get(cardId).orElse(MarketDataEntry.builder().build());

            // Build metadata
            Map<String, String> meta = new HashMap<>(existing.metadata() != null ? existing.metadata() : Map.of());
            meta.put("sportscardspro_id", item.productId());
            if (item.ungradedPrice() != null) meta.put("sportscardspro_ungraded", String.valueOf(item.ungradedPrice()));
            if (item.grade9Price() != null) meta.put("sportscardspro_grade9", String.valueOf(item.grade9Price()));
            if (item.psa10Price() != null) meta.put("sportscardspro_psa10", String.valueOf(item.psa10Price()));
            if (item.url() != null && !item.url().isBlank()) meta.put("sportscardspro_url", item.url());

            // Determine estimatedValue fallback if missing
            Double estimatedValue = existing.estimatedValue();
            if (estimatedValue == null || estimatedValue <= 0.0) {
                estimatedValue = price;
            }

            // Create price history entry if none exists
            List<PricePoint> history = new ArrayList<>(existing.priceHistory());
            if (history.isEmpty()) {
                String gradeLabel = card.get("Grade");
                history.add(new PricePoint(todayIso, price, "SportsCardsPro", (gradeLabel != null && !gradeLabel.isBlank()) ? gradeLabel : "Raw"));
            }

            MarketDataEntry updated = MarketDataEntry.builder()
                    .certNumber(existing.certNumber() != null ? existing.certNumber() : card.certNumber)
                    .lastQueried(todayIso)
                    .popReport(existing.popReport())
                    .estimatedValue(estimatedValue)
                    .lastSoldPrice(existing.lastSoldPrice() != null ? existing.lastSoldPrice() : price)
                    .lastSoldDate(existing.lastSoldDate() != null ? existing.lastSoldDate() : todayIso)
                    .purchasePrice(existing.purchasePrice())
                    .priceHistory(history)
                    .metadata(meta)
                    .build();

            cache.put(cardId, updated);
            matchedCount++;
            logger.info("   -> Linked card [{}] to SportsCardsPro #{} (Estimated: ${})", card.filenameBase, item.productId(), price);
        }

        return matchedCount;
    }

    private static Double parsePrice(Element el) {
        if (el == null) return null;
        String text = el.text().trim();
        if (text.isBlank()) return null;
        try {
            String sanitized = text.replaceAll("[^0-9.]", "").trim();
            if (sanitized.isEmpty()) return null;
            return Double.parseDouble(sanitized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractCardNumber(String title) {
        if (title == null) return null;
        Matcher m = CARD_NUMBER_PATTERN.matcher(title);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractYear(String setName) {
        if (setName == null) return null;
        Matcher m = YEAR_PATTERN.matcher(setName);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        Path htmlPath = Paths.get("content/raw/sportscardspro-juwan-howard.html");
        Path cardsPath = Paths.get("content/json/cards.json");

        if (args != null && args.length > 0) {
            htmlPath = Paths.get(args[0]);
        }

        logger.info("==================================================");
        logger.info("🛠️ SPORTSCARDSPRO OFFLINE HTML ENRICHER");
        logger.info("   • Source HTML: {}", htmlPath);
        logger.info("   • Cards DB:    {}", cardsPath);
        logger.info("==================================================");

        List<CardData> cards = CardDataLoader.loadCards(cardsPath);
        MarketDataCache cache = MarketDataCache.loadDefault();

        SportsCardsProParser parser = new SportsCardsProParser();
        int matched = parser.enrichCache(htmlPath, cards, cache);

        logger.info("==================================================");
        logger.info("📊 ENRICHMENT SUMMARY");
        logger.info("   • Matched & Enriched Cards: {}", matched);
        logger.info("   • Total Priced Cache Size:  {}", cache.size());
        logger.info("==================================================");

        cache.saveDefault();
        logger.info("✅ Successfully updated content/json/market-data-cache.json");
    }
}
