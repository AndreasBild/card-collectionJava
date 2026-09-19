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
 * Thread-safe persistent cache for card Beckett Book Values.
 * Reads and writes JSON snapshots to content/json/beckett-values.json.
 */
public class BeckettValueCache {

    private static final Logger logger = LoggerFactory.getLogger(BeckettValueCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path DEFAULT_CACHE_PATH = Paths.get("content/json/beckett-values.json");

    private final Map<String, BeckettValueEntry> entriesById = new ConcurrentHashMap<>();

    public BeckettValueCache() {}

    public static BeckettValueCache loadDefault() {
        return load(DEFAULT_CACHE_PATH);
    }

    public static BeckettValueCache load(Path cachePath) {
        BeckettValueCache cache = new BeckettValueCache();
        if (cachePath == null || !Files.exists(cachePath)) {
            logger.info("Beckett value cache not found at {}. Starting with empty cache.", cachePath);
            return cache;
        }

        try {
            Map<String, BeckettValueEntry> map = MAPPER.readValue(
                    cachePath.toFile(),
                    new TypeReference<Map<String, BeckettValueEntry>>() {}
            );
            if (map != null) {
                map.forEach(cache::put);
                logger.info("Loaded {} Beckett value entries from {}", cache.size(), cachePath);
            }
        } catch (IOException e) {
            logger.warn("Failed to read Beckett value cache from {}: {}", cachePath, e.getMessage());
        }
        return cache;
    }

    public void put(String cardId, BeckettValueEntry entry) {
        if (cardId != null && !cardId.isBlank() && entry != null) {
            entriesById.put(cardId, entry);
        }
    }

    public Optional<BeckettValueEntry> get(String cardId) {
        if (cardId == null) return Optional.empty();
        return Optional.ofNullable(entriesById.get(cardId));
    }

    public boolean contains(String cardId) {
        return cardId != null && entriesById.containsKey(cardId);
    }

    public int size() {
        return entriesById.size();
    }

    public Map<String, BeckettValueEntry> entries() {
        return Collections.unmodifiableMap(entriesById);
    }

    public synchronized void saveDefault() throws IOException {
        save(DEFAULT_CACHE_PATH);
    }

    public synchronized void save(Path cachePath) throws IOException {
        if (cachePath == null) {
            throw new IllegalArgumentException("Cache path must not be null");
        }
        Path parent = cachePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        MAPPER.writeValue(cachePath.toFile(), entriesById);
        logger.info("Saved {} Beckett value entries to {}", entriesById.size(), cachePath);
    }
}
