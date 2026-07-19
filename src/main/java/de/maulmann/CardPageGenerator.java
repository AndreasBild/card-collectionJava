package de.maulmann;

import org.jsoup.Jsoup;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import java.io.StringWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Comparator;
import java.util.stream.Stream;

public class CardPageGenerator {

    private static final String BASE_FOLDER = "output/cards";
    private static final String RELATIVE_IMAGES_PATH = "../../images";
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final Logger log = LoggerFactory.getLogger(CardPageGenerator.class);
    public static final String ROOT = "../../";

    private static final List<String> duplicateLog = new ArrayList<>();
    private static final TriviaManager triviaManager = new TriviaManager();
    private static final FirebaseConfigManager firebaseConfigManager = new FirebaseConfigManager();
    private static TimestampTracker timestampTracker;

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

    static class CardData {
        Map<String, String> attributes;
        String stableId;
        String filenameBase;
        String filename;
        String seasonFolder;
        String fullRelativePath;

        public CardData(Map<String, String> attributes, String uniqueId) {
            this.attributes = new HashMap<>(attributes);
            this.stableId = uniqueId;

            String currentTeam = this.attributes.get("Team");
            if (!isValid(currentTeam)) {
                String player = this.attributes.get("Player");
                if (player != null && player.startsWith("Juwan Howard")) {
                    String calculatedTeam = getTeamBySeason(this.attributes.get("Season"));
                    this.attributes.put("Team", calculatedTeam);
                }
            }
            calculatePaths(uniqueId);
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
                filenameTokens.add("sn" + serial.replace("#", "").replace("/", "-"));
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
            return attributes.getOrDefault(key, "").trim();
        }

        public boolean has(String key) {
            String val = get(key);
            return isValid(val) && !val.equalsIgnoreCase("No") && !val.equalsIgnoreCase("None");
        }
    }

    public static void run() {
        log.info("Starting high-speed Card Page Generation...");
        long startTime = System.currentTimeMillis();

        duplicateLog.clear();
        duplicateLog.add("FILTERED DUPLICATES LOG");
        duplicateLog.add("=======================");
        duplicateLog.add("These un-numbered base/insert cards were skipped during HTML generation because a duplicate already exists in the collection.\n");

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

        processCollection("output/Juwan-Howard-Collection.html", "output/Juwan-Howard-Collection.html", "Juwan-Howard-Collection.html");

        String[] otherFiles = {
                "output/Baseball.html",
                "output/Flawless.html",
                "output/Wantlist.html",
                "output/Panini.html"
        };

        for (String filePath : otherFiles) {
            processCollection(filePath, filePath, new File(filePath).getName());
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

    private static void processCollection(String inputPath, String outputPath, String overviewPage) {
        try {
            File input = new File(inputPath);
            if (!input.exists()) return;

            Document doc = Jsoup.parse(input, "UTF-8");
            Elements tables = doc.select("table");
            if (tables.isEmpty()) return;

            List<CardData> rawCards = new ArrayList<>();

            for (Element table : tables) {
                extractTableDataAndUpdateDom(table, rawCards);
            }

            if (rawCards.isEmpty()) return;

            List<CardData> filteredCards = new ArrayList<>();
            Set<String> seenFingerprints = new HashSet<>();

            duplicateLog.add("\n--- From " + overviewPage + " ---");

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

            updateDomLinks(tables, filteredCards);

            File outIndex = new File(outputPath);
            if (outIndex.getParentFile() != null) outIndex.getParentFile().mkdirs();
            Files.writeString(outIndex.toPath(), doc.outerHtml(), StandardCharsets.UTF_8);

            generateSubPagesMultithreaded(filteredCards, overviewPage);

        } catch (IOException e) {
            log.error("Error processing collection " + inputPath, e);
        }
    }

    private static void extractTableDataAndUpdateDom(Element table, List<CardData> globalCardList) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return;

        int headerRowIndex = -1;
        String[] headers = null;

        for (int i = 0; i < rows.size(); i++) {
            Elements cells = rows.get(i).children();
            if (cells.isEmpty()) continue;
            headers = new String[cells.size()];
            for (int j = 0; j < cells.size(); j++) {
                headers[j] = cells.get(j).text().trim();
            }
            if (headers.length > 0) {
                headerRowIndex = i;
                break;
            }
        }

        if (headerRowIndex == -1) return;

        for (int i = headerRowIndex + 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.children();
            if (cols.isEmpty()) continue;

            Map<String, String> dataMap = new HashMap<>();
            for (int j = 0; j < cols.size() && j < headers.length; j++) {
                dataMap.put(headers[j], cols.get(j).text().trim());
            }

            String stableId = generateStableId(dataMap);
            CardData currentCard = new CardData(dataMap, stableId);
            globalCardList.add(currentCard);
            row.attr("data-card-id", stableId);
        }
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

    private static void updateDomLinks(Elements tables, List<CardData> filteredCards) {
        Set<String> approvedCardIds = new HashSet<>();
        for (CardData card : filteredCards) {
            approvedCardIds.add(card.stableId);
        }

        for (Element table : tables) {
            Elements rows = table.select("tr");
            int playerColIndex = 0;

            if (!rows.isEmpty()) {
                Elements headers = rows.getFirst().children();
                for (int j = 0; j < headers.size(); j++) {
                    if (headers.get(j).text().trim().equalsIgnoreCase("Player")) {
                        playerColIndex = j;
                        break;
                    }
                }
            }

            for (Element row : rows) {
                String rowId = row.attr("data-card-id");
                if (rowId.isEmpty()) continue;

                if (approvedCardIds.contains(rowId)) {
                    Elements cols = row.children();
                    if (cols.size() > playerColIndex) {
                        Element playerCell = cols.get(playerColIndex);

                        CardData matchingCard = filteredCards.stream()
                                .filter(c -> c.stableId.equals(rowId))
                                .findFirst().orElse(null);

                        if (matchingCard != null) {
                            row.attr("id", matchingCard.filenameBase);
                            String originalText = playerCell.text();
                            playerCell.empty();
                            playerCell.appendElement("a")
                                    .attr("href", matchingCard.fullRelativePath)
                                    .attr("class", "table-button")
                                    .attr("title", "View details for " + matchingCard.get("Season") + " " + matchingCard.get("Brand") + " #" + matchingCard.get("Number"))
                                    .text(originalText);
                        }
                    }
                    row.removeAttr("data-card-id");
                } else {
                    row.remove();
                }
            }
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

        String frontImgPath = seasonImgFolder + "/" + imageBaseName + "-front.webp";
        String backImgPath = seasonImgFolder + "/" + imageBaseName + "-back.webp";

        Map<String, Object> data = new HashMap<>();
        data.put("cardId", c.stableId);

        String faqHtml = generateFaqHtml(c);
        data.put("headHtml", SharedTemplates.getHead(browserTitle, metaDesc, ROOT, overviewPage, frontImgPath));
        data.put("jsonLd", generateJsonLd(c, metaDesc, h1Title, overviewPage, imageBaseName, faqHtml));
        data.put("topNavHtml", SharedTemplates.getTopNav(ROOT, "collection"));

        List<Map<String, String>> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(Map.of("name", "Home", "link", ROOT + "index.html"));
        breadcrumbItems.add(Map.of("name", "Collection", "link", ROOT + overviewPage));
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
        data.put("rookie", isValid(c.get("Rookie")) ? c.get("Rookie") : "-");
        data.put("memorabilia", isValid(c.get("Game Used")) ? c.get("Game Used") : "-");
        data.put("autograph", isValid(c.get("Autograph")) ? c.get("Autograph") : "-");

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

        data.put("hobbyTrivia", getHobbyTrivia(c));
        data.put("techTrivia", getCardTechTrivia(c));
        data.put("playerHighlights", getSeasonHighlights(c.get("Season"), c.get("Player")));
        data.put("eraContext", getNbaEraContext(c.get("Season"), c.get("Player")));
        data.put("cardBackText", "");

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
            log.error("Failed to process FreeMarker template for " + c.filename, e);
        }
    }

    private static String getHobbyTrivia(CardData c) {
        return triviaManager.getTrivia("hobbyTrivia", c.attributes);
    }

    private static String getCardTechTrivia(CardData c) {
        return triviaManager.getTrivia("techTrivia", c.attributes);
    }

    private static String getSeasonHighlights(String season, String player) {
        Map<String, String> context = new HashMap<>();
        context.put("Season", season);
        context.put("Player", player);
        return triviaManager.getTrivia("playerHighlights", context);
    }

    private static String getNbaEraContext(String season, String player) {
        Map<String, String> context = new HashMap<>();
        context.put("Season", season);
        context.put("Player", player);
        return triviaManager.getTrivia("eraContext", context);
    }

    private static String getTeamBySeason(String season) {
        if (season == null || season.isEmpty()) return "Unknown Team";
        String s = season.trim();
        if (s.startsWith("1994") || s.startsWith("1995") || s.startsWith("1996")) return "Washington Bullets";
        if (s.startsWith("1997") || s.startsWith("1998") || s.startsWith("1999") || s.startsWith("2000")) return "Washington Wizards";
        if (s.startsWith("2001")) return "Dallas Mavericks";
        if (s.startsWith("2002")) return "Denver Nuggets";
        if (s.startsWith("2003")) return "Orlando Magic";
        if (s.startsWith("2004") || s.startsWith("2005") || s.startsWith("2006")) return "Houston Rockets";
        if (s.startsWith("2007")) return "Dallas Mavericks";
        if (s.startsWith("2008")) return "Charlotte Bobcats";
        if (s.startsWith("2009")) return "Portland Trail Blazers";
        if (s.startsWith("2010") || s.startsWith("2011") || s.startsWith("2012")) return "Miami Heat";
        return "NBA";
    }

    private static String generateH1(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatMulti(c.get("Player"))).append(" ");
        sb.append(c.get("Season")).append(" ");
        sb.append(c.get("Company")).append(" ");
        sb.append(c.get("Brand")).append(" ");
        if (c.has("Theme") && !c.get("Brand").contains(c.get("Theme"))) sb.append(" ").append(c.get("Theme"));
        if (c.has("Variant")) sb.append(" ").append(c.get("Variant"));
        if (c.has("Number")) sb.append(" #").append(c.get("Number"));
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String generateBrowserTitle(CardData c, String overviewPage) {
        String player = getPrimaryPlayer(c);
        if (!isValid(player)) player = "Card";
        return generateH1(c) + " | " + player + " Private Collection";
    }

    private static String generateAltText(CardData c, String view) {
        String base = formatMulti(c.get("Player")) + " " + c.get("Season") + " " + c.get("Brand") + " #" + c.get("Number");
        if (view.equals("front")) return "Front scan of " + base + " - " + c.get("Variant") + " edition (" + formatMulti(c.get("Team")) + ") - "+getPrimaryPlayer(c) + " Collector Private Collection";
        else return "Back scan of " + base + " showing stats for " + formatMulti(c.get("Team")) + " - " +getPrimaryPlayer(c) + " Collector Private Collection";
    }

    private static String generateMetaDescription(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append("View details for the ").append(c.get("Season")).append(" ").append(c.get("Brand")).append(" ");
        sb.append(formatMulti(c.get("Player"))).append(" card #").append(c.get("Number")).append(" from our ").append(getPrimaryPlayer(c)).append(" Private Collection. ");
        if (c.has("Variant")) sb.append("Rare ").append(c.get("Variant")).append(" variant. ");
        String combinedSerial = c.get("Serial/Print Run");
        if (isValid(combinedSerial)) {
            if (combinedSerial.contains("1/1") || combinedSerial.equals("1")) sb.append("Includes 1/1 Masterpiece details. ");
            else sb.append("Serial numbered ").append(combinedSerial).append(". ");
        }
        else if (c.has("Serial")) {
            if (c.get("Serial").equals("1") && c.get("Print Run").equals("1")) sb.append("One-of-One (1/1) Masterpiece. ");
            else sb.append("Numbered ").append(c.get("Serial")).append("/").append(c.get("Print Run")).append(". ");
        }
        sb.append("A must-see for any ").append(c.get("Player")).append(" Super Collector. High-res scans and hobby history.");
        return sb.toString();
    }

    private static String generateAiSnapshotText(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append("View details for the ");
        sb.append("<strong>").append(escapeHtml(c.get("Season"))).append(" ").append(escapeHtml(c.get("Brand"))).append(" ").append(escapeHtml(formatMulti(c.get("Player")))).append("</strong>");
        sb.append(" card #").append(escapeHtml(c.get("Number"))).append(" from our ").append(escapeHtml(getPrimaryPlayer(c))).append(" Private Collection. ");

        if (c.has("Variant")) {
            sb.append("Rare <strong>").append(escapeHtml(c.get("Variant"))).append("</strong> variant. ");
        }

        String combinedSerial = c.get("Serial/Print Run");
        if (isValid(combinedSerial)) {
            if (combinedSerial.contains("1/1") || combinedSerial.equals("1")) {
                sb.append("Includes <strong>1/1 Masterpiece</strong> details. ");
            } else {
                sb.append("Serial numbered <strong>").append(escapeHtml(combinedSerial)).append("</strong>. ");
            }
        } else if (c.has("Serial")) {
            if (c.get("Serial").equals("1") && c.get("Print Run").equals("1")) {
                sb.append("One-of-One (<strong>1/1</strong>) Masterpiece. ");
            } else {
                sb.append("Numbered <strong>").append(escapeHtml(c.get("Serial"))).append("/").append(escapeHtml(c.get("Print Run"))).append("</strong>. ");
            }
        }
        sb.append("A must-see for any ").append(escapeHtml(getPrimaryPlayer(c))).append(" Super Collector. High-res scans and hobby history.");
        return sb.toString();
    }

    private static boolean isHolyGrail(CardData c) {
        String theme = c.get("Theme").toLowerCase();
        String brand = c.get("Brand").toLowerCase();
        String variant = c.get("Variant").toLowerCase();
        List<String> grails = Arrays.asList("pmg", "precious metal gems", "star rubies", "legacy collection", "masterpiece", "1/1", "essential credentials");
        return grails.stream().anyMatch(g -> theme.contains(g) || brand.contains(g) || variant.contains(g));
    }

    private static String generateFaqHtml(CardData c) {
        StringBuilder sb = new StringBuilder();
        String season = c.get("Season");
        String company = c.get("Company");
        String player = formatMulti(c.get("Player"));
        String primaryPlayer = getPrimaryPlayer(c);

        if (isHolyGrail(c)) {
            sb.append(createFaqItem("Is this a 'Holy Grail' card?", "Yes, this card belongs to one of the most prestigious parallel series in the hobby. These are extremely rare and heavily targeted by high-end investors."));
        }

        String combined = c.get("Serial/Print Run");
        if (isValid(combined)) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + combined + ", making it a strictly limited edition collectible."));
        } else if (c.has("Serial")) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + c.get("Serial") + " out of a total print run of " + c.get("Print Run") + "."));
        }

        if (c.has("Rookie")) {
            String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ?
                    "Yes, this is an official Rookie Card (RC) from " + primaryPlayer + "'s debut season, holding premium value for collectors." :
                    "No, this card was released during the " + season + " season, later in " + primaryPlayer + "'s career.";
            sb.append(createFaqItem("Is this a " + player + " Rookie Card?", rookieAns));
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(createFaqItem("Is the autograph authentic?", "Yes, this card features a manufacturer-certified autograph guaranteed by " + company + "."));
        }

        if (c.has("Grade")) {
            sb.append(createFaqItem("Is this card professionally graded?", "Yes, this card has been graded by " + c.get("Grading Co.") + " and received a condition score of " + c.get("Grade") + "."));
        }

        return sb.toString();
    }

    private static String createFaqItem(String question, String answer) {
        return "<details class=\"faq-details\">" + "<summary class=\"faq-summary\">" + escapeHtml(question) + "</summary>" + "<p class=\"faq-answer\">" + escapeHtml(answer) + "</p>" + "</details>";
    }

    private static String generateJsonLd(CardData c, String desc, String h1Title, String overviewPage, String imageBaseName, String faqHtml) {
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-front.webp";
        String backImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-back.webp";
        String cardUrl = BASE_URL + "/cards/" + c.seasonFolder + "/" + c.filename;

        StringBuilder sb = new StringBuilder();

        // --- MAIN VALID SCHEMA BLOCK ---
        sb.append("<script type=\"application/ld+json\">\n");
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@graph\": [\n");

        // 1. BreadcrumbList
        List<Map<String, String>> bcItems = new ArrayList<>();
        bcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
        bcItems.add(Map.of("name", "Collection", "link", BASE_URL + "/" + overviewPage));
        bcItems.add(Map.of("name", c.get("Season"), "link", BASE_URL + "/" + overviewPage + "#" + c.seasonFolder.toLowerCase()));
        bcItems.add(Map.of("name", h1Title, "link", cardUrl));
        sb.append(SharedTemplates.getBreadcrumbJsonLd(bcItems)).append(",\n");

        // 2. VisualArtwork
        sb.append("    {\n");
        sb.append("      \"@type\": \"VisualArtwork\",\n");
        sb.append("      \"@id\": \"").append(cardUrl).append("#artwork\",\n");
        sb.append("      \"mainEntityOfPage\": \"").append(cardUrl).append("\",\n");
        sb.append("      \"name\": \"").append(escapeJson(h1Title)).append("\",\n");
        sb.append("      \"image\": [ \"").append(frontImgUrl).append("\", \"").append(backImgUrl).append("\" ],\n");
        sb.append("      \"description\": \"").append(escapeJson(desc)).append("\",\n");
        sb.append("      \"creator\": { \"@type\": \"Organization\", \"name\": \"").append(escapeJson(c.get("Company"))).append("\" },\n");
        sb.append("      \"about\": {\n");
        sb.append("        \"@type\": \"Person\",\n");
        sb.append("        \"name\": \"").append(escapeJson(formatMulti(c.get("Player")))).append("\",\n");
        sb.append("        \"sameAs\": \"https://en.wikipedia.org/wiki/").append(escapeJson(getPrimaryPlayer(c)).replace(" ", "_")).append("\"\n");
        sb.append("      },\n");
        sb.append("      \"artMedium\": \"Trading Card\",\n");
        sb.append("      \"artform\": \"Sports Memorabilia\"\n");
        sb.append("    }");

        // 3. FAQPage (if present)
        if (faqHtml != null && !faqHtml.isEmpty()) {
            sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"@type\": \"FAQPage\",\n");
            sb.append("      \"name\": \"Frequently Asked Questions\",\n");
            sb.append("      \"mainEntity\": [\n");

            Document doc = Jsoup.parseBodyFragment(faqHtml);
            Elements details = doc.select("details");
            for (int i = 0; i < details.size(); i++) {
                Element detail = details.get(i);
                String q = detail.select("summary").text();
                String a = detail.select("p").text();
                sb.append("        {\n");
                sb.append("          \"@type\": \"Question\",\n");
                sb.append("          \"name\": \"").append(escapeJson(q)).append("\",\n");
                sb.append("          \"acceptedAnswer\": {\n");
                sb.append("            \"@type\": \"Answer\",\n");
                sb.append("            \"text\": \"").append(escapeJson(a)).append("\"\n");
                sb.append("          }\n");
                sb.append("        }");
                if (i < details.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("      ]\n");
            sb.append("    }\n");
        } else {
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("</script>\n");

        // --- DORMANT PRODUCT TEMPLATE ---
        // Hidden from Google until activated by FirestoreRatingInjector
        sb.append("<script type=\"application/json\" id=\"product-schema-template\">\n");
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@type\": \"Product\",\n");
        sb.append("  \"@id\": \"").append(cardUrl).append("#product\",\n");
        sb.append("  \"mainEntityOfPage\": \"").append(cardUrl).append("\",\n");
        sb.append("  \"name\": \"").append(escapeJson(h1Title)).append("\",\n");
        sb.append("  \"image\": [ \"").append(frontImgUrl).append("\", \"").append(backImgUrl).append("\" ],\n");
        sb.append("  \"description\": \"").append(escapeJson(desc)).append("\",\n");
        sb.append("  \"sku\": \"").append(c.stableId).append("\",\n");
        sb.append("  \"mpn\": \"").append(escapeJson(c.get("Number"))).append("\",\n");
        sb.append("  \"brand\": { \"@type\": \"Brand\", \"name\": \"").append(escapeJson(c.get("Brand"))).append("\" },\n");
        sb.append("  \"manufacturer\": { \"@type\": \"Organization\", \"name\": \"").append(escapeJson(c.get("Company"))).append("\" },\n");

        if (isHolyGrail(c)) {
            sb.append("  \"category\": \"Sports Trading Cards\",\n");
            sb.append("  \"material\": \"Premium Hobby Parallel\"\n");
        } else {
            sb.append("  \"category\": \"Sports Trading Cards\"\n");
        }
        sb.append("}\n");
        sb.append("</script>\n");

        return sb.toString();
    }


    private static String getPrimaryPlayer(CardData c) {
        String p = c.get("Player");
        if (p == null) return "";
        if (p.contains(",")) return p.split(",")[0].trim();
        return p;
    }

    private static String getPrimaryTeam(CardData c) {
        String t = c.get("Team");
        if (t == null) return "";
        if (t.contains(",")) return t.split(",")[0].trim();
        return t;
    }

    private static String formatMulti(String val) {
        if (val == null) return "";
        return val.replaceAll("\\s*,\\s*", " / ");
    }

    private static void addTableRow(StringBuilder sb, String title, String value) {
        sb.append("            <tr><th class=\"specs-th\">").append(title).append("</th><td class=\"specs-td\">").append(isValid(value) ? escapeHtml(value) : "-").append("</td></tr>\n");
    }

    private static void addIfPresent(List<String> list, String value) {
        if (isValid(value)) list.add(value);
    }

    private static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty() && !value.equals("0");
    }

    private static String cleanFilename(String text) {
        if (text == null) return "";
        String clean = text.replace("/", "-").replace("\\", "-").replace("\"", "");
        clean = clean.replaceAll("[^a-zA-Z0-9\\s-]", "");
        clean = clean.trim().replace(" ", "-");
        clean = clean.replaceAll("-+", "-");
        return clean;
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"");
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}