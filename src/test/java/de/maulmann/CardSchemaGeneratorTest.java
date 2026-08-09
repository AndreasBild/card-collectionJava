package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CardSchemaGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testGetBreadcrumbJsonLdEscapingAndId() throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(Map.of("name", "Home", "link", "https://www.maulmann.de/index.html"));
        items.add(Map.of("name", "Fleer & SkyBox \"Collection\"", "link", "https://www.maulmann.de/Juwan-Howard-Collection.html"));

        String jsonLdStr = SharedTemplates.getBreadcrumbJsonLd(items, "https://www.maulmann.de/Juwan-Howard-Collection.html#breadcrumb");

        assertFalse(jsonLdStr.contains("&amp;"), "JSON-LD must not contain HTML entity &amp;");
        assertTrue(jsonLdStr.contains("Fleer & SkyBox \\\"Collection\\\""), "JSON-LD must use JSON quotes escaping");
        assertTrue(jsonLdStr.contains("\"@id\": \"https://www.maulmann.de/Juwan-Howard-Collection.html#breadcrumb\""), "JSON-LD must include @id");

        JsonNode jsonNode = objectMapper.readTree(jsonLdStr);
        assertEquals("BreadcrumbList", jsonNode.get("@type").asText());
        assertEquals("https://www.maulmann.de/Juwan-Howard-Collection.html#breadcrumb", jsonNode.get("@id").asText());
    }

    @Test
    void testGenerateJsonLdFlawlessCategoryAndMatchingId() throws Exception {
        CardJson c = new CardJson();
        c.player = "Juwan Howard";
        c.season = "2014-15";
        c.company = "Panini";
        c.brand = "Flawless";
        c.cardNumber = "15";
        c.variant = "Ruby";

        CardPageGenerator.CardData cardData = new CardPageGenerator.CardData(c, "flawless-15");
        cardData.seasonFolder = "2014-15";
        cardData.filename = "juwan-howard-flawless-ruby-15.html";

        String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "Flawless card description", "2014-15 Panini Flawless #15", "Flawless.html", "2014-15-flawless-ruby-15", "");

        Document doc = Jsoup.parseBodyFragment(jsonLdHtml);
        Element ldJsonScript = doc.selectFirst("script[type=application/ld+json]");
        assertNotNull(ldJsonScript);

        JsonNode root = objectMapper.readTree(ldJsonScript.html());
        JsonNode graph = root.get("@graph");
        assertNotNull(graph);

        // Find nodes in @graph
        JsonNode breadcrumbNode = null;
        JsonNode itemPageNode = null;
        JsonNode artworkNode = null;

        for (JsonNode node : graph) {
            String type = node.get("@type").asText();
            if ("BreadcrumbList".equals(type)) breadcrumbNode = node;
            if ("ItemPage".equals(type)) itemPageNode = node;
            if ("VisualArtwork".equals(type)) artworkNode = node;
        }

        assertNotNull(breadcrumbNode);
        assertNotNull(itemPageNode);
        assertNotNull(artworkNode);

        // Verify breadcrumb category matches "Flawless" instead of hardcoded "Collection"
        JsonNode items = breadcrumbNode.get("itemListElement");
        assertEquals("Flawless", items.get(1).get("name").asText());

        // Verify @id linking
        String breadcrumbId = breadcrumbNode.get("@id").asText();
        String itemPageBreadcrumbRef = itemPageNode.get("breadcrumb").get("@id").asText();
        assertEquals(breadcrumbId, itemPageBreadcrumbRef, "ItemPage.breadcrumb @id must match BreadcrumbList @id");

        // Verify VisualArtwork additions
        assertEquals("2014", artworkNode.get("dateCreated").asText());

        // Verify Product schema template additions
        Element productTemplate = doc.selectFirst("script#product-schema-template");
        assertNotNull(productTemplate);
        JsonNode productJson = objectMapper.readTree(productTemplate.html());
        assertEquals("Product", productJson.get("@type").asText());
        assertEquals("Flawless", productJson.get("brand").get("name").asText());
        assertEquals("Ruby", productJson.get("color").asText());
    }

    @Test
    void testGenerateJsonLdHandlesMissingPlayerSameAs() throws Exception {
        CardJson c = new CardJson();
        c.season = "1995-96";

        CardPageGenerator.CardData cardData = new CardPageGenerator.CardData(c, "anon-card");
        cardData.seasonFolder = "1995-96";
        cardData.filename = "anon-card.html";

        String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "Description", "Title", "Juwan-Howard-Collection.html", "anon-card", "");

        Document doc = Jsoup.parseBodyFragment(jsonLdHtml);
        Element ldJsonScript = doc.selectFirst("script[type=application/ld+json]");
        assertNotNull(ldJsonScript);

        JsonNode root = objectMapper.readTree(ldJsonScript.html());
        JsonNode graph = root.get("@graph");

        JsonNode artworkNode = null;
        for (JsonNode node : graph) {
            if ("VisualArtwork".equals(node.get("@type").asText())) {
                artworkNode = node;
            }
        }
        assertNotNull(artworkNode);
        assertFalse(artworkNode.get("about").has("sameAs"), "Missing player should not generate invalid Wikipedia link");
    }

    @Test
    void testGenerateBrowserTitleIncludesVariant() {
        CardJson cVariant = new CardJson();
        cVariant.player = "Juwan Howard";
        cVariant.season = "1997-98";
        cVariant.brand = "Fleer Metal Universe";
        cVariant.variant = "Precious Metal Gems Green";
        cVariant.cardNumber = "33";

        CardPageGenerator.CardData cardDataVariant = new CardPageGenerator.CardData(cVariant, "pmg-green-33");
        String titleVariant = CardPageGenerator.generateBrowserTitle(cardDataVariant, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green #33 | Juwan Howard Private Collection", titleVariant);

        CardJson cBase = new CardJson();
        cBase.player = "Juwan Howard";
        cBase.season = "1994-95";
        cBase.brand = "Collectors Choice";
        cBase.variant = "Base";
        cBase.cardNumber = "278";

        CardPageGenerator.CardData cardDataBase = new CardPageGenerator.CardData(cBase, "cc-278");
        String titleBase = CardPageGenerator.generateBrowserTitle(cardDataBase, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1994-95 Collectors Choice #278 | Juwan Howard Private Collection", titleBase);
    }

    @Test
    void testGenerateBrowserTitleAndH1IncludeGrading() {
        CardJson cGraded = new CardJson();
        cGraded.player = "Juwan Howard";
        cGraded.season = "1995-96";
        cGraded.brand = "Topps Finest";
        cGraded.theme = "Mystery Bordered Test";
        cGraded.variant = "Refractor";
        cGraded.cardNumber = "M20";
        cGraded.gradingCompany = "PSA";
        cGraded.grade = "9";

        CardPageGenerator.CardData cardDataGraded = new CardPageGenerator.CardData(cGraded, "finest-m20");
        String titleGraded = CardPageGenerator.generateBrowserTitle(cardDataGraded, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1995-96 Topps Finest Refractor #M20 PSA-9 | Juwan Howard Private Collection", titleGraded);

        String h1Graded = CardPageGenerator.generateH1(cardDataGraded);
        assertEquals("Juwan Howard | 1995-96 Topps Finest Mystery Bordered Test Refractor #M20 PSA-9", h1Graded);

        String h1HtmlGraded = CardPageGenerator.generateH1Html(cardDataGraded);
        assertTrue(h1HtmlGraded.contains("<br><span class=\"sub-title grading-subtitle\">PSA-9</span>"));
    }
}
