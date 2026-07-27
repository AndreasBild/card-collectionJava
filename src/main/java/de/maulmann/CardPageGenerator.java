package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * High-speed parallel Card Page Generator for generating rich HTML detail pages.
 */
public class CardPageGenerator {

    private static final String BASE_FOLDER = "output/cards";
    private static final String RELATIVE_IMAGES_PATH = "../../images";
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final Logger log = LoggerFactory.getLogger(CardPageGenerator.class);
    public static final String ROOT = "../../";

    private static final List<String> duplicateLog = Collections.synchronizedList(new ArrayList<>());
    private static final TriviaManager triviaManager = new TriviaManager();
    private static final FirebaseConfigManager firebaseConfigManager = new FirebaseConfigManager();
    private static TimestampTracker timestampTracker;

    private static final Map<String, String> DISK_IMAGE_CACHE = new ConcurrentHashMap<>();

    public static void setTimestampTracker(TimestampTracker tracker) {
        timestampTracker = tracker;
    }

    private static final Configuration fmConfig;
    static {
        fmConfig = new Configuration(Configuration.VERSION_2_3_34);
        fmConfig.setClassForTemplateLoading(CardPageGenerator.class, "/templates");
        fmConfig.setDefaultEncoding("UTF-8");
        fmConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    public static class CardData {
        Map<String, String> attributes;
        String stableId;
        String filenameBase;
        String filename;
        String seasonFolder;
        String fullRelativePath;

        public CardData(CardJson c, String uniqueId) {
            this.attributes = new HashMap<>();
            if (c.player != null) this.attributes.put("Player", c.player);
            if (c.season != null) this.attributes.put("Season", c.season);
            if (c.team != null) this.attributes.put("Team", c.team);
            if (c.company != null) this.attributes.put("Company", c.company);
            if (c.brand != null) this.attributes.put("Brand", c.brand);
            if (c.theme != null) this.attributes.put("Theme", c.theme);
            if (c.variant != null) this.attributes.put("Variant", c.variant);
            if (c.cardNumber != null) this.attributes.put("Number", c.cardNumber);
            if (c.serialNumber != null) this.attributes.put("Serial", c.serialNumber);
            if (c.printRun != null) this.attributes.put("Print Run", String.valueOf(c.printRun));
            if (c.gradingCompany != null) this.attributes.put("Grading Co.", c.gradingCompany);
            if (c.grade != null) this.attributes.put("Grade", c.grade);
            if (c.notes != null) this.attributes.put("Notes", c.notes);
            String autoVal = c.isAutograph ? "Yes" : "No";
            this.attributes.put("Autograph", autoVal);
            this.attributes.put("Auto", autoVal);
            this.attributes.put("isAutograph", autoVal);

            String memVal = c.isPatch ? "Yes" : "No";
            this.attributes.put("Memorabilia", memVal);
            this.attributes.put("Game Used", memVal);
            this.attributes.put("Mem / Patch", memVal);

            String rkVal = c.isRookie ? "Yes" : "No";
            this.attributes.put("Rookie", rkVal);

            String currentTeam = this.attributes.get("Team");
            if (!isValid(currentTeam)) {
                String player = this.attributes.get("Player");
                if (player != null && player.startsWith("Juwan Howard")) {
                    String calculatedTeam = getTeamBySeason(this.attributes.get("Season"));
                    this.attributes.put("Team", calculatedTeam);
                }
            }

            if (uniqueId != null && !uniqueId.isEmpty()) {
                this.stableId = uniqueId;
            } else {
                this.stableId = generateStableId(this.attributes);
            }

            calculatePaths(this.stableId);
        }

        private void calculatePaths(String uniqueId) {
            List<String> filenameTokens = new ArrayList<>();
            String pStr = attributes.get("Player");
            if (pStr != null && pStr.contains(",")) pStr = pStr.split(",")[0].trim();
            addIfPresent(filenameTokens, pStr);

            String tStr = attributes.get("Team");
            if (tStr != null && tStr.contains(",")) tStr = tStr.split(",")[0].trim();
            addIfPresent(filenameTokens, tStr);
            addIfPresent(filenameTokens, attributes.get("Season"));
            addIfPresent(filenameTokens, attributes.get("Company"));
            addIfPresent(filenameTokens, attributes.get("Brand"));
            addIfPresent(filenameTokens, attributes.get("Theme"));
            addIfPresent(filenameTokens, attributes.get("Variant"));
            addIfPresent(filenameTokens, attributes.get("Number"));

            String serial = attributes.get("Serial");
            if (!isValid(serial)) {
                serial = attributes.get("Serial/Print Run");
            }
            String printRun = attributes.get("Print Run");
            if (isValid(serial) && !serial.equals("0")) {
                String cleanSerial = serial.replace("#", "").replace("/", "-");
                if (!cleanSerial.contains("-") && isValid(printRun) && !printRun.equals("0")) {
                    int prInt = 0;
                    try { prInt = Integer.parseInt(printRun); } catch (Exception ignored) {}
                    if (cleanSerial.length() == 1 && Character.isDigit(cleanSerial.charAt(0)) && prInt >= 10) {
                        cleanSerial = "0" + cleanSerial;
                    }
                    cleanSerial = cleanSerial + "-" + printRun;
                }
                filenameTokens.add("sn" + cleanSerial);
            }

            String grade = attributes.get("Grade");
            if (isValid(grade)) filenameTokens.add(grade);

            this.filenameBase = cleanFilename(String.join("-", filenameTokens)) + "-" + uniqueId;
            this.filename = this.filenameBase + ".html";
            String seasonRaw = attributes.get("Season");
            this.seasonFolder = isValid(seasonRaw) ? cleanFilename(seasonRaw) : "Unknown_Season";
            this.fullRelativePath = "cards/" + this.seasonFolder + "/" + this.filename;
        }

        public String get(String key) {
            return attributes.getOrDefault(key, "");
        }

        public boolean has(String key) {
            String val = attributes.get(key);
            return isValid(val);
        }
    }

    public static void run() {
        log.info("Starting high-speed Card Page Generation...");
        long startTime = System.currentTimeMillis();
        duplicateLog.clear();
        duplicateLog.add("=== DUPLICATE CARDS LOG ===");
        duplicateLog.add("Generated: " + new java.util.Date());
        duplicateLog.add("This file lists all un-numbered cards that were filtered out to prevent duplicate pages.\n");

        Path cardsDir = Paths.get(BASE_FOLDER);
        if (Files.exists(cardsDir)) {
            try (Stream<Path> walk = Files.walk(cardsDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        List<CardJson> jsonCards = CardDataLoader.loadCardsFromJson("content/json/cards.json");
        if (!jsonCards.isEmpty()) {
            log.info("Generating Juwan Howard card pages from content/json/cards.json ({} cards)...", jsonCards.size());
            List<CardData> juwanCards = new ArrayList<>();
            for (CardJson c : jsonCards) {
                juwanCards.add(new CardData(c, null));
            }
            List<CardData> filteredJuwanCards = filterDuplicateCards(juwanCards, "content/json/cards.json");
            log.info("Deduplication complete: {} cards queued for generation (skipped {} un-numbered duplicates).",
                    filteredJuwanCards.size(), (juwanCards.size() - filteredJuwanCards.size()));
            generateSubPagesMultithreaded(filteredJuwanCards, "Juwan-Howard-Collection.html");
        }

        Map<String, String> otherJsonBuckets = Map.of(
                "content/json/baseball.json", "Baseball.html",
                "content/json/flawless.json", "Flawless.html",
                "content/json/wantlist.json", "Wantlist.html",
                "content/json/panini.json", "Panini.html"
        );

        for (Map.Entry<String, String> entry : otherJsonBuckets.entrySet()) {
            String jsonPath = entry.getKey();
            String overviewPage = entry.getValue();

            List<CardJson> cards = CardDataLoader.loadCardsFromJson(jsonPath);
            if (!cards.isEmpty()) {
                log.info("Generating subpages from {} ({} cards)...", jsonPath, cards.size());
                List<CardData> cardDataList = new ArrayList<>();
                for (CardJson c : cards) {
                    cardDataList.add(new CardData(c, null));
                }
                List<CardData> filtered = filterDuplicateCards(cardDataList, jsonPath);
                generateSubPagesMultithreaded(filtered, overviewPage);
            }
        }

        try {
            File dupFile = new File("output/Duplicates.txt");
            Files.write(dupFile.toPath(), duplicateLog, StandardCharsets.UTF_8);
            log.info("Saved Duplicates.txt with {} entries.", duplicateLog.size() - 4);
        } catch (IOException e) {
            log.error("Failed to write Duplicates.txt", e);
        }

        long endTime = System.currentTimeMillis();
        log.info("All card pages generated in {} ms.", (endTime - startTime));
    }

    public static void main(String[] args) {
        run();
    }

    private static List<CardData> filterDuplicateCards(List<CardData> rawCards, String sourceName) {
        List<CardData> filteredCards = new ArrayList<>();
        Set<String> seenFingerprints = new HashSet<>();

        duplicateLog.add("\n--- From " + sourceName + " ---");

        for (CardData card : rawCards) {
            String fingerprint = (card.get("Season") + "|" + card.get("Company") + "|" +
                    card.get("Brand") + "|" + card.get("Theme") + "|" +
                    card.get("Variant") + "|" + card.get("Number") + "|" +
                    card.get("Grading Co.") + "|" + card.get("Grade")).toLowerCase();

            String serial = card.get("Serial");
            if (!isValid(serial)) serial = card.get("Serial/Print Run");

            boolean hasSerial = isValid(serial) && !serial.equals("0");

            if (seenFingerprints.contains(fingerprint)) {
                if (!hasSerial) {
                    String dupInfo = card.get("Season") + " " + card.get("Company") + " " +
                            card.get("Brand") + " " + card.get("Theme") + " " +
                            card.get("Variant") + " #" + card.get("Number") + " - " + card.get("Player");
                    duplicateLog.add("[SKIPPED] " + dupInfo.replaceAll("\\s+", " "));
                    continue;
                } else {
                    filteredCards.add(card);
                }
            } else {
                seenFingerprints.add(fingerprint);
                filteredCards.add(card);
            }
        }
        return filteredCards;
    }

    public static String generateStableId(Map<String, String> attributes) {
        String[] relevantKeys = {
                "Player", "Team", "Season", "Company", "Brand",
                "Theme", "Variant", "Number", "Serial", "Print Run",
                "Serial/Print Run", "Grade"
        };

        StringBuilder sb = new StringBuilder();
        for (String key : relevantKeys) {
            String val = attributes.getOrDefault(key, "").trim();
            if (key.equals("Player") || key.equals("Team")) {
                if (val.contains(",")) val = val.split(",")[0].trim();
            }
            if (!val.isEmpty() && !val.equals("0")) {
                sb.append(key).append(":").append(val).append("|");
            }
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                String hex = Integer.toHexString(0xff & digest[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    private static void generateSubPagesMultithreaded(List<CardData> allCards, String overviewPage) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < allCards.size(); i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        CardData currentCard = allCards.get(index);
                        CardData prevCard = (index > 0) ? allCards.get(index - 1) : null;
                        CardData nextCard = (index < allCards.size() - 1) ? allCards.get(index + 1) : null;

                        Path folderPath = Paths.get(BASE_FOLDER, currentCard.seasonFolder);
                        Files.createDirectories(folderPath);
                        Path filePath = folderPath.resolve(currentCard.filename);

                        createSubPage(currentCard, filePath, prevCard, nextCard, allCards, overviewPage);
                    } catch (Exception e) {
                        log.error("Failed to generate subpage for card at index " + index, e);
                    }
                });
            }
        }
    }

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards, String overviewPage) {
        String h1Title = generateH1(c);
        String browserTitle = generateBrowserTitle(c, overviewPage);
        String metaDesc = generateMetaDescription(c);

        String seasonImgFolder = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder;
        String imageBaseName = c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-"));
        String resolvedImageBase = resolveDiskImageBase(c.seasonFolder, imageBaseName, c);

        String frontImgPath = seasonImgFolder + "/" + resolvedImageBase + "-front.avif";
        String backImgPath = seasonImgFolder + "/" + resolvedImageBase + "-back.avif";

        Map<String, Object> data = new HashMap<>();
        data.put("cardId", c.stableId);

        String faqHtml = CardSchemaGenerator.generateFaqHtml(c);
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + resolvedImageBase + "-front.avif";
        data.put("headHtml", SharedTemplates.getHead(browserTitle, metaDesc, ROOT, c.fullRelativePath, frontImgUrl));
        data.put("jsonLd", CardSchemaGenerator.generateJsonLd(c, metaDesc, h1Title, overviewPage, resolvedImageBase, faqHtml));
        String collectionName = "Collection";
        String activeNav = "collection";
        if ("Flawless.html".equals(overviewPage)) {
            collectionName = "Flawless";
            activeNav = "flawless";
        } else if ("Baseball.html".equals(overviewPage)) {
            collectionName = "Baseball";
            activeNav = "baseball";
        } else if ("Panini.html".equals(overviewPage)) {
            collectionName = "Panini";
            activeNav = "panini";
        } else if ("Wantlist.html".equals(overviewPage)) {
            collectionName = "Wantlist";
            activeNav = "wantlist";
        }

        data.put("topNavHtml", SharedTemplates.getTopNav(ROOT, activeNav));

        List<Map<String, String>> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(Map.of("name", "Home", "link", ROOT + "index.html"));
        breadcrumbItems.add(Map.of("name", collectionName, "link", ROOT + overviewPage));
        breadcrumbItems.add(Map.of("name", c.get("Season"), "link", ROOT + overviewPage + "#" + c.seasonFolder.toLowerCase()));
        breadcrumbItems.add(Map.of("name", h1Title, "link", ""));
        data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(breadcrumbItems));

        data.put("footerHtml", SharedTemplates.getFooter(ROOT));

        data.put("overviewPage", overviewPage);
        data.put("prevLink", prev != null ? "../" + prev.seasonFolder + "/" + prev.filename : null);
        data.put("nextLink", next != null ? "../" + next.seasonFolder + "/" + next.filename : null);

        data.put("h1Title", h1Title);
        data.put("aiSnapshotText", generateAiSnapshotText(c));

        data.put("frontImgPath", frontImgPath);
        data.put("backImgPath", backImgPath);
        data.put("frontAlt", generateAltText(c, "front"));
        data.put("backAlt", generateAltText(c, "back"));
        data.put("frontImgTitle", getPrimaryPlayer(c) +" Private Collection - Front scan: " + formatMulti(c.get("Player")) + " " + c.get("Season") + " " + c.get("Brand") + " " + c.get("Variant"));
        data.put("backImgTitle", getPrimaryPlayer(c) +" Private Collection - Back scan: " + formatMulti(c.get("Player")) + " " + c.get("Season") +" " + c.get("Brand") + " " + c.get("Variant"));

        data.put("player", isValid(c.get("Player")) ? formatMulti(c.get("Player")) : "-");
        data.put("season", isValid(c.get("Season")) ? c.get("Season") : "-");
        data.put("team", isValid(c.get("Team")) ? formatMulti(c.get("Team")) : "-");
        data.put("company", isValid(c.get("Company")) ? c.get("Company") : "-");
        data.put("brand", isValid(c.get("Brand")) ? c.get("Brand") : "-");
        data.put("theme", isValid(c.get("Theme")) ? c.get("Theme") : "-");
        data.put("variant", isValid(c.get("Variant")) ? c.get("Variant") : "-");
        data.put("number", isValid(c.get("Number")) ? c.get("Number") : "-");
        data.put("rookie", c.has("Rookie") ? c.get("Rookie") : "-");
        data.put("memorabilia", c.has("Memorabilia") ? c.get("Memorabilia") : "-");
        data.put("autograph", c.has("Autograph") ? c.get("Autograph") : "-");

        String combined = c.get("Serial/Print Run");
        String serialDisplay = "-";
        if (isValid(combined)) {
            serialDisplay = combined;
        } else if (c.has("Serial") || c.has("Print Run")) {
            serialDisplay = (c.has("Serial") ? c.get("Serial") : "?") + " / " + (c.has("Print Run") ? c.get("Print Run") : "?");
        }
        data.put("serialDisplay", serialDisplay);

        String grading = c.get("Grading Co.") + " " + c.get("Grade");
        data.put("grading", (grading.trim().length() > 1 && !grading.trim().equals("null null")) ? grading : "");

        data.put("hobbyTrivia", triviaManager.getTrivia("hobbyTrivia", c.attributes));
        data.put("techTrivia", triviaManager.getTrivia("cardTechTrivia", c.attributes));
        data.put("playerHighlights", getSeasonHighlights(c.get("Season"), c.get("Player"), overviewPage));
        data.put("eraContext", getEraContext(c.get("Season"), c.get("Player"), overviewPage));
        String primaryP = getPrimaryPlayerName(c.get("Player"));
        boolean isBaseball = "Baseball.html".equals(overviewPage) || isBaseballPlayer(primaryP);
        data.put("eraTitle", isBaseball ? "&#x26BE; MLB Era & Pop Culture" : "&#x1F3C0; NBA Era & Pop Culture");
        data.put("cardBackText", "");

        data.put("relatedCards", findRelatedCards(c, allCards, 4));
        data.put("externalLinks", generateExternalLinks(c));

        data.put("faqHtml", faqHtml);
        data.put("firebaseConfig", firebaseConfigManager.getConfig());

        try {
            Template template = fmConfig.getTemplate("card-detail.ftlh");
            StringWriter sw = new StringWriter();
            template.process(data, sw);
            String finalHtml = sw.toString();

            if (timestampTracker != null && finalHtml.contains("[[STABLE_TIME]]")) {
                String relativeOutputPath = Paths.get("output").toUri().relativize(path.toUri()).getPath();
                String stableTime = timestampTracker.getStableTimestamp(relativeOutputPath, finalHtml);
                finalHtml = finalHtml.replace("[[STABLE_TIME]]", stableTime);
            }

            if (finalHtml.contains("{{CONSENT_BANNER}}")) {
                finalHtml = finalHtml.replace("{{CONSENT_BANNER}}", SharedTemplates.getConsentBanner(ROOT));
            }

            Files.writeString(path, finalHtml, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error generating detail page for " + path, e);
        }
    }

    private static String generateBrowserTitle(CardData c, String overviewPage) {
        String player = getPrimaryPlayer(c);
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String brand = c.get("Brand");
        String season = c.get("Season");
        return player + " " + season + " " + brand + number + " | " + player + " Private Collection";
    }

    private static String generateH1(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.has("Number") ? " #" + c.get("Number") : "";

        StringBuilder sb = new StringBuilder();
        sb.append(player).append(" ").append(season).append(" ").append(company).append(" ").append(brand);
        if (isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" ").append(theme);
        }
        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number);
        return sb.toString();
    }

    private static String generateMetaDescription(CardData c) {
        String player = getPrimaryPlayer(c);
        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");

        StringBuilder sb = new StringBuilder();
        sb.append("View details for the ").append(season).append(" ").append(brand).append(" ").append(player);

        if (c.has("Number")) {
            sb.append(" card #").append(c.get("Number"));
        }
        sb.append(" from our ").append(player).append(" Private Collection. ");

        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append("Rare ").append(variant).append(" variant. ");
        } else if (isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append("Features ").append(theme).append(" design. ");
        }

        String serial = c.get("Serial/Print Run");
        if (isValid(serial)) {
            sb.append("Numbered ").append(serial).append(". ");
        }

        sb.append("A must-see for any ").append(player).append(" Super Collector. High-res scans and hobby history.");

        String result = sb.toString();
        if (result.length() > 160) {
            result = result.substring(0, 157) + "...";
        }
        return result;
    }

    private static String generateAltText(CardData c, String view) {
        String base = c.get("Season") + " " + c.get("Brand") + " " + formatMulti(c.get("Player"));
        if (view.equals("front")) return "Front scan of " + base + " - " + c.get("Variant") + " edition (" + formatMulti(c.get("Team")) + ") - "+getPrimaryPlayer(c) + " Collector Private Collection";
        else return "Back scan of " + base + " showing stats for " + formatMulti(c.get("Team")) + " - " +getPrimaryPlayer(c) + " Collector Private Collection";
    }

    private static String generateAiSnapshotText(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.get("Number");
        String team = formatMulti(c.get("Team"));
        String serial = c.get("Serial/Print Run");

        StringBuilder sb = new StringBuilder();
        sb.append("This ").append(season).append(" ").append(company).append(" ").append(brand);

        if (isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" (").append(theme).append(")");
        }

        sb.append(" card features ").append(player).append(" during his tenure with the ").append(team).append(".");

        if (isValid(number) && !number.equals("-")) {
            sb.append(" Card #").append(number).append(".");
        }

        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" This is the coveted ").append(variant).append(" parallel variation.");
        }

        if (isValid(serial)) {
            sb.append(" Strictly limited edition serial numbered ").append(serial).append(".");
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(" Includes official certified autograph.");
        }

        if (c.has("Memorabilia") && c.get("Memorabilia").equalsIgnoreCase("Yes")) {
            sb.append(" Contains authentic game-used memorabilia patch.");
        }

        return sb.toString();
    }

    private static List<Map<String, String>> findRelatedCards(CardData target, List<CardData> pool, int limit) {
        if (pool == null || pool.isEmpty()) return Collections.emptyList();

        List<CardData> candidates = pool.stream()
                .filter(c -> !c.stableId.equals(target.stableId))
                .collect(Collectors.toList());

        Map<CardData, Integer> scored = new HashMap<>();

        for (CardData c : candidates) {
            int score = 0;
            if (c.get("Season").equals(target.get("Season"))) score += 10;

            String cBrand = c.get("Brand");
            String tBrand = target.get("Brand");
            if (cBrand.equalsIgnoreCase(tBrand)) score += 8;
            else if (cBrand.contains(tBrand) || tBrand.contains(cBrand)) score += 5;

            if (c.get("Company").equals(target.get("Company"))) score += 3;

            String cPlayer = c.get("Player");
            String tPlayer = target.get("Player");
            if (cPlayer.equals(tPlayer)) score += 15;

            boolean targetIsRare = isRareParallel(target);
            boolean cIsRare = isRareParallel(c);
            if (targetIsRare && cIsRare) score += 7;

            scored.put(c, score);
        }

        List<CardData> top = scored.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Map<String, String>> result = new ArrayList<>();
        for (CardData c : top) {
            Map<String, String> item = new HashMap<>();
            String relUrl = "../" + c.seasonFolder + "/" + c.filename;
            item.put("title", generateH1(c));
            item.put("url", relUrl);
            item.put("link", relUrl);

            String rawImageBase = c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-"));
            String imageBaseName = resolveDiskImageBase(c.seasonFolder, rawImageBase, c);
            String thumbAvif = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder + "/" + imageBaseName + "-front-400w.avif";
            String thumbFallback = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder + "/" + imageBaseName + "-front.avif";

            item.put("thumbWebp", thumbAvif);
            item.put("thumbAvif", thumbAvif);
            item.put("thumb", thumbAvif);
            item.put("thumbFallback", thumbFallback);
            item.put("alt", generateAltText(c, "front"));
            item.put("variant", c.has("Variant") ? c.get("Variant") : "Base");
            item.put("season", c.get("Season"));
            item.put("brand", c.get("Brand"));
            item.put("meta", c.get("Season") + " " + c.get("Brand") + (c.has("Variant") ? " - " + c.get("Variant") : ""));

            result.add(item);
        }
        return result;
    }

    private static boolean isRareParallel(CardData c) {
        String variant = c.get("Variant").toLowerCase();
        String theme = c.get("Theme").toLowerCase();
        return variant.contains("refractor") || variant.contains("pmg") || variant.contains("ruby") ||
                variant.contains("autograph") || variant.contains("patch") || c.has("Serial") ||
                theme.contains("flawless") || theme.contains("exquisite");
    }

    private static List<Map<String, String>> generateExternalLinks(CardData c) {
        List<Map<String, String>> links = new ArrayList<>();

        String primaryPlayer = getPrimaryPlayer(c);
        String season = c.get("Season");
        String brand = c.get("Brand");
        String variant = c.get("Variant");
        String number = c.get("Number");

        String ebayQuery = primaryPlayer + " " + season + " " + brand + " " + (isValid(variant) && !variant.equals("Base") ? variant : "") + " " + (isValid(number) ? "#" + number : "");
        String ebayUrl = "https://www.ebay.com/sch/i.html?_nkw=" + ebayQuery.trim().replace(" ", "+");
        links.add(Map.of("name", "Similar cards on eBay", "url", ebayUrl, "icon", "ebay"));

        String bkpQuery = primaryPlayer + " " + season + " " + brand;
        String bkpUrl = "https://www.beckett.com/search?q=" + bkpQuery.trim().replace(" ", "+");
        links.add(Map.of("name", "Beckett Checklist", "url", bkpUrl, "icon", "beckett"));

        if (primaryPlayer.equalsIgnoreCase("Juwan Howard")) {
            links.add(Map.of("name", "Juwan Howard Career Stats", "url", "https://www.basketball-reference.com/players/h/howarju01.html", "icon", "bref"));
        } else {
            String wikiUrl = "https://en.wikipedia.org/wiki/" + primaryPlayer.replace(" ", "_");
            links.add(Map.of("name", primaryPlayer + " Wikipedia Profile", "url", wikiUrl, "icon", "wiki"));
        }

        return links;
    }



    private static String getSeasonHighlights(String season, String player, String overviewPage) {
        if (season == null) return null;
        String p = getPrimaryPlayerName(player);

        if ("Baseball.html".equals(overviewPage) || isBaseballPlayer(p)) {
            return getBaseballPlayerHighlights(p, season);
        }

        if (p.equalsIgnoreCase("Juwan Howard")) {
            return getJuwanHowardHighlights(season);
        }

        return getOtherNbaPlayerHighlights(p, season);
    }

    private static String getBaseballPlayerHighlights(String p, String season) {
        return switch (p.toLowerCase()) {
            case "eric gagne", "eric \"game over\" gagne" -> "2003 NL Cy Young Award winner with the LA Dodgers. Set the all-time MLB record with 84 consecutive converted saves (2002–2004) and earned 3 NL All-Star selections.";
            case "jim bunning", "jim \"hof 96\" bunning" -> "National Baseball Hall of Fame (1996). 9x All-Star pitcher who pitched a perfect game on Father's Day 1964 with the Phillies and accumulated 2,855 career strikeouts.";
            case "ozzie smith", "ozzie \"the wiz\" smith" -> "National Baseball Hall of Fame (2002). 'The Wizard' won 13 consecutive Gold Glove Awards at shortstop, 15 All-Star selections, and the 1982 World Series title with St. Louis.";
            case "steve carlton", "steve \"lefty\" carlton" -> "National Baseball Hall of Fame (1994). 4x NL Cy Young Award winner (1972, 1977, 1980, 1982), 10x All-Star, 1980 World Champion, and 3,988 career strikeouts.";
            case "will clark", "will \"the thrill\" clark" -> "6x MLB All-Star first baseman, 1989 NLCS MVP with the San Francisco Giants (.650 AVG in NLCS), and Golden Spikes Award winner.";
            case "ken griffey jr.", "ken griffey jr" -> "National Baseball Hall of Fame (2016). 13x All-Star, 10x Gold Glove winner, 1997 AL MVP with Seattle, and 630 career home runs.";
            default -> p + " is an iconic Major League Baseball player featured in this premium sports memorabilia release.";
        };
    }

    private static String getJuwanHowardHighlights(String season) {
        return switch (season) {
            case "1994-95", "1994" -> "Rookie Season with Washington Bullets: Named to NBA All-Rookie First Team (17.0 PPG, 8.4 RPG).";
            case "1995-96", "1995" -> "Career Year: NBA All-Star selection, All-NBA Third Team. Averaged a career-high 22.1 PPG, 8.1 RPG, 4.4 APG. Scored 42 pts vs Toronto.";
            case "1996-97", "1996" -> "Led Bullets to the NBA Playoffs for the first time since 1988 (19.1 PPG, 8.0 RPG). Signed historic $100M contract.";
            case "1997-98", "1997" -> "Washington Wizards Rebranding Era: Co-captain alongside Chris Webber (18.5 PPG, 7.0 RPG).";
            case "1998-99", "1998" -> "Lockout Season: Co-captain leading Washington in scoring (18.9 PPG, 8.1 RPG).";
            case "1999-00", "1999" -> "Final full season in Washington: Averaged 14.9 PPG and 5.7 RPG.";
            case "2000-01", "2000" -> "Traded to Dallas Mavericks mid-season: Strong playoff performance alongside Dirk Nowitzki (17.8 PPG).";
            case "2001-02", "2001" -> "Dallas & Denver Nuggets transition: Reliable veteran presence (14.6 PPG, 7.6 RPG).";
            case "2002-03", "2002" -> "Denver Nuggets Leader: Primary frontcourt scoring option (18.4 PPG, 7.6 RPG).";
            case "2003-04", "2003" -> "Orlando Magic: Veteran anchor alongside Tracy McGrady (17.0 PPG, 7.0 RPG).";
            case "2004-05", "2004" -> "Houston Rockets: Key frontcourt starter with Yao Ming and Tracy McGrady (9.6 PPG, 5.7 RPG).";
            case "2005-06", "2005" -> "Houston Rockets: Contributed 11.8 PPG and 6.7 RPG in Western Conference competition.";
            case "2006-07", "2006" -> "Houston Rockets: Helped guide Houston to 52 wins and Western Conference Playoff berth.";
            case "2007-08", "2007" -> "Dallas Mavericks Return: Provided playoff experience and frontcourt depth.";
            case "2008-09", "2008" -> "Denver Nuggets & Charlotte Bobcats: Veteran leadership across 50 NBA games.";
            case "2009-10", "2009" -> "Portland Trail Blazers: Played 73 games, key reserve in Western Conference Playoffs (6.0 PPG, 4.6 RPG).";
            case "2010-11", "2010" -> "Miami Heat 'Big Three' Era: Reached 2011 NBA Finals alongside LeBron James, Dwyane Wade, and Chris Bosh.";
            case "2011-12", "2011" -> "NBA Champion with Miami Heat: Won his 1st NBA Championship ring.";
            case "2012-13", "2012" -> "Back-to-Back NBA Champion with Miami Heat: Retired as a 2x NBA Champion after 19 seasons.";
            default -> "Juwan Howard enjoyed a remarkable 19-season NBA career (1994-2013), winning 2 NBA Championships with the Miami Heat.";
        };
    }

    private static String getOtherNbaPlayerHighlights(String p, String season) {
        return switch (p.toLowerCase()) {
            case "michael jordan" -> "6x NBA Champion, 6x Finals MVP, 5x NBA MVP, 14x All-Star. Widely revered as the greatest basketball player of all time.";
            case "kobe bryant" -> "5x NBA Champion, 2x Finals MVP, 2008 NBA MVP, 18x All-Star. Legendary guard who spent his entire 20-year career with the Los Angeles Lakers.";
            case "lebron james" -> "4x NBA Champion, 4x Finals MVP, 4x NBA MVP. During the 2008-09 season, LeBron won his 1st NBA MVP award with 66 wins for Cleveland.";
            case "bill russell" -> "11x NBA Champion, 5x NBA MVP, 12x All-Star. The ultimate winner in sports history and defensive anchor of the Boston Celtics dynasty.";
            case "kareem abdul-jabbar" -> "6x NBA MVP, 6x NBA Champion, 19x All-Star. Held the NBA all-time scoring record for 39 years with his unstoppable Skyhook.";
            case "julius erving" -> "1983 NBA Champion, 1981 NBA MVP, 11x NBA All-Star. 'Dr. J' revolutionized the game above the rim for the Philadelphia 76ers and Nets.";
            case "jerry west" -> "1972 NBA Champion, 1969 Finals MVP, 14x All-Star. Hall of Fame guard whose iconic silhouette serves as the official NBA logo.";
            case "isiah thomas" -> "2x NBA Champion, 1990 Finals MVP, 12x All-Star. Legendary point guard and floor general of the Detroit Pistons 'Bad Boys' dynasty.";
            case "clyde drexler" -> "1995 NBA Champion with Houston, 10x All-Star, Hall of Famer. 'The Glide' was one of the most dynamic guards in NBA history.";
            case "alonzo mourning" -> "2006 NBA Champion with Miami, 2x Defensive Player of the Year, 7x All-Star center for the Heat and Hornets.";
            case "oscar robertson" -> "1971 NBA Champion, 1964 NBA MVP, 12x All-Star. 'The Big O' was the first player in NBA history to average a season triple-double.";
            case "rick barry" -> "1975 NBA Champion & Finals MVP with Golden State, 12x All-Star, Hall of Famer famous for his flawless underhand free throws.";
            case "kevin durant" -> "2014 NBA MVP, 2x NBA Champion, 2x Finals MVP. 2008-09 marked his standout sophomore campaign following his Rookie of the Year season.";
            case "chris paul" -> "12x All-Star, 11x All-NBA. Finished runner-up for 2008 MVP, leading the NBA in assists (11.6 APG) and steals (2.7 SPG) for New Orleans.";
            case "paul pierce" -> "2008 NBA Champion and 2008 Finals MVP with the Boston Celtics, 10x All-Star known as 'The Truth'.";
            case "kevin garnett" -> "2008 NBA Champion and 2008 Defensive Player of the Year with Boston, 2004 NBA MVP, 15x All-Star.";
            case "ray allen" -> "2x NBA Champion (2008 Celtics, 2013 Heat), 10x All-Star, one of the greatest 3-point shooters in NBA history.";
            case "vince carter" -> "8x All-Star, 2000 Slam Dunk Champion. High-flying superstar for the New Jersey Nets during the 2008 season.";
            case "tracy mcgrady" -> "7x All-Star, 2x NBA Scoring Champion, Hall of Fame swingman for the Houston Rockets and Magic.";
            case "deron williams" -> "3x All-Star, 2x All-NBA point guard, led the Utah Jazz to consecutive Western Conference playoff appearances.";
            case "brandon roy" -> "2007 NBA Rookie of the Year, 3x All-Star franchise guard for the Portland Trail Blazers.";
            case "baron davis" -> "2x All-Star point guard who led the 'We Believe' Warriors before joining the Los Angeles Clippers in 2008.";
            case "derek fisher" -> "5x NBA Champion point guard with the Los Angeles Lakers, famous for clutch postseason shooting.";
            case "al horford" -> "5x All-Star, 2024 NBA Champion, 2008 All-Rookie First Team center for the Atlanta Hawks after winning back-to-back NCAA titles at Florida.";
            case "joakim noah" -> "2014 NBA Defensive Player of the Year, 2x All-Star center for the Chicago Bulls and 2x NCAA Champion at Florida.";
            case "andrew bynum" -> "2x NBA Champion starting center for the Los Angeles Lakers (2009, 2010), 2012 All-Star.";
            case "rodney stuckey" -> "2008 NBA All-Rookie Second Team guard for the Detroit Pistons.";
            case "michael cooper" -> "5x NBA Champion, 1987 NBA Defensive Player of the Year, elite perimeter defender for the 'Showtime' Lakers.";
            case "mitch kupchak" -> "3x NBA Champion player with the Lakers and Wizards, long-time General Manager who built multiple Lakers championship teams.";
            case "hakeem olajuwon" -> "2x NBA Champion, 2x Finals MVP, 1994 NBA MVP, all-time NBA leader in blocked shots. 'The Dream' for Houston.";
            case "walt frazier" -> "2x NBA Champion with the New York Knicks (1970, 1973), 7x All-Star, Hall of Fame point guard 'Clyde'.";
            case "spencer haywood" -> "1980 NBA Champion, 1970 ABA MVP & Rookie of the Year, 4x NBA All-Star, Basketball Hall of Famer.";
            case "hal greer" -> "1967 NBA Champion with the 76ers, 10x All-NBA, 10x All-Star guard, 76ers all-time leading scorer.";
            case "nate archibald", "nate \"tiny\" archibald" -> "1981 NBA Champion with Celtics, 6x All-Star. Only player in NBA history to lead the league in Scoring (34.0 PPG) and Assists (11.4 APG) in the same season (1972-73).";
            case "earl monroe", "earl \"the pearl\" monroe" -> "1973 NBA Champion with the Knicks, 1968 Rookie of the Year, 4x All-Star, 'The Pearl' of basketball showmanship.";
            case "bob lanier" -> "8x All-Star center for the Detroit Pistons and Bucks, Hall of Famer known for his dominant left-handed hook shot.";
            case "james worthy" -> "3x NBA Champion, 1988 NBA Finals MVP, 7x All-Star. 'Big Game James' for the Showtime Los Angeles Lakers.";
            case "roy hibbert" -> "2x NBA All-Star center, elite rim protector and defensive anchor for the Indiana Pacers.";
            default -> p + " is an iconic professional basketball star featured in this ultra-premium collection release.";
        };
    }

    private static String getEraContext(String season, String player, String overviewPage) {
        String p = getPrimaryPlayerName(player);
        if ("Baseball.html".equals(overviewPage) || isBaseballPlayer(p)) {
            return "MLB Baseball Autograph & Relic Era: Premium certified signatures and authentic game-used memorabilia preserved for baseball collectors.";
        }
        if ("Flawless.html".equals(overviewPage) || "Panini.html".equals(overviewPage)) {
            return "Ultra-High-End Premium Era: Featuring low-numbered parallel cards, certified signatures, and game-worn patch swatches of basketball icons.";
        }
        return getNbaEraContext(season, player);
    }

    private static String getPrimaryPlayerName(String player) {
        if (player == null) return "";
        return player.contains(",") ? player.split(",")[0].trim() : player.trim();
    }

    private static boolean isBaseballPlayer(String p) {
        if (p == null) return false;
        String l = p.toLowerCase();
        return l.contains("gagne") || l.contains("bunning") || l.contains("ozzie") ||
               l.contains("carlton") || l.contains("will clark") || l.contains("griffey");
    }

    private static String getNbaEraContext(String season, String player) {
        if (season == null) return null;
        return switch (season) {
            case "1994-95", "1994", "1995-96", "1995" -> "Mid-90s NBA Golden Era: Peak physical play, expansion teams, and Michael Jordan's first comeback.";
            case "1996-97", "1996", "1997-98", "1997" -> "Late 90s Premium Insert Craze: Introduction of high-end parallels (PMG, Rubies, Refractors).";
            case "1998-99", "1998", "1999-00", "1999" -> "Post-Jordan Transition Era: Rise of Kobe Bryant, Allen Iverson, and new young superstars.";
            case "2000-01", "2000", "2001-02", "2001", "2002-03", "2002" -> "Early 2000s Autograph & Relic Revolution: Upper Deck Exquisite and SP Authentic define modern high-end collecting.";
            case "2003-04", "2003", "2004-05", "2004", "2005-06", "2005" -> "Mid-2000s Era: Legendary 2003 draft class (LeBron, Wade, Carmelo) elevates hobby interest.";
            case "2010-11", "2010", "2011-12", "2011", "2012-13", "2012" -> "Panini Exclusive Era: Debut of Panini Flawless, National Treasures Logomans, and Immaculate Collection.";
            default -> "Modern NBA Hobby Era: Continuous innovation in card technology, serial numbering, and certified autographs.";
        };
    }

    private static String getTeamBySeason(String season) {
        if (season == null) return "Washington Bullets";
        return switch (season) {
            case "1994-95", "1994", "1995-96", "1995", "1996-97", "1996" -> "Washington Bullets";
            case "1997-98", "1997", "1998-99", "1998", "1999-00", "1999", "2000-01" -> "Washington Wizards";
            case "2001-02" -> "Dallas Mavericks";
            case "2002-03" -> "Denver Nuggets";
            case "2003-04" -> "Orlando Magic";
            case "2004-05", "2005-06", "2006-07" -> "Houston Rockets";
            case "2007-08" -> "Dallas Mavericks";
            case "2008-09" -> "Charlotte Bobcats";
            case "2009-10" -> "Portland Trail Blazers";
            case "2010-11", "2011-12", "2012-13" -> "Miami Heat";
            default -> "Washington Bullets";
        };
    }

    private static String getPrimaryPlayer(CardData c) {
        String p = c.get("Player");
        if (p == null) return "";
        if (p.contains(",")) return p.split(",")[0].trim();
        return p;
    }

    private static String formatMulti(String val) {
        if (val == null) return "";
        return val.replaceAll("\\s*,\\s*", " / ");
    }

    private static void addIfPresent(List<String> list, String value) {
        if (isValid(value)) list.add(value);
    }

    private static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty() && !value.equals("0");
    }

    private static String resolveDiskImageBase(String seasonFolder, String imageBaseName, CardData c) {
        String cacheKey = seasonFolder + ":" + imageBaseName;
        return DISK_IMAGE_CACHE.computeIfAbsent(cacheKey, k -> resolveDiskImageBaseInternal(seasonFolder, imageBaseName, c));
    }

    private static String resolveDiskImageBaseInternal(String seasonFolder, String imageBaseName, CardData c) {
        if (checkExists(seasonFolder, imageBaseName)) return imageBaseName;

        String altBase = imageBaseName.replaceAll("-sn(\\d+)-\\d+", "-sn$1");
        if (checkExists(seasonFolder, altBase)) return altBase;

        String altBaseNoZero = altBase.replaceAll("-sn0(\\d+)", "-sn$1");
        if (checkExists(seasonFolder, altBaseNoZero)) return altBaseNoZero;

        String altVariant1 = imageBaseName.replaceAll("-[^-]+-(\\d+|[A-Z0-9]+)$", "-Base-$1");
        if (checkExists(seasonFolder, altVariant1)) return altVariant1;

        String altVariant2 = imageBaseName.replaceAll("-[^-]+-[^-]+-(\\d+|[A-Z0-9]+)$", "-Base-$1");
        if (checkExists(seasonFolder, altVariant2)) return altVariant2;

        return imageBaseName;
    }

    private static boolean checkExists(String seasonFolder, String name) {
        Path pJpg = Paths.get("images", seasonFolder, name + "-front.jpg");
        Path pPng = Paths.get("images", seasonFolder, name + "-front.png");
        Path pAvif = Paths.get("output", "images", seasonFolder, name + "-front.avif");
        return Files.exists(pJpg) || Files.exists(pPng) || Files.exists(pAvif);
    }

    private static String cleanFilename(String text) {
        if (text == null) return "";
        return text.replace("'", "")
                .replaceAll("[^a-zA-Z0-9\\-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}