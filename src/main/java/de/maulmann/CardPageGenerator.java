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
    private static final TriviaManager triviaManager = TriviaManager.getInstance();
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
            if (c.player != null) {
                this.attributes.put("Player", c.player);
            } else if (c.collection != null && !c.collection.trim().isEmpty()) {
                this.attributes.put("Player", c.collection);
            }
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
            if (isValid(serial) && !serial.equals("0")) {
                String cleanSerial = serial.replace("#", "").replace("/", "-");
                filenameTokens.add("sn" + cleanSerial);
            }

            String gradingCo = attributes.get("Grading Co.");
            if (isValid(gradingCo)) filenameTokens.add(gradingCo);

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

    // Cache for CardData objects to avoid redundant MD5 hash computation across multiple passes
    private static final ConcurrentHashMap<String, CardData> CARD_DATA_CACHE = new ConcurrentHashMap<>();

    /**
     * Returns a cached CardData for the given CardJson, avoiding redundant attribute mapping and MD5 hashing.
     * Used by FileGenerator to get path info without reconstructing CardData for each pass.
     */
    public static CardData computeCardData(CardJson c) {
        String fingerprint = (c.player != null ? c.player : "") + "|" +
                (c.season != null ? c.season : "") + "|" +
                (c.brand != null ? c.brand : "") + "|" +
                (c.variant != null ? c.variant : "") + "|" +
                (c.cardNumber != null ? c.cardNumber : "") + "|" +
                (c.serialNumber != null ? c.serialNumber : "") + "|" +
                (c.gradingCompany != null ? c.gradingCompany : "") + "|" +
                (c.grade != null ? c.grade : "");
        return CARD_DATA_CACHE.computeIfAbsent(fingerprint, k -> new CardData(c, null));
    }

    public static void run() {
        log.info("Starting high-speed Card Page Generation...");
        long startTime = System.currentTimeMillis();
        CardSchemaGenerator.loadRatingCache();
        IndexNowService.ensureValidationFile();
        duplicateLog.clear();
        duplicateLog.add("=== DUPLICATE CARDS LOG ===");
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
        // Pre-compute rare card IDs once to avoid redundant isRareParallel() calls in O(n²) findRelatedCards
        Set<String> rareCardIds = allCards.stream()
                .filter(CardPageGenerator::isRareParallel)
                .map(c -> c.stableId)
                .collect(Collectors.toSet());

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

                        createSubPage(currentCard, filePath, prevCard, nextCard, allCards, overviewPage, rareCardIds);
                    } catch (Exception e) {
                        log.error("Failed to generate subpage for card at index " + index, e);
                    }
                });
            }
        }
    }

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards, String overviewPage, Set<String> rareCardIds) {
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
        data.put("h1Html", generateH1Html(c));
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
        String serialDisplay = "—";
        if (isValid(combined)) {
            serialDisplay = combined;
        } else if (c.has("Serial") || c.has("Print Run")) {
            serialDisplay = (c.has("Serial") ? c.get("Serial") : "—") + " / " + (c.has("Print Run") ? c.get("Print Run") : "—");
        }
        data.put("serialDisplay", serialDisplay);

        String grading = c.get("Grading Co.") + " " + c.get("Grade");
        data.put("grading", (grading.trim().length() > 1 && !grading.trim().equals("null null")) ? grading : "");

        data.put("hobbyTrivia", triviaManager.getTrivia("hobbyTrivia", c.attributes));
        data.put("techTrivia", triviaManager.getTrivia("cardTechTrivia", c.attributes));
        data.put("playerHighlights", getSeasonHighlights(c, overviewPage));
        data.put("eraContext", getEraContext(c, overviewPage));
        String primaryP = getPrimaryPlayerName(c.get("Player"));
        boolean isBaseball = "Baseball.html".equals(overviewPage) || isBaseballPlayer(primaryP);
        data.put("eraTitle", isBaseball ? "&#x26BE; MLB Era & Pop Culture" : "&#x1F3C0; NBA Era & Pop Culture");
        data.put("cardBackText", "");

        List<Map<String, String>> sameBrandCards = findSameBrandCards(c, allCards, 6);
        Set<String> brandCardIds = sameBrandCards.stream().map(m -> m.get("stableId")).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, String>> sameCompanyCards = findSameCompanyCards(c, allCards, brandCardIds, 6);

        String sameBrandTitle = isValid(c.get("Season")) ? "More from " + c.get("Season") + " " + c.get("Brand") : "More from " + c.get("Brand");
        String sameCompanyTitle = isValid(c.get("Season")) ? "More from " + c.get("Season") + " " + c.get("Company") : "More from " + c.get("Company");

        data.put("sameBrandCards", sameBrandCards);
        data.put("sameBrandTitle", sameBrandTitle);
        data.put("sameCompanyCards", sameCompanyCards);
        data.put("sameCompanyTitle", sameCompanyTitle);

        data.put("relatedCards", findRelatedCards(c, allCards, 4, rareCardIds));
        data.put("externalLinks", generateExternalLinks(c));

        data.put("faqHtml", faqHtml);
        data.put("firebaseConfig", firebaseConfigManager.getConfig());

        String fullCardUrl = BASE_URL + "/cards/" + c.seasonFolder + "/" + c.filename;
        String fullImageUrl = BASE_URL + "/" + frontImgPath.replace("../../", "");
        String bbCode = "[url=" + fullCardUrl + "][img]" + fullImageUrl + "[/img][/url]\n[b]" + h1Title + "[/b]";
        String markdownCode = "[![" + h1Title + "](" + fullImageUrl + ")](" + fullCardUrl + ")";

        data.put("fullCardUrl", fullCardUrl);
        data.put("fullImageUrl", fullImageUrl);
        data.put("bbCode", bbCode);
        data.put("markdownCode", markdownCode);
        data.put("cardStableId", c.stableId);

        try {
            Template template = fmConfig.getTemplate("card-detail.ftlh");
            StringWriter sw = new StringWriter();
            template.process(data, sw);
            String finalHtml = sw.toString();

            boolean isCardModified = true;
            if (timestampTracker != null && finalHtml.contains("[[STABLE_TIME]]")) {
                String relativeOutputPath = Paths.get("output").toUri().relativize(path.toUri()).getPath();
                isCardModified = timestampTracker.isModified(relativeOutputPath, finalHtml);
                String stableTime = timestampTracker.getStableTimestamp(relativeOutputPath, finalHtml);
                finalHtml = finalHtml.replace("[[STABLE_TIME]]", stableTime);
            }

            if (finalHtml.contains("{{CONSENT_BANNER}}")) {
                finalHtml = finalHtml.replace("{{CONSENT_BANNER}}", SharedTemplates.getConsentBanner(ROOT));
            }

            Files.writeString(path, finalHtml, StandardCharsets.UTF_8);
            if (isCardModified) {
                IndexNowService.queueUrl(fullCardUrl);
            }

        } catch (Exception e) {
            log.error("Error generating detail page for " + path, e);
        }
    }

    private static String getGradingString(CardData c) {
        String gradingCo = c.get("Grading Co.");
        String grade = c.get("Grade");
        if (isValid(gradingCo) && isValid(grade)) {
            return gradingCo + "-" + grade;
        } else if (isValid(gradingCo)) {
            return gradingCo;
        } else if (isValid(grade)) {
            return grade;
        }
        return "";
    }

    static String generateBrowserTitle(CardData c, String overviewPage) {
        String player = getPrimaryPlayer(c);
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String brand = c.get("Brand");
        String season = c.get("Season");
        String variant = c.get("Variant");
        String gradingStr = getGradingString(c);

        StringBuilder sb = new StringBuilder();
        sb.append(player).append(" ").append(season).append(" ").append(brand);
        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number);
        if (!gradingStr.isEmpty()) {
            sb.append(" ").append(gradingStr);
        }
        sb.append(" | ").append(player).append(" Private Collection");
        return sb.toString();
    }

    static String generateH1(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String gradingStr = getGradingString(c);

        StringBuilder sb = new StringBuilder();
        sb.append(player).append(" | ").append(season).append(" ").append(brand);
        if (isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" ").append(theme);
        }
        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number);
        if (!gradingStr.isEmpty()) {
            sb.append(" ").append(gradingStr);
        }
        return sb.toString();
    }

    static String generateH1Html(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String gradingStr = getGradingString(c);

        StringBuilder sb = new StringBuilder();
        sb.append("<span class=\"player-name\">").append(player).append("</span><br>");
        sb.append("<span class=\"sub-title\">").append(season).append(" ").append(brand);
        if (isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" ").append(theme);
        }
        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number).append("</span>");
        if (!gradingStr.isEmpty()) {
            sb.append("<br><span class=\"sub-title grading-subtitle\">").append(gradingStr).append("</span>");
        }
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
        String serial = c.get("Serial");
        String printRun = c.get("Print Run");

        StringBuilder sb = new StringBuilder();
        sb.append("This ").append(season).append(" ").append(company).append(" ").append(brand);

        if (isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" (").append(theme).append(")");
        }

        sb.append(" card features ").append(player).append(" during his tenure with the ").append(team).append(".");

        if (isValid(number) && !number.equals("-")) {
            sb.append(" Card number #").append(number).append(".");
        }

        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" This is the coveted ").append(variant).append(" parallel variation");
        }

        if (isValid(printRun)) {
            if ("1".equals(printRun)) {
                if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
                    sb.append(" with a printrun of 1. Masterpiece.");
                } else {
                    sb.append(" Masterpiece with a printrun of 1.");
                }
            } else {
                if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
                    sb.append(" with a printrun of ").append(isValid(serial) ? serial + "/" + printRun : printRun).append(".");
                } else {
                    sb.append(" This card has a printrun of ").append(isValid(serial) ? serial + "/" + printRun : printRun).append(".");
                }
            }
        } else if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(".");
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(" Includes official certified autograph.");
        }

        if (c.has("Memorabilia") && c.get("Memorabilia").equalsIgnoreCase("Yes")) {
            sb.append(" Contains authentic game-used memorabilia patch.");
        }

        return sb.toString();
    }

    private static List<Map<String, String>> findRelatedCards(CardData target, List<CardData> pool, int limit, Set<String> rareCardIds) {
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

            boolean targetIsRare = rareCardIds.contains(target.stableId);
            boolean cIsRare = rareCardIds.contains(c.stableId);
            if (targetIsRare && cIsRare) score += 7;

            scored.put(c, score);
        }

        List<CardData> top = scored.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue());
                    if (cmp != 0) return cmp;
                    if (e1.getKey().stableId != null && e2.getKey().stableId != null) {
                        return e1.getKey().stableId.compareTo(e2.getKey().stableId);
                    }
                    return e1.getKey().filename.compareTo(e2.getKey().filename);
                })
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
        String cleanPlayer = cleanPlayerName(primaryPlayer);
        String season = c.get("Season");
        String brand = c.get("Brand");
        String variant = c.get("Variant");
        String number = c.get("Number");

        String ebayQuery = cleanPlayer + " " + season + " " + brand + " " + (isValid(variant) && !variant.equals("Base") ? variant : "") + " " + (isValid(number) ? "#" + number : "");
        String ebayUrl = "https://www.ebay.com/sch/i.html?_nkw=" + ebayQuery.trim().replace(" ", "+");
        links.add(Map.of("name", "Similar cards on eBay", "url", ebayUrl, "icon", "ebay"));

        String bkpQuery = cleanPlayer + " " + season + " " + brand;
        String bkpUrl = "https://www.beckett.com/search?q=" + bkpQuery.trim().replace(" ", "+");
        links.add(Map.of("name", "Beckett Checklist", "url", bkpUrl, "icon", "beckett"));

        if (cleanPlayer.equalsIgnoreCase("Juwan Howard")) {
            links.add(Map.of("name", "Juwan Howard Career Stats", "url", "https://www.basketball-reference.com/players/h/howarju01.html", "icon", "bref"));
        } else {
            String wikiUrl = "https://en.wikipedia.org/wiki/" + cleanPlayer.replace(" ", "_");
            links.add(Map.of("name", " Wikipedia Profile", "url", wikiUrl, "icon", "wiki"));
        }

        return links;
    }



    private static String getSeasonHighlights(CardData c, String overviewPage) {
        String triviaText = triviaManager.getTrivia("playerHighlights", c.attributes);
        if (triviaText != null && !triviaText.trim().isEmpty()) {
            return triviaText;
        }

        String p = getPrimaryPlayerName(c.get("Player"));
        if ("Baseball.html".equals(overviewPage) || isBaseballPlayer(p)) {
            return p + " is an iconic Major League Baseball player featured in this premium sports memorabilia release.";
        }
        return p + " is an iconic professional basketball star featured in this ultra-premium collection release.";
    }

    private static String getEraContext(CardData c, String overviewPage) {
        String p = getPrimaryPlayerName(c.get("Player"));
        if ("Baseball.html".equals(overviewPage) || isBaseballPlayer(p)) {
            return "MLB Baseball Autograph & Relic Era: Premium certified signatures and authentic game-used memorabilia preserved for baseball collectors.";
        }
        if ("Flawless.html".equals(overviewPage) || "Panini.html".equals(overviewPage)) {
            return "Ultra-High-End Premium Era: Featuring low-numbered parallel cards, certified signatures, and game-worn patch swatches of basketball icons.";
        }

        String triviaText = triviaManager.getTrivia("eraContext", c.attributes);
        if (triviaText != null && !triviaText.trim().isEmpty()) {
            return triviaText;
        }

        return "Modern NBA Hobby Era: Continuous innovation in card technology, serial numbering, and certified autographs.";
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

    public static String cleanPlayerName(String player) {
        if (player == null || player.trim().isEmpty()) return "";
        String cleaned = player.replaceAll("[\"“„”«»'].*?[\"“„”«»']", "");
        return cleaned.replaceAll("\\s+", " ").trim();
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
        return value != null && !value.trim().isEmpty() && !value.equals("0") && !value.equals("-") && !value.equals("—");
    }

    public static String resolveDiskImageBase(String seasonFolder, String imageBaseName, CardData c) {
        String cacheKey = seasonFolder + ":" + imageBaseName;
        return DISK_IMAGE_CACHE.computeIfAbsent(cacheKey, k -> resolveDiskImageBaseInternal(seasonFolder, imageBaseName, c));
    }

    private static String resolveDiskImageBaseInternal(String seasonFolder, String imageBaseName, CardData c) {
        if (checkExists(seasonFolder, imageBaseName)) return imageBaseName;

        String altBase = imageBaseName.replaceAll("-sn(-?\\d+)-\\d+", "-sn$1");
        if (checkExists(seasonFolder, altBase)) return altBase;

        String altBaseNoZero = altBase.replaceAll("-sn(-?)0(\\d+)", "-sn$1$2");
        if (checkExists(seasonFolder, altBaseNoZero)) return altBaseNoZero;

        String altBaseNegative = imageBaseName.replaceAll("-sn-(\\d+)", "-sn$1");
        if (checkExists(seasonFolder, altBaseNegative)) return altBaseNegative;

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

    public static List<Map<String, String>> findSameBrandCards(CardData currentCard, List<CardData> allCards, int limit) {
        if (currentCard == null || allCards == null || limit <= 0) return Collections.emptyList();

        String season = currentCard.get("Season");
        String brand = currentCard.get("Brand");
        if (!isValid(brand)) return Collections.emptyList();

        List<CardData> selected = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        if (currentCard.stableId != null) addedIds.add(currentCard.stableId);

        // Pass 1: Same season & same brand
        if (isValid(season)) {
            for (CardData c : allCards) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && season.equalsIgnoreCase(c.get("Season")) && brand.equalsIgnoreCase(c.get("Brand"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        // Pass 2: Fallback across other seasons if count < limit
        if (selected.size() < limit) {
            for (CardData c : allCards) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && brand.equalsIgnoreCase(c.get("Brand"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (CardData c : selected) {
            String title = formatShowcaseCardTitle(c);
            String url = getRelativeCardUrl(currentCard, c);
            result.add(Map.of("title", title, "url", url, "stableId", c.stableId != null ? c.stableId : ""));
        }
        return result;
    }

    public static List<Map<String, String>> findSameCompanyCards(CardData currentCard, List<CardData> allCards, Set<String> excludeStableIds, int limit) {
        if (currentCard == null || allCards == null || limit <= 0) return Collections.emptyList();

        String season = currentCard.get("Season");
        String company = currentCard.get("Company");
        if (!isValid(company)) return Collections.emptyList();

        List<CardData> selected = new ArrayList<>();
        Set<String> addedIds = new HashSet<>(excludeStableIds != null ? excludeStableIds : Collections.emptySet());
        if (currentCard.stableId != null) addedIds.add(currentCard.stableId);

        // Pass 1: Same season & same company
        if (isValid(season)) {
            for (CardData c : allCards) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && season.equalsIgnoreCase(c.get("Season")) && company.equalsIgnoreCase(c.get("Company"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        // Pass 2: Fallback across other seasons if count < limit
        if (selected.size() < limit) {
            for (CardData c : allCards) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && company.equalsIgnoreCase(c.get("Company"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (CardData c : selected) {
            String title = formatShowcaseCardTitle(c);
            String url = getRelativeCardUrl(currentCard, c);
            result.add(Map.of("title", title, "url", url));
        }
        return result;
    }

    private static String formatShowcaseCardTitle(CardData c) {
        String cleanPlayer = cleanPlayerName(getPrimaryPlayer(c));
        String seasonText = isValid(c.get("Season")) ? c.get("Season") : "";
        String brandText = isValid(c.get("Brand")) ? c.get("Brand") : "";
        String variantText = isValid(c.get("Variant")) ? c.get("Variant") : "";
        String printRunText = isValid(c.get("Print Run")) ? "(/" + c.get("Print Run") + ")" : "";
        if ("1".equals(c.get("Print Run")) || "1/1".equalsIgnoreCase(c.get("Serial"))) {
            printRunText = "(1/1)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(cleanPlayer);
        if (!seasonText.isEmpty()) sb.append(" ").append(seasonText);
        if (!brandText.isEmpty()) sb.append(" ").append(brandText);
        if (!variantText.isEmpty() && !variantText.equalsIgnoreCase("Base")) sb.append(" ").append(variantText);
        if (!printRunText.isEmpty()) sb.append(" ").append(printRunText);
        return sb.toString().trim();
    }

    private static String getRelativeCardUrl(CardData currentCard, CardData targetCard) {
        if (currentCard.seasonFolder != null && currentCard.seasonFolder.equalsIgnoreCase(targetCard.seasonFolder)) {
            return targetCard.filename;
        } else {
            return "../" + targetCard.seasonFolder + "/" + targetCard.filename;
        }
    }
}