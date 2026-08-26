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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * High-performance Card Detail Page generator using Java Virtual Threads and FreeMarker.
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

    /**
     * Preserved as a static class subclassing CardData for backwards compatibility.
     */
    public static class CardData extends de.maulmann.CardData {
        public CardData(CardJson c, String uniqueId) {
            super(c, uniqueId);
        }
    }

    /**
     * Preserved as a static class subclassing CardIndex for backwards compatibility.
     */
    public static class CardIndex extends de.maulmann.CardIndex {
        public CardIndex(List<de.maulmann.CardData> allCards) {
            super(allCards);
        }
    }

    // Cache for CardData objects to avoid redundant MD5 hash computation across multiple passes
    private static final ConcurrentHashMap<String, de.maulmann.CardData> CARD_DATA_CACHE = new ConcurrentHashMap<>();

    /**
     * Returns a cached CardData for the given CardJson, avoiding redundant attribute mapping and MD5 hashing.
     */
    public static de.maulmann.CardData computeCardData(CardJson c) {
        String fingerprint = (c.id() != null ? c.id() : "") + "|" +
                (c.player() != null ? c.player() : "") + "|" +
                (c.season() != null ? c.season() : "") + "|" +
                (c.brand() != null ? c.brand() : "") + "|" +
                (c.variant() != null ? c.variant() : "") + "|" +
                (c.cardNumber() != null ? c.cardNumber() : "") + "|" +
                (c.serialNumber() != null ? c.serialNumber() : "") + "|" +
                (c.gradingCompany() != null ? c.gradingCompany() : "") + "|" +
                (c.grade() != null ? c.grade() : "") + "|" +
                (c.certNumber() != null ? c.certNumber() : "");
        return CARD_DATA_CACHE.computeIfAbsent(fingerprint, k -> new CardData(c, null));
    }

    private static final Pattern PATTERN_SPACES = Pattern.compile("\\s+");
    private static final Pattern PATTERN_ALT_BASE = Pattern.compile("-sn(-?\\d+)-\\d+");
    private static final Pattern PATTERN_ALT_BASE_NO_ZERO = Pattern.compile("-sn(-?)0(\\d+)");
    private static final Pattern PATTERN_ALT_BASE_NEG = Pattern.compile("-sn-(\\d+)");
    private static final Pattern PATTERN_ALT_VAR1 = Pattern.compile("-[^-]+-(\\d+|[A-Z0-9]+)$");
    private static final Pattern PATTERN_ALT_VAR2 = Pattern.compile("-[^-]+-[^-]+-(\\d+|[A-Z0-9]+)$");

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

    public static List<de.maulmann.CardData> run() {
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

        List<de.maulmann.CardData> allProcessedCards = new ArrayList<>();

        List<CardJson> jsonCards = CardDataLoader.loadCardsFromJson("content/json/cards.json");
        if (!jsonCards.isEmpty()) {
            log.info("Generating Juwan Howard card pages from content/json/cards.json ({} cards)...", jsonCards.size());
            List<de.maulmann.CardData> juwanCards = new ArrayList<>();
            for (CardJson c : jsonCards) {
                juwanCards.add(new CardData(c, null));
            }
            List<de.maulmann.CardData> filteredJuwanCards = filterDuplicateCards(juwanCards, "content/json/cards.json");
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
                List<de.maulmann.CardData> cardDataList = new ArrayList<>();
                for (CardJson c : cards) {
                    cardDataList.add(new CardData(c, null));
                }
                List<de.maulmann.CardData> filtered = filterDuplicateCards(cardDataList, jsonPath);
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

    private static void cleanOrphanedCardFiles(List<de.maulmann.CardData> validCards) {
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

    private static List<de.maulmann.CardData> filterDuplicateCards(List<de.maulmann.CardData> rawCards, String sourceName) {
        List<de.maulmann.CardData> filteredCards = new ArrayList<>();
        Set<String> seenFingerprints = new HashSet<>();

        duplicateLog.add("\n--- From " + sourceName + " ---");

        for (de.maulmann.CardData card : rawCards) {
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
        return de.maulmann.CardData.generateStableId(attributes);
    }

    private static void generateSubPagesMultithreaded(List<de.maulmann.CardData> allCards, String overviewPage) {
        Set<String> rareCardIds = allCards.stream()
                .filter(CardMetadataRenderer::isRareParallel)
                .map(c -> c.stableId)
                .collect(Collectors.toSet());

        de.maulmann.CardIndex cardIndex = new de.maulmann.CardIndex(allCards);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < allCards.size(); i++) {
                final int index = i;
                final de.maulmann.CardData currentCard = allCards.get(index);
                final de.maulmann.CardData prevCard = (index > 0) ? allCards.get(index - 1) : null;
                final de.maulmann.CardData nextCard = (index < allCards.size() - 1) ? allCards.get(index + 1) : null;

                executor.submit(() -> {
                    try {
                        Path seasonFolder = Paths.get(BASE_FOLDER, currentCard.seasonFolder);
                        if (!Files.exists(seasonFolder)) {
                            Files.createDirectories(seasonFolder);
                        }
                        Path subPagePath = seasonFolder.resolve(currentCard.filename);
                        createSubPage(currentCard, subPagePath, prevCard, nextCard, cardIndex, overviewPage, rareCardIds);
                    } catch (Exception e) {
                        log.error("Failed to generate subpage for card at index " + index, e);
                    }
                });
            }
        }
    }

    private static void createSubPage(de.maulmann.CardData c, Path path, de.maulmann.CardData prev, de.maulmann.CardData next, de.maulmann.CardIndex cardIndex, String overviewPage, Set<String> rareCardIds) {
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

        CardSchemaGenerator.CachedRatingData cachedRating = CardSchemaGenerator.getCachedRating(c);
        data.put("initialRatingCount", cachedRating.ratingCount());
        data.put("initialRatingAverage", String.format(Locale.US, "%.1f", cachedRating.average()));
        data.put("initialRatingPercentage", (int) Math.round(cachedRating.percentage()));

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
        boolean isLandscape = isImageLandscape(c.seasonFolder, resolvedImageBase, "front");
        int ogWidth = isLandscape ? 1680 : 1200;
        int ogHeight = isLandscape ? 1200 : 1680;
        String frontAlt = generateAltText(c, "front");

        data.put("headHtml", SharedTemplates.getHead(browserTitle, metaDesc, ROOT, c.fullRelativePath, frontImgUrl, cardPreload, ogWidth, ogHeight, frontAlt));
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
        data.put("frontImgTitle", getPrimaryPlayer(c) + " Private Collection - Front scan: " + formatMulti(c.get("Player")) + " " + c.get("Season") + " " + c.get("Brand") + " " + c.get("Variant"));
        data.put("backImgTitle", getPrimaryPlayer(c) + " Private Collection - Back scan: " + formatMulti(c.get("Player")) + " " + c.get("Season") + " " + c.get("Brand") + " " + c.get("Variant"));

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
        String primaryP = CardMetadataRenderer.getPrimaryPlayerName(c.get("Player"));
        boolean isBaseball = "Baseball.html".equals(overviewPage) || CardMetadataRenderer.isBaseballPlayer(primaryP);
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

        data.put("relatedCards", findRelatedCards(c, cardIndex, 6, rareCardIds));
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

        // Collector & Pricing Suite data
        String sparklineSvg = SvgSparklineGenerator.generateSparkline(c.priceHistory, c.stableId);
        data.put("sparklineSvg", sparklineSvg);
        data.put("hasPriceHistory", !c.priceHistory.isEmpty());
        data.put("priceHistory", c.priceHistory);
        data.put("estimatedValueFormatted", CardPricingService.formatUsd(c.estimatedValue));
        data.put("lastSoldPriceFormatted", CardPricingService.formatUsd(c.lastSoldPrice));
        data.put("purchasePriceFormatted", CardPricingService.formatUsd(c.purchasePrice));
        data.put("lastSoldDate", c.lastSoldDate != null ? c.lastSoldDate : "");

        Double growthPct = CardPricingService.calculateGrowthPct(c);
        if (growthPct != null) {
            data.put("growthPctFormatted", String.format(java.util.Locale.US, "%s%.1f%%", growthPct >= 0 ? "+" : "", growthPct));
            data.put("isGrowthPositive", growthPct >= 0);
        } else {
            data.put("growthPctFormatted", "");
            data.put("isGrowthPositive", true);
        }
        data.put("hasPricing", (c.estimatedValue != null && c.estimatedValue > 0)
                || (c.lastSoldPrice != null && c.lastSoldPrice > 0)
                || (c.purchasePrice != null && c.purchasePrice > 0)
                || (c.priceHistory != null && !c.priceHistory.isEmpty()));

        data.put("point130Url", MarketPriceFetcher.build130PointUrl(c));
        data.put("ebaySoldUrl", MarketPriceFetcher.buildEbaySoldUrl(c));
        data.put("psaAprUrl", MarketPriceFetcher.buildPsaAprUrl(c));
        data.put("fanaticsUrl", MarketPriceFetcher.buildFanaticsCollectUrl(c));

        data.put("certNumber", c.certNumber != null ? c.certNumber : "");
        data.put("verificationUrl", c.getVerificationUrl() != null ? c.getVerificationUrl() : "");
        data.put("popTotal", c.popTotal != null ? String.valueOf(c.popTotal) : "");
        data.put("popHigher", c.popHigher != null ? String.valueOf(c.popHigher) : "");
        data.put("hasPopReport", c.popTotal != null || c.popHigher != null || (c.certNumber != null && !c.certNumber.isBlank()) || c.isGraded());


        data.put("isJerseyNumber", c.isJerseyNumberMatch());
        data.put("isOneOfOne", c.isOneOfOne());
        data.put("isBookendSerial", c.isBookendSerial());
        data.put("isRefractorOrFoil", c.isRefractorOrFoil());

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

    public static String generateBrowserTitle(de.maulmann.CardData c, String overviewPage) {
        return CardMetadataRenderer.generateBrowserTitle(c, overviewPage);
    }

    public static String generateH1(de.maulmann.CardData c) {
        return CardMetadataRenderer.generateH1(c);
    }

    public static String generateH1Html(de.maulmann.CardData c) {
        return CardMetadataRenderer.generateH1Html(c);
    }

    public static String generateMetaDescription(de.maulmann.CardData c) {
        return CardMetadataRenderer.generateMetaDescription(c);
    }

    public static String generateAltText(de.maulmann.CardData c, String view) {
        return CardMetadataRenderer.generateAltText(c, view);
    }

    public static String generateAiSnapshotText(de.maulmann.CardData c) {
        return CardMetadataRenderer.generateAiSnapshotText(c);
    }

    public static List<Map<String, String>> findRelatedCards(de.maulmann.CardData target, List<de.maulmann.CardData> pool, int limit, Set<String> rareCardIds) {
        return CardMetadataRenderer.findRelatedCards(target, new de.maulmann.CardIndex(pool), limit, rareCardIds);
    }

    public static List<Map<String, String>> findRelatedCards(de.maulmann.CardData target, de.maulmann.CardIndex index, int limit, Set<String> rareCardIds) {
        return CardMetadataRenderer.findRelatedCards(target, index, limit, rareCardIds);
    }

    public static boolean isRareParallel(de.maulmann.CardData c) {
        return CardMetadataRenderer.isRareParallel(c);
    }

    public static List<Map<String, String>> generateExternalLinks(de.maulmann.CardData c) {
        return CardMetadataRenderer.generateExternalLinks(c);
    }

    public static String getSeasonHighlights(de.maulmann.CardData c, String overviewPage) {
        return CardMetadataRenderer.getSeasonHighlights(c, overviewPage, triviaManager);
    }

    public static String getEraContext(de.maulmann.CardData c, String overviewPage) {
        return CardMetadataRenderer.getEraContext(c, overviewPage, triviaManager);
    }

    public static String cleanPlayerName(String player) {
        return de.maulmann.CardData.cleanPlayerName(player);
    }

    private static String getPrimaryPlayer(de.maulmann.CardData c) {
        return CardMetadataRenderer.getPrimaryPlayer(c);
    }

    private static String formatMulti(String val) {
        return CardUtils.formatMulti(val);
    }

    private static boolean isValid(String value) {
        return CardUtils.isValidForDisplay(value);
    }

    public static String resolveDiskImageBase(String seasonFolder, String imageBaseName, de.maulmann.CardData c) {
        String cacheKey = seasonFolder + ":" + imageBaseName;
        return DISK_IMAGE_CACHE.computeIfAbsent(cacheKey, k -> resolveDiskImageBaseInternal(seasonFolder, imageBaseName, c));
    }

    private static String resolveDiskImageBaseInternal(String seasonFolder, String imageBaseName, de.maulmann.CardData c) {
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
        return isImageLandscape(seasonFolder, imageBaseName, "front");
    }

    public static boolean isImageLandscape(String seasonFolder, String imageBaseName, String side) {
        if (seasonFolder == null || imageBaseName == null) return false;
        String s = (side == null || side.isBlank()) ? "front" : side.toLowerCase();
        String cacheKey = seasonFolder + ":" + imageBaseName + ":" + s;
        return ORIENTATION_CACHE.computeIfAbsent(cacheKey, k -> {
            Path[] candidates = {
                    Paths.get("images", seasonFolder, imageBaseName + "-" + s + ".jpg"),
                    Paths.get("images", seasonFolder, imageBaseName + "-" + s + ".png"),
                    Paths.get("images", seasonFolder, imageBaseName + "-" + s + ".avif"),
                    Paths.get("output", "images", seasonFolder, imageBaseName + "-" + s + "-400w.avif")
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

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> findSameBrandCards(de.maulmann.CardData currentCard, List<? extends de.maulmann.CardData> allCards, int limit) {
        return findSameBrandCards(currentCard, new de.maulmann.CardIndex((List<de.maulmann.CardData>) allCards), limit);
    }

    public static List<Map<String, String>> findSameBrandCards(de.maulmann.CardData currentCard, de.maulmann.CardIndex index, int limit) {
        if (currentCard == null || index == null || limit <= 0) return Collections.emptyList();

        String season = currentCard.get("Season");
        String brand = currentCard.get("Brand");
        if (!isValid(brand)) return Collections.emptyList();

        List<de.maulmann.CardData> pool = index.getByBrand(brand);
        List<de.maulmann.CardData> selected = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        if (currentCard.stableId != null) addedIds.add(currentCard.stableId);

        // Pass 1: Same season & same brand
        if (isValid(season)) {
            for (de.maulmann.CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && season.equalsIgnoreCase(c.get("Season"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        // Pass 2: Fallback across other seasons if count < limit
        if (selected.size() < limit) {
            for (de.maulmann.CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId)) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (de.maulmann.CardData c : selected) {
            String title = formatShowcaseCardTitle(c);
            String url = getRelativeCardUrl(currentCard, c);
            result.add(Map.of("title", title, "url", url, "stableId", c.stableId != null ? c.stableId : ""));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> findSameCompanyCards(de.maulmann.CardData currentCard, List<? extends de.maulmann.CardData> allCards, Set<String> excludeStableIds, int limit) {
        return findSameCompanyCards(currentCard, new de.maulmann.CardIndex((List<de.maulmann.CardData>) allCards), excludeStableIds, limit);
    }

    public static List<Map<String, String>> findSameCompanyCards(de.maulmann.CardData currentCard, de.maulmann.CardIndex index, Set<String> excludeStableIds, int limit) {
        if (currentCard == null || index == null || limit <= 0) return Collections.emptyList();

        String season = currentCard.get("Season");
        String company = currentCard.get("Company");
        if (!isValid(company)) return Collections.emptyList();

        List<de.maulmann.CardData> pool = index.getByCompany(company);
        List<de.maulmann.CardData> selected = new ArrayList<>();
        Set<String> addedIds = new HashSet<>(excludeStableIds != null ? excludeStableIds : Collections.emptySet());
        if (currentCard.stableId != null) addedIds.add(currentCard.stableId);

        // Pass 1: Same season & same company
        if (isValid(season)) {
            for (de.maulmann.CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId) && season.equalsIgnoreCase(c.get("Season"))) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        // Pass 2: Fallback across other seasons if count < limit
        if (selected.size() < limit) {
            for (de.maulmann.CardData c : pool) {
                if (selected.size() >= limit) break;
                if (c.stableId != null && !addedIds.contains(c.stableId)) {
                    selected.add(c);
                    addedIds.add(c.stableId);
                }
            }
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (de.maulmann.CardData c : selected) {
            String title = formatShowcaseCardTitle(c);
            String url = getRelativeCardUrl(currentCard, c);
            result.add(Map.of("title", title, "url", url));
        }
        return result;
    }

    private static String formatShowcaseCardTitle(de.maulmann.CardData c) {
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

    private static String getRelativeCardUrl(de.maulmann.CardData currentCard, de.maulmann.CardData targetCard) {
        if (currentCard.seasonFolder != null && currentCard.seasonFolder.equalsIgnoreCase(targetCard.seasonFolder)) {
            return targetCard.filename;
        } else {
            return "../" + targetCard.seasonFolder + "/" + targetCard.filename;
        }
    }

    public static void generateMissingImagesReport(List<de.maulmann.CardData> cards) {
        if (cards == null || cards.isEmpty()) return;

        Map<String, List<String>> missingBySeason = new TreeMap<>();
        int totalFrontMissing = 0;
        int totalBackMissing = 0;

        String[] extensions = {".avif"};

        for (de.maulmann.CardData c : cards) {
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
