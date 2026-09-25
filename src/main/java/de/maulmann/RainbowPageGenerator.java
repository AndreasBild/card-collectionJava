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

            // 1. Explicit Featured Single-Card Rainbow Checklists
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
                            "title", "2018-19 Panini Contenders Optic Legendary Contenders Autographs #LC-JWH Rainbow",
                            "season", "2018-19", "company", "Panini", "brand", "Panini Contenders Optic", "theme", "Legendary Contenders Autographs", "number", "LC-JWH",
                            "variants", List.of(
                                    Map.of("variant", "Gold Vinyl", "serial", "1/1"),
                                    Map.of("variant", "Gold", "serial", "/10"),
                                    Map.of("variant", "Blue", "serial", "/99"),
                                    Map.of("variant", "Contenders Autographs", "serial", "Auto")
                            )
                    ),
                    Map.of(
                            "title", "1999-00 Topps Gold Label #27 Master Rainbow",
                            "season", "1999-00", "company", "Topps", "brand", "Topps Gold Label", "theme", "Master Rainbow", "number", "27",
                            "variants", List.of(
                                    Map.of("theme", "Class 1", "variant", "Base", "displayVariant", "Class 1 Base", "serial", "Base"),
                                    Map.of("theme", "Class 1", "variant", "Black Label", "displayVariant", "Class 1 Black Label", "serial", "Parallel"),
                                    Map.of("theme", "Class 1", "variant", "Red Label", "displayVariant", "Class 1 Red Label", "serial", "/100"),
                                    Map.of("theme", "Class 2", "variant", "Base", "displayVariant", "Class 2 Base", "serial", "Parallel"),
                                    Map.of("theme", "Class 2", "variant", "Black Label", "displayVariant", "Class 2 Black Label", "serial", "Parallel"),
                                    Map.of("theme", "Class 2", "variant", "Red Label", "displayVariant", "Class 2 Red Label", "serial", "/50"),
                                    Map.of("theme", "Class 3", "variant", "Base", "displayVariant", "Class 3 Base", "serial", "Parallel"),
                                    Map.of("theme", "Class 3", "variant", "Black Label", "displayVariant", "Class 3 Black Label", "serial", "Parallel"),
                                    Map.of("theme", "Class 3", "variant", "Red Label", "displayVariant", "Class 3 Red Label", "serial", "/25")
                            )
                    ),
                    Map.of(
                            "title", "1998-99 Fleer Flair Showcase #42 Master Rainbow",
                            "season", "1998-99", "company", "Fleer", "brand", "Flair Showcase", "theme", "Master Rainbow", "number", "42",
                            "variants", List.of(
                                    Map.of("theme", "Showcase", "variant", "Base", "displayVariant", "Row 3 Base (Showcase)", "serial", "Base"),
                                    Map.of("theme", "Showcase", "variant", "Legacy Collection", "displayVariant", "Row 3 Legacy Collection", "serial", "/99"),
                                    Map.of("theme", "Passion", "variant", "Base", "displayVariant", "Row 2 Base (Passion)", "serial", "Parallel"),
                                    Map.of("theme", "Passion", "variant", "Legacy Collection", "displayVariant", "Row 2 Legacy Collection", "serial", "/99"),
                                    Map.of("theme", "Power", "variant", "Base", "displayVariant", "Row 1 Base (Power)", "serial", "Parallel"),
                                    Map.of("theme", "Power", "variant", "Legacy Collection", "displayVariant", "Row 1 Legacy Collection", "serial", "/99")
                            )
                    ),
                    Map.of(
                            "title", "1997-98 SkyBox E-X2001 #32 Essential Credentials Rainbow",
                            "season", "1997-98", "company", "Fleer", "brand", "SkyBox E-X2001", "theme", "Essential Credentials", "number", "32",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Credentials Now", "displayVariant", "Essential Credentials Now", "serial", "/32"),
                                    Map.of("variant", "Credentials Future", "displayVariant", "Essential Credentials Future", "serial", "/49")
                            )
                    ),
                    Map.of(
                            "title", "1998-99 SkyBox E-X Century #34 Essential Credentials Rainbow",
                            "season", "1998-99", "company", "Fleer", "brand", "SkyBox E-X Century", "theme", "Essential Credentials", "number", "34",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Credentials Now", "displayVariant", "Essential Credentials Now", "serial", "/57"),
                                    Map.of("variant", "Credentials Future", "displayVariant", "Essential Credentials Future", "serial", "/34")
                            )
                    ),
                    Map.of(
                            "title", "1999-00 Fleer E-X #15 Essential Credentials Rainbow",
                            "season", "1999-00", "company", "Fleer", "brand", "E-X", "theme", "Essential Credentials", "number", "15",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Credentials Now", "displayVariant", "Essential Credentials Now", "serial", "/15"),
                                    Map.of("variant", "Credentials Future", "displayVariant", "Essential Credentials Future", "serial", "/46")
                            )
                    ),
                    Map.of(
                            "title", "2003-04 Fleer E-X #25 Essential Credentials Rainbow",
                            "season", "2003-04", "company", "Fleer", "brand", "E-X", "theme", "Essential Credentials", "number", "25",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Credentials Now", "displayVariant", "Essential Credentials Now", "serial", "/18"),
                                    Map.of("variant", "Credentials Future", "displayVariant", "Essential Credentials Future", "serial", "/49")
                            )
                    ),
                    Map.of(
                            "title", "1996-97 Topps Bowman's Best #33 Rainbow",
                            "season", "1996-97", "company", "Topps", "brand", "Topps Bowman's Best", "theme", "Base Set", "number", "33",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Refractor", "displayVariant", "Refractor", "serial", "Parallel"),
                                    Map.of("variant", "Atomic Refractor", "displayVariant", "Atomic Refractor", "serial", "Parallel")
                            )
                    ),
                    Map.of(
                            "title", "1995 Classic #102 Early Career Rainbow",
                            "season", "1995", "company", "Classic", "brand", "Classic", "theme", "Base Set & Autographs", "number", "102",
                            "variants", List.of(
                                    Map.of("theme", "Base Set", "variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("theme", "Base Set", "variant", "Silver", "displayVariant", "Silver", "serial", "Parallel"),
                                    Map.of("theme", "Base Set", "variant", "Printers Proof", "displayVariant", "Printers Proof", "serial", "Proof"),
                                    Map.of("theme", "Autographs", "variant", "Base", "displayVariant", "Autograph", "serial", "/3490"),
                                    Map.of("theme", "Certified Autographs", "variant", "Base", "displayVariant", "Certified Autograph", "serial", "/990")
                            )
                    ),
                    Map.of(
                            "title", "1994-95 Collectors Choice #278 Rainbow",
                            "season", "1994-95", "company", "Upper Deck", "brand", "Collectors Choice", "theme", "Base Set & Signatures", "number", "278",
                            "variants", List.of(
                                    Map.of("theme", "Base Set", "variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("theme", "Signature", "variant", "Silver", "displayVariant", "Silver Signature", "serial", "Parallel"),
                                    Map.of("theme", "Signature", "variant", "Gold", "displayVariant", "Gold Signature", "serial", "Parallel"),
                                    Map.of("theme", "Autographs", "variant", "Base", "displayVariant", "Autograph", "serial", "/750")
                            )
                    ),
                    Map.of(
                            "title", "2010-11 Panini Donruss #171 Rainbow",
                            "season", "2010-11", "company", "Panini", "brand", "Panini Donruss", "theme", "Base Set", "number", "171",
                            "variants", List.of(
                                    Map.of("theme", "Base Set", "variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("theme", "Press Proof", "variant", "Base", "displayVariant", "Press Proof", "serial", "/100"),
                                    Map.of("theme", "Die Cut", "variant", "Emerald", "displayVariant", "Emerald Die Cut", "serial", "Parallel"),
                                    Map.of("theme", "Die Cut", "variant", "Sapphire", "displayVariant", "Sapphire Die Cut", "serial", "/49")
                            )
                    ),
                    Map.of(
                            "title", "1994 Signature Rookies #56 Tetrad Rainbow",
                            "season", "1994", "company", "Signature Rookies", "brand", "Signature Rookies", "theme", "Tetrad", "number", "56",
                            "variants", List.of(
                                    Map.of("theme", "Tetrad", "variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("theme", "Tetrad", "variant", "Autograph", "displayVariant", "Autograph", "serial", "Auto"),
                                    Map.of("theme", "Tetrad Authentic Signature", "variant", "Base", "displayVariant", "Authentic Signature", "serial", "/7750")
                            )
                    ),
                    Map.of(
                            "title", "1995-96 Upper Deck Base Set #160 Rainbow",
                            "season", "1995-96", "company", "Upper Deck", "brand", "Upper Deck", "theme", "Base Set", "number", "160",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Electric Court", "displayVariant", "Electric Court", "serial", "Parallel"),
                                    Map.of("variant", "Electric Court Gold", "displayVariant", "Electric Court Gold", "serial", "Parallel")
                            )
                    ),
                    Map.of(
                            "title", "1995-96 Upper Deck Base Set #207 Rainbow",
                            "season", "1995-96", "company", "Upper Deck", "brand", "Upper Deck", "theme", "Base Set", "number", "207",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Electric Court", "displayVariant", "Electric Court", "serial", "Parallel"),
                                    Map.of("variant", "Electric Court Gold", "displayVariant", "Electric Court Gold", "serial", "Parallel")
                            )
                    ),
                    Map.of(
                            "title", "1994 Classic 4 Sports #5 Rainbow",
                            "season", "1994", "company", "Classic", "brand", "Classic 4 Sports", "theme", "Base Set & Autograph", "number", "5",
                            "variants", List.of(
                                    Map.of("theme", "Base Set", "variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("theme", "Base Set", "variant", "Printers Proof", "displayVariant", "Printers Proof", "serial", "Proof"),
                                    Map.of("theme", "Autograph", "variant", "Base", "displayVariant", "Autograph", "serial", "/1275")
                            )
                    ),
                    Map.of(
                            "title", "1994 Classic Comic #103 Rainbow",
                            "season", "1994", "company", "Classic", "brand", "Classic", "theme", "Comic", "number", "103",
                            "variants", List.of(
                                    Map.of("variant", "Base", "displayVariant", "Base", "serial", "Base"),
                                    Map.of("variant", "Gold", "displayVariant", "Gold", "serial", "Parallel"),
                                    Map.of("variant", "Printers Proof", "displayVariant", "Printers Proof", "serial", "/975")
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

                if (expectedVariants.size() < 3) continue;

                Map<String, Object> setMap = new HashMap<>();
                setMap.put("name", title);
                setMap.put("season", season);
                setMap.put("company", company);
                setMap.put("brand", brand);
                setMap.put("theme", theme);
                setMap.put("number", number);

                List<Map<String, Object>> cardItems = new ArrayList<>();
                int acquiredCount = 0;
                List<CardJson> candidates = cardsBySeasonAndNumber.getOrDefault((season + "|" + normalizeCardNumber(number)).toLowerCase(), Collections.emptyList());
                Set<String> matchedCardIds = new HashSet<>();

                for (Map<String, String> spec : expectedVariants) {
                    String reqVariant = spec.get("variant");
                    String reqSerial = spec.get("serial");
                    String reqTheme = spec.get("theme");
                    String displayVariant = spec.getOrDefault("displayVariant", reqVariant);

                    CardJson matched = null;
                    for (CardJson c : candidates) {
                        if (c.id() != null && matchedCardIds.contains(c.id())) {
                            continue;
                        }
                        if (c.brand() != null && !brand.equalsIgnoreCase(c.brand())
                                && !c.brand().toLowerCase().contains(brand.toLowerCase())
                                && !brand.toLowerCase().contains(c.brand().toLowerCase())) {
                            continue;
                        }
                        if (reqTheme != null && (c.theme() == null || !c.theme().equalsIgnoreCase(reqTheme))) {
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
                        itemMap.put("variant", displayVariant);
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
                    } else {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("variant", displayVariant);
                        itemMap.put("serial", reqSerial);
                        itemMap.put("acquired", false);
                        itemMap.put("title", "Seeking " + season + " " + brand + " " + displayVariant + " #" + number);
                        cardItems.add(itemMap);
                    }
                }

                if (acquiredCount < 1) continue;

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
                    for (Map<String, Object> c : cards) {
                        if (Boolean.TRUE.equals(c.get("acquired"))) {
                            totalRainbowCards++;
                            String serial = (String) c.get("serial");
                            String variant = (String) c.get("variant");
                            if ((serial != null && (serial.contains("1/1") || serial.equals("1/1") || serial.equals("#1/1")))
                                    || (variant != null && (variant.toLowerCase().contains("1 of 1") || variant.toLowerCase().contains("masterpiece")))) {
                                totalRainbow1of1++;
                            }
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
        if (isDiamondGroup(cv, sv, "quadruple", "quadruple diamond")) return true;

        if (isCredentialsGroup(cv, sv, "credentials now", "essential credentials now")) return true;
        return isCredentialsGroup(cv, sv, "credentials future", "essential credentials future");
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

    private static boolean isCredentialsGroup(String cv, String sv, String... aliases) {
        boolean cvMatch = false;
        boolean svMatch = false;
        for (String alias : aliases) {
            if (cv.equals(alias)) cvMatch = true;
            if (sv.equals(alias)) svMatch = true;
        }
        return cvMatch && svMatch;
    }
}
