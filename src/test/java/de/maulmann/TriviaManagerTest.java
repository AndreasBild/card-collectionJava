package de.maulmann;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TriviaManagerTest {

    @Test
    void testFaqItemRecord() {
        TriviaManager.FaqItem item = new TriviaManager.FaqItem("Is this card graded?", "Yes, PSA 10.");
        assertEquals("Is this card graded?", item.question());
        assertEquals("Yes, PSA 10.", item.answer());
    }

    @Test
    void testTriviaManagerLoadAndMatch() {
        TriviaManager manager = new TriviaManager();
        Map<String, String> cardData = new HashMap<>();
        cardData.put("Season", "1994-95");
        cardData.put("Player", "Juwan Howard");
        cardData.put("Brand", "Collector's Choice");
        cardData.put("Rookie", "Yes");

        List<TriviaManager.FaqItem> faqs = manager.getFaqs("rookieFaq", cardData);
        assertNotNull(faqs, "getFaqs should never return null.");
    }
}
