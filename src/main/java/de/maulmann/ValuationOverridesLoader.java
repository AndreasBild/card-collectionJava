package de.maulmann;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Thread-safe loader for curated valuation and auction comp overrides.
 * Ingests content/json/valuation-overrides.json.
 */
public class ValuationOverridesLoader {

    private static final Logger logger = LoggerFactory.getLogger(ValuationOverridesLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path DEFAULT_PATH = Paths.get("content/json/valuation-overrides.json");

    private final Map<String, ValuationOverride> overrides = new ConcurrentHashMap<>();

    public ValuationOverridesLoader() {}

    public static ValuationOverridesLoader loadDefault() {
        return load(DEFAULT_PATH);
    }

    public static ValuationOverridesLoader load(Path path) {
        ValuationOverridesLoader loader = new ValuationOverridesLoader();
        if (path == null || !Files.exists(path)) {
            logger.info("Valuation overrides file not found at {}. Proceeding without overrides.", path);
            return loader;
        }

        try {
            Map<String, ValuationOverride> map = MAPPER.readValue(
                    path.toFile(),
                    new TypeReference<Map<String, ValuationOverride>>() {}
            );
            if (map != null) {
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        loader.overrides.put(k.toLowerCase().trim(), v);
                    }
                });
                logger.info("Loaded {} curated valuation overrides from {}", loader.overrides.size(), path);
            }
        } catch (IOException e) {
            logger.warn("Failed to load valuation overrides from {}: {}", path, e.getMessage());
        }
        return loader;
    }

    public Optional<ValuationOverride> getOverride(String cardId) {
        if (cardId == null || cardId.isBlank()) return Optional.empty();
        return Optional.ofNullable(overrides.get(cardId.toLowerCase().trim()));
    }

    public boolean hasOverride(String cardId) {
        if (cardId == null || cardId.isBlank()) return false;
        return overrides.containsKey(cardId.toLowerCase().trim());
    }

    public Map<String, ValuationOverride> getAllOverrides() {
        return Collections.unmodifiableMap(overrides);
    }

    public int size() {
        return overrides.size();
    }
}
