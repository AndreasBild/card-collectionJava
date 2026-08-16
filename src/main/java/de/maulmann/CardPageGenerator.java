package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.Template;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
    private static final String BASE_URL = CardUtils.BASE_URL;
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

    private static final Configuration fmConfig = CardUtils.getFreeMarkerConfig();

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

    /**
     * O(1) attribute index for instant relationship and brand/company lookups across large card sets.
     */
    public static class CardIndex {
        private final Map<String, List<CardData>> brandMap = new HashMap<>();
        private final Map<String, List<CardData>> companyMap = new HashMap<>();
        private final Map<String, List<CardData>> seasonMap = new HashMap<>();
        private final Map<String, List<CardData>> playerMap = new HashMap<>();

        public CardIndex(List<CardData> allCards) {
            if (allCards == null) return;
            for (CardData c : allCards) {
                String brand = c.get("Brand");
                if (isValid(brand)) {
                    brandMap.computeIfAbsent(brand.toLowerCase(), k -> new ArrayList<>()).add(c);
                }
                String company = c.get("Company");
                if (isValid(company)) {
                    companyMap.computeIfAbsent(company.toLowerCase(), k -> new ArrayList<>()).add(c);
                }
                String season = c.get("Season");
                if (isValid(season)) {
                    seasonMap.computeIfAbsent(season.toLowerCase(), k -> new ArrayList<>()).add(c);
                }
                String player = c.get("Player");
                if (isValid(player)) {
                    playerMap.computeIfAbsent(player.toLowerCase(), k -> new ArrayList<>()).add(c);
                }
            }
        }

        public List<CardData> getByBrand(String brand) {
            if (brand == null) return Collections.emptyList();
            return brandMap.getOrDefault(brand.toLowerCase(), Collections.emptyList());
        }

        public List<CardData> getByCompany(String company) {
            if (company == null) return Collections.emptyList();
            return companyMap.getOrDefault(company.toLowerCase(), Collections.emptyList());
        }

        public List<CardData> getCandidatesForRelated(CardData target) {
            if (target == null) return Collections.emptyList();
            Set<CardData> candidates = new LinkedHashSet<>();
            String player = target.get("Player");
            if (isValid(player)) candidates.addAll(playerMap.getOrDefault(player.toLowerCase(), Collections.emptyList()));
            String season = target.get("Season");
            if (isValid(season)) candidates.addAll(seasonMap.getOrDefault(season.toLowerCase(), Collections.emptyList()));
            String brand = target.get("Brand");
            if (isValid(brand)) candidates.addAll(brandMap.getOrDefault(brand.toLowerCase(), Collections.emptyList()));
            return new ArrayList<>(candidates);
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

    private static final java.util.regex.Pattern PATTERN_CLEAN_FILENAME_CHARS = java.util.regex.Pattern.compile("[^a-zA-Z0-9\\-_]");
    private static final java.util.regex.Pattern PATTERN_CLEAN_FILENAME_HYPHENS = java.util.regex.Pattern.compile("-+");
    private static final java.util.regex.Pattern PATTERN_CLEAN_FILENAME_EDGES = java.util.regex.Pattern.compile("^-|-$");
    private static final java.util.regex.Pattern PATTERN_CLEAN_PLAYER_QUOTES = java.util.regex.Pattern.compile("[\"“„”«»'].*?[\"“„”«»']");
    private static final java.util.regex.Pattern PATTERN_SPACES = java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern PATTERN_ALT_BASE = java.util.regex.Pattern.compile("-sn(-?\\d+)-\\d+");
    private static final java.util.regex.Pattern PATTERN_ALT_BASE_NO_ZERO = java.util.regex.Pattern.compile("-sn(-?)0(\\d+)");
    private static final java.util.regex.Pattern PATTERN_ALT_BASE_NEG = java.util.regex.Pattern.compile("-sn-(\\d+)");
    private static final java.util.regex.Pattern PATTERN_ALT_VAR1 = java.util.regex.Pattern.compile("-[^-]+-(\\d+|[A-Z0-9]+)$");
    private static final java.util.regex.Pattern PATTERN_ALT_VAR2 = java.util.regex.Pattern.compile("-[^-]+-[^-]+-(\\d+|[A-Z0-9]+)$");

    private static volatile Set<String> EXISTING_IMAGE_KEYS = null;

    private static Set<String> getExistingImageKeys() {
        if (EXISTING_IMAGE_KEYS == null) {
            synchronized (CardPageGenerator.class) {
                if (EXISTING_IMAGE_KEYS == null) {
                    Set<String> keys = new HashSet<>();
                    indexImageDir(Paths.get("images"), keys);
                    indexImageDir(Paths.get("output", "images"), keys);
                    EXISTING_IMAGE_KEYS = Collections.unmodifiableSet(keys);
                }
            }
        }
        return EXISTING_IMAGE_KEYS;
    }

    private static void indexImageDir(Path dir, Set<String> keys) {
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    String rel = dir.relativize(p).toString().replace('\\', '/');
                    keys.add(rel.toLowerCase());
                });
            } catch (IOException e) {
                log.warn("Could not index images in {}: {}", dir, e.getMessage());
            }
        }
    }

    public static List<CardData> run() {
        log.info("Starting high-speed Card Page Generation...");
        long startTime = System.currentTimeMillis();
        EXISTING_IMAGE_KEYS = null; // Refresh image cache
        CardSchemaGenerator.loadRatingCache();
        IndexNowService.ensureValidationFile();
        duplicateLog.clear();
        duplicateLog.add("=== DUPLICATE CARDS LOG ===");
        duplicateLog.add("This file lists all un-numbered cards that were filtered out to prevent duplicate pages.\n");

        Path cardsDir = Paths.get(BASE_FOLDER);
        if (!Files.exists(cardsDir)) {
            try {
                Files.createDirectories(cardsDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        List<CardData> allProcessedCards = new ArrayList<>();

        List<CardJson> jsonCards = CardDataLoader.loadCardsFromJson("content/json/cards.json");
        if (!jsonCards.isEmpty()) {
            log.info("Generating Juwan Howard card pages from content/json/cards.json ({} cards)...", jsonCards.size());
            List<CardData> juwanCards = new ArrayList<>();
            for (CardJson c : jsonCards) {
                juwanCards.add(new CardData(c, null));
            }
            List<CardData> filteredJuwanCards = filterDuplicateCards(juwanCards, "content/json/cards.json");
            allProcessedCards.addAll(filteredJuwanCards);
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
                allProcessedCards.addAll(filtered);
                generateSubPagesMultithreaded(filtered, overviewPage);
            }
        }

        try {
            Path dupPath = Paths.get("output", "Duplicates.txt");
            if (dupPath.getParent() != null) {
                Files.createDirectories(dupPath.getParent());
            }
            Files.write(dupPath, duplicateLog, StandardCharsets.UTF_8);
            log.info("Saved Duplicates.txt with {} entries.", duplicateLog.size() - 4);
        } catch (IOException e) {
            log.error("Failed to write Duplicates.txt", e);
        }

        cleanOrphanedCardFiles(allProcessedCards);
        generateMissingImagesReport(allProcessedCards);

        long endTime = System.currentTimeMillis();
        log.info("All card pages generated in {} ms.", (endTime - startTime));
        return allProcessedCards;
    }

    private static void cleanOrphanedCardFiles(List<CardData> validCards) {
        Path cardsDir = Paths.get(BASE_FOLDER);
        if (!Files.exists(cardsDir)) return;
        Set<Path> validPaths = validCards.stream()
                .map(c -> Paths.get(BASE_FOLDER, c.seasonFolder, c.filename).toAbsolutePath().normalize())
                .collect(Collectors.toSet());

        try (Stream<Path> stream = Files.walk(cardsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))
                    .forEach(p -> {
                        if (!validPaths.contains(p.toAbsolutePath().normalize())) {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not clean orphaned cards: {}", e.getMessage());
        }
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
                    duplicateLog.add("[SKIPPED] " + PATTERN_SPACES.matcher(dupInfo).replaceAll(" "));
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
        // Pre-compute rare card IDs and CardIndex once for O(1) lookups in findRelatedCards/Brand/Company
        Set<String> rareCardIds = allCards.stream()
                .filter(CardPageGenerator::isRareParallel)
                .map(c -> c.stableId)
                .collect(Collectors.toSet());

        CardIndex cardIndex = new CardIndex(allCards);

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

                        createSubPage(currentCard, filePath, prevCard, nextCard, cardIndex, overviewPage, rareCardIds);
                    } catch (Exception e) {
                        log.error("Failed to generate subpage for card at index " + index, e);
                    }
                });
            }
        }
    }

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, CardIndex cardIndex, String overviewPage, Set<String> rareCardIds) {
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

        List<CardSchemaGenerator.FaqItem> faqItems = CardSchemaGenerator.computeFaqItems(c);
        String faqHtml = CardSchemaGenerator.generateFaqHtml(faqItems);
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + resolvedImageBase + "-front.avif";
        String cardPreload = "<link rel=\"preload\" as=\"image\" type=\"image/avif\" " +
                "href=\"" + frontImgPath.replace(".avif", "-400w.avif") + "\" " +
                "imagesrcset=\"" + frontImgPath.replace(".avif", "-400w.avif") + " 400w, " +
                frontImgPath.replace(".avif", "-600w.avif") + " 600w, " +
                frontImgPath.replace(".avif", "-900w.avif") + " 900w, " +
                frontImgPath + " 1200w\" " +
                "imagesizes=\"(max-width: 600px) 380px, (max-width: 1024px) 50vw, 580px\" " +
                "fetchpriority=\"high\">";
        data.put("headHtml", SharedTemplates.getHead(browserTitle, metaDesc, ROOT, c.fullRelativePath, frontImgUrl, cardPreload));
        data.put("jsonLd", CardSchemaGenerator.generateJsonLd(c, metaDesc, h1Title, overviewPage, resolvedImageBase, faqItems));
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

        List<Map<String, String>> sameBrandCards = findSameBrandCards(c, cardIndex, 6);
        Set<String> brandCardIds = sameBrandCards.stream().map(m -> m.get("stableId")).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, String>> sameCompanyCards = findSameCompanyCards(c, cardIndex, brandCardIds, 6);

        String sameBrandTitle = isValid(c.get("Season")) ? "More from " + c.get("Season") + " " + c.get("Brand") : "More from " + c.get("Brand");
        String sameCompanyTitle = isValid(c.get("Season")) ? "More from " + c.get("Season") + " " + c.get("Company") : "More from " + c.get("Company");

        data.put("sameBrandCards", sameBrandCards);
        data.put("sameBrandTitle", sameBrandTitle);
        data.put("sameCompanyCards", sameCompanyCards);
        data.put("sameCompanyTitle", sameCompanyTitle);

        data.put("relatedCards", findRelatedCards(c, cardIndex, 4, rareCardIds));
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

            if (isCardModified || !Files.exists(path)) {
                Files.writeString(path, finalHtml, StandardCharsets.UTF_8);
            }
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

    public static String generateH1(CardData c) {
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

    public static String generateH1Html(CardData c) {
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

    public static String generateMetaDescription(CardData c) {
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

        boolean hasVariant = isValid(variant) && !variant.equalsIgnoreCase("Base");
        if (hasVariant) {
            sb.append(" This is the coveted ").append(variant).append(" parallel variation");
        }

        if (isValid(printRun)) {
            if ("1".equals(printRun)) {
                if (hasVariant) {
                    sb.append(" with a printrun of 1. Masterpiece.");
                } else {
                    sb.append(" Masterpiece with a printrun of 1.");
                }
            } else {
                if (hasVariant) {
                    sb.append(" with a printrun of ").append(isValid(serial) ? serial + "/" + printRun : printRun).append(".");
                } else {
                    sb.append(" This card has a printrun of ").append(isValid(serial) ? serial + "/" + printRun : printRun).append(".");
                }
            }
        } else if (hasVariant) {
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
        return findRelatedCards(target, new CardIndex(pool), limit, rareCardIds);
    }

    private static List<Map<String, String>> findRelatedCards(CardData target, CardIndex index, int limit, Set<String> rareCardIds) {
        if (target == null || index == null || limit <= 0) return Collections.emptyList();

        List<CardData> candidates = index.getCandidatesForRelated(target);
        Map<CardData, Integer> scored = new HashMap<>();

        for (CardData c : candidates) {
            if (c.stableId != null && c.stableId.equals(target.stableId)) continue;

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

            boolean targetIsRare = rareCardIds != null && rareCardIds.contains(target.stableId);
            boolean cIsRare = rareCardIds != null && rareCardIds.contains(c.stableId);
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
            String imgBase = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder + "/" + imageBaseName + "-front";
            String thumbAvif = imgBase + "-200w.avif";
            String thumbFallback = imgBase + ".avif";

            item.put("imgBase", imgBase);
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
        String cleaned = PATTERN_CLEAN_PLAYER_QUOTES.matcher(player).replaceAll("");
        return PATTERN_SPACES.matcher(cleaned).replaceAll(" ").trim();
    }

    private static String getPrimaryPlayer(CardData c) {
        String p = c.get("Player");
        if (p == null) return "";
        if (p.contains(",")) return p.split(",")[0].trim();
        return p;
    }

    private static String formatMulti(String val) {
        return CardUtils.formatMulti(val);
    }

    private static void addIfPresent(List<String> list, String value) {
        if (isValid(value)) list.add(value);
    }

    private static boolean isValid(String value) {
        return CardUtils.isValidForDisplay(value);
    }

    public static String resolveDiskImageBase(String seasonFolder, String imageBaseName, CardData c) {
        String cacheKey = seasonFolder + ":" + imageBaseName;
        return DISK_IMAGE_CACHE.computeIfAbsent(cacheKey, k -> resolveDiskImageBaseInternal(seasonFolder, imageBaseName, c));
    }

    private static String resolveDiskImageBaseInternal(String seasonFolder, String imageBaseName, CardData c) {
        if (checkExists(seasonFolder, imageBaseName)) return imageBaseName;

        String altBase = PATTERN_ALT_BASE.matcher(imageBaseName).replaceAll("-sn$1");
        if (checkExists(seasonFolder, altBase)) return altBase;

        String altBaseNoZero = PATTERN_ALT_BASE_NO_ZERO.matcher(altBase).replaceAll("-sn$1$2");
        if (checkExists(seasonFolder, altBaseNoZero)) return altBaseNoZero;

        String altBaseNegative = PATTERN_ALT_BASE_NEG.matcher(imageBaseName).replaceAll("-sn$1");
        if (checkExists(seasonFolder, altBaseNegative)) return altBaseNegative;

        String altVariant1 = PATTERN_ALT_VAR1.matcher(imageBaseName).replaceAll("-Base-$1");
        if (checkExists(seasonFolder, altVariant1)) return altVariant1;

        String altVariant2 = PATTERN_ALT_VAR2.matcher(imageBaseName).replaceAll("-Base-$1");
        if (checkExists(seasonFolder, altVariant2)) return altVariant2;

        return imageBaseName;
    }

    private static boolean checkExists(String seasonFolder, String name) {
        Set<String> keys = getExistingImageKeys();
        String prefix = seasonFolder.toLowerCase() + "/" + name.toLowerCase() + "-front";
        return keys.contains(prefix + ".jpg") || keys.contains(prefix + ".png") || keys.contains(prefix + ".avif");
    }

    private static final Map<String, Boolean> ORIENTATION_CACHE = new ConcurrentHashMap<>();

    public static boolean isImageLandscape(String seasonFolder, String imageBaseName) {
        if (seasonFolder == null || imageBaseName == null) return false;
        String cacheKey = seasonFolder + ":" + imageBaseName;
        return ORIENTATION_CACHE.computeIfAbsent(cacheKey, k -> {
            Path[] candidates = {
                    Paths.get("images", seasonFolder, imageBaseName + "-front.jpg"),
                    Paths.get("images", seasonFolder, imageBaseName + "-front.png"),
                    Paths.get("images", seasonFolder, imageBaseName + "-front.avif"),
                    Paths.get("output", "images", seasonFolder, imageBaseName + "-front-400w.avif")
            };
            for (Path p : candidates) {
                if (Files.exists(p)) {
                    try {
                        try (var iis = javax.imageio.ImageIO.createImageInputStream(p.toFile())) {
                            if (iis != null) {
                                var readers = javax.imageio.ImageIO.getImageReaders(iis);
                                if (readers.hasNext()) {
                                    var reader = readers.next();
                                    try {
                                        reader.setInput(iis);
                                        int w = reader.getWidth(0);
                                        int h = reader.getHeight(0);
                                        return w > h;
                                    } finally {
                                        reader.dispose();
                                    }
                                }
                            }
                        }
                        var img = javax.imageio.ImageIO.read(p.toFile());
                        if (img != null) {
                            return img.getWidth() > img.getHeight();
                        }
                    } catch (Exception ignored) {}
                }
            }
            return false;
        });
    }

    private static String cleanFilename(String text) {
        if (text == null) return "";
        String s = text.replace("'", "");
        s = PATTERN_CLEAN_FILENAME_CHARS.matcher(s).replaceAll("-");
        s = PATTERN_CLEAN_FILENAME_HYPHENS.matcher(s).replaceAll("-");
        return PATTERN_CLEAN_FILENAME_EDGES.matcher(s).replaceAll("");
    }

    public static List<Map<String, String>> findSameBrandCards(CardData currentCard, List<CardData> allCards, int limit) {
        return findSameBrandCards(currentCard, new CardIndex(allCards), limit);
    }

    public static List<Map<String, String>> findSameBrandCards(CardData currentCard, CardIndex index, int limit) {
        if (currentCard == null || index == null || limit <= 0) return Collections.emptyList();

        String season = currentCard.get("Season");
        String brand = currentCard.get("Brand");
        if (!isValid(brand)) return Collections.emptyList();

        List<CardData> pool = index.getByBrand(brand);
        List<CardData> selected = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        if (currentCard.stableId != null) addedIds.add(currentCard.stableId);

        // Pass 1: Same season & same brand
        if (isValid(season)) {
            for (CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && season.equalsIgnoreCase(c.get("Season"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        // Pass 2: Fallback across other seasons if count < limit
        if (selected.size() < limit) {
            for (CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId)) {
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
        return findSameCompanyCards(currentCard, new CardIndex(allCards), excludeStableIds, limit);
    }

    public static List<Map<String, String>> findSameCompanyCards(CardData currentCard, CardIndex index, Set<String> excludeStableIds, int limit) {
        if (currentCard == null || index == null || limit <= 0) return Collections.emptyList();

        String season = currentCard.get("Season");
        String company = currentCard.get("Company");
        if (!isValid(company)) return Collections.emptyList();

        List<CardData> pool = index.getByCompany(company);
        List<CardData> selected = new ArrayList<>();
        Set<String> addedIds = new HashSet<>(excludeStableIds != null ? excludeStableIds : Collections.emptySet());
        if (currentCard.stableId != null) addedIds.add(currentCard.stableId);

        // Pass 1: Same season & same company
        if (isValid(season)) {
            for (CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && season.equalsIgnoreCase(c.get("Season"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        // Pass 2: Fallback across other seasons if count < limit
        if (selected.size() < limit) {
            for (CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId)) {
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

    public static void generateMissingImagesReport(List<CardData> cards) {
        if (cards == null || cards.isEmpty()) return;

        Map<String, List<String>> missingBySeason = new TreeMap<>();
        int totalFrontMissing = 0;
        int totalBackMissing = 0;

        String[] extensions = {".avif"};

        for (CardData c : cards) {
            String seasonFolder = c.seasonFolder != null ? c.seasonFolder : "Unknown_Season";
            String rawImageBase = c.filenameBase.contains("-") ? c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-")) : c.filenameBase;
            String resolvedImageBase = resolveDiskImageBase(seasonFolder, rawImageBase, c);

            boolean frontExists = checkSideImageExists(seasonFolder, resolvedImageBase, "front", extensions);
            boolean backExists = checkSideImageExists(seasonFolder, resolvedImageBase, "back", extensions);

            if (!frontExists || !backExists) {
                String player = c.get("Player");
                String season = c.get("Season");
                String brand = c.get("Brand");
                String variant = c.get("Variant");
                String num = c.get("Number");
                String serial = c.get("Serial");

                StringBuilder desc = new StringBuilder();
                if (isValid(player)) desc.append(player);
                if (isValid(season)) desc.append(" - ").append(season);
                if (isValid(brand)) desc.append(" ").append(brand);
                if (isValid(variant) && !variant.equalsIgnoreCase("Base")) desc.append(" ").append(variant);
                if (isValid(num)) desc.append(" #").append(num);
                if (isValid(serial) && !serial.equals("0")) desc.append(" (sn").append(serial.replace("#", "").replace("/", "-")).append(")");

                List<String> seasonList = missingBySeason.computeIfAbsent(seasonFolder, k -> new ArrayList<>());

                if (!frontExists) {
                    totalFrontMissing++;
                    seasonList.add("[MISSING FRONT] " + desc + " (Expected: " + resolvedImageBase + "-front)");
                }
                if (!backExists) {
                    totalBackMissing++;
                    seasonList.add("[MISSING BACK]  " + desc + " (Expected: " + resolvedImageBase + "-back)");
                }
            }
        }

        List<String> reportLines = new ArrayList<>();
        reportLines.add("================================================================================");
        reportLines.add("MISSING IMAGES SUMMARY REPORT");
        reportLines.add("Total Missing Entries: " + (totalFrontMissing + totalBackMissing) + " (Front: " + totalFrontMissing + ", Back: " + totalBackMissing + ")");
        reportLines.add("================================================================================");
        reportLines.add("");

        for (Map.Entry<String, List<String>> entry : missingBySeason.entrySet()) {
            reportLines.add("--- SEASON: " + entry.getKey() + " (" + entry.getValue().size() + " missing) ---");
            reportLines.addAll(entry.getValue());
            reportLines.add("");
        }

        try {
            Path outPath = Paths.get("output", "MissingImages.txt");
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            Path rootPath = Paths.get("MissingImages.txt");
            Files.write(outPath, reportLines, StandardCharsets.UTF_8);
            Files.write(rootPath, reportLines, StandardCharsets.UTF_8);
            log.info("Saved MissingImages.txt with {} entries across {} seasons.",
                    (totalFrontMissing + totalBackMissing), missingBySeason.size());
        } catch (IOException e) {
            log.error("Failed to write MissingImages.txt", e);
        }
    }

    private static boolean checkSideImageExists(String seasonFolder, String imageBaseName, String side, String[] extensions) {
        Set<String> keys = getExistingImageKeys();
        String prefix = seasonFolder.toLowerCase() + "/" + imageBaseName.toLowerCase() + "-" + side.toLowerCase();
        for (String ext : extensions) {
            if (keys.contains(prefix + ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}