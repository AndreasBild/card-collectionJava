package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class FirebaseConfigManager {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfigManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFIG_PATH = "/config/firebase_config.json";

    private final SimpleLazyConstant<Map<String, String>> config = SimpleLazyConstant.of(() -> {
        Map<String, String> configMap = new HashMap<>();

        // Try environment variables first
        String[] keys = {"apiKey", "authDomain", "projectId", "storageBucket", "messagingSenderId", "appId", "measurementId"};
        boolean foundInEnv = false;
        for (String key : keys) {
            // Pattern 1: FIREBASE_APIKEY
            String envValue = System.getenv("FIREBASE_" + key.toUpperCase());

            // Pattern 2: FIREBASE_API_KEY (screaming snake case)
            if (envValue == null || envValue.isEmpty()) {
                String snakeKey = key.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
                envValue = System.getenv("FIREBASE_" + snakeKey);
            }

            // Pattern 3: apiKey (exact)
            if (envValue == null || envValue.isEmpty()) {
                envValue = System.getenv(key);
            }

            if (envValue != null && !envValue.isEmpty()) {
                configMap.put(key, envValue);
                foundInEnv = true;
            }
        }

        if (foundInEnv) {
            log.info("Loaded Firebase configuration from environment variables.");
            // Fill missing with empty if partially defined in env
            for (String key : keys) {
                configMap.putIfAbsent(key, "");
            }
            return configMap;
        }

        // Try config file
        try (InputStream is = FirebaseConfigManager.class.getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
                JsonNode node = MAPPER.readTree(is);
                for (String key : keys) {
                    if (node.has(key)) {
                        configMap.put(key, node.get(key).asText());
                    } else {
                        configMap.put(key, "");
                    }
                }
                log.info("Loaded Firebase configuration from {}.", CONFIG_PATH);
                return configMap;
            } else {
                log.warn("Firebase configuration file {} not found.", CONFIG_PATH);
            }
        } catch (Exception e) {
            log.error("Error loading Firebase configuration from {}: {}", CONFIG_PATH, e.getMessage());
        }

        // Return empty map as fallback
        for (String key : keys) {
            configMap.put(key, "");
        }
        return configMap;
    });

    public Map<String, String> getConfig() {
        return config.get();
    }

    private static class SimpleLazyConstant<T> {
        private final Supplier<T> supplier;
        private volatile T value;

        private SimpleLazyConstant(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        public static <T> SimpleLazyConstant<T> of(Supplier<T> supplier) {
            return new SimpleLazyConstant<>(supplier);
        }

        public T get() {
            T result = value;
            if (result == null) {
                synchronized (this) {
                    result = value;
                    if (result == null) {
                        value = result = supplier.get();
                    }
                }
            }
            return result;
        }
    }
}
