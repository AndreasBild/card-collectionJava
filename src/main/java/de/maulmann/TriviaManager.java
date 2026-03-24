package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TriviaManager {
    private JsonNode config;
    private final ObjectMapper mapper = new ObjectMapper();

    public TriviaManager() {
        // Pfad muss mit deiner Ordnerstruktur in src/main/resources übereinstimmen
        try (InputStream is = getClass().getResourceAsStream("/config/trivia_config.json")) {
            if (is != null) {
                this.config = mapper.readTree(is);
            } else {
                System.err.println("trivia_config.json wurde im Pfad /config/ nicht gefunden!");
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Laden der trivia_config.json: " + e.getMessage());
        }
    }

    public String getTrivia(String type, Map<String, String> cardData) {
        if (config == null || !config.has(type)) return "";

        Set<String> results = new LinkedHashSet<>();
        for (JsonNode rule : config.get(type)) {
            if (matches(rule.get("condition"), cardData)) {
                results.add(rule.get("text").asText());
            }
        }
        return String.join(" ", results);
    }

    private boolean matches(JsonNode condition, Map<String, String> cardData) {
        Iterator<Map.Entry<String, JsonNode>> fields = condition.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fullKey = entry.getKey();

            // Handle logical variants like "Variant!", "Variant_2", "Serial="
            String baseKey = fullKey;
            boolean negate = false;
            boolean exact = false;

            if (fullKey.endsWith("!")) {
                baseKey = fullKey.substring(0, fullKey.length() - 1);
                negate = true;
            } else if (fullKey.endsWith("=")) {
                baseKey = fullKey.substring(0, fullKey.length() - 1);
                exact = true;
            }

            if (baseKey.contains("_")) {
                baseKey = baseKey.split("_")[0];
            }

            String cardValue = cardData.getOrDefault(baseKey, "").trim().toLowerCase();
            String conditionValue = entry.getValue().asText().trim().toLowerCase();

            if (negate) {
                if (cardValue.contains(conditionValue)) {
                    return false;
                }
            } else if (exact) {
                if (!cardValue.equals(conditionValue)) {
                    return false;
                }
            } else {
                if (!cardValue.contains(conditionValue)) {
                    return false;
                }
            }
        }
        return true;
    }
}