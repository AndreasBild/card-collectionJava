package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriviaManager {
    private static final Logger log = LoggerFactory.getLogger(TriviaManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final SimpleLazyConstant<TriviaManager> INSTANCE =
            SimpleLazyConstant.of(TriviaManager::new);

    /**
     * Returns the shared TriviaManager singleton instance.
     * Avoids duplicate config loading across CardPageGenerator and CardSchemaGenerator.
     */
    public static TriviaManager getInstance() {
        return INSTANCE.get();
    }

    private final SimpleLazyConstant<JsonNode> config = SimpleLazyConstant.of(() -> {
        try (InputStream is = getClass().getResourceAsStream("/config/trivia_config.json")) {
            if (is != null) {
                return MAPPER.readTree(is);
            } else {
                log.warn("trivia_config.json not found at /config/trivia_config.json");
            }
        } catch (Exception e) {
            log.error("Error loading trivia_config.json: {}", e.getMessage());
        }
        return MAPPER.createObjectNode();
    });

    public TriviaManager() {
    }

    public record FaqItem(String question, String answer) {}

    public List<FaqItem> getFaqs(String type, Map<String, String> cardData) {
        JsonNode configNode = config.get();
        if (configNode == null || !configNode.has(type)) return Collections.emptyList();

        List<FaqItem> list = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        for (JsonNode rule : configNode.get(type)) {
            if (matches(rule.get("condition"), cardData)) {
                String q = rule.has("question") ? rule.get("question").asText() : "";
                String a = rule.has("answer") ? rule.get("answer").asText() : "";
                if (!q.isEmpty() && seenQuestions.add(q)) {
                    list.add(new FaqItem(q, a));
                }
            }
        }
        return list;
    }

    public String getTrivia(String type, Map<String, String> cardData) {
        JsonNode configNode = config.get();
        if (configNode == null || !configNode.has(type)) return "";

        Set<String> results = new LinkedHashSet<>();
        for (JsonNode rule : configNode.get(type)) {
            if (matches(rule.get("condition"), cardData)) {
                results.add(rule.get("text").asText());
            }
        }
        return String.join(" ", results);
    }

    private boolean matches(JsonNode condition, Map<String, String> cardData) {
        if (condition == null || !condition.isObject() || cardData == null) return true;
        Iterator<Map.Entry<String, JsonNode>> fields = condition.properties().iterator();
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