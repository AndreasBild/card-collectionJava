package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Master orchestrator for generating static HTML collections and overview pages.
 */
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
     */
    public static synchronized List<CardJson> getCachedCards() {
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
            Map<String, Object> data = SharedTemplates.createBaseData("Juwan Howard Private Collection | Juwan Howard Super Collector", "Explore the Juwan Howard Masterpiece Collection. A massive private collection featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.", "Juwan-Howard-Collection.html", "collection", "");

            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
            bcItems.add(Map.of("name", "Collection", "link", BASE_URL + "/Juwan-Howard-Collection.html"));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(bcItems));

            List<CardJson> jsonCards = getCachedCards();
            List<CardJson> masterpieceCards = jsonCards.stream()
                    .filter(CardStatsService::isOneOfOneMasterpiece)
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
                CardData cd = CardPageGenerator.computeCardData(c);
                String cardTitle = CardData.cleanPlayerName(c.player()) + " " + (c.season() != null ? c.season() : "") + " " + (c.brand() != null ? c.brand() : "") + " " + (c.variant() != null ? c.variant() : "") + " #" + (c.cardNumber() != null ? c.cardNumber() : "");
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
                    "      \"mainEntity\": " + itemListSb + "\n" +
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
                        .map(c -> c.season() != null ? c.season() : "Unknown")
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
                    String sk = c.season() != null ? c.season() : "Unknown";
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
                        CardData cardData = CardPageGenerator.computeCardData(c);
                        String detailPath = cardData.fullRelativePath;
                        String cleanPlayer = CardData.cleanPlayerName(c.player());
                        String playerTitle = "View " + CardUtils.escapeHtml(cleanPlayer) + " " + CardUtils.escapeHtml(c.season()) + " " + CardUtils.escapeHtml(c.brand()) + " #" + CardUtils.escapeHtml(c.cardNumber() != null ? c.cardNumber() : "") + " card detail page";

                        htmlBuilder.append("<tr id=\"").append(cardData.filenameBase).append("\" data-card-id=\"").append(cardData.stableId).append("\">")
                                .append("<td data-label=\"Card\"><a href=\"").append(detailPath).append("\" class=\"table-button\" title=\"").append(playerTitle).append("\" itemprop=\"url\"><span itemprop=\"name\">").append(CardUtils.escapeHtml(cleanPlayer)).append("</span></a></td>")
                                .append("<td data-label=\"Team\">").append(CardUtils.escapeHtml(c.team())).append("</td>")
                                .append("<td data-label=\"Sport\">Basketball</td>")
                                .append("<td data-label=\"Season\">").append(CardUtils.escapeHtml(c.season())).append("</td>")
                                .append("<td data-label=\"Company\">").append(CardUtils.escapeHtml(c.company())).append("</td>")
                                .append("<td data-label=\"Brand\">").append(CardUtils.escapeHtml(c.brand())).append("</td>")
                                .append("<td data-label=\"Set / Theme\">").append(CardUtils.escapeHtml(c.theme())).append("</td>")
                                .append("<td data-label=\"Variant\">").append(CardUtils.escapeHtml(c.variant())).append("</td>")
                                .append("<td data-label=\"Card #\">").append(CardUtils.escapeHtml(c.cardNumber())).append("</td>")
                                .append("<td data-label=\"Serial #\">").append((c.serialNumber() != null && !c.serialNumber().trim().isEmpty() && !c.serialNumber().trim().equals("0")) ? CardUtils.escapeHtml(c.serialNumber()) : "—").append("</td>")
                                .append("<td data-label=\"Print Run\">").append((c.printRun() != null && c.printRun() > 0) ? String.valueOf(c.printRun()) : "—").append("</td>")
                                .append("<td data-label=\"Rookie\">").append(c.isRookie() ? "Yes" : "No").append("</td>")
                                .append("<td data-label=\"Patch\">").append(c.isPatch() ? "Yes" : "No").append("</td>")
                                .append("<td data-label=\"Autograph\">").append(c.isAutograph() ? "Yes" : "No").append("</td>")
                                .append("<td data-label=\"Grade\">").append(CardUtils.escapeHtml(c.grade() != null ? c.grade() : "No")).append("</td>")
                                .append("</tr>");
                    }
                    htmlBuilder.append("</table>");

                    int seasonCardCount = seasonCardList.size();
                    cumulativeTotal += seasonCardCount;

                    seasonMap.put("count", String.valueOf(seasonCardCount));
                    seasonMap.put("seasonCount", String.valueOf(seasonCardCount));
                    seasonMap.put("cumulativeTotal", String.valueOf(cumulativeTotal));
                    seasonMap.put("content", htmlBuilder.toString());
                    seasonMap.put("tableHtml", htmlBuilder.toString());
                    seasons.add(seasonMap);
                }
            }

            data.put("seasons", seasons);
            data.put("totalCards", String.valueOf(cumulativeTotal));

            processTemplate("collection-overview.ftlh", data, pathOutput + "Juwan-Howard-Collection.html");
        } catch (Exception e) {
            log.error("Fehler bei Haupt-Collection: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // --- 2. NEBEN-SAMMLUNGEN BAUEN ---
    public static void buildOtherCollections() {
        StaticPageGenerator.buildOtherCollections(pathSource, pathOutput, timestampTracker);
    }

    // --- 3. STATISCHE SEITEN BAUEN ---
    public static void buildStaticPages() {
        StaticPageGenerator.buildStaticPages(getCachedCards(), timestampTracker, pathOutput);
    }

    // --- 4. RAINBOWS & BINDER DELEGATION ---
    public static void buildRainbowsPage() {
        RainbowPageGenerator.buildRainbowsPage(getCachedCards(), timestampTracker, pathOutput);
    }

    public static void buildBinderPage() {
        BinderPageGenerator.buildBinderPage(getCachedCards(), timestampTracker, pathOutput);
    }

    public static void copyResources() {
        StaticPageGenerator.copyResources(pathOutput);
    }

    public static String generateHtmlTableFromJson(List<CardJson> cardList) {
        return StaticPageGenerator.generateHtmlTableFromJson(cardList);
    }

    public static String formatSerialAndPrintRun(String serialNum, Integer printRun, String fallback) {
        return CardStatsService.formatSerialAndPrintRun(serialNum, printRun, fallback);
    }

    public static List<CardJson> loadCardsFromJson() {
        return CardDataLoader.loadCardsFromJson(pathSource + "json/cards.json");
    }

    public static List<CardJson> filterDuplicateJsonCards(List<CardJson> rawCards) {
        return CardStatsService.filterDuplicateJsonCards(rawCards);
    }

    public static Map<String, Object> computeCollectionStats(List<CardJson> jsonCards) {
        return CardStatsService.computeCollectionStats(jsonCards);
    }

    public static boolean isOneOfOneMasterpiece(CardJson c) {
        return CardStatsService.isOneOfOneMasterpiece(c);
    }

    public static void processTemplate(String templateName, Map<String, Object> data, String outputPath) throws Exception {
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

    public static void main(String[] args) {
        log.info("Starting FileGenerator build...");
        generateLatestMetadata(getCachedCards().size());
        copyResources();
        buildCollectionOverview();
        buildOtherCollections();
        buildStaticPages();
        log.info("FileGenerator finished successfully!");
    }
}