package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Specialized generator for the Parallel Rainbow Tracker and Set Checklists.
 */
public class RainbowPageGenerator {

    private static final Logger log = LoggerFactory.getLogger(RainbowPageGenerator.class);
    private static final Pattern PATTERN_CLEAN_NUM = Pattern.compile("(?i)\\s*(PMG|Refractor|Parallel|Base).*$");

    public static String normalizeCardNumber(String num) {
        if (num == null || num.trim().isEmpty()) return "N/A";
        return PATTERN_CLEAN_NUM.matcher(num).replaceAll("").trim();
    }

    public static void buildRainbowsPage(List<CardJson> allCards, TimestampTracker timestampTracker, String pathOutput) {
        try {
            log.info("Baue rainbows.html (Strict Single-Card Parallel Rainbow Tracker)...");
            Map<String, Object> data = SharedTemplates.createBaseData(
                    "Parallel Rainbow Tracker & Set Checklists | Juwan Howard Private Vault",
                    "Track completion progress of Juwan Howard single-card parallel rainbows (cards with identical season, manufacturer, brand, theme, and card number).",
                    "rainbows.html", "rainbows", "");

            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", "index.html"));
            bcItems.add(Map.of("name", "Rainbow Tracker", "link", ""));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(bcItems));

            List<Map<String, Object>> rainbowSets = new ArrayList<>();

            // Group all cards strictly by: Season | Company | Brand | Theme | Normalized Card Number
            Map<String, List<CardJson>> strictRainbowGroups = new LinkedHashMap<>();

            for (CardJson c : allCards) {
                if (c.season() == null || c.brand() == null || c.cardNumber() == null) continue;
                String comp = (c.company() != null && !c.company().isEmpty()) ? c.company() : c.brand();
                String thm = (c.theme() != null && !c.theme().isEmpty()) ? c.theme() : "Base Set";
                String normNum = normalizeCardNumber(c.cardNumber());

                String key = c.season() + " | " + comp + " | " + c.brand() + " | " + thm + " | #" + normNum;
                strictRainbowGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            }

            // 1. Explicit Featured Single-Card Rainbow Checklists (> 3 cards)
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
                if (c.season() != null && c.cardNumber() != null) {
                    String lookupKey = (c.season() + "|" + normalizeCardNumber(c.cardNumber())).toLowerCase();
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

                if (expectedVariants.size() <= 3) continue;

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
                        if (c.id() != null && matchedCardIds.contains(c.id())) {
                            continue;
                        }
                        if (isVariantMatch(c.variant(), reqVariant)) {
                            matched = c;
                            if (c.id() != null) {
                                matchedCardIds.add(c.id());
                            }
                            break;
                        }
                    }

                    if (matched != null) {
                        acquiredCount++;
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("variant", reqVariant);
                        itemMap.put("serial", CardStatsService.formatSerialAndPrintRun(matched.serialNumber(), matched.printRun(), reqSerial));
                        itemMap.put("acquired", true);
                        CardData cd = CardPageGenerator.computeCardData(matched);
                        itemMap.put("url", cd.fullRelativePath.replace("../../", ""));
                        itemMap.put("title", matched.player() + " " + matched.season() + " " + matched.brand() + " " + matched.variant() + " #" + matched.cardNumber());

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

                if (acquiredCount <= 1) continue;

                int totalCount = expectedVariants.size();
                int percentage = (int) Math.round(((double) acquiredCount / totalCount) * 100);
                setMap.put("cards", cardItems);
                setMap.put("acquiredCount", acquiredCount);
                setMap.put("totalCount", totalCount);
                setMap.put("percentage", percentage);

                rainbowSets.add(setMap);
            }

            // 2. Process all dynamically discovered single-card groups with > 3 distinct variants
            for (Map.Entry<String, List<CardJson>> entry : strictRainbowGroups.entrySet()) {
                List<CardJson> groupCards = entry.getValue();
                CardJson sample = groupCards.getFirst();
                String normNum = normalizeCardNumber(sample.cardNumber());

                Map<String, CardJson> distinctCardsMap = new LinkedHashMap<>();
                for (CardJson c : groupCards) {
                    String v = (c.variant() != null && !c.variant().trim().isEmpty()) ? c.variant().trim() : "Base";
                    String serial = (c.serialNumber() != null) ? c.serialNumber().trim() : "";
                    String cardKey = v.toLowerCase() + "||" + serial.toLowerCase();
                    distinctCardsMap.putIfAbsent(cardKey, c);
                }

                if (distinctCardsMap.size() > 3) {
                    boolean alreadyFeatured = rainbowSets.stream()
                            .anyMatch(s -> s.get("season").equals(sample.season())
                                    && s.get("brand").equals(sample.brand())
                                    && s.get("number").equals(normNum));

                    if (!alreadyFeatured) {
                        String comp = (sample.company() != null && !sample.company().isEmpty()) ? sample.company() : sample.brand();
                        String thm = (sample.theme() != null && !sample.theme().isEmpty()) ? sample.theme() : "Base Set";

                        Map<String, Object> setMap = new HashMap<>();
                        setMap.put("name", sample.season() + " " + sample.brand() + " " + thm + " #" + normNum + " Rainbow");
                        setMap.put("season", sample.season());
                        setMap.put("company", comp);
                        setMap.put("brand", sample.brand());
                        setMap.put("theme", thm);
                        setMap.put("number", normNum);

                        List<Map<String, Object>> cardItems = new ArrayList<>();
                        int acquiredCount = 0;

                        for (Map.Entry<String, CardJson> varEntry : distinctCardsMap.entrySet()) {
                            CardJson c = varEntry.getValue();
                            acquiredCount++;
                            Map<String, Object> itemMap = new HashMap<>();
                            itemMap.put("variant", c.variant() != null ? c.variant() : "Base");
                            itemMap.put("serial", CardStatsService.formatSerialAndPrintRun(c.serialNumber(), c.printRun(), null));
                            itemMap.put("acquired", true);

                            CardData cd = CardPageGenerator.computeCardData(c);
                            itemMap.put("url", cd.fullRelativePath.replace("../../", ""));
                            itemMap.put("title", c.player() + " " + c.season() + " " + c.brand() + " " + c.variant() + " #" + c.cardNumber());

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

            // Sort rainbow sets descending by card count
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
            FileGenerator.processTemplate("rainbows.ftlh", data, pathOutput + "rainbows.html");

        } catch (Exception e) {
            log.error("Fehler bei Rainbows Page: {}", e.getMessage(), e);
        }
    }

    public static boolean isVariantMatch(String cardVariant, String specVariant) {
        if (cardVariant == null || specVariant == null) return false;
        String cv = cardVariant.trim().toLowerCase();
        String sv = specVariant.trim().toLowerCase();
        if (cv.equals(sv)) return true;

        if (isBaseVariant(cv) && isBaseVariant(sv)) {
            return true;
        }

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
}
