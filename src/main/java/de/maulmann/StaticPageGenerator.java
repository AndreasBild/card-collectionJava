package de.maulmann;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generator for static pages (index.html, error.html, etc.), subsidiary collection pages, and static assets.
 */
public class StaticPageGenerator {

    private static final Logger log = LoggerFactory.getLogger(StaticPageGenerator.class);
    private static final String BASE_URL = CardUtils.BASE_URL;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PATTERN_SCRIPT_LDJSON = Pattern.compile("(?s)<script type=\"application/ld\\+json\">.*?</script>");

    public static void buildStaticPages(List<CardJson> allCards, TimestampTracker timestampTracker, String pathOutput) {
        try {
            log.info("Baue index.html & error.html...");

            Map<String, Object> indexData = SharedTemplates.createBaseData(
                    "Juwan Howard Super Collector | Private Collection",
                    "Welcome to the ultimate Juwan Howard Private Collection. A Super Collector showcase featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.",
                    "index.html", "index", "");

            String indexHeroPreload = "<link rel=\"preload\" as=\"image\" type=\"image/avif\" " +
                    "href=\"images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-200w.avif\" " +
                    "imagesrcset=\"images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-200w.avif 200w, " +
                    "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-400w.avif 400w, " +
                    "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-600w.avif 600w, " +
                    "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-900w.avif 900w, " +
                    "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front.avif 1200w\" " +
                    "imagesizes=\"(max-width: 768px) 190px, 260px\" fetchpriority=\"high\">";

            indexData.put("headHtml", SharedTemplates.getHead(
                    "Juwan Howard Super Collector | Private Collection",
                    "Welcome to the ultimate Juwan Howard Private Collection. A Super Collector showcase featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.",
                    "", "index.html", FileGenerator.DEFAULT_IMAGE, indexHeroPreload));

            List<Map<String, String>> indexBreadcrumbItems = new ArrayList<>();
            indexBreadcrumbItems.add(Map.of("name", "Home", "link", ""));
            indexData.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(indexBreadcrumbItems));

            List<Map<String, String>> indexBcItems = new ArrayList<>();
            indexBcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));

            String indexJsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    " + SharedTemplates.getBreadcrumbJsonLd(indexBcItems, BASE_URL + "/#breadcrumb") + ",\n" +
                    "    {\n" +
                    "      \"@type\": \"WebSite\",\n" +
                    "      \"name\": \"Maulmann Trading Cards\",\n" +
                    "      \"url\": \"" + BASE_URL + "\",\n" +
                    "      \"description\": \"Private collection of the Juwan Howard Super Collector\",\n" +
                    "      \"potentialAction\": {\n" +
                    "        \"@type\": \"SearchAction\",\n" +
                    "        \"target\": {\n" +
                    "          \"@type\": \"EntryPoint\",\n" +
                    "          \"urlTemplate\": \"" + BASE_URL + "/Juwan-Howard-Collection.html?search={search_term_string}\"\n" +
                    "        },\n" +
                    "        \"query-input\": \"required name=search_term_string\"\n" +
                    "      }\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"Person\",\n" +
                    "      \"name\": \"Mauli Maulmann\",\n" +
                    "      \"jobTitle\": \"Juwan Howard Super Collector\",\n" +
                    "      \"url\": \"" + BASE_URL + "\",\n" +
                    "      \"sameAs\": [\n" +
                    "        \"https://www.instagram.com/maulmann_cards/\"\n" +
                    "      ],\n" +
                    "      \"knowsAbout\": [\n" +
                    "        \"Juwan Howard\",\n" +
                    "        \"Trading Cards\",\n" +
                    "        \"90s Basketball Cards\",\n" +
                    "        \"NBA Collectibles\",\n" +
                    "        \"Precious Metal Gems\",\n" +
                    "        \"Panini Flawless\"\n" +
                    "      ]\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"CollectionPage\",\n" +
                    "      \"name\": \"Juwan Howard Masterpiece Collection\",\n" +
                    "      \"description\": \"A massive private collection of rare Juwan Howard basketball cards.\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "</script>";
            indexData.put("jsonLd", indexJsonLd);

            // SEO "New In" Linkjuice internal links (1 < printRun < 10) - Limited to 6
            List<CardJson> rareNewInCards = allCards.stream()
                    .filter(c -> c.printRun() != null && c.printRun() > 1 && c.printRun() < 10)
                    .limit(6)
                    .toList();

            List<Map<String, String>> newInLinks = new ArrayList<>();
            for (CardJson c : rareNewInCards) {
                CardData cardData = CardPageGenerator.computeCardData(c);
                String cleanPlayer = CardData.cleanPlayerName(c.player());
                String brandText = c.brand() != null ? c.brand() : "";
                String variantText = c.variant() != null ? c.variant() : "Base";
                String seasonText = c.season() != null ? c.season() : "";
                String cardTitle = cleanPlayer + " " + seasonText + " " + brandText + " " + variantText + " (/" + c.printRun() + ")";
                newInLinks.add(Map.of(
                        "url", cardData.fullRelativePath,
                        "title", cardTitle.trim()
                ));
            }
            indexData.put("newInLinks", newInLinks);

            // SEO "Masterpieces" Linkjuice internal links (1/1 Non-Plate, Non-Proof cards) - Limited to 6
            List<CardJson> masterpieceCards = allCards.stream()
                    .filter(CardStatsService::isOneOfOneMasterpiece)
                    .limit(6)
                    .toList();

            List<Map<String, String>> masterpieceLinks = new ArrayList<>();
            for (CardJson c : masterpieceCards) {
                CardData cardData = CardPageGenerator.computeCardData(c);
                String cleanPlayer = CardData.cleanPlayerName(c.player());
                String brandText = c.brand() != null ? c.brand() : "";
                String variantText = c.variant() != null ? c.variant() : "1/1";
                String seasonText = c.season() != null ? c.season() : "";
                String cardTitle = cleanPlayer + " " + seasonText + " " + brandText + " " + variantText + " (1/1)";
                masterpieceLinks.add(Map.of(
                        "url", cardData.fullRelativePath,
                        "title", cardTitle.trim()
                ));
            }
            indexData.put("masterpieceLinks", masterpieceLinks);

            FileGenerator.processTemplate("index.ftlh", indexData, pathOutput + "index.html");

            // Build Rainbow Tracker page
            RainbowPageGenerator.buildRainbowsPage(allCards, timestampTracker, pathOutput);

            // Build 3D Collector's 9-Pocket Binder page
            BinderPageGenerator.buildBinderPage(allCards, timestampTracker, pathOutput);

            // Error 404
            Map<String, Object> errorData = SharedTemplates.createBaseData("Error Page| Maulmann Trading Cards", "The page you are looking for does not exist in the Maulmann Trading Cards collection.", "error.html", "", "/");

            List<Map<String, String>> errorBreadcrumbItems = new ArrayList<>();
            errorBreadcrumbItems.add(Map.of("name", "Home", "link", "index.html"));
            errorBreadcrumbItems.add(Map.of("name", "Error", "link", ""));
            errorData.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(errorBreadcrumbItems));

            FileGenerator.processTemplate("error.ftlh", errorData, pathOutput + "error.html");

        } catch (Exception e) {
            log.error("Fehler bei statischen Seiten: {}", e.getMessage(), e);
        }
    }

    public static void buildOtherCollections(String pathSource, String pathOutput, TimestampTracker timestampTracker) {
        Map<String, String[]> collectionMetas = loadCollectionMetas();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Map.Entry<String, String[]> entry : collectionMetas.entrySet()) {
                executor.submit(() -> {
                    String coll = entry.getKey();
                    String title = entry.getValue()[0];
                    String description = entry.getValue()[1];

                    try {
                        log.info("Baue {}.html...", coll);
                        Map<String, Object> data = SharedTemplates.createBaseData(title, description, coll + ".html", coll.toLowerCase(), "");

                        List<Map<String, String>> breadcrumbItems = new ArrayList<>();
                        breadcrumbItems.add(Map.of("name", "Home", "link", "index.html"));
                        breadcrumbItems.add(Map.of("name", coll, "link", ""));
                        data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(breadcrumbItems));

                        List<Map<String, String>> collBcItems = new ArrayList<>();
                        collBcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
                        collBcItems.add(Map.of("name", coll, "link", BASE_URL + "/" + coll + ".html"));

                        Path jsonPath = Paths.get(pathSource, "json", coll.toLowerCase() + ".json");
                        String tableHtml = "";
                        String summaryHtml = "";
                        List<CardJson> cardList = Collections.emptyList();
                        if (Files.exists(jsonPath)) {
                            cardList = CardDataLoader.loadCardsFromJson(jsonPath.toString());
                            tableHtml = generateHtmlTableFromJson(cardList);
                            Map<String, Object> stats = CardStatsService.computeCollectionStats(cardList);
                            summaryHtml = generateCollectionSummaryHtml(coll, cardList, stats);
                        }

                        StringBuilder itemListSb = new StringBuilder();
                        itemListSb.append("{\n");
                        itemListSb.append("        \"@type\": \"ItemList\",\n");
                        itemListSb.append("        \"name\": \"").append(CardUtils.escapeJson(title.split("\\|")[0].trim())).append(" List\",\n");
                        itemListSb.append("        \"numberOfItems\": ").append(cardList.size());
                        if (!cardList.isEmpty()) {
                            itemListSb.append(",\n        \"itemListElement\": [\n");
                            for (int i = 0; i < cardList.size(); i++) {
                                CardJson c = cardList.get(i);
                                CardData cd = CardPageGenerator.computeCardData(c);
                                String cleanPlayer = CardData.cleanPlayerName(c.player());
                                String cardTitle = cleanPlayer + " " + (c.season() != null ? c.season() : "") + " " + (c.brand() != null ? c.brand() : "") + " " + (c.variant() != null ? c.variant() : "") + " #" + (c.cardNumber() != null ? c.cardNumber() : "");
                                String cardUrl = BASE_URL + "/" + cd.fullRelativePath.replace("../../", "");
                                itemListSb.append("          {\n")
                                        .append("            \"@type\": \"ListItem\",\n")
                                        .append("            \"position\": ").append(i + 1).append(",\n")
                                        .append("            \"name\": \"").append(CardUtils.escapeJson(cardTitle.trim())).append("\",\n")
                                        .append("            \"url\": \"").append(CardUtils.escapeJson(cardUrl)).append("\"\n")
                                        .append("          }").append(i < cardList.size() - 1 ? "," : "").append("\n");
                            }
                            itemListSb.append("        ]\n");
                            itemListSb.append("      }");
                        } else {
                            itemListSb.append("\n      }");
                        }

                        String jsonLd = "<script type=\"application/ld+json\">\n" +
                                "{\n" +
                                "  \"@context\": \"https://schema.org\",\n" +
                                "  \"@graph\": [\n" +
                                "    " + SharedTemplates.getBreadcrumbJsonLd(collBcItems, BASE_URL + "/" + coll + ".html#breadcrumb") + ",\n" +
                                "    {\n" +
                                "      \"@type\": \"CollectionPage\",\n" +
                                "      \"@id\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                                "      \"name\": \"" + CardUtils.escapeJson(title.split("\\|")[0].trim()) + "\",\n" +
                                "      \"description\": \"" + CardUtils.escapeJson(description) + "\",\n" +
                                "      \"url\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                                "      \"publisher\": { \"@type\": \"Person\", \"name\": \"Mauli Maulmann\", \"url\": \"" + BASE_URL + "/\" },\n" +
                                "      \"mainEntity\": " + itemListSb + "\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}\n" +
                                "</script>";
                        data.put("jsonLd", jsonLd);

                        Path sourcePath = Paths.get(pathSource, "other", coll + ".html");
                        if (Files.exists(sourcePath)) {
                            String rawContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
                            Document doc = Jsoup.parse(rawContent);

                            if (rawContent.contains("application/ld+json") && rawContent.contains("FAQPage")) {
                                try {
                                    Element faqScript = doc.selectFirst("script[type=application/ld+json]");
                                    if (faqScript != null) {
                                        String faqJson = faqScript.data().trim();
                                        jsonLd = "<script type=\"application/ld+json\">\n" +
                                                "{\n" +
                                                "  \"@context\": \"https://schema.org\",\n" +
                                                "  \"@graph\": [\n" +
                                                "    " + SharedTemplates.getBreadcrumbJsonLd(collBcItems, BASE_URL + "/" + coll + ".html#breadcrumb") + ",\n" +
                                                "    {\n" +
                                                "      \"@type\": \"CollectionPage\",\n" +
                                                "      \"@id\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                                                "      \"name\": \"" + CardUtils.escapeJson(title.split("\\|")[0].trim()) + "\",\n" +
                                                "      \"description\": \"" + CardUtils.escapeJson(description) + "\",\n" +
                                                "      \"url\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                                                "      \"publisher\": { \"@type\": \"Person\", \"name\": \"Mauli Maulmann\", \"url\": \"" + BASE_URL + "/\" },\n" +
                                                "      \"mainEntity\": " + itemListSb + "\n" +
                                                "    },\n" +
                                                "    " + faqJson + "\n" +
                                                "  ]\n" +
                                                "}\n" +
                                                "</script>";
                                        data.put("jsonLd", jsonLd);
                                    }
                                } catch (Exception e) {
                                    log.error("FAQ Extraction failed for {}", coll);
                                }
                            }

                            Element mainElement = doc.selectFirst("main");
                            String processedContent;
                            if (mainElement != null) {
                                mainElement.select("table, .table-responsive, .analytics-accordion").remove();
                                processedContent = mainElement.html() + (summaryHtml.isEmpty() ? "" : "\n" + summaryHtml) + "\n" + tableHtml;
                            } else {
                                processedContent = rawContent + (summaryHtml.isEmpty() ? "" : "\n" + summaryHtml) + "\n" + tableHtml;
                            }

                            data.put("pageContent", cleanOldPlaceholders(processedContent));
                        } else {
                            String combined = (summaryHtml.isEmpty() ? "" : summaryHtml + "\n") + tableHtml;
                            data.put("pageContent", combined.isEmpty() ? "<p>No data found for this collection yet.</p>" : combined);
                        }

                        FileGenerator.processTemplate("generic-collection.ftlh", data, pathOutput + coll + ".html");
                    } catch (Exception e) {
                        log.error("Fehler bei {}: {}", coll, e.getMessage());
                    }
                });
            }
        }
    }

    public static Map<String, String[]> loadCollectionMetas() {
        Map<String, String[]> map = new HashMap<>();
        try (InputStream is = StaticPageGenerator.class.getResourceAsStream("/config/collections_config.json")) {
            if (is != null) {
                JsonNode root = MAPPER.readTree(is);
                for (Map.Entry<String, JsonNode> entry : root.properties()) {
                    String key = entry.getKey();
                    JsonNode val = entry.getValue();
                    String title = val.has("title") ? val.get("title").asText() : "";
                    String desc = val.has("description") ? val.get("description").asText() : "";
                    map.put(key, new String[]{title, desc});
                }
            }
        } catch (Exception e) {
            log.error("Error loading collections_config.json: {}", e.getMessage());
        }
        return map;
    }

    public static String generateHtmlTableFromJson(List<CardJson> cardList) {
        if (cardList == null || cardList.isEmpty()) {
            return "<p>No cards found in this collection.</p>";
        }

        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<div class=\"collection-controls filter-container\">")
                .append("<div class=\"search-input-wrapper\">")
                .append("<input type=\"text\" class=\"filter-input table-search-input\" placeholder=\"Search collection... (Press / or Cmd+K to focus)\" aria-label=\"Search collection\">")
                .append("<span class=\"search-hotkey-badge\" title=\"Press / or Cmd+K to search\"><kbd>Cmd+K</kbd> <kbd>/</kbd></span>")
                .append("</div></div>");

        htmlBuilder.append("<div class=\"table-responsive card-container-box\"><table><thead><tr>")
                .append("<th>Player</th><th>Team</th><th>Sport</th><th>Season</th>")
                .append("<th>Company</th><th>Brand</th><th>Theme</th><th>Variant</th>")
                .append("<th>Number</th><th>Serial</th><th>Print Run</th>")
                .append("<th>Rookie</th><th>Game Used</th><th>Autograph</th><th>Grade</th>")
                .append("</tr></thead><tbody>");

        for (CardJson c : cardList) {
            CardData cardData = CardPageGenerator.computeCardData(c);
            String detailPath = cardData.fullRelativePath;
            String cleanPlayer = CardData.cleanPlayerName(c.player());
            String playerTitle = "View " + CardUtils.escapeHtml(cleanPlayer) + " " + CardUtils.escapeHtml(c.season()) + " " + CardUtils.escapeHtml(c.brand()) + " #" + CardUtils.escapeHtml(c.cardNumber() != null ? c.cardNumber() : "") + " card detail page";
            String variantText = CardUtils.escapeHtml(c.variant() != null ? c.variant() : "Base");
            htmlBuilder.append("<tr id=\"").append(cardData.filenameBase).append("\">")
                    .append("<td data-label=\"Card\"><a href=\"").append(detailPath).append("\" class=\"table-button\" title=\"").append(playerTitle).append("\" itemprop=\"url\"><span itemprop=\"name\">").append(CardUtils.escapeHtml(cleanPlayer)).append("</span></a></td>")
                    .append("<td data-label=\"Team\">").append(CardUtils.escapeHtml(c.team())).append("</td>")
                    .append("<td data-label=\"Sport\">Basketball</td>")
                    .append("<td data-label=\"Season\">").append(CardUtils.escapeHtml(c.season())).append("</td>")
                    .append("<td data-label=\"Company\">").append(CardUtils.escapeHtml(c.company())).append("</td>")
                    .append("<td data-label=\"Brand\">").append(CardUtils.escapeHtml(c.brand())).append("</td>")
                    .append("<td data-label=\"Set / Theme\">").append(CardUtils.escapeHtml(c.theme())).append("</td>")
                    .append("<td data-label=\"Variant\">").append(variantText).append("</td>")
                    .append("<td data-label=\"Card #\">").append(CardUtils.escapeHtml(c.cardNumber())).append("</td>")
                    .append("<td data-label=\"Serial #\">").append((c.serialNumber() != null && !c.serialNumber().trim().isEmpty() && !c.serialNumber().trim().equals("0")) ? CardUtils.escapeHtml(c.serialNumber()) : "—").append("</td>")
                    .append("<td data-label=\"Print Run\">").append((c.printRun() != null && c.printRun() > 0) ? String.valueOf(c.printRun()) : "—").append("</td>")
                    .append("<td data-label=\"Rookie\">").append(c.isRookie() ? "Yes" : "No").append("</td>")
                    .append("<td data-label=\"Patch\">").append(c.isPatch() ? "Yes" : "No").append("</td>")
                    .append("<td data-label=\"Autograph\">").append(c.isAutograph() ? "Yes" : "No").append("</td>")
                    .append("<td data-label=\"Grade\">").append(CardUtils.escapeHtml(c.grade() != null ? c.grade() : "No")).append("</td>")
                    .append("</tr>");
        }
        htmlBuilder.append("</tbody></table></div>");
        return htmlBuilder.toString();
    }

    public static String generateCollectionSummaryHtml(String collName, List<CardJson> cardList, Map<String, Object> stats) {
        if (cardList == null || cardList.isEmpty() || stats == null) {
            return "";
        }

        boolean isTargetCollection = collName.equalsIgnoreCase("Flawless")
                || collName.equalsIgnoreCase("Panini")
                || collName.equalsIgnoreCase("Baseball");
        double totalEstimatedVal = getDouble(stats, "totalEstimatedValue", 0.0);
        if (totalEstimatedVal <= 0.0 && !isTargetCollection) {
            return "";
        }

        String formattedEstimatedVal = getString(stats, "formattedEstimatedValue", "$0");
        String formattedAvgVal = getString(stats, "formattedAvgCardValue", "$0.00");
        String totalCards = getString(stats, "totalCards", String.valueOf(cardList.size()));
        int rawTotal = getInt(stats, "rawTotalCards", cardList.size());
        int countPriced = getInt(stats, "countPriced", 0);
        int marketCoveragePct = getInt(stats, "marketCoveragePct", 0);

        int count1of1 = getInt(stats, "count1of1", 0);
        int pct1of1 = getInt(stats, "pct1of1", 0);
        int countUltraSp = getInt(stats, "countUltraSp", 0);
        int pctUltraSp = getInt(stats, "pctUltraSp", 0);
        int countSerialized = getInt(stats, "countSerialized", 0);
        int pctSerialized = getInt(stats, "pctSerialized", 0);

        int countAutographs = getInt(stats, "countAutographs", 0);
        int pctAutographs = getInt(stats, "pctAutographs", 0);
        int countPatches = getInt(stats, "countPatches", 0);
        int pctPatches = getInt(stats, "pctPatches", 0);
        int countRookies = getInt(stats, "countRookies", 0);
        int pctRookies = getInt(stats, "pctRookies", 0);
        int countGraded = getInt(stats, "countGradedTotal", 0);
        int pctGraded = getInt(stats, "pctGradedTotal", 0);

        StringBuilder sb = new StringBuilder();
        sb.append("<details class=\"analytics-accordion context-card full-width\" id=\"analyticsAccordion\" open>\n");
        sb.append("    <summary class=\"analytics-summary-bar\">\n");
        sb.append("        <div class=\"summary-left\">\n");
        sb.append("            <span class=\"summary-title\">&#x1F4C8; Vault Analytics</span>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"summary-badges\">\n");
        if (totalEstimatedVal > 0) {
            sb.append("            <span class=\"vault-valuation-badge\" title=\"Estimated Total Collection Market Valuation\">&#x1F4B0; <strong>")
                    .append(formattedEstimatedVal).append("</strong></span>\n");
        }
        sb.append("            <span class=\"vault-count-badge\"><strong>").append(totalCards).append("</strong> Cards</span>\n");
        if (count1of1 > 0) {
            sb.append("            <span class=\"vault-count-badge gold\"><strong>").append(count1of1).append("</strong> 1/1s</span>\n");
        } else if (countAutographs > 0) {
            sb.append("            <span class=\"vault-count-badge gold\"><strong>").append(countAutographs).append("</strong> Autos</span>\n");
        }
        sb.append("        </div>\n");
        sb.append("        <span class=\"summary-toggle-icon\" aria-hidden=\"true\">&#x25BE;</span>\n");
        sb.append("    </summary>\n");
        sb.append("    <div class=\"metrics-grid\">\n");

        // Metric Card 1: Valuation & Market Stats
        sb.append("        <div class=\"metric-card\">\n");
        sb.append("            <h3>&#x1F4B0; Valuation &amp; Market Stats</h3>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Estimated Total Value</span> <strong>").append(formattedEstimatedVal).append("</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar emerald\" style=\"width: 100%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Average Card Value</span> <strong>").append(formattedAvgVal).append("</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar cyan\" style=\"width: 100%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Pricing Coverage</span> <strong>").append(countPriced).append(" / ").append(rawTotal).append(" (").append(marketCoveragePct).append("%)</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar gold\" style=\"width: ").append(marketCoveragePct).append("%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");

        // Metric Card 2: Attributes & Features
        sb.append("        <div class=\"metric-card\">\n");
        sb.append("            <h3>&#x270D;&#xFE0F; Attributes &amp; Features</h3>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Autographs</span> <strong>").append(countAutographs).append(" (").append(pctAutographs).append("%)</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar green\" style=\"width: ").append(pctAutographs).append("%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Game-Used Patch / Mem</span> <strong>").append(countPatches).append(" (").append(pctPatches).append("%)</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar purple\" style=\"width: ").append(pctPatches).append("%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"meter-group\">\n");
        if (countGraded > 0) {
            sb.append("                <div class=\"meter-label\"><span>Graded Cards</span> <strong>").append(countGraded).append(" (").append(pctGraded).append("%)</strong></div>\n");
            sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar cyan\" style=\"width: ").append(pctGraded).append("%;\"></div></div>\n");
        } else {
            sb.append("                <div class=\"meter-label\"><span>Rookie Cards (RC)</span> <strong>").append(countRookies).append(" (").append(pctRookies).append("%)</strong></div>\n");
            sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar orange\" style=\"width: ").append(pctRookies).append("%;\"></div></div>\n");
        }
        sb.append("            </div>\n");
        sb.append("        </div>\n");

        // Metric Card 3: Rarity Tier Breakdown
        sb.append("        <div class=\"metric-card\">\n");
        sb.append("            <h3>&#x1F48E; Rarity Tier Breakdown</h3>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>1/1 Masterpieces</span> <strong>").append(count1of1).append("</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar\" style=\"width: ").append(pct1of1).append("%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Ultra SP (&le; 10)</span> <strong>").append(countUltraSp).append("</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar gold\" style=\"width: ").append(pctUltraSp).append("%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"meter-group\">\n");
        sb.append("                <div class=\"meter-label\"><span>Serialized Cards</span> <strong>").append(countSerialized).append(" (").append(pctSerialized).append("%)</strong></div>\n");
        sb.append("                <div class=\"meter-bar-track\"><div class=\"meter-bar blue\" style=\"width: ").append(pctSerialized).append("%;\"></div></div>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");

        sb.append("    </div>\n");
        sb.append("</details>\n");

        return sb.toString();
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return def;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    public static void copyResources(String pathOutput) {
        try {
            // 1. Copy CSS
            Path cssDir = Paths.get(pathOutput, "css");
            Files.createDirectories(cssDir);
            Path cssSource = Paths.get("src/main/resources/css/main.css");
            if (Files.exists(cssSource)) {
                Files.copy(cssSource, cssDir.resolve("main.css"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Copy llms.txt from root
            Path llmsSource = Paths.get("llms.txt");
            if (Files.exists(llmsSource)) {
                Files.copy(llmsSource, Paths.get(pathOutput, "llms.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 3. Copy PWA assets
            Path pwaSourceDir = Paths.get("src/main/resources/pwa");
            if (Files.exists(pwaSourceDir)) {
                try (var stream = Files.walk(pwaSourceDir)) {
                    stream.filter(Files::isRegularFile).forEach(source -> {
                        try {
                            Path target = Paths.get(pathOutput).resolve(pwaSourceDir.relativize(source));
                            Files.createDirectories(target.getParent());

                            if (source.getFileName().toString().equals("serviceWorker.js")) {
                                String content = Files.readString(source, StandardCharsets.UTF_8);
                                content = content.replace("[[BUILD_ID]]", SharedTemplates.BUILD_ID);
                                Files.writeString(target, content, StandardCharsets.UTF_8);
                            } else {
                                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            log.error("Error copying PWA asset {}: {}", source, e.getMessage());
                        }
                    });
                }
            }

            // 4. Copy Favicon assets
            Path faviconSourceDir = Paths.get("src/main/resources/favicon");
            if (Files.exists(faviconSourceDir)) {
                try (var stream = Files.walk(faviconSourceDir)) {
                    stream.filter(Files::isRegularFile).forEach(source -> {
                        try {
                            Path target = Paths.get(pathOutput, "favicon").resolve(faviconSourceDir.relativize(source));
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.error("Error copying Favicon asset {}: {}", source, e.getMessage());
                        }
                    });
                }
            }

            // 5. Copy SEO assets (sitemap.xsl)
            Path seoSourceDir = Paths.get("src/main/resources/seo");
            if (Files.exists(seoSourceDir)) {
                try (var stream = Files.walk(seoSourceDir)) {
                    stream.filter(Files::isRegularFile).forEach(source -> {
                        try {
                            Path target = Paths.get(pathOutput).resolve(seoSourceDir.relativize(source));
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.error("Error copying SEO asset {}: {}", source, e.getMessage());
                        }
                    });
                }
            }

            // 6. Copy OpenSearch definition
            Path opensearchSource = Paths.get("src/main/resources/opensearch.xml");
            if (Files.exists(opensearchSource)) {
                Files.copy(opensearchSource, Paths.get(pathOutput, "opensearch.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Error copying resources: {}", e.getMessage());
        }
    }

    public static String cleanOldPlaceholders(String content) {
        String cleaned = PATTERN_SCRIPT_LDJSON.matcher(content).replaceAll("");
        return cleaned.replace("{{HEAD}}", "")
                .replace("{{TOP_NAV}}", "")
                .replace("{{FOOTER_NAV}}", "")
                .replace("{{FOOTER}}", "")
                .replace("{{TIME}}", "");
    }
}
