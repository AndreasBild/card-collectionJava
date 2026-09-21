package de.maulmann;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline CLI Tool to query and enrich trading cards with exact market pricing and PSA pop data.
 * Queries verified grading certs (PSA/BGS/SGC/CGC) and confirmed eBay / 130point sales comps.
 * Avoids generating vague synthetic guesses for unverified cards.
 */
public class CardMarketEnricher {

    private static final Logger logger = LoggerFactory.getLogger(CardMarketEnricher.class);
    private static final long DEFAULT_DELAY_MS = 1500;

    private final MarketDataCache cache;
    private final PsaCertScraper psaScraper;
    private final Point130Client point130Client;
    private final ValuationOverridesLoader overridesLoader;
    private final long delayMs;

    public CardMarketEnricher() {
        this(MarketDataCache.loadDefault(), new PsaCertScraper(), new Point130Client(), ValuationOverridesLoader.loadDefault(), DEFAULT_DELAY_MS);
    }

    public CardMarketEnricher(MarketDataCache cache, PsaCertScraper psaScraper, Point130Client point130Client, long delayMs) {
        this(cache, psaScraper, point130Client, ValuationOverridesLoader.loadDefault(), delayMs);
    }

    public CardMarketEnricher(MarketDataCache cache, PsaCertScraper psaScraper, Point130Client point130Client, ValuationOverridesLoader overridesLoader, long delayMs) {
        this.cache = cache;
        this.psaScraper = psaScraper;
        this.point130Client = point130Client;
        this.overridesLoader = overridesLoader;
        this.delayMs = delayMs;
    }

    public static void main(String[] args) {
        logger.info("==================================================");
        logger.info("🛠️ STARTING EXACT CARD MARKET & CENSUS ENRICHER");
        logger.info("==================================================");

        Path cardsPath = Paths.get("content/json/cards.json");
        List<CardData> cards = CardDataLoader.loadCards(cardsPath);
        logger.info("Loaded {} cards from {}", cards.size(), cardsPath);

        boolean forceRefresh = false;
        boolean enrichCerts = false;
        boolean enrichComps = false;
        boolean serialOnly = false;
        int limit = Integer.MAX_VALUE;
        String targetCardId = null;
        int staleDays = -1;
        boolean tieredStaleness = false;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i].toLowerCase();
                if ("--force".equals(arg) || "--refresh".equals(arg)) {
                    forceRefresh = true;
                } else if ("--certs".equals(arg)) {
                    enrichCerts = true;
                } else if ("--comps".equals(arg) || "--prices".equals(arg)) {
                    enrichComps = true;
                } else if ("--all".equals(arg)) {
                    enrichCerts = true;
                    enrichComps = true;
                } else if ("--serial".equals(arg) || "--serial-only".equals(arg) || "--numbered".equals(arg)) {
                    serialOnly = true;
                } else if ("--tiered".equals(arg) || "--tiered-staleness".equals(arg)) {
                    tieredStaleness = true;
                } else if ("--limit".equals(arg) && i + 1 < args.length) {
                    try {
                        limit = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ignored) {}
                } else if ("--stale-days".equals(arg) && i + 1 < args.length) {
                    try {
                        staleDays = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ignored) {}
                } else if ("--card".equals(arg) && i + 1 < args.length) {
                    targetCardId = args[++i];
                }
            }
        }

        // Default to both certs and comps if neither is explicitly passed
        if (!enrichCerts && !enrichComps) {
            enrichCerts = true;
            enrichComps = true;
        }

        CardMarketEnricher enricher = new CardMarketEnricher();
        EnrichmentReport report = enricher.enrichCards(cards, enrichCerts, enrichComps, forceRefresh, limit, targetCardId, serialOnly, staleDays, tieredStaleness);

        logger.info("==================================================");
        logger.info("📊 EXACT ENRICHMENT REPORT");
        logger.info("   • Total Cards Inspected: {}", report.totalInspected());
        logger.info("   • Graded Certs Queried:  {}", report.certsFound());
        logger.info("   • Comps Queried:         {}", report.compsQueried());
        logger.info("   • Already Cached (Skip): {}", report.skippedCached());
        logger.info("   • Queries Successful:    {}", report.queriedSuccess());
        logger.info("   • Queries Failed:        {}", report.queriedFailed());
        logger.info("   • Total Priced Cards:    {}", enricher.cache.size());
        logger.info("==================================================");
    }

    public EnrichmentReport enrichCards(List<CardData> cards, boolean forceRefresh) {
        return enrichCards(cards, true, true, forceRefresh, Integer.MAX_VALUE, null, false);
    }

    public EnrichmentReport enrichCards(
            List<CardData> cards,
            boolean enrichCerts,
            boolean enrichComps,
            boolean forceRefresh,
            int limit,
            String targetCardId
    ) {
        return enrichCards(cards, enrichCerts, enrichComps, forceRefresh, limit, targetCardId, false);
    }

    public EnrichmentReport enrichCards(
            List<CardData> cards,
            boolean enrichCerts,
            boolean enrichComps,
            boolean forceRefresh,
            int limit,
            String targetCardId,
            boolean serialOnly
    ) {
        return enrichCards(cards, enrichCerts, enrichComps, forceRefresh, limit, targetCardId, serialOnly, -1);
    }

    public EnrichmentReport enrichCards(
            List<CardData> cards,
            boolean enrichCerts,
            boolean enrichComps,
            boolean forceRefresh,
            int limit,
            String targetCardId,
            boolean serialOnly,
            int staleDays
    ) {
        return enrichCards(cards, enrichCerts, enrichComps, forceRefresh, limit, targetCardId, serialOnly, staleDays, false);
    }

    public EnrichmentReport enrichCards(
            List<CardData> cards,
            boolean enrichCerts,
            boolean enrichComps,
            boolean forceRefresh,
            int limit,
            String targetCardId,
            boolean serialOnly,
            int staleDays,
            boolean tieredStaleness
    ) {
        int totalInspected = 0;
        int certsFound = 0;
        int compsQueried = 0;
        int skippedCached = 0;
        int queriedSuccess = 0;
        int queriedFailed = 0;
        int exactPriced = 0;
        int processedCount = 0;

        for (CardData c : cards) {
            String cardId = c.id != null ? c.id : (c.sourceJson != null ? c.sourceJson.id() : null);
            if (cardId == null || cardId.isBlank()) continue;

            if (targetCardId != null && !targetCardId.equalsIgnoreCase(cardId)) {
                continue;
            }

            boolean isSerial = (c.sourceJson != null && (c.sourceJson.serialNumber() != null || c.sourceJson.printRun() != null))
                    || (c.attributes != null && (c.attributes.get("Serial") != null || c.attributes.get("Print Run") != null));

            if (serialOnly && !isSerial) {
                continue;
            }

            if (processedCount >= limit) {
                break;
            }

            totalInspected++;
            String certNum = c.certNumber;

            // Check if card has a curated override
            if (overridesLoader != null && overridesLoader.hasOverride(cardId) && !forceRefresh) {
                skippedCached++;
                continue;
            }

            Optional<MarketDataEntry> existingOpt = cache.get(cardId);
            if (existingOpt.isPresent()) {
                MarketDataEntry existing = existingOpt.get();
                if (existing.metadata() != null && "true".equalsIgnoreCase(existing.metadata().get("manual"))) {
                    skippedCached++;
                    continue;
                }
            }

            MarketDataEntry currentEntry = existingOpt.orElse(MarketDataEntry.builder().build());
            boolean modified = false;

            int effectiveStaleDays = staleDays;
            if (tieredStaleness) {
                if (isSerial) {
                    effectiveStaleDays = 14;
                } else if (certNum != null && !certNum.isBlank()) {
                    effectiveStaleDays = 30;
                } else {
                    effectiveStaleDays = 90;
                }
            }

            boolean isStale = effectiveStaleDays > 0 && cache.isStale(cardId, effectiveStaleDays);

            boolean queried = false;

            // 1. Graded Census / Pop Report Lookup
            if (enrichCerts && certNum != null && !certNum.isBlank()) {
                String grader = c.get("Grading Co.");
                if (grader != null && !grader.isBlank() && (forceRefresh || isStale || currentEntry.popReport() == null)) {
                    certsFound++;
                    queried = true;
                    logger.info("Querying {} cert #{} for card: {} (ID: {})", grader, certNum, c.filenameBase, cardId);
                    Optional<MarketDataEntry> certDataOpt = psaScraper.fetchCertData(grader, certNum);

                    if (certDataOpt.isPresent()) {
                        MarketDataEntry certData = certDataOpt.get();
                        currentEntry = MarketDataEntry.builder()
                                .certNumber(certNum)
                                .lastQueried(Instant.now().toString())
                                .popReport(certData.popReport())
                                .estimatedValue(currentEntry.estimatedValue())
                                .lastSoldPrice(currentEntry.lastSoldPrice())
                                .lastSoldDate(currentEntry.lastSoldDate())
                                .purchasePrice(currentEntry.purchasePrice())
                                .priceHistory(currentEntry.priceHistory())
                                .metadata(mergeMetadata(currentEntry.metadata(), certData.metadata()))
                                .build();
                        queriedSuccess++;
                        modified = true;
                    } else {
                        queriedFailed++;
                    }
                    throttle();
                }
            }

            // 2. 130point / eBay Market Sales Comps Lookup
            if (enrichComps && (forceRefresh || isStale || currentEntry.estimatedValue() == null || currentEntry.priceHistory().isEmpty())) {
                compsQueried++;
                queried = true;
                logger.info("Querying 130point comps for card: {} (ID: {})", c.filenameBase, cardId);
                Optional<Point130Client.CardCompResult> compResultOpt = point130Client.fetchComps(c);

                if (compResultOpt.isPresent() && !compResultOpt.get().comps().isEmpty()) {
                    Point130Client.CardCompResult compResult = compResultOpt.get();
                    List<PricePoint> mergedComps = mergePriceHistory(currentEntry.priceHistory(), compResult.comps());
                    currentEntry = MarketDataEntry.builder()
                            .certNumber(currentEntry.certNumber() != null ? currentEntry.certNumber() : certNum)
                            .lastQueried(Instant.now().toString())
                            .popReport(currentEntry.popReport())
                            .estimatedValue(compResult.estimatedValue())
                            .lastSoldPrice(compResult.lastSoldPrice())
                            .lastSoldDate(compResult.lastSoldDate())
                            .purchasePrice(currentEntry.purchasePrice())
                            .priceHistory(mergedComps)
                            .metadata(currentEntry.metadata())
                            .build();
                    queriedSuccess++;
                    modified = true;
                    logger.info("   -> Found {} comps: FMV=${}, Last Sold=${} ({})",
                            compResult.comps().size(), compResult.estimatedValue(), compResult.lastSoldPrice(), compResult.lastSoldDate());
                } else {
                    queriedFailed++;
                    if (existingOpt.isPresent()) {
                        MarketDataEntry refreshed = MarketDataEntry.builder()
                                .certNumber(currentEntry.certNumber())
                                .lastQueried(Instant.now().toString())
                                .popReport(currentEntry.popReport())
                                .estimatedValue(currentEntry.estimatedValue())
                                .lastSoldPrice(currentEntry.lastSoldPrice())
                                .lastSoldDate(currentEntry.lastSoldDate())
                                .purchasePrice(currentEntry.purchasePrice())
                                .priceHistory(currentEntry.priceHistory())
                                .metadata(currentEntry.metadata())
                                .build();
                        cache.put(cardId, refreshed);
                    }
                }
                throttle();
            }

            if (modified) {
                cache.put(cardId, currentEntry);
            } else if (existingOpt.isPresent()) {
                skippedCached++;
            }

            if (queried) {
                processedCount++;
            }

            if (currentEntry.estimatedValue() != null || currentEntry.lastSoldPrice() != null) {
                exactPriced++;
            }
        }

        try {
            cache.saveDefault();
        } catch (IOException e) {
            logger.error("Failed to save market data cache: {}", e.getMessage(), e);
        }

        return new EnrichmentReport(totalInspected, certsFound, compsQueried, skippedCached, queriedSuccess, queriedFailed, exactPriced);
    }

    public static List<PricePoint> mergePriceHistory(List<PricePoint> existing, List<PricePoint> incoming) {
        if ((existing == null || existing.isEmpty()) && (incoming == null || incoming.isEmpty())) {
            return Collections.emptyList();
        }
        if (existing == null || existing.isEmpty()) return incoming;
        if (incoming == null || incoming.isEmpty()) return existing;

        Map<String, PricePoint> merged = new LinkedHashMap<>();
        for (PricePoint p : existing) {
            if (p != null) {
                String key = (p.date() != null ? p.date() : "") + "|" + p.price() + "|" + (p.grade() != null ? p.grade() : "");
                merged.put(key, p);
            }
        }
        for (PricePoint p : incoming) {
            if (p != null) {
                String key = (p.date() != null ? p.date() : "") + "|" + p.price() + "|" + (p.grade() != null ? p.grade() : "");
                merged.put(key, p);
            }
        }
        List<PricePoint> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(p -> (p.date() != null ? p.date() : "")));
        return Collections.unmodifiableList(result);
    }

    private void throttle() {
        if (delayMs > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Enrichment throttle interrupted.");
            }
        }
    }

    private static Map<String, String> mergeMetadata(Map<String, String> m1, Map<String, String> m2) {
        Map<String, String> merged = new HashMap<>();
        if (m1 != null) merged.putAll(m1);
        if (m2 != null) merged.putAll(m2);
        return Collections.unmodifiableMap(merged);
    }

    public record EnrichmentReport(
            int totalInspected,
            int certsFound,
            int compsQueried,
            int skippedCached,
            int queriedSuccess,
            int queriedFailed,
            int exactPriced
    ) {}
}
