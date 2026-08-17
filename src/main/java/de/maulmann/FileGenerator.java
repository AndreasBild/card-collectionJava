package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// --- NEU: Jsoup Imports für die Tabellen-Analyse ---
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class FileGenerator {

    private static final Logger log = LoggerFactory.getLogger(FileGenerator.class);
    private static final String BASE_URL = CardUtils.BASE_URL;
    public static final String DEFAULT_IMAGE = "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-front.avif";
    static String pathSource = "content/";
    static String pathOutput = "output/";

    private static TimestampTracker timestampTracker;
    private static List<CardJson> cachedFilteredCards;

    public static void setTimestampTracker(TimestampTracker tracker) {
        timestampTracker = tracker;
    }

    /**
     * Returns the filtered card list, loading and caching it on first access.
     * Avoids parsing cards.json from disk multiple times during a single build.
     */
    private static synchronized List<CardJson> getCachedCards() {
        if (cachedFilteredCards == null) {
            cachedFilteredCards = filterDuplicateJsonCards(loadCardsFromJson());
        }
        return cachedFilteredCards;
    }

    private static final SimpleLazyConstant<Configuration> FM_CONFIG = SimpleLazyConstant.of(CardUtils::getFreeMarkerConfig);

    // --- 0. LATEST METADATA FÜR PWA ---
    public static void generateLatestMetadata(int totalCardCount) {
        try {
            log.info("Generiere latest.json...");
            String json = "{\n" +
                    "  \"cardCount\": " + totalCardCount + ",\n" +
                    "  \"lastUpdate\": \"" + SharedTemplates.BUILD_ID + "\"\n" +
                    "}";
            Files.writeString(Paths.get(pathOutput, "latest.json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Fehler bei latest.json: {}", e.getMessage());
        }
    }

    // --- 1. HAUPT-SAMMLUNG BAUEN ---
    public static void buildCollectionOverview() {
        try {
            log.info("Baue Juwan-Howard-Collection.html...");
            Map<String, Object> data = createBaseData("Juwan Howard Private Collection | Juwan Howard Super Collector", "Explore the Juwan Howard Masterpiece Collection. A massive private collection featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.", "Juwan-Howard-Collection.html", "collection", "");

            // Schema.org Breadcrumb & CollectionPage
            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
            bcItems.add(Map.of("name", "Collection", "link", BASE_URL + "/Juwan-Howard-Collection.html"));

            List<CardJson> jsonCards = getCachedCards();
            List<CardJson> masterpieceCards = jsonCards.stream()
                    .filter(FileGenerator::isOneOfOneMasterpiece)
                    .limit(10)
                    .toList();

            StringBuilder itemListSb = new StringBuilder();
            itemListSb.append("{\n");
            itemListSb.append("        \"@type\": \"ItemList\",\n");
            itemListSb.append("        \"name\": \"Juwan Howard Trading Card Collection\",\n");
            itemListSb.append("        \"numberOfItems\": ").append(jsonCards.size()).append(",\n");
            itemListSb.append("        \"itemListElement\": [\n");

            for (int i = 0; i < masterpieceCards.size(); i++) {
                CardJson c = masterpieceCards.get(i);
                CardPageGenerator.CardData cd = CardPageGenerator.computeCardData(c);
                String cardTitle = CardPageGenerator.cleanPlayerName(c.player) + " " + (c.season != null ? c.season : "") + " " + (c.brand != null ? c.brand : "") + " " + (c.variant != null ? c.variant : "") + " #" + (c.cardNumber != null ? c.cardNumber : "");
                String cardUrl = BASE_URL + "/" + cd.fullRelativePath.replace("../../", "");
                itemListSb.append("          {\n");
                itemListSb.append("            \"@type\": \"ListItem\",\n");
                itemListSb.append("            \"position\": ").append(i + 1).append(",\n");
                itemListSb.append("            \"name\": \"").append(CardUtils.escapeJson(cardTitle.trim())).append("\",\n");
                itemListSb.append("            \"url\": \"").append(CardUtils.escapeJson(cardUrl)).append("\"\n");
                itemListSb.append("          }").append(i < masterpieceCards.size() - 1 ? "," : "").append("\n");
            }
            itemListSb.append("        ]\n");
            itemListSb.append("      }");

            String jsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    " + SharedTemplates.getBreadcrumbJsonLd(bcItems, BASE_URL + "/Juwan-Howard-Collection.html#breadcrumb") + ",\n" +
                    "    {\n" +
                    "      \"@type\": \"CollectionPage\",\n" +
                    "      \"@id\": \"" + BASE_URL + "/Juwan-Howard-Collection.html\",\n" +
                    "      \"name\": \"Juwan Howard Private Collection\",\n" +
                    "      \"description\": \"A massive private collection featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.\",\n" +
                    "      \"publisher\": { \"@type\": \"Person\", \"name\": \"Mauli Maulmann\" },\n" +
                    "      \"mainEntity\": " + itemListSb.toString() + "\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "</script>";
            data.put("jsonLd", jsonLd);
            data.put("stats", computeCollectionStats(jsonCards));
            List<Map<String, String>> seasons = new ArrayList<>();
            int cumulativeTotal = 0;

            if (!jsonCards.isEmpty()) {
                Map<String, List<CardJson>> grouped = new LinkedHashMap<>();

                List<String> seasonKeys = jsonCards.stream()
                        .map(c -> c.season != null ? c.season : "Unknown")
                        .distinct()
                        .sorted((s1, s2) -> {
                            if (s1.equalsIgnoreCase("College")) return -1;
                            if (s2.equalsIgnoreCase("College")) return 1;
                            return s1.compareTo(s2);
                        })
                        .toList();

                for (String sk : seasonKeys) {
                    grouped.put(sk, new ArrayList<>());
                }
                for (CardJson c : jsonCards) {
                    String sk = c.season != null ? c.season : "Unknown";
                    grouped.computeIfAbsent(sk, k -> new ArrayList<>()).add(c);
                }

                for (Map.Entry<String, List<CardJson>> entry : grouped.entrySet()) {
                    String seasonKey = entry.getKey();
                    List<CardJson> seasonCardList = entry.getValue();

                    Map<String, String> seasonMap = new HashMap<>();
                    seasonMap.put("id", seasonKey.toLowerCase());
                    seasonMap.put("name", seasonKey.equalsIgnoreCase("College") ? "College" : "Season " + seasonKey);

                    StringBuilder htmlBuilder = new StringBuilder();
                    htmlBuilder.append("<table><tr>")
                            .append("<th>Player</th><th>Team</th><th>Sport</th><th>Season</th>")
                            .append("<th>Company</th><th>Brand</th><th>Theme</th><th>Variant</th>")
                            .append("<th>Number</th><th>Serial</th><th>Print Run</th>")
                            .append("<th>Rookie</th><th>Game Used</th><th>Autograph</th><th>Grade</th>")
                            .append("</tr>");

                    for (CardJson c : seasonCardList) {
                        CardPageGenerator.CardData cardData = CardPageGenerator.computeCardData(c);
                        String detailPath = cardData.fullRelativePath;
                        String cleanPlayer = CardPageGenerator.cleanPlayerName(c.player);
                        String playerTitle = "View " + escapeHtml(cleanPlayer) + " " + escapeHtml(c.season) + " " + escapeHtml(c.brand) + " #" + escapeHtml(c.cardNumber != null ? c.cardNumber : "") + " card detail page";
                        String variantText = escapeHtml(c.variant != null ? c.variant : "Base");
                        String variantTitle = "View details for " + escapeHtml(cleanPlayer) + " " + escapeHtml(c.season) + " " + escapeHtml(c.brand) + " " + variantText + " parallel";

                        htmlBuilder.append("<tr id=\"").append(cardData.filenameBase).append("\" data-card-id=\"").append(cardData.stableId).append("\">")
                                .append("<td data-label=\"Card\"><a href=\"").append(detailPath).append("\" class=\"table-button\" title=\"").append(playerTitle).append("\" itemprop=\"url\"><span itemprop=\"name\">").append(escapeHtml(cleanPlayer)).append("</span></a></td>")
                                .append("<td data-label=\"Team\">").append(escapeHtml(c.team)).append("</td>")
                                .append("<td data-label=\"Sport\">Basketball</td>")
                                .append("<td data-label=\"Season\">").append(escapeHtml(c.season)).append("</td>")
                                .append("<td data-label=\"Company\">").append(escapeHtml(c.company)).append("</td>")
                                .append("<td data-label=\"Brand\">").append(escapeHtml(c.brand)).append("</td>")
                                .append("<td data-label=\"Set / Theme\">").append(escapeHtml(c.theme)).append("</td>")
                                .append("<td data-label=\"Variant\">").append(escapeHtml(c.variant)).append("</td>")
                                .append("<td data-label=\"Card #\">").append(escapeHtml(c.cardNumber)).append("</td>")
                                .append("<td data-label=\"Serial #\">").append((c.serialNumber != null && !c.serialNumber.trim().isEmpty() && !c.serialNumber.trim().equals("0")) ? escapeHtml(c.serialNumber) : "—").append("</td>")
                                .append("<td data-label=\"Print Run\">").append((c.printRun != null && c.printRun > 0) ? String.valueOf(c.printRun) : "—").append("</td>")
                                .append("<td data-label=\"Rookie\">").append(c.isRookie ? "Yes" : "No").append("</td>")
                                .append("<td data-label=\"Patch\">").append(c.isPatch ? "Yes" : "No").append("</td>")
                                .append("<td data-label=\"Autograph\">").append(c.isAutograph ? "Yes" : "No").append("</td>")
                                .append("<td data-label=\"Grade\">").append(escapeHtml(c.grade != null ? c.grade : "No")).append("</td>")
                                .append("</tr>");
                    }
                    htmlBuilder.append("</table>");

                    int seasonCardCount = seasonCardList.size();
                    cumulativeTotal += seasonCardCount;

                    seasonMap.put("content", htmlBuilder.toString());
                    seasonMap.put("seasonCount", String.valueOf(seasonCardCount));
                    seasonMap.put("cumulativeTotal", String.valueOf(cumulativeTotal));

                    seasons.add(seasonMap);
                }
            }
            data.put("seasons", seasons);

            List<Map<String, String>> breadcrumbItems = new ArrayList<>();
            breadcrumbItems.add(Map.of("name", "Home", "link", "index.html"));
            breadcrumbItems.add(Map.of("name", "Collection", "link", ""));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(breadcrumbItems));

            processTemplate("collection-overview.ftlh", data, pathOutput + "Juwan-Howard-Collection.html");

            // Metadaten für PWA generieren
            generateLatestMetadata(cumulativeTotal);

        } catch (Exception e) { log.error("Fehler bei Haupt-Collection: {}", e.getMessage()); }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    // --- 2. NEBEN-SAMMLUNGEN BAUEN (Baseball, Panini, etc.) ---
    public static void buildOtherCollections() {
        Map<String, String[]> collectionMetas = loadCollectionMetas();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Map.Entry<String, String[]> entry : collectionMetas.entrySet()) {
                executor.submit(() -> {
                    String coll = entry.getKey();
                    String title = entry.getValue()[0];
                    String description = entry.getValue()[1];

                    try {
                        log.info("Baue {}.html...", coll);
                        Map<String, Object> data = createBaseData(title, description, coll + ".html", coll.toLowerCase(), "");

                        List<Map<String, String>> breadcrumbItems = new ArrayList<>();
                        breadcrumbItems.add(Map.of("name", "Home", "link", "index.html"));
                        breadcrumbItems.add(Map.of("name", coll, "link", ""));
                        data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(breadcrumbItems));

                        // Initial Breadcrumb & CollectionPage
                        List<Map<String, String>> collBcItems = new ArrayList<>();
                        collBcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
                        collBcItems.add(Map.of("name", coll, "link", BASE_URL + "/" + coll + ".html"));

                        String jsonLd = "<script type=\"application/ld+json\">\n" +
                                "{\n" +
                                "  \"@context\": \"https://schema.org\",\n" +
                                "  \"@graph\": [\n" +
                                "    " + SharedTemplates.getBreadcrumbJsonLd(collBcItems, BASE_URL + "/" + coll + ".html#breadcrumb") + ",\n" +
                                "    {\n" +
                                "      \"@type\": \"CollectionPage\",\n" +
                                "      \"@id\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                                "      \"name\": \"" + title.split("\\|")[0].trim() + "\",\n" +
                                "      \"description\": \"" + description + "\",\n" +
                                "      \"mainEntity\": {\n" +
                                "        \"@type\": \"ItemList\",\n" +
                                "        \"name\": \"" + title.split("\\|")[0].trim() + " List\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}\n" +
                                "</script>";
                        data.put("jsonLd", jsonLd);

                        Path jsonPath = Paths.get(pathSource, "json", coll.toLowerCase() + ".json");
                        String tableHtml = "";
                        if (Files.exists(jsonPath)) {
                            List<CardJson> cardList = CardDataLoader.loadCardsFromJson(jsonPath.toString());
                            tableHtml = generateHtmlTableFromJson(cardList);
                        }

                        Path sourcePath = Paths.get(pathSource, "other", coll + ".html");
                        if (Files.exists(sourcePath)) {
                            String rawContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
                            Document doc = Jsoup.parse(rawContent);

                            // Extrahiere FAQ aus dem Content für das JSON-LD (falls vorhanden)
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
                                                "      \"name\": \"" + title.split("\\|")[0].trim() + "\",\n" +
                                                "      \"description\": \"" + description + "\"\n" +
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
                                mainElement.select("table, .table-responsive").remove();
                                processedContent = mainElement.html() + "\n" + tableHtml;
                            } else {
                                processedContent = rawContent + "\n" + tableHtml;
                            }

                            data.put("pageContent", cleanOldPlaceholders(processedContent));
                        } else {
                            data.put("pageContent", tableHtml.isEmpty() ? "<p>No data found for this collection yet.</p>" : tableHtml);
                        }

                        processTemplate("generic-collection.ftlh", data, pathOutput + coll + ".html");
                    } catch (Exception e) { log.error("Fehler bei {}: {}", coll, e.getMessage()); }
                });
            }
        }
    }

    private static Map<String, String[]> loadCollectionMetas() {
        Map<String, String[]> map = new HashMap<>();
        try (java.io.InputStream is = FileGenerator.class.getResourceAsStream("/config/collections_config.json")) {
            if (is != null) {
                com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(is);
                for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry : root.properties()) {
                    String key = entry.getKey();
                    com.fasterxml.jackson.databind.JsonNode val = entry.getValue();
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
        htmlBuilder.append("<div class=\"table-responsive card-container-box\"><table><thead><tr>")
                .append("<th>Player</th><th>Team</th><th>Sport</th><th>Season</th>")
                .append("<th>Company</th><th>Brand</th><th>Theme</th><th>Variant</th>")
                .append("<th>Number</th><th>Serial</th><th>Print Run</th>")
                .append("<th>Rookie</th><th>Game Used</th><th>Autograph</th><th>Grade</th>")
                .append("</tr></thead><tbody>");

        for (CardJson c : cardList) {
            CardPageGenerator.CardData cardData = CardPageGenerator.computeCardData(c);
            String detailPath = cardData.fullRelativePath;
            String cleanPlayer = CardPageGenerator.cleanPlayerName(c.player);
            String playerTitle = "View " + escapeHtml(cleanPlayer) + " " + escapeHtml(c.season) + " " + escapeHtml(c.brand) + " #" + escapeHtml(c.cardNumber != null ? c.cardNumber : "") + " card detail page";
            String variantText = escapeHtml(c.variant != null ? c.variant : "Base");
            htmlBuilder.append("<tr id=\"").append(cardData.filenameBase).append("\">")
                    .append("<td data-label=\"Card\"><a href=\"").append(detailPath).append("\" class=\"table-button\" title=\"").append(playerTitle).append("\" itemprop=\"url\"><span itemprop=\"name\">").append(escapeHtml(cleanPlayer)).append("</span></a></td>")
                    .append("<td data-label=\"Team\">").append(escapeHtml(c.team)).append("</td>")
                    .append("<td data-label=\"Sport\">Basketball</td>")
                    .append("<td data-label=\"Season\">").append(escapeHtml(c.season)).append("</td>")
                    .append("<td data-label=\"Company\">").append(escapeHtml(c.company)).append("</td>")
                    .append("<td data-label=\"Brand\">").append(escapeHtml(c.brand)).append("</td>")
                    .append("<td data-label=\"Set / Theme\">").append(escapeHtml(c.theme)).append("</td>")
                    .append("<td data-label=\"Variant\">").append(variantText).append("</td>")
                    .append("<td data-label=\"Card #\">").append(escapeHtml(c.cardNumber)).append("</td>")
                    .append("<td data-label=\"Serial #\">").append((c.serialNumber != null && !c.serialNumber.trim().isEmpty() && !c.serialNumber.trim().equals("0")) ? escapeHtml(c.serialNumber) : "—").append("</td>")
                    .append("<td data-label=\"Print Run\">").append((c.printRun != null && c.printRun > 0) ? String.valueOf(c.printRun) : "—").append("</td>")
                    .append("<td data-label=\"Rookie\">").append(c.isRookie ? "Yes" : "No").append("</td>")
                    .append("<td data-label=\"Patch\">").append(c.isPatch ? "Yes" : "No").append("</td>")
                    .append("<td data-label=\"Autograph\">").append(c.isAutograph ? "Yes" : "No").append("</td>")
                    .append("<td data-label=\"Grade\">").append(escapeHtml(c.grade != null ? c.grade : "No")).append("</td>")
                    .append("</tr>");
        }
        htmlBuilder.append("</tbody></table></div>");
        return htmlBuilder.toString();
    }

    public static void copyResources() {
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

    // --- 3. STATISCHE SEITEN BAUEN (Index, Error) ---
    public static void buildStaticPages() {
        try {
            log.info("Baue index.html & error.html...");

            // Index (Navigations-Highlight für "index.html")
            Map<String, Object> indexData = createBaseData(
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
                    "", "index.html", DEFAULT_IMAGE, indexHeroPreload));

            List<Map<String, String>> indexBreadcrumbItems = new ArrayList<>();
            indexBreadcrumbItems.add(Map.of("name", "Home", "link", ""));
            indexData.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(indexBreadcrumbItems));

            // Complex JSON-LD for Index (WebSite, Person, Collection, Breadcrumbs)
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
            List<CardJson> allCardsForNewIn = getCachedCards();
            List<CardJson> rareNewInCards = allCardsForNewIn.stream()
                    .filter(c -> c.printRun != null && c.printRun > 1 && c.printRun < 10)
                    .limit(6)
                    .toList();

            List<Map<String, String>> newInLinks = new ArrayList<>();
            for (CardJson c : rareNewInCards) {
                CardPageGenerator.CardData cardData = CardPageGenerator.computeCardData(c);
                String cleanPlayer = CardPageGenerator.cleanPlayerName(c.player);
                String brandText = c.brand != null ? c.brand : "";
                String variantText = c.variant != null ? c.variant : "Base";
                String seasonText = c.season != null ? c.season : "";
                String cardTitle = cleanPlayer + " " + seasonText + " " + brandText + " " + variantText + " (/" + c.printRun + ")";
                newInLinks.add(Map.of(
                        "url", cardData.fullRelativePath,
                        "title", cardTitle.trim()
                ));
            }
            indexData.put("newInLinks", newInLinks);

            // SEO "Masterpieces" Linkjuice internal links (1/1 Non-Plate, Non-Proof cards) - Limited to 6
            List<CardJson> masterpieceCards = allCardsForNewIn.stream()
                    .filter(FileGenerator::isOneOfOneMasterpiece)
                    .limit(6)
                    .toList();

            List<Map<String, String>> masterpieceLinks = new ArrayList<>();
            for (CardJson c : masterpieceCards) {
                CardPageGenerator.CardData cardData = CardPageGenerator.computeCardData(c);
                String cleanPlayer = CardPageGenerator.cleanPlayerName(c.player);
                String brandText = c.brand != null ? c.brand : "";
                String variantText = c.variant != null ? c.variant : "1/1";
                String seasonText = c.season != null ? c.season : "";
                String cardTitle = cleanPlayer + " " + seasonText + " " + brandText + " " + variantText + " (1/1)";
                masterpieceLinks.add(Map.of(
                        "url", cardData.fullRelativePath,
                        "title", cardTitle.trim()
                ));
            }
            indexData.put("masterpieceLinks", masterpieceLinks);

            processTemplate("index.ftlh", indexData, pathOutput + "index.html");

            // Build Rainbow Tracker page
            buildRainbowsPage();

            // Build 3D Collector's 9-Pocket Binder page
            buildBinderPage();

            // Error 404 (Kein Navigations-Highlight)
            // Hier nutzen wir "/" als Root, damit Links auch bei tiefen Pfaden funktionieren (404-Handling im Server)
            Map<String, Object> errorData = createBaseData("Error Page| Maulmann Trading Cards", "The page you are looking for does not exist in the Maulmann Trading Cards collection.", "error.html", "", "/");

            List<Map<String, String>> errorBreadcrumbItems = new ArrayList<>();
            errorBreadcrumbItems.add(Map.of("name", "Home", "link", "index.html"));
            errorBreadcrumbItems.add(Map.of("name", "Error", "link", ""));
            errorData.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(errorBreadcrumbItems));

            processTemplate("error.ftlh", errorData, pathOutput + "error.html");

        } catch (Exception e) { log.error("Fehler bei statischen Seiten: {}", e.getMessage()); }
    }

    private static final java.util.regex.Pattern PATTERN_CLEAN_NUM = java.util.regex.Pattern.compile("(?i)\\s*(PMG|Refractor|Parallel|Base).*$");
    private static final java.util.regex.Pattern PATTERN_SCRIPT_LDJSON = java.util.regex.Pattern.compile("(?s)<script type=\"application/ld\\+json\">.*?</script>");

    public static String normalizeCardNumber(String num) {
        if (num == null || num.trim().isEmpty()) return "N/A";
        return PATTERN_CLEAN_NUM.matcher(num).replaceAll("").trim();
    }

    public static void buildRainbowsPage() {
        try {
            log.info("Baue rainbows.html (Strict Single-Card Parallel Rainbow Tracker)...");
            Map<String, Object> data = createBaseData(
                    "Parallel Rainbow Tracker & Set Checklists | Maulmann Private Vault",
                    "Track completion progress of Juwan Howard single-card parallel rainbows (cards with identical season, manufacturer, brand, theme, and card number).",
                    "rainbows.html", "rainbows", "");

            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", "index.html"));
            bcItems.add(Map.of("name", "Rainbow Tracker", "link", ""));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(bcItems));

            List<CardJson> allCards = getCachedCards();
            List<Map<String, Object>> rainbowSets = new ArrayList<>();

            // Group all cards strictly by: Season | Company | Brand | Theme | Normalized Card Number
            Map<String, List<CardJson>> strictRainbowGroups = new LinkedHashMap<>();

            for (CardJson c : allCards) {
                if (c.season == null || c.brand == null || c.cardNumber == null) continue;
                String comp = (c.company != null && !c.company.isEmpty()) ? c.company : c.brand;
                String thm = (c.theme != null && !c.theme.isEmpty()) ? c.theme : "Base Set";
                String normNum = normalizeCardNumber(c.cardNumber);

                String key = c.season + " | " + comp + " | " + c.brand + " | " + thm + " | #" + normNum;
                strictRainbowGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            }

            // 1. Explicit Featured Single-Card Rainbow Checklists (with > 3 cards in the list)
            List<Map<String, Object>> targetRainbows = List.of(
                    Map.of(
                            "title", "1997-98 Fleer Metal Universe Base Set #33 Rainbow",
                            "season", "1997-98", "company", "Fleer", "brand", "Fleer Metal Universe", "theme", "Base Set", "number", "33",
                            "variants", List.of(
                                    Map.of("variant", "Precious Metal Gems Red", "serial", "/90"),
                                    Map.of("variant", "Precious Metal Gems Green", "serial", "/10"),
                                    Map.of("variant", "Precious Metal Gems Gold", "serial", "1/1"),
                                    Map.of("variant", "Base Set", "serial", "Base")
                            )
                    ),
                    Map.of(
                            "title", "1996-97 Topps Finest Sterling #140 Rainbow",
                            "season", "1996-97", "company", "Topps", "brand", "Topps Finest", "theme", "Sterling", "number", "140",
                            "variants", List.of(
                                    Map.of("variant", "Refractor", "serial", "Parallel"),
                                    Map.of("variant", "Gold Refractor", "serial", "Parallel"),
                                    Map.of("variant", "Atomic Refractor", "serial", "Parallel"),
                                    Map.of("variant", "Base Sterling", "serial", "Base")
                            )
                    ),
                    Map.of(
                            "title", "1998-99 Upper Deck Black Diamond Base Set #89 Rainbow",
                            "season", "1998-99", "company", "Upper Deck", "brand", "UD Black Diamond", "theme", "Base Set", "number", "89",
                            "variants", List.of(
                                    Map.of("variant", "Single", "serial", "Base"),
                                    Map.of("variant", "Double", "serial", "/3000"),
                                    Map.of("variant", "Triple", "serial", "/1000"),
                                    Map.of("variant", "Quadruple", "serial", "/150")
                            )
                    ),
                    Map.of(
                            "title", "2018-19 Panini Contenders Optic Contenders Autographs #LC-JWH Rainbow",
                            "season", "2018-19", "company", "Panini", "brand", "Panini Contenders Optic", "theme", "Contenders Autographs", "number", "LC-JWH",
                            "variants", List.of(
                                    Map.of("variant", "Gold Vinyl", "serial", "1/1"),
                                    Map.of("variant", "Gold", "serial", "/10"),
                                    Map.of("variant", "Blue", "serial", "/99"),
                                    Map.of("variant", "Contenders Autographs", "serial", "Auto")
                            )
                    )
            );

            // Index cards by season and normalized card number for fast lookup in targetRainbows
            Map<String, List<CardJson>> cardsBySeasonAndNumber = new HashMap<>();
            for (CardJson c : allCards) {
                if (c.season != null && c.cardNumber != null) {
                    String lookupKey = (c.season + "|" + normalizeCardNumber(c.cardNumber)).toLowerCase();
                    cardsBySeasonAndNumber.computeIfAbsent(lookupKey, k -> new ArrayList<>()).add(c);
                }
            }

            for (Map<String, Object> target : targetRainbows) {
                String title = (String) target.get("title");
                String season = (String) target.get("season");
                String company = (String) target.get("company");
                String brand = (String) target.get("brand");
                String theme = (String) target.get("theme");
                String number = (String) target.get("number");
                @SuppressWarnings("unchecked")
                List<Map<String, String>> expectedVariants = (List<Map<String, String>>) target.get("variants");

                if (expectedVariants.size() <= 3) continue; // Rainbows must have more than 3 cards in the list

                Map<String, Object> setMap = new HashMap<>();
                setMap.put("name", title);
                setMap.put("season", season);
                setMap.put("company", company);
                setMap.put("brand", brand);
                setMap.put("theme", theme);
                setMap.put("number", number);

                List<Map<String, Object>> cardItems = new ArrayList<>();
                int acquiredCount = 0;
                List<CardJson> candidates = cardsBySeasonAndNumber.getOrDefault((season + "|" + number).toLowerCase(), Collections.emptyList());
                Set<String> matchedCardIds = new HashSet<>();

                for (Map<String, String> spec : expectedVariants) {
                    String reqVariant = spec.get("variant");
                    String reqSerial = spec.get("serial");

                    CardJson matched = null;
                    for (CardJson c : candidates) {
                        if (c.id != null && matchedCardIds.contains(c.id)) {
                            continue; // Card already matched to another variant slot
                        }
                        if (isVariantMatch(c.variant, reqVariant)) {
                            matched = c;
                            if (c.id != null) {
                                matchedCardIds.add(c.id);
                            }
                            break;
                        }
                    }

                    if (matched != null) {
                        acquiredCount++;
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("variant", reqVariant);
                        itemMap.put("serial", formatSerialAndPrintRun(matched.serialNumber, matched.printRun, reqSerial));
                        itemMap.put("acquired", true);
                        CardPageGenerator.CardData cd = CardPageGenerator.computeCardData(matched);
                        itemMap.put("url", cd.fullRelativePath.replace("../../", ""));
                        itemMap.put("title", matched.player + " " + matched.season + " " + matched.brand + " " + matched.variant + " #" + matched.cardNumber);

                        String rawImageBase = cd.filenameBase.contains("-") ? cd.filenameBase.substring(0, cd.filenameBase.lastIndexOf("-")) : cd.filenameBase;
                        String imageBaseName = CardPageGenerator.resolveDiskImageBase(cd.seasonFolder, rawImageBase, cd);
                        String imgBase = "images/" + cd.seasonFolder + "/" + imageBaseName + "-front";
                        itemMap.put("imgBase", imgBase);
                        String frontImg = imgBase + "-200w.avif";
                        itemMap.put("imgPath", frontImg);
                        boolean isLandscape = CardPageGenerator.isImageLandscape(cd.seasonFolder, imageBaseName);
                        itemMap.put("isLandscape", isLandscape);
                        itemMap.put("orientationClass", isLandscape ? "is-landscape" : "is-portrait");

                        cardItems.add(itemMap);
                    }
                }

                if (acquiredCount <= 1) continue; // Do not display sets with only 1 card

                int totalCount = expectedVariants.size();
                int percentage = (int) Math.round(((double) acquiredCount / totalCount) * 100);
                setMap.put("cards", cardItems);
                setMap.put("acquiredCount", acquiredCount);
                setMap.put("totalCount", totalCount);
                setMap.put("percentage", percentage);

                rainbowSets.add(setMap);
            }

            // 2. Process all dynamically discovered single-card groups with MORE THAN 3 distinct variants (> 3 cards)
            for (Map.Entry<String, List<CardJson>> entry : strictRainbowGroups.entrySet()) {
                List<CardJson> groupCards = entry.getValue();
                CardJson sample = groupCards.getFirst();
                String normNum = normalizeCardNumber(sample.cardNumber);

                Map<String, CardJson> distinctCardsMap = new LinkedHashMap<>();
                for (CardJson c : groupCards) {
                    String v = (c.variant != null && !c.variant.trim().isEmpty()) ? c.variant.trim() : "Base";
                    String serial = (c.serialNumber != null) ? c.serialNumber.trim() : "";
                    String cardKey = v.toLowerCase() + "||" + serial.toLowerCase();
                    distinctCardsMap.putIfAbsent(cardKey, c);
                }

                if (distinctCardsMap.size() > 3) {
                    boolean alreadyFeatured = rainbowSets.stream()
                            .anyMatch(s -> s.get("season").equals(sample.season)
                                    && s.get("brand").equals(sample.brand)
                                    && s.get("number").equals(normNum));

                    if (!alreadyFeatured) {
                        String comp = (sample.company != null && !sample.company.isEmpty()) ? sample.company : sample.brand;
                        String thm = (sample.theme != null && !sample.theme.isEmpty()) ? sample.theme : "Base Set";

                        Map<String, Object> setMap = new HashMap<>();
                        setMap.put("name", sample.season + " " + sample.brand + " " + thm + " #" + normNum + " Rainbow");
                        setMap.put("season", sample.season);
                        setMap.put("company", comp);
                        setMap.put("brand", sample.brand);
                        setMap.put("theme", thm);
                        setMap.put("number", normNum);

                        List<Map<String, Object>> cardItems = new ArrayList<>();
                        int acquiredCount = 0;

                        for (Map.Entry<String, CardJson> varEntry : distinctCardsMap.entrySet()) {
                            CardJson c = varEntry.getValue();
                            acquiredCount++;
                            Map<String, Object> itemMap = new HashMap<>();
                            itemMap.put("variant", c.variant != null ? c.variant : "Base");
                            itemMap.put("serial", formatSerialAndPrintRun(c.serialNumber, c.printRun, null));
                            itemMap.put("acquired", true);

                            CardPageGenerator.CardData cd = CardPageGenerator.computeCardData(c);
                            itemMap.put("url", cd.fullRelativePath.replace("../../", ""));
                            itemMap.put("title", c.player + " " + c.season + " " + c.brand + " " + c.variant + " #" + c.cardNumber);

                            String rawImageBase = cd.filenameBase.contains("-") ? cd.filenameBase.substring(0, cd.filenameBase.lastIndexOf("-")) : cd.filenameBase;
                            String imageBaseName = CardPageGenerator.resolveDiskImageBase(cd.seasonFolder, rawImageBase, cd);
                            String imgBase = "images/" + cd.seasonFolder + "/" + imageBaseName + "-front";
                            itemMap.put("imgBase", imgBase);
                            String frontImg = imgBase + "-200w.avif";
                            itemMap.put("imgPath", frontImg);
                            boolean isLandscape = CardPageGenerator.isImageLandscape(cd.seasonFolder, imageBaseName);
                            itemMap.put("isLandscape", isLandscape);
                            itemMap.put("orientationClass", isLandscape ? "is-landscape" : "is-portrait");

                            cardItems.add(itemMap);
                        }

                        int totalCount = distinctCardsMap.size();
                        setMap.put("cards", cardItems);
                        setMap.put("acquiredCount", acquiredCount);
                        setMap.put("totalCount", totalCount);
                        setMap.put("percentage", 100);

                        rainbowSets.add(setMap);
                    }
                }
            }

            // Sort rainbow sets descending by card count (acquiredCount descending, totalCount descending, then name ascending)
            rainbowSets.sort((a, b) -> {
                int acqA = (Integer) a.get("acquiredCount");
                int acqB = (Integer) b.get("acquiredCount");
                if (acqA != acqB) {
                    return Integer.compare(acqB, acqA);
                }
                int totA = (Integer) a.get("totalCount");
                int totB = (Integer) b.get("totalCount");
                if (totA != totB) {
                    return Integer.compare(totB, totA);
                }
                return ((String) a.get("name")).compareTo((String) b.get("name"));
            });

            // Compute Master Rainbow Statistics
            int totalRainbowSets = rainbowSets.size();
            int totalRainbowCards = 0;
            int totalRainbow1of1 = 0;

            for (Map<String, Object> set : rainbowSets) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cards = (List<Map<String, Object>>) set.get("cards");
                if (cards != null) {
                    totalRainbowCards += cards.size();
                    for (Map<String, Object> c : cards) {
                        String serial = (String) c.get("serial");
                        String variant = (String) c.get("variant");
                        if ((serial != null && (serial.contains("1/1") || serial.equals("1/1") || serial.equals("#1/1"))) 
                            || (variant != null && (variant.toLowerCase().contains("1 of 1") || variant.toLowerCase().contains("masterpiece")))) {
                            totalRainbow1of1++;
                        }
                    }
                }
            }

            data.put("totalRainbowSets", totalRainbowSets);
            data.put("totalRainbowCards", totalRainbowCards);
            data.put("totalRainbow1of1", totalRainbow1of1);

            data.put("rainbowSets", rainbowSets);
            data.put("jsonLd", CardSchemaGenerator.generateRainbowJsonLd(rainbowSets));
            processTemplate("rainbows.ftlh", data, pathOutput + "rainbows.html");

        } catch (Exception e) {
            log.error("Fehler bei Rainbows Page: {}", e.getMessage(), e);
        }
    }

    public static void buildBinderPage() {
        try {
            log.info("Baue binder.html (3D Collector's 9-Pocket Binder View)...");
            Map<String, Object> data = createBaseData(
                    "3D Collector's 9-Pocket Binder | Maulmann Private Vault",
                    "Flip through the complete Juwan Howard card collection in an interactive 3D 9-Pocket Ultra-PRO style digital binder.",
                    "binder.html", "binder", "");

            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", "index.html"));
            bcItems.add(Map.of("name", "3D Binder", "link", ""));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(bcItems));

            List<CardJson> allCards = getCachedCards();
            
            // Transform cards into structured binder slot items
            List<Map<String, Object>> binderCardItems = new ArrayList<>();
            for (CardJson c : allCards) {
                CardPageGenerator.CardData cd = CardPageGenerator.computeCardData(c);
                Map<String, Object> item = new HashMap<>();
                item.put("id", c.id != null ? c.id : cd.filenameBase);
                item.put("player", c.player != null ? c.player : "");
                item.put("season", c.season != null ? c.season : "");
                item.put("brand", c.brand != null ? c.brand : "");
                item.put("variant", c.variant != null ? c.variant : "Base");
                item.put("cardNumber", c.cardNumber != null ? c.cardNumber : "");
                item.put("serial", formatSerialAndPrintRun(c.serialNumber, c.printRun, ""));
                item.put("url", cd.fullRelativePath.replace("../../", ""));
                item.put("title", c.player + " " + c.season + " " + c.brand + " " + (c.variant != null ? c.variant : "") + " #" + c.cardNumber);

                String rawImageBase = cd.filenameBase.contains("-") ? cd.filenameBase.substring(0, cd.filenameBase.lastIndexOf("-")) : cd.filenameBase;
                String imageBaseName = CardPageGenerator.resolveDiskImageBase(cd.seasonFolder, rawImageBase, cd);
                String frontBase = "images/" + cd.seasonFolder + "/" + imageBaseName + "-front";
                String backBase = "images/" + cd.seasonFolder + "/" + imageBaseName + "-back";

                item.put("frontImg", frontBase + "-400w.avif");
                item.put("frontImgFallback", frontBase + "-400w.webp");
                item.put("backImg", backBase + "-400w.avif");
                item.put("backImgFallback", backBase + "-400w.webp");

                boolean isLandscape = CardPageGenerator.isImageLandscape(cd.seasonFolder, imageBaseName);
                item.put("isLandscape", isLandscape);
                item.put("orientationClass", isLandscape ? "is-landscape" : "is-portrait");
                
                // Attributes
                boolean is1of1 = (c.printRun != null && c.printRun == 1) || (c.serialNumber != null && c.serialNumber.trim().equals("1/1"));
                boolean isAuto = c.isAutograph;
                boolean isPatch = c.isPatch;
                boolean isRookie = c.isRookie;
                item.put("is1of1", is1of1);
                item.put("isAuto", isAuto);
                item.put("isPatch", isPatch);
                item.put("isRookie", isRookie);

                binderCardItems.add(item);
            }

            // Chunk cards into 9-card pages (3x3 grid)
            List<List<Map<String, Object>>> binderPages = new ArrayList<>();
            final int POCKETS_PER_PAGE = 9;
            for (int i = 0; i < binderCardItems.size(); i += POCKETS_PER_PAGE) {
                int end = Math.min(i + POCKETS_PER_PAGE, binderCardItems.size());
                List<Map<String, Object>> pageSlots = new ArrayList<>(binderCardItems.subList(i, end));
                // Pad to 9 slots if last page has fewer cards
                while (pageSlots.size() < POCKETS_PER_PAGE) {
                    Map<String, Object> emptySlot = new HashMap<>();
                    emptySlot.put("isEmpty", true);
                    pageSlots.add(emptySlot);
                }
                binderPages.add(pageSlots);
            }

            data.put("binderPages", binderPages);
            data.put("totalCards", binderCardItems.size());
            data.put("totalPages", binderPages.size());

            processTemplate("binder.ftlh", data, pathOutput + "binder.html");
        } catch (Exception e) {
            log.error("Fehler bei Binder Page: {}", e.getMessage(), e);
        }
    }

    // --- HILFSMETHODEN ---
    public static String formatSerialAndPrintRun(String serialNum, Integer printRun, String fallback) {
        boolean hasSerial = serialNum != null && !serialNum.trim().isEmpty() && !serialNum.equals("0");
        boolean hasPrintRun = printRun != null && printRun > 0;

        if (hasSerial && hasPrintRun) {
            return serialNum.contains("/") ? serialNum : serialNum + "/" + printRun;
        } else if (hasSerial) {
            return serialNum.startsWith("#") ? serialNum : "#" + serialNum;
        } else if (hasPrintRun) {
            return "/" + printRun;
        } else if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return "Parallel";
    }

    private static boolean isVariantMatch(String cardVariant, String specVariant) {
        if (cardVariant == null || specVariant == null) return false;
        String cv = cardVariant.trim().toLowerCase();
        String sv = specVariant.trim().toLowerCase();
        if (cv.equals(sv)) return true;

        // Base aliases (symmetrical & clean)
        if (isBaseVariant(cv) && isBaseVariant(sv)) {
            return true;
        }

        // Black Diamond aliases (symmetrical & clean)
        if (isDiamondGroup(cv, sv, "single", "single diamond", "diamond")) return true;
        if (isDiamondGroup(cv, sv, "double", "double diamond")) return true;
        if (isDiamondGroup(cv, sv, "triple", "triple diamond")) return true;
        return isDiamondGroup(cv, sv, "quadruple", "quadruple diamond");
    }

    private static boolean isBaseVariant(String v) {
        return v.equals("base") || v.equals("base set") || v.startsWith("base ");
    }

    private static boolean isDiamondGroup(String cv, String sv, String... aliases) {
        boolean cvMatch = false;
        boolean svMatch = false;
        for (String alias : aliases) {
            if (cv.equals(alias)) cvMatch = true;
            if (sv.equals(alias)) svMatch = true;
        }
        return cvMatch && svMatch;
    }

    private static Map<String, Object> createBaseData(String title, String subTitle, String filename, String navTargetUrl, String root) {
        Map<String, Object> data = new HashMap<>();

        String headHtml = SharedTemplates.getHead(title, subTitle, root, filename, root + DEFAULT_IMAGE);

        String topnav = SharedTemplates.getTopNav(root, navTargetUrl.replace(".html", "").toLowerCase());

        String footerHtml = SharedTemplates.getFooter(root);

        data.put("headHtml", headHtml);
        data.put("topNavHtml", topnav);
        data.put("footerHtml", footerHtml);
        data.put("pageTitle", title);
        data.put("subTitle", subTitle);
        data.put("root", root);

        return data;
    }

    private static String cleanOldPlaceholders(String content) {
        // Entfernt auch vorhandene ld+json Blöcke aus dem Content, da diese nun im Head via FreeMarker landen
        String cleaned = PATTERN_SCRIPT_LDJSON.matcher(content).replaceAll("");

        return cleaned.replace("{{HEAD}}", "")
                .replace("{{TOP_NAV}}", "")
                .replace("{{FOOTER_NAV}}", "")
                .replace("{{FOOTER}}", "")
                .replace("{{TIME}}", "");
    }

    private static void processTemplate(String templateName, Map<String, Object> data, String outputPath) throws Exception {
        Path outPath = Paths.get(outputPath);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }

        Template template = FM_CONFIG.get().getTemplate(templateName);

        StringWriter stringWriter = new StringWriter();
        template.process(data, stringWriter);
        String finalHtml = stringWriter.toString();

        if (timestampTracker != null && finalHtml.contains("[[STABLE_TIME]]")) {
            String relativeOutputPath = Paths.get(pathOutput).toUri().relativize(outPath.toUri()).getPath();
            String stableTime = timestampTracker.getStableTimestamp(relativeOutputPath, finalHtml);
            finalHtml = finalHtml.replace("[[STABLE_TIME]]", stableTime);
        }

        if (finalHtml.contains("{{CONSENT_BANNER}}")) {
            String root = (String) data.getOrDefault("root", "");
            finalHtml = finalHtml.replace("{{CONSENT_BANNER}}", SharedTemplates.getConsentBanner(root));
        }

        Files.writeString(outPath, finalHtml, StandardCharsets.UTF_8);
    }

    public static List<CardJson> loadCardsFromJson() {
        return CardDataLoader.loadCardsFromJson(pathSource + "json/cards.json");
    }

    public static List<CardJson> filterDuplicateJsonCards(List<CardJson> rawCards) {
        List<CardJson> filtered = new ArrayList<>();
        Set<String> seenFingerprints = new HashSet<>();

        for (CardJson c : rawCards) {
            String season = c.season != null ? c.season : "";
            String company = c.company != null ? c.company : "";
            String brand = c.brand != null ? c.brand : "";
            String theme = c.theme != null ? c.theme : "";
            String variant = c.variant != null ? c.variant : "";
            String number = c.cardNumber != null ? c.cardNumber : "";
            String gradingCo = c.gradingCompany != null ? c.gradingCompany : "";
            String grade = c.grade != null ? c.grade : "";

            String fingerprint = (season + "|" + company + "|" + brand + "|" + theme + "|" +
                    variant + "|" + number + "|" + gradingCo + "|" + grade).toLowerCase();

            String serial = c.serialNumber;
            boolean hasSerial = serial != null && !serial.trim().isEmpty() && !serial.trim().equals("0");

            if (seenFingerprints.contains(fingerprint)) {
                if (!hasSerial) {
                    continue;
                } else {
                    filtered.add(c);
                }
            } else {
                seenFingerprints.add(fingerprint);
                filtered.add(c);
            }
        }
        return filtered;
    }

    public static Map<String, Object> computeCollectionStats(List<CardJson> jsonCards) {
        Map<String, Object> stats = new HashMap<>();
        if (jsonCards == null || jsonCards.isEmpty()) {
            stats.put("totalCards", "0");
            stats.put("count1of1", 0);
            stats.put("pct1of1", 0);
            stats.put("countUltraSp", 0);
            stats.put("pctUltraSp", 0);
            stats.put("countSerialized", 0);
            stats.put("pctSerialized", 0);
            stats.put("countAutographs", 0);
            stats.put("pctAutographs", 0);
            stats.put("countPatches", 0);
            stats.put("pctPatches", 0);
            stats.put("countRookies", 0);
            stats.put("pctRookies", 0);
            stats.put("countGradedTotal", 0);
            stats.put("pctGradedTotal", 0);
            stats.put("countGemMint", 0);
            stats.put("pctGemMint", 0);
            return stats;
        }

        int count1of1 = 0;
        int countUltraSp = 0;
        int countSerialized = 0;
        int countAutographs = 0;
        int countPatches = 0;
        int countRookies = 0;
        int countGradedTotal = 0;
        int countGemMint = 0;

        for (CardJson c : jsonCards) {
            Integer pr = c.printRun;
            String sn = c.serialNumber != null ? c.serialNumber.trim() : "";
            String v = c.variant != null ? c.variant.trim() : "";
            String t = c.theme != null ? c.theme.trim() : "";

            boolean isOneOfOne = (pr != null && pr == 1) ||
                    "1/1".equalsIgnoreCase(sn) || "1/1".equalsIgnoreCase(v) || "1/1".equalsIgnoreCase(t) ||
                    "1 of 1".equalsIgnoreCase(sn) || "1 of 1".equalsIgnoreCase(v) || "1 of 1".equalsIgnoreCase(t);
            if (isOneOfOne) count1of1++;

            if (pr != null && pr > 0 && pr <= 10) countUltraSp++;

            boolean isSerialized100 = (pr != null && pr > 0 && pr <= 100) ||
                    ((pr == null || pr == 0) && !sn.isEmpty() && !sn.equals("0"));
            if (isSerialized100) countSerialized++;

            if (c.isAutograph) countAutographs++;
            if (c.isPatch) countPatches++;
            if (c.isRookie) countRookies++;

            String gCo = c.gradingCompany;
            String g = c.grade;
            boolean isGraded = (gCo != null && !gCo.trim().isEmpty() && !gCo.trim().equalsIgnoreCase("No")) ||
                    (g != null && !g.trim().isEmpty() && !g.trim().equalsIgnoreCase("No") && !g.trim().equals("-"));
            if (isGraded) {
                countGradedTotal++;
                if (g != null && (g.contains("10") || g.contains("9.5"))) {
                    countGemMint++;
                }
            }
        }

        int totalCardCount = jsonCards.size();
        stats.put("totalCards", String.format(Locale.US, "%,d", totalCardCount));
        stats.put("rawTotalCards", totalCardCount);

        stats.put("count1of1", count1of1);
        stats.put("pct1of1", count1of1 > 0 ? Math.max(5, (int) Math.round((count1of1 * 100.0) / totalCardCount)) : 0);

        stats.put("countUltraSp", countUltraSp);
        stats.put("pctUltraSp", countUltraSp > 0 ? Math.max(5, (int) Math.round((countUltraSp * 100.0) / totalCardCount)) : 0);

        stats.put("countSerialized", countSerialized);
        stats.put("pctSerialized", countSerialized > 0 ? Math.max(5, (int) Math.round((countSerialized * 100.0) / totalCardCount)) : 0);

        stats.put("countAutographs", countAutographs);
        stats.put("pctAutographs", countAutographs > 0 ? Math.max(5, (int) Math.round((countAutographs * 100.0) / totalCardCount)) : 0);

        stats.put("countPatches", countPatches);
        stats.put("pctPatches", countPatches > 0 ? Math.max(5, (int) Math.round((countPatches * 100.0) / totalCardCount)) : 0);

        stats.put("countRookies", countRookies);
        stats.put("pctRookies", countRookies > 0 ? Math.max(5, (int) Math.round((countRookies * 100.0) / totalCardCount)) : 0);

        stats.put("countGradedTotal", countGradedTotal);
        stats.put("pctGradedTotal", countGradedTotal > 0 ? Math.max(5, (int) Math.round((countGradedTotal * 100.0) / totalCardCount)) : 0);

        stats.put("countGemMint", countGemMint);
        stats.put("pctGemMint", countGradedTotal > 0 ? Math.max(5, (int) Math.round((countGemMint * 100.0) / countGradedTotal)) : 0);

        return stats;
    }

    public static boolean isOneOfOneMasterpiece(CardJson c) {
        if (c == null) return false;
        String var = c.variant != null ? c.variant.toLowerCase() : "";
        String theme = c.theme != null ? c.theme.toLowerCase() : "";
        String brand = c.brand != null ? c.brand.toLowerCase() : "";
        String sn = c.serialNumber != null ? c.serialNumber.trim() : "";

        // Exclude Printing Plates & Proofs
        if (var.contains("plate") || theme.contains("plate") || brand.contains("plate") ||
            var.contains("proof") || theme.contains("proof") || brand.contains("proof")) {
            return false;
        }

        if (c.printRun != null && c.printRun == 1) {
            return true;
        }
        if (sn.equals("1/1") || sn.equalsIgnoreCase("1 of 1")) {
            return true;
        }
        return var.contains("1/1") || var.contains("1 of 1");
    }

    private static String escapeHtml(String input) {
        return CardUtils.escapeHtml(input);
    }

    public static void main(String[] args) {
        copyResources();
        buildCollectionOverview();
        buildOtherCollections();
        buildStaticPages();
        log.info("Alle statischen Seiten erfolgreich generiert!");
    }
}