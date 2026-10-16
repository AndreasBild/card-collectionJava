package de.maulmann;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe persistent cache for card market data, sales comps, and population reports.
 * Reads and writes JSON snapshots to content/json/market-data-cache.json.
 */
public class MarketDataCache {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path DEFAULT_CACHE_PATH = Paths.get("content/json/market-data-cache.json");

    private final Map<String, MarketDataEntry> entriesById = new ConcurrentHashMap<>();
    private final Map<String, MarketDataEntry> entriesByCert = new ConcurrentHashMap<>();

    public MarketDataCache() {}

    private static volatile SimpleLazyConstant<MarketDataCache> defaultCache =
            SimpleLazyConstant.of(() -> load(DEFAULT_CACHE_PATH));

    public static MarketDataCache loadDefault() {
        return defaultCache.get();
    }

    public static void refreshDefault() {
        defaultCache = SimpleLazyConstant.of(() -> load(DEFAULT_CACHE_PATH));
    }

    public static MarketDataCache load(Path cachePath) {
        MarketDataCache cache = new MarketDataCache();
        if (cachePath == null || !Files.exists(cachePath)) {
            logger.info("Market data cache not found at {}. Starting with empty cache.", cachePath);
            return cache;
        }

        try {
            Map<String, MarketDataEntry> map = MAPPER.readValue(
                    cachePath.toFile(),
                    new TypeReference<Map<String, MarketDataEntry>>() {}
            );
            if (map != null) {
                map.forEach(cache::put);
                logger.info("Loaded {} market data entries from {}", cache.size(), cachePath);
            }
        } catch (IOException e) {
            logger.warn("Failed to read market data cache from {}: {}", cachePath, e.getMessage());
            // Keep the unreadable file: otherwise the next save() would overwrite it with an empty cache.
            try {
                Files.copy(cachePath, cachePath.resolveSibling(cachePath.getFileName() + ".corrupt"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyError) {
                logger.warn("Could not preserve unreadable cache {}: {}", cachePath, copyError.getMessage());
            }
        }
        return cache;
    }

    public void put(String cardId, MarketDataEntry entry) {
        if (cardId != null && !cardId.isBlank() && entry != null) {
            entriesById.put(cardId, entry);
            if (entry.certNumber() != null && !entry.certNumber().isBlank()) {
                entriesByCert.put(entry.certNumber(), entry);
            }
        }
    }

    public Optional<MarketDataEntry> get(String cardId) {
        if (cardId == null) return Optional.empty();
        return Optional.ofNullable(entriesById.get(cardId));
    }

    public Optional<MarketDataEntry> getByCert(String certNumber) {
        if (certNumber == null) return Optional.empty();
        return Optional.ofNullable(entriesByCert.get(certNumber));
    }

    public Optional<MarketDataEntry> findMatch(String cardId, String certNumber) {
        if (cardId != null && entriesById.containsKey(cardId)) {
            return Optional.of(entriesById.get(cardId));
        }
        if (certNumber != null && entriesByCert.containsKey(certNumber)) {
            return Optional.of(entriesByCert.get(certNumber));
        }
        return Optional.empty();
    }

    public boolean contains(String cardId) {
        return cardId != null && entriesById.containsKey(cardId);
    }

    public boolean containsCert(String certNumber) {
        return certNumber != null && entriesByCert.containsKey(certNumber);
    }

    public int size() {
        return entriesById.size();
    }

    public Map<String, MarketDataEntry> entries() {
        return Collections.unmodifiableMap(entriesById);
    }

    public boolean isStale(String cardId, long maxAgeDays) {
        if (cardId == null) return true;
        MarketDataEntry entry = entriesById.get(cardId);
        if (entry == null || entry.lastQueried() == null || entry.lastQueried().isBlank()) {
            return true;
        }
        try {
            java.time.Instant queriedAt = java.time.Instant.parse(entry.lastQueried());
            java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(maxAgeDays));
            return queriedAt.isBefore(cutoff);
        } catch (Exception e) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(entry.lastQueried().substring(0, 10));
                return date.plusDays(maxAgeDays).isBefore(java.time.LocalDate.now());
            } catch (Exception ignored) {
                return true;
            }
        }
    }

    public void saveDefault() throws IOException {
        save(DEFAULT_CACHE_PATH);
    }

    public synchronized void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        // Write to a temp file first, keep the previous version as .bak, then move atomically so a
        // crash mid-write can never leave a truncated cache behind.
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        MAPPER.writeValue(tmp.toFile(), entriesById);
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        logger.info("Successfully persisted {} market data entries to {}", entriesById.size(), path);
        if (DEFAULT_CACHE_PATH.equals(path)) {
            refreshDefault();
        }
    }
}
