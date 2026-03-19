package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Iterator; // WICHTIG: Fehlender Import
import java.util.Map;

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

        for (JsonNode rule : config.get(type)) {
            if (matches(rule.get("condition"), cardData)) {
                return rule.get("text").asText();
            }
        }
        return "";
    }

    private boolean matches(JsonNode condition, Map<String, String> cardData) {
        Iterator<Map.Entry<String, JsonNode>> fields = condition.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();

            // Hole den Wert aus den Kartendaten (z.B. "Upper Deck")
            String cardValue = cardData.getOrDefault(entry.getKey(), "").toLowerCase();
            // Hole den Wert aus der Bedingung (z.B. "upper deck")
            String conditionValue = entry.getValue().asText().toLowerCase();

            // contains ist super, da es auch Teil-Matches erlaubt (z.B. "PMG Green" enthält "PMG")
            if (!cardValue.contains(conditionValue)) {
                return false;
            }
        }
        return true;
    }
}