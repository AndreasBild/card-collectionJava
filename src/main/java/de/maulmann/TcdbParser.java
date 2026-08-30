package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance Parser and Entity Linker for Trading Card Database (TCDB) exports.
 * Extracts TCDB identifiers (sid, cid), verified set/card numbers, print runs, and checklist links.
 */
public class TcdbParser {

    private static final Logger logger = LoggerFactory.getLogger(TcdbParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("#([A-Za-z0-9\\-]+)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b");
    private static final Pattern PRINT_RUN_PATTERN = Pattern.compile("(?i)PR\\s*(\\d+)");
    private static final Pattern SID_CID_PATTERN = Pattern.compile("sid/(\\d+)/cid/(\\d+)");

    public record TcdbCardItem(
            String sid,
            String cid,
            String title,
            String cardNumber,
            String year,
            String setName,
            String parallel,
            String printRun,
            String url
    ) {}

    /**
     * Parses the JSON export generated from TCDB checklist pages.
     */
    public List<TcdbCardItem> parseJson(Path jsonPath) throws IOException {
        if (jsonPath == null || !Files.exists(jsonPath)) {
            logger.warn("TCDB JSON export file not found at {}", jsonPath);
            return List.of();
        }

        JsonNode root = MAPPER.readTree(jsonPath.toFile());
        if (!root.isArray()) {
            return List.of();
        }

        List<TcdbCardItem> items = new ArrayList<>();

        for (JsonNode node : root) {
            String text = node.has("text") ? node.get("text").asText().trim() : "";
            if (text.isBlank() || text.equalsIgnoreCase("Search Advanced")) {
                continue;
            }

            String url = "";
            String sid = null;
            String cid = null;

            if (node.has("links") && node.get("links").isArray()) {
                for (JsonNode link : node.get("links")) {
                    String href = link.has("href") ? link.get("href").asText() : "";
                    if (href.contains("ViewCard.cfm")) {
                        url = href;
                        Matcher scMatcher = SID_CID_PATTERN.matcher(href);
                        if (scMatcher.find()) {
                            sid = scMatcher.group(1);
                            cid = scMatcher.group(2);
                        }
                        break;
                    }
                }
            }

            if (url.isBlank()) {
                continue;
            }

            String cardNumber = extractCardNumber(text);
            String year = extractYear(text);
            String printRun = extractPrintRun(text);
            String setName = extractSetName(text);
            String parallel = extractParallel(text);

            items.add(new TcdbCardItem(
                    sid,
                    cid,
                    text,
                    cardNumber,
                    year,
                    setName,
                    parallel,
                    printRun,
                    url
            ));
        }

        return Collections.unmodifiableList(items);
    }

    /**
     * Finds the best matching CardData entity for a TCDB item.
     */
    public Optional<CardData> findMatchingCard(TcdbCardItem item, List<CardData> cards) {
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

            // 1. Check card number match
            if (itemNum != null && !itemNum.isBlank()) {
                String cleanItemNum = itemNum.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                String cleanCardNum = cardNum.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                if (!cleanItemNum.equals(cleanCardNum)) {
                    continue;
                }
            } else {
                continue;
            }

            // 2. Check year / season
            String season = card.get("Season");
            if (itemYear != null && season != null && !season.contains(itemYear)) {
                continue;
            }

            // 3. Check brand / set tokens
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
            if (!setMatch && itemSet.contains("topps") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("topps"))) {
                setMatch = true;
            }
            if (!setMatch && itemSet.contains("upper deck") && (brand != null && brand.toLowerCase(Locale.ROOT).contains("upper deck"))) {
                setMatch = true;
            }

            // 4. Parallel match
            boolean isParallelItem = item.parallel() != null && !item.parallel().isBlank();
            boolean isParallelCard = (variant != null && !variant.isBlank() && !"Base".equalsIgnoreCase(variant))
                    || (theme != null && (theme.toLowerCase(Locale.ROOT).contains("refractor") || theme.toLowerCase(Locale.ROOT).contains("gold")));

            if (isParallelItem != isParallelCard) {
                continue;
            }

            if (setMatch) {
                return Optional.of(card);
            }
        }

        return Optional.empty();
    }

    /**
     * Enriches the MarketDataCache using items parsed from a TCDB JSON export.
     */
    public int enrichCache(Path jsonPath, List<CardData> cards, MarketDataCache cache) throws IOException {
        List<TcdbCardItem> items = parseJson(jsonPath);
        if (items.isEmpty()) {
            logger.warn("No valid card items parsed from TCDB export at {}", jsonPath);
            return 0;
        }

        logger.info("Parsed {} items from TCDB checklist export ({})", items.size(), jsonPath);
        int matchedCount = 0;

        for (TcdbCardItem item : items) {
            Optional<CardData> matchOpt = findMatchingCard(item, cards);
            if (matchOpt.isEmpty()) continue;

            CardData card = matchOpt.get();
            String cardId = card.id != null ? card.id : (card.sourceJson != null ? card.sourceJson.id() : null);
            if (cardId == null || cardId.isBlank()) continue;

            MarketDataEntry existing = cache.get(cardId).orElse(MarketDataEntry.builder().build());

            // Build enriched metadata
            Map<String, String> meta = new HashMap<>(existing.metadata() != null ? existing.metadata() : Map.of());
            if (item.sid() != null) meta.put("tcdb_sid", item.sid());
            if (item.cid() != null) meta.put("tcdb_cid", item.cid());
            if (item.url() != null) meta.put("tcdb_url", item.url());
            if (item.printRun() != null) meta.put("tcdb_print_run", item.printRun());

            MarketDataEntry updated = MarketDataEntry.builder()
                    .certNumber(existing.certNumber() != null ? existing.certNumber() : card.certNumber)
                    .lastQueried(existing.lastQueried() != null ? existing.lastQueried() : java.time.LocalDate.now().toString())
                    .popReport(existing.popReport())
                    .estimatedValue(existing.estimatedValue())
                    .lastSoldPrice(existing.lastSoldPrice())
                    .lastSoldDate(existing.lastSoldDate())
                    .purchasePrice(existing.purchasePrice())
                    .priceHistory(existing.priceHistory())
                    .metadata(meta)
                    .build();

            cache.put(cardId, updated);
            matchedCount++;
            logger.info("   -> Linked card [{}] to TCDB Card cid/{} ({})", card.filenameBase, item.cid(), item.url());
        }

        return matchedCount;
    }

    private static String extractCardNumber(String title) {
        if (title == null) return null;
        Matcher m = CARD_NUMBER_PATTERN.matcher(title);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractYear(String text) {
        if (text == null) return null;
        Matcher m = YEAR_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractPrintRun(String text) {
        if (text == null) return null;
        Matcher m = PRINT_RUN_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractSetName(String text) {
        if (text == null) return "";
        int hashIdx = text.indexOf('#');
        if (hashIdx > 0) {
            String prefix = text.substring(0, hashIdx).trim();
            if (prefix.contains("-")) {
                return prefix.split("-")[0].trim();
            }
            return prefix;
        }
        return text;
    }

    private static String extractParallel(String text) {
        if (text == null) return null;
        int dashIdx = text.indexOf('-');
        int hashIdx = text.indexOf('#');
        if (dashIdx > 0 && hashIdx > dashIdx) {
            return text.substring(dashIdx + 1, hashIdx).trim();
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        Path jsonPath = Paths.get("content/raw/tcdb-juwan-howard-all.json");
        Path cardsPath = Paths.get("content/json/cards.json");

        if (args != null && args.length > 0) {
            jsonPath = Paths.get(args[0]);
        }

        logger.info("==================================================");
        logger.info("🛠️ TCDB OFFLINE CHECKLIST & ENTITY ENRICHER");
        logger.info("   • Source JSON: {}", jsonPath);
        logger.info("   • Cards DB:    {}", cardsPath);
        logger.info("==================================================");

        List<CardData> cards = CardDataLoader.loadCards(cardsPath);
        MarketDataCache cache = MarketDataCache.loadDefault();

        TcdbParser parser = new TcdbParser();
        int matched = parser.enrichCache(jsonPath, cards, cache);

        logger.info("==================================================");
        logger.info("📊 ENRICHMENT SUMMARY");
        logger.info("   • Matched & Linked Cards:   {}", matched);
        logger.info("   • Total Priced Cache Size:  {}", cache.size());
        logger.info("==================================================");

        cache.saveDefault();
        logger.info("✅ Successfully updated content/json/market-data-cache.json");
    }
}
