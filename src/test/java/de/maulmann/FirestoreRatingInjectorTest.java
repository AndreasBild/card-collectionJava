package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreRatingInjectorTest {

    @Test
    void testInjectRatingActivatesScriptWhenRatingsExist() {
        String html = "<html><body>" +
                "<div data-card-id=\"card123\"></div>" +
                "<script id=\"product-schema-template\" type=\"application/json\">{\"@type\":\"Product\"}</script>" +
                "</body></html>";
        Document doc = Jsoup.parse(html);

        Map<String, Map<String, Object>> firestoreData = new HashMap<>();
        Map<String, Object> cardData = new HashMap<>();
        cardData.put("ratingCount", 5L);
        cardData.put("ratingSum", 24.5);
        firestoreData.put("card123", cardData);

        boolean modified = FirestoreRatingInjector.processDocument(doc, "test.html", firestoreData);

        assertTrue(modified);
        assertNull(doc.selectFirst("script#product-schema-template"));
        assertNotNull(doc.selectFirst("script[type='application/ld+json']"));
        assertTrue(doc.html().contains("AggregateRating"));
        assertTrue(doc.html().contains("4.9"));
    }

    @Test
    void testInjectRatingRemovesTemplateWhenNoRatingFound() {
        String html = "<html><body>" +
                "<div data-card-id=\"card999\"></div>" +
                "<script id=\"product-schema-template\" type=\"application/json\">{\"@type\":\"Product\"}</script>" +
                "</body></html>";
        Document doc = Jsoup.parse(html);

        Map<String, Map<String, Object>> firestoreData = new HashMap<>();

        boolean modified = FirestoreRatingInjector.processDocument(doc, "test.html", firestoreData);

        assertTrue(modified);
        assertNull(doc.selectFirst("script#product-schema-template"));
        assertNull(doc.selectFirst("script[type='application/ld+json']"));
    }
}
