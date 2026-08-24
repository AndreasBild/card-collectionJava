package de.maulmann;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public static MarketDataCache loadDefault() {
        return load(DEFAULT_CACHE_PATH);
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

    public void saveDefault() throws IOException {
        save(DEFAULT_CACHE_PATH);
    }

    public synchronized void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        MAPPER.writeValue(path.toFile(), entriesById);
        logger.info("Successfully persisted {} market data entries to {}", entriesById.size(), path);
    }
}
