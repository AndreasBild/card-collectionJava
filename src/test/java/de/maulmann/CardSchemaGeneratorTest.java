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
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("2014-15")
                .company("Panini")
                .brand("Flawless")
                .cardNumber("15")
                .variant("Ruby")
                .build();

        CardData cardData = new CardData(c, "flawless-15");
        cardData.seasonFolder = "2014-15";
        cardData.filename = "juwan-howard-flawless-ruby-15.html";

        String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "Flawless card description", "2014-15 Panini Flawless #15", "Flawless.html", "2014-15-flawless-ruby-15", "");

        Document doc = Jsoup.parseBodyFragment(jsonLdHtml);
        Element ldJsonScript = doc.selectFirst("script[type=application/ld+json]");
        assertNotNull(ldJsonScript);

        JsonNode root = objectMapper.readTree(ldJsonScript.html());
        JsonNode graph = root.get("@graph");
        assertNotNull(graph);

        JsonNode artworkNode = null;
        for (JsonNode node : graph) {
            String type = node.get("@type").asText();
            if ("VisualArtwork".equals(type)) {
                artworkNode = node;
            }
        }
        assertNotNull(artworkNode);

        // Check Artwork ID
        assertEquals("https://www.maulmann.de/cards/2014-15/juwan-howard-flawless-ruby-15.html#artwork", artworkNode.get("@id").asText());

        // Check Breadcrumb collection name
        JsonNode breadcrumbNode = null;
        for (JsonNode node : graph) {
            if ("BreadcrumbList".equals(node.get("@type").asText())) {
                breadcrumbNode = node;
            }
        }
        assertNotNull(breadcrumbNode);
        JsonNode elements = breadcrumbNode.get("itemListElement");
        assertEquals(4, elements.size());
        assertEquals("Flawless", elements.get(1).get("name").asText());
        assertEquals("https://www.maulmann.de/Flawless.html", elements.get(1).get("item").asText());

        // Check Product JSON Category and Color from product template script
        Element productScript = doc.selectFirst("script#product-schema-template");
        assertNotNull(productScript);
        JsonNode productJson = objectMapper.readTree(productScript.html());
        assertEquals("Sports Trading Cards", productJson.get("category").asText());
        assertEquals("Ruby", productJson.get("color").asText());
    }

    @Test
    void testGenerateJsonLdWithPreCachedRatingsEmitsActiveAggregateRating() throws Exception {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("Precious Metal Gems Green")
                .cardNumber("33")
                .build();

        CardData cardData = new CardData(c, "pmg-33");
        cardData.seasonFolder = "1997-98";
        cardData.filename = "juwan-howard-pmg-33.html";

        CardSchemaGenerator.setRatingProperty("pmg-33", "10:48.0");

        try {
            String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "PMG Green 33", "1997-98 PMG Green #33", "Juwan-Howard-Collection.html", "pmg-33", "");
            Document doc = Jsoup.parseBodyFragment(jsonLdHtml);

            // Should have 2 script[type=application/ld+json] tags (Graph + Active Product)
            var scripts = doc.select("script[type='application/ld+json']");
            assertEquals(2, scripts.size());

            Element productScript = scripts.get(1);
            JsonNode productJson = objectMapper.readTree(productScript.html());
            assertEquals("Product", productJson.get("@type").asText());
            assertTrue(productJson.has("aggregateRating"));
            assertEquals(4.8, productJson.get("aggregateRating").get("ratingValue").asDouble(), 0.01);
            assertEquals(10, productJson.get("aggregateRating").get("reviewCount").asInt());
        } finally {
            CardSchemaGenerator.clearRatingCache();
        }
    }

    @Test
    void testGenerateJsonLdHandlesMissingPlayerSameAs() throws Exception {
        CardJson c = CardJson.builder()
                .season("1995-96")
                .build();

        CardData cardData = new CardData(c, "anon-card");
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
        CardJson cVariant = CardJson.builder()
                .player("Juwan Howard")
                .season("1997-98")
                .brand("Fleer Metal Universe")
                .variant("Precious Metal Gems Green")
                .cardNumber("33")
                .build();

        CardData cardDataVariant = new CardData(cVariant, "pmg-green-33");
        String titleVariant = CardPageGenerator.generateBrowserTitle(cardDataVariant, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green #33 | Juwan Howard Private Collection", titleVariant);

        CardJson cBase = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Collectors Choice")
                .variant("Base")
                .cardNumber("278")
                .build();

        CardData cardDataBase = new CardData(cBase, "cc-278");
        String titleBase = CardPageGenerator.generateBrowserTitle(cardDataBase, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1994-95 Collectors Choice #278 | Juwan Howard Private Collection", titleBase);
    }

    @Test
    void testGenerateBrowserTitleAndH1IncludeGrading() {
        CardJson cGraded = CardJson.builder()
                .player("Juwan Howard")
                .season("1995-96")
                .brand("Topps Finest")
                .theme("Mystery Bordered Test")
                .variant("Refractor")
                .cardNumber("M20")
                .gradingCompany("PSA")
                .grade("9")
                .build();

        CardData cardDataGraded = new CardData(cGraded, "finest-m20");
        String titleGraded = CardPageGenerator.generateBrowserTitle(cardDataGraded, "Juwan-Howard-Collection.html");
        assertEquals("Juwan Howard 1995-96 Topps Finest Refractor #M20 PSA-9 | Juwan Howard Private Collection", titleGraded);

        String h1Graded = CardPageGenerator.generateH1(cardDataGraded);
        assertEquals("Juwan Howard | 1995-96 Topps Finest Mystery Bordered Test Refractor #M20 PSA-9", h1Graded);

        String h1HtmlGraded = CardPageGenerator.generateH1Html(cardDataGraded);
        assertTrue(h1HtmlGraded.contains("<br><span class=\"sub-title grading-subtitle\">PSA-9</span>"));
    }

    @Test
    void testArtworkSurfaceAndOffersSchema() throws Exception {
        CardJson cRefractor = CardJson.builder()
                .player("Juwan Howard")
                .season("1996-97")
                .company("Topps")
                .brand("Topps Chrome")
                .variant("Refractor")
                .cardNumber("55")
                .gradingCompany("BGS")
                .grade("9.5")
                .build();

        CardData cardData = new CardData(cRefractor, "chrome-refractor-55");
        cardData.seasonFolder = "1996-97";
        cardData.filename = "juwan-howard-topps-chrome-refractor-55.html";

        String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "Chromium Refractor card", "1996-97 Topps Chrome Refractor #55", "Juwan-Howard-Collection.html", "1996-97-chrome-55", "");

        Document doc = Jsoup.parseBodyFragment(jsonLdHtml);
        Element ldJsonScript = doc.selectFirst("script[type=application/ld+json]");
        assertNotNull(ldJsonScript);

        JsonNode root = objectMapper.readTree(ldJsonScript.html());
        JsonNode graph = root.get("@graph");
        assertNotNull(graph);

        JsonNode artworkNode = null;
        for (JsonNode node : graph) {
            if ("VisualArtwork".equals(node.get("@type").asText())) {
                artworkNode = node;
            }
        }
        assertNotNull(artworkNode);
        assertEquals("Chromium / Refractor Foil", artworkNode.get("artworkSurface").asText());
        assertEquals("Trading Card", artworkNode.get("artMedium").asText());

        // Check Product Offer
        Element productScript = doc.selectFirst("script#product-schema-template");
        assertNotNull(productScript);
        JsonNode productJson = objectMapper.readTree(productScript.html());
        JsonNode offerNode = productJson.get("offers");
        assertNotNull(offerNode);
        assertEquals("https://schema.org/InStock", offerNode.get("availability").asText());
        assertEquals("https://schema.org/NewCondition", offerNode.get("itemCondition").asText());
    }

    @Test
    void testJuwanHowardEntityLinkingInSchema() throws Exception {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1994-95")
                .brand("Finest")
                .cardNumber("240")
                .build();

        CardData cardData = new CardData(c, "finest-240");
        cardData.seasonFolder = "1994-95";
        cardData.filename = "juwan-howard-finest-240.html";

        String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "Rookie Card", "1994-95 Finest #240", "Juwan-Howard-Collection.html", "1994-95-finest-240", "");

        Document doc = Jsoup.parseBodyFragment(jsonLdHtml);
        Element ldJsonScript = doc.selectFirst("script[type=application/ld+json]");
        assertNotNull(ldJsonScript);

        JsonNode root = objectMapper.readTree(ldJsonScript.html());
        JsonNode graph = root.get("@graph");
        assertNotNull(graph);

        JsonNode artworkNode = null;
        for (JsonNode node : graph) {
            if ("VisualArtwork".equals(node.get("@type").asText())) {
                artworkNode = node;
            }
        }
        assertNotNull(artworkNode);
        JsonNode aboutNode = artworkNode.get("about");
        assertNotNull(aboutNode);
        assertEquals("Person", aboutNode.get("@type").asText());
        assertEquals("Juwan Howard", aboutNode.get("name").asText());
        assertTrue(aboutNode.has("sameAs"));
        JsonNode sameAsArray = aboutNode.get("sameAs");
        assertTrue(sameAsArray.isArray());
        List<String> links = new ArrayList<>();
        sameAsArray.forEach(elem -> links.add(elem.asText()));
        assertTrue(links.contains("https://www.wikidata.org/wiki/Q358748"));
        assertTrue(links.contains("https://en.wikipedia.org/wiki/Juwan_Howard"));
        assertTrue(links.contains("https://www.basketball-reference.com/players/h/howarju01.html"));
        assertTrue(links.contains("https://www.nba.com/stats/player/434"));
        assertEquals("Professional Basketball Player & Coach", aboutNode.get("jobTitle").asText());
    }

    @Test
    void testPackOddsInProductSchemaAndFaqAndDateModified() throws Exception {
        CardJson c = CardJson.builder()
                .player("Juwan Howard")
                .season("1996-97")
                .brand("Flair Showcase")
                .theme("Row 0")
                .variant("Legacy Collection")
                .cardNumber("5")
                .serialNumber("5")
                .printRun(150)
                .packOdds("1:30 Packs")
                .lastSoldDate("2026-02-15")
                .build();

        CardData cardData = new CardData(c, "flair-showcase-row0-legacy-5");
        cardData.seasonFolder = "1996-97";
        cardData.filename = "juwan-howard-flair-legacy-5.html";

        var faqItems = CardSchemaGenerator.computeFaqItems(cardData);
        String jsonLdHtml = CardSchemaGenerator.generateJsonLd(cardData, "Legacy Collection card", "1996-97 Flair Showcase Legacy Collection #5", "Juwan-Howard-Collection.html", "1996-97-flair-legacy-5", faqItems);

        Document doc = Jsoup.parseBodyFragment(jsonLdHtml);
        Element ldJsonScript = doc.selectFirst("script[type=application/ld+json]");
        assertNotNull(ldJsonScript);

        JsonNode root = objectMapper.readTree(ldJsonScript.html());
        JsonNode graph = root.get("@graph");
        assertNotNull(graph);

        // 1. Verify ItemPage dateModified
        JsonNode itemPageNode = null;
        JsonNode faqNode = null;
        for (JsonNode node : graph) {
            String type = node.get("@type").asText();
            if ("ItemPage".equals(type)) itemPageNode = node;
            if ("FAQPage".equals(type)) faqNode = node;
        }
        assertNotNull(itemPageNode, "ItemPage node must exist");
        assertEquals("2026-02-15", itemPageNode.get("dateModified").asText(), "ItemPage should contain dateModified from lastSoldDate");

        // 2. Verify FAQPage question and answer for pack odds
        assertNotNull(faqNode, "FAQPage node must exist");
        JsonNode mainEntity = faqNode.get("mainEntity");
        assertTrue(mainEntity.isArray());
        boolean foundOddsFaq = false;
        for (JsonNode qNode : mainEntity) {
            if ("What are the pack insertion odds for this card?".equals(qNode.get("name").asText())) {
                foundOddsFaq = true;
                assertEquals("This card features an official factory pack insertion ratio of 1:30 Packs, making it a rare and coveted find from original packs.",
                        qNode.get("acceptedAnswer").get("text").asText());
            }
        }
        assertTrue(foundOddsFaq, "FAQPage must contain question regarding pack insertion odds");

        // 3. Verify Product additionalProperty has Pack Insertion Odds
        Element productScript = doc.selectFirst("script#product-schema-template");
        assertNotNull(productScript);
        JsonNode productJson = objectMapper.readTree(productScript.html());
        JsonNode additionalProps = productJson.get("additionalProperty");
        assertNotNull(additionalProps);
        assertTrue(additionalProps.isArray());

        boolean foundOddsProp = false;
        for (JsonNode prop : additionalProps) {
            if ("Pack Insertion Odds".equals(prop.get("name").asText())) {
                foundOddsProp = true;
                assertEquals("1:30 Packs", prop.get("value").asText());
            }
        }
        assertTrue(foundOddsProp, "Product schema additionalProperty must contain Pack Insertion Odds");
    }
}
