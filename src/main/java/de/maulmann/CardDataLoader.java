package de.maulmann;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Unified service for loading and deserializing Card JSON datasets.
 */
public class CardDataLoader {

    private static final Logger log = LoggerFactory.getLogger(CardDataLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<CardJson> loadCardsFromJson(String jsonPath) {
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
