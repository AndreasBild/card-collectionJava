package de.maulmann;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified service for loading and deserializing Card JSON datasets.
 * Supports transparent build-time market data overlay from MarketDataCache.
 */
public class CardDataLoader {

    private static final Logger log = LoggerFactory.getLogger(CardDataLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<CardJson> loadCardsFromJson(String jsonPath) {
        return loadCardsFromJson(jsonPath, MarketDataCache.loadDefault());
    }

    public static List<CardJson> loadCardsFromJson(String jsonPath, MarketDataCache marketCache) {
        List<CardJson> rawCards = loadRawCards(jsonPath);
        if (marketCache == null || marketCache.size() == 0) {
            return rawCards;
        }

        return rawCards.stream().map(c -> {
            Optional<MarketDataEntry> match = marketCache.findMatch(c.id(), c.certNumber());
            return match.map(c::enrichWith).orElse(c);
        }).toList();
    }

    public static List<CardData> loadCards(Path path) {
        List<CardJson> jsonList = loadCardsFromJson(path.toString());
        return jsonList.stream().map(CardData::new).toList();
    }

    public static List<CardData> loadCards(Path path, MarketDataCache marketCache) {
        List<CardJson> jsonList = loadCardsFromJson(path.toString(), marketCache);
        return jsonList.stream().map(CardData::new).toList();
    }

    public static List<CardJson> loadRawCards(String jsonPath) {
        Path path = Paths.get(jsonPath);

        // 1. Try local file system path first
        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                return MAPPER.readValue(is, new TypeReference<List<CardJson>>() {});
            } catch (IOException e) {
                log.error("Failed to load cards from file path: {}", jsonPath, e);
            }
        }

        // 2. Try classpath resource fallback
        String resourcePath = jsonPath.startsWith("/") ? jsonPath : "/" + jsonPath;
        try (InputStream is = CardDataLoader.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return MAPPER.readValue(is, new TypeReference<List<CardJson>>() {});
            }
        } catch (IOException e) {
            log.error("Failed to load cards from classpath resource: {}", jsonPath, e);
        }

        log.warn("Card dataset not found at {} or on classpath.", jsonPath);
        return Collections.emptyList();
    }
}
