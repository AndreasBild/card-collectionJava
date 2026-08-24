package de.maulmann;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline CLI Tool to query and enrich trading cards with exact market pricing and PSA pop data.
 * Strictly enriches exact card matches (verified grading certs, exact sales comps, and manual entries).
 * Avoids generating vague synthetic guesses for unverified cards.
 */
public class CardMarketEnricher {

    private static final Logger logger = LoggerFactory.getLogger(CardMarketEnricher.class);
    private static final long DEFAULT_DELAY_MS = 350;

    private final MarketDataCache cache;
    private final PsaCertScraper psaScraper;
    private final long delayMs;

    public CardMarketEnricher() {
        this(MarketDataCache.loadDefault(), new PsaCertScraper(), DEFAULT_DELAY_MS);
    }

    public CardMarketEnricher(MarketDataCache cache, PsaCertScraper psaScraper, long delayMs) {
        this.cache = cache;
        this.psaScraper = psaScraper;
        this.delayMs = delayMs;
    }

    public static void main(String[] args) {
        logger.info("==================================================");
        logger.info("🛠️ STARTING EXACT CARD MARKET & CENSUS ENRICHER");
        logger.info("==================================================");

        Path cardsPath = Paths.get("content/json/cards.json");
        List<CardData> cards = CardDataLoader.loadCards(cardsPath);
        logger.info("Loaded {} cards from {}", cards.size(), cardsPath);

        boolean forceRefresh = args != null && args.length > 0 &&
                ("--force".equalsIgnoreCase(args[0]) || "--refresh".equalsIgnoreCase(args[0]));

        CardMarketEnricher enricher = new CardMarketEnricher();
        EnrichmentReport report = enricher.enrichCards(cards, forceRefresh);

        logger.info("==================================================");
        logger.info("📊 EXACT ENRICHMENT REPORT");
        logger.info("   • Total Cards Inspected: {}", report.totalInspected());
        logger.info("   • Graded Certs Found:    {}", report.certsFound());
        logger.info("   • Already Cached (Skip): {}", report.skippedCached());
        logger.info("   • PSA Queried Success:   {}", report.queriedSuccess());
        logger.info("   • PSA Fallback/Failed:   {}", report.queriedFailed());
        logger.info("   • Exact Priced Cards:    {}", enricher.cache.size());
        logger.info("==================================================");
    }

    public EnrichmentReport enrichCards(List<CardData> cards, boolean forceRefresh) {
        int totalInspected = 0;
        int certsFound = 0;
        int skippedCached = 0;
        int queriedSuccess = 0;
        int queriedFailed = 0;
        int exactPriced = 0;

        for (CardData c : cards) {
            totalInspected++;
            String certNum = c.certNumber;
            String cardId = c.id != null ? c.id : (c.sourceJson != null ? c.sourceJson.id() : null);
            if (cardId == null || cardId.isBlank()) continue;

            Optional<MarketDataEntry> existingOpt = cache.get(cardId);
            if (existingOpt.isPresent()) {
                MarketDataEntry existing = existingOpt.get();
                if (existing.metadata() != null && "true".equalsIgnoreCase(existing.metadata().get("manual"))) {
                    // Manual price override protected
                    skippedCached++;
                    continue;
                }
            }

            if (!forceRefresh && existingOpt.isPresent()) {
                skippedCached++;
                continue;
            }

            // Strictly enrich only exact cards with verified grading certificates or explicit matching data
            if (certNum != null && !certNum.isBlank()) {
                certsFound++;
                String grader = c.get("Grading Co.");

                if (grader != null && !grader.isBlank()) {
                    logger.info("Querying {} cert #{} for exact card: {} (ID: {})", grader, certNum, c.filenameBase, cardId);
                    Optional<MarketDataEntry> entryOpt = psaScraper.fetchCertData(grader, certNum);
                    MarketDataEntry estimated = MarketPriceFetcher.estimateMarketData(c);

                    if (entryOpt.isPresent()) {
                        MarketDataEntry entry = entryOpt.get();

                        // Merge census with exact pricing data
                        MarketDataEntry combined = MarketDataEntry.builder()
                                .certNumber(certNum)
                                .lastQueried(entry.lastQueried())
                                .popReport(entry.popReport())
                                .estimatedValue(estimated != null ? estimated.estimatedValue() : null)
                                .lastSoldPrice(estimated != null ? estimated.lastSoldPrice() : null)
                                .lastSoldDate(estimated != null ? estimated.lastSoldDate() : null)
                                .purchasePrice(estimated != null ? estimated.purchasePrice() : null)
                                .priceHistory(estimated != null ? estimated.priceHistory() : List.of())
                                .metadata(entry.metadata())
                                .build();

                        cache.put(cardId, combined);
                        queriedSuccess++;
                        exactPriced++;
                        logger.info("   -> Success for exact card #{}: Grader={}, Pop Total={}, Pop Higher={}, FMV=${}",
                                certNum,
                                grader,
                                entry.popReport() != null ? entry.popReport().totalGraded() : "N/A",
                                entry.popReport() != null ? entry.popReport().popHigher() : "N/A",
                                combined.estimatedValue());
                    } else {
                        if (estimated != null) {
                            cache.put(cardId, estimated);
                            exactPriced++;
                        }
                        queriedFailed++;
                    }

                    if (delayMs > 0) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("Enrichment batch interrupted.");
                            break;
                        }
                    }
                }
            }
        }

        try {
            cache.saveDefault();
        } catch (IOException e) {
            logger.error("Failed to save market data cache: {}", e.getMessage(), e);
        }

        return new EnrichmentReport(totalInspected, certsFound, skippedCached, queriedSuccess, queriedFailed, exactPriced);
    }

    public record EnrichmentReport(
            int totalInspected,
            int certsFound,
            int skippedCached,
            int queriedSuccess,
            int queriedFailed,
            int exactPriced
    ) {}
}
