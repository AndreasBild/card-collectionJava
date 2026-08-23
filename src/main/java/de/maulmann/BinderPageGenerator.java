package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialized generator for the 3D Collector's 9-Pocket digital binder page.
 */
public class BinderPageGenerator {

    private static final Logger log = LoggerFactory.getLogger(BinderPageGenerator.class);
    private static final String BASE_URL = CardUtils.BASE_URL;

    public static void buildBinderPage(List<CardJson> allCards, TimestampTracker timestampTracker, String pathOutput) {
        try {
            log.info("Baue binder.html (3D Collector's 9-Pocket Binder View)...");
            Map<String, Object> data = SharedTemplates.createBaseData(
                    "3D Collector's 9-Pocket Binder | Juwan Howard Private Vault",
                    "Flip through the complete Juwan Howard card collection in an interactive 3D 9-Pocket Ultra-PRO style digital binder.",
                    "binder.html", "binder", "");

            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", "index.html"));
            bcItems.add(Map.of("name", "3D Binder", "link", ""));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(bcItems));

            // Transform cards into structured binder slot items
            List<Map<String, Object>> binderCardItems = new ArrayList<>();
            for (CardJson c : allCards) {
                CardData cd = CardPageGenerator.computeCardData(c);
                Map<String, Object> item = new HashMap<>();
                item.put("id", c.id() != null ? c.id() : cd.filenameBase);
                item.put("player", c.player() != null ? c.player() : "");
                item.put("season", c.season() != null ? c.season() : "");
                item.put("brand", c.brand() != null ? c.brand() : "");
                item.put("variant", c.variant() != null ? c.variant() : "Base");
                item.put("cardNumber", c.cardNumber() != null ? c.cardNumber() : "");
                item.put("serial", CardStatsService.formatSerialAndPrintRun(c.serialNumber(), c.printRun(), ""));
                item.put("url", cd.fullRelativePath.replace("../../", ""));
                item.put("title", c.player() + " " + c.season() + " " + c.brand() + " " + (c.variant() != null ? c.variant() : "") + " #" + c.cardNumber());

                String rawImageBase = cd.filenameBase.contains("-") ? cd.filenameBase.substring(0, cd.filenameBase.lastIndexOf("-")) : cd.filenameBase;
                String imageBaseName = CardPageGenerator.resolveDiskImageBase(cd.seasonFolder, rawImageBase, cd);
                String frontBase = "images/" + cd.seasonFolder + "/" + imageBaseName + "-front";
                String backBase = "images/" + cd.seasonFolder + "/" + imageBaseName + "-back";

                item.put("frontImg", frontBase + "-400w.avif");
                item.put("frontImgFallback", frontBase + "-400w.avif");
                item.put("backImg", backBase + "-400w.avif");
                item.put("backImgFallback", backBase + "-400w.avif");

                boolean isFrontLandscape = CardPageGenerator.isImageLandscape(cd.seasonFolder, imageBaseName, "front");
                boolean isBackLandscape = CardPageGenerator.isImageLandscape(cd.seasonFolder, imageBaseName, "back");
                item.put("isFrontLandscape", isFrontLandscape);
                item.put("isBackLandscape", isBackLandscape);
                item.put("frontOrientationClass", isFrontLandscape ? "is-landscape" : "is-portrait");
                item.put("backOrientationClass", isBackLandscape ? "is-landscape" : "is-portrait");

                // Attributes
                boolean is1of1 = (c.printRun() != null && c.printRun() == 1) || (c.serialNumber() != null && c.serialNumber().trim().equals("1/1"));
                boolean isAuto = c.isAutograph();
                boolean isPatch = c.isPatch();
                boolean isRookie = c.isRookie();
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

            List<Map<String, String>> schemaBcItems = new ArrayList<>();
            schemaBcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
            schemaBcItems.add(Map.of("name", "3D Binder", "link", BASE_URL + "/binder.html"));

            String binderJsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    " + SharedTemplates.getBreadcrumbJsonLd(schemaBcItems, BASE_URL + "/binder.html#breadcrumb") + ",\n" +
                    "    {\n" +
                    "      \"@type\": \"CollectionPage\",\n" +
                    "      \"@id\": \"" + BASE_URL + "/binder.html\",\n" +
                    "      \"name\": \"Juwan Howard 3D 9-Pocket Binder View\",\n" +
                    "      \"description\": \"Flip through the complete Juwan Howard card collection in an interactive 3D 9-Pocket Ultra-PRO style digital binder.\",\n" +
                    "      \"publisher\": { \"@type\": \"Person\", \"name\": \"Mauli Maulmann\" },\n" +
                    "      \"isPartOf\": { \"@type\": \"WebSite\", \"name\": \"Maulmann Trading Cards\", \"url\": \"" + BASE_URL + "\" }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "</script>";
            data.put("jsonLd", binderJsonLd);

            FileGenerator.processTemplate("binder.ftlh", data, pathOutput + "binder.html");
        } catch (Exception e) {
            log.error("Fehler bei Binder Page: {}", e.getMessage(), e);
        }
    }
}
