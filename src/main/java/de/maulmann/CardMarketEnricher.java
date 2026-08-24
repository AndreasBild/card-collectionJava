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
 * Offline CLI Tool to query and enrich trading cards with external market and PSA pop data.
 * Executes once per card and updates content/json/market-data-cache.json.
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
        logger.info("🛠️ STARTING CARD MARKET & CENSUS ENRICHER");
        logger.info("==================================================");

        Path cardsPath = Paths.get("content/json/cards.json");
        List<CardData> cards = CardDataLoader.loadCards(cardsPath);
        logger.info("Loaded {} cards from {}", cards.size(), cardsPath);

        CardMarketEnricher enricher = new CardMarketEnricher();
        EnrichmentReport report = enricher.enrichCards(cards, false);

        logger.info("==================================================");
        logger.info("📊 ENRICHMENT REPORT");
        logger.info("   • Total Cards Inspected: {}", report.totalInspected());
        logger.info("   • Graded Certs Found:    {}", report.certsFound());
        logger.info("   • Already Cached (Skip): {}", report.skippedCached());
        logger.info("   • Successfully Queried:  {}", report.queriedSuccess());
        logger.info("   • Failed / Not Found:    {}", report.queriedFailed());
        logger.info("   • Final Cache Size:      {}", enricher.cache.size());
        logger.info("==================================================");
    }

    public EnrichmentReport enrichCards(List<CardData> cards, boolean forceRefresh) {
        int totalInspected = 0;
        int certsFound = 0;
        int skippedCached = 0;
        int queriedSuccess = 0;
        int queriedFailed = 0;

        for (CardData c : cards) {
            totalInspected++;
            String certNum = c.certNumber;
            String cardId = c.id;

            if (certNum != null && !certNum.isBlank()) {
                certsFound++;
                String grader = c.get("Grading Co.");

                if (grader != null && grader.toUpperCase().contains("PSA")) {
                    if (!forceRefresh && cache.containsCert(certNum)) {
                        skippedCached++;
                        continue;
                    }

                    logger.info("Querying PSA cert #{} for card: {} (ID: {})", certNum, c.filenameBase, cardId);
                    Optional<MarketDataEntry> entryOpt = psaScraper.fetchPsaData(certNum);

                    if (entryOpt.isPresent()) {
                        MarketDataEntry entry = entryOpt.get();
                        cache.put(cardId != null ? cardId : certNum, entry);
                        queriedSuccess++;
                        logger.info("   -> Success: Pop Total={}, Pop Higher={}",
                                entry.popReport() != null ? entry.popReport().totalGraded() : "N/A",
                                entry.popReport() != null ? entry.popReport().popHigher() : "N/A");
                    } else {
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

        return new EnrichmentReport(totalInspected, certsFound, skippedCached, queriedSuccess, queriedFailed);
    }

    public record EnrichmentReport(
            int totalInspected,
            int certsFound,
            int skippedCached,
            int queriedSuccess,
            int queriedFailed
    ) {}
}
