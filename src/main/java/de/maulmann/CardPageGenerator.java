package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Comparator;

public class CardPageGenerator {

    private static final String BASE_FOLDER = "output/cards";
    private static final String RELATIVE_IMAGES_PATH = "../../images";
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final Logger log = LoggerFactory.getLogger(CardPageGenerator.class);
    public static final String ROOT = "../../";

    private static final List<String> duplicateLog = new ArrayList<>();

    private static final Configuration fmConfig;
    static {
        fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        fmConfig.setClassForTemplateLoading(CardPageGenerator.class, "/templates");
        fmConfig.setDefaultEncoding("UTF-8");
        fmConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    static class CardData {
        Map<String, String> attributes;
        String filenameBase;
        String filename;
        String seasonFolder;
        String fullRelativePath;

        public CardData(Map<String, String> attributes, int uniqueId) {
            this.attributes = new HashMap<>(attributes);

            String currentTeam = this.attributes.get("Team");
            if (!isValid(currentTeam)) {
                String player = this.attributes.get("Player");
                if ("Juwan Howard".equals(player)) {
                    String calculatedTeam = getTeamBySeason(this.attributes.get("Season"));
                    this.attributes.put("Team", calculatedTeam);
                }
            }
            calculatePaths(uniqueId);
        }

        private void calculatePaths(int uniqueId) {
            List<String> filenameTokens = new ArrayList<>();
            addIfPresent(filenameTokens, attributes.get("Player"));
            addIfPresent(filenameTokens, attributes.get("Team"));
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

        try {
            Path cardsDir = Paths.get(BASE_FOLDER);
            if (Files.exists(cardsDir)) {
                Files.walk(cardsDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            log.warn("Could not clean output/cards directory: " + e.getMessage());
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
            int globalCardCounter = 0;

            for (Element table : tables) {
                globalCardCounter = extractTableDataAndUpdateDom(table, rawCards, globalCardCounter);
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

        } catch (IOException | InterruptedException e) {
            log.error("Error processing collection " + inputPath, e);
        }
    }

    private static int extractTableDataAndUpdateDom(Element table, List<CardData> globalCardList, int counter) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return counter;

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

        if (headerRowIndex == -1 || headers == null) return counter;

        for (int i = headerRowIndex + 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.children();
            if (cols.isEmpty()) continue;

            Map<String, String> dataMap = new HashMap<>();
            for (int j = 0; j < cols.size() && j < headers.length; j++) {
                dataMap.put(headers[j], cols.get(j).text().trim());
            }

            CardData currentCard = new CardData(dataMap, counter++);
            globalCardList.add(currentCard);
            row.attr("data-card-id", String.valueOf(currentCard.hashCode()));
        }
        return counter;
    }

    private static void updateDomLinks(Elements tables, List<CardData> filteredCards) {
        Set<String> approvedCardIds = new HashSet<>();
        for (CardData card : filteredCards) {
            approvedCardIds.add(String.valueOf(card.hashCode()));
        }

        for (Element table : tables) {
            Elements rows = table.select("tr");
            int playerColIndex = 0;

            if (!rows.isEmpty()) {
                Elements headers = rows.get(0).children();
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
                                .filter(c -> String.valueOf(c.hashCode()).equals(rowId))
                                .findFirst().orElse(null);

                        if (matchingCard != null) {
                            String originalText = playerCell.text();
                            playerCell.empty();
                            playerCell.appendElement("a")
                                    .attr("href", matchingCard.fullRelativePath)
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

    private static void generateSubPagesMultithreaded(List<CardData> allCards, String overviewPage) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);

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

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards, String overviewPage) {
        String h1Title = generateH1(c);
        String browserTitle = generateBrowserTitle(c, overviewPage);
        String metaDesc = generateMetaDescription(c);

        String seasonImgFolder = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder;
        String imageBaseName = c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-"));

        String frontImgPath = seasonImgFolder + "/" + imageBaseName + "-front.webp";
        String backImgPath = seasonImgFolder + "/" + imageBaseName + "-back.webp";

        // OCR Logic
        String originalPath = "images/" + c.seasonFolder + "/" + imageBaseName + "-back.jpg";
        String backText = "";
         //     backText=  new File(originalPath).exists() ? CardTextExtractor.getBackText(originalPath) : "";

        // Baue die Map für FreeMarker auf
        Map<String, Object> data = new HashMap<>();

        // Globale HTML Bausteine
        data.put("headHtml", SharedTemplates.getHead(escapeHtml(browserTitle), escapeHtml(metaDesc), ROOT, overviewPage, frontImgPath));
        data.put("jsonLd", generateJsonLd(c, metaDesc, h1Title, overviewPage, imageBaseName));
        data.put("topNavHtml", SharedTemplates.getTopNav(ROOT, "collection"));
        data.put("footerHtml", SharedTemplates.getFooter(ROOT));

        // Navigation
        data.put("overviewPage", overviewPage);
        data.put("prevLink", prev != null ? "../" + prev.seasonFolder + "/" + prev.filename : null);
        data.put("nextLink", next != null ? "../" + next.seasonFolder + "/" + next.filename : null);

        // Titel und SEO Text
        data.put("h1Title", escapeHtml(h1Title));
        data.put("aiSnapshotText", escapeHtml(metaDesc));

        // Bilder
        data.put("frontImgPath", frontImgPath);
        data.put("backImgPath", backImgPath);
        data.put("frontAlt", escapeHtml(generateAltText(c, "front")));
        data.put("backAlt", escapeHtml(generateAltText(c, "back")));
        data.put("frontImgTitle", escapeHtml("Front scan of " + c.get("Player") + " " + c.get("Brand")));
        data.put("backImgTitle", escapeHtml("Back scan of " + c.get("Player") + " " + c.get("Brand")));

        // Technische Daten (Tabelle)
        data.put("season", isValid(c.get("Season")) ? escapeHtml(c.get("Season")) : "-");
        data.put("team", isValid(c.get("Team")) ? escapeHtml(c.get("Team")) : "-");
        data.put("company", isValid(c.get("Company")) ? escapeHtml(c.get("Company")) : "-");
        data.put("brand", isValid(c.get("Brand")) ? escapeHtml(c.get("Brand")) : "-");
        data.put("theme", isValid(c.get("Theme")) ? escapeHtml(c.get("Theme")) : "-");
        data.put("variant", isValid(c.get("Variant")) ? escapeHtml(c.get("Variant")) : "-");
        data.put("number", isValid(c.get("Number")) ? escapeHtml(c.get("Number")) : "-");
        data.put("rookie", isValid(c.get("Rookie")) ? escapeHtml(c.get("Rookie")) : "-");
        data.put("memorabilia", isValid(c.get("Game Used")) ? escapeHtml(c.get("Game Used")) : "-");
        data.put("autograph", isValid(c.get("Autograph")) ? escapeHtml(c.get("Autograph")) : "-");

        String combined = c.get("Serial/Print Run");
        String serialDisplay = "-";
        if (isValid(combined)) {
            serialDisplay = combined;
        } else if (c.has("Serial") || c.has("Print Run")) {
            serialDisplay = (c.has("Serial") ? c.get("Serial") : "?") + " / " + (c.has("Print Run") ? c.get("Print Run") : "?");
        }
        data.put("serialDisplay", escapeHtml(serialDisplay));

        String grading = c.get("Grading Co.") + " " + c.get("Grade");
        data.put("grading", (grading.trim().length() > 1 && !grading.trim().equals("null null")) ? escapeHtml(grading) : "");

        // Trivia & Context Engines
        data.put("hobbyTrivia", getHobbyTrivia(c));
        data.put("techTrivia", getCardTechTrivia(c));
        data.put("playerHighlights", getSeasonHighlights(c.get("Season"), c.get("Player")));
        data.put("eraContext", getNbaEraContext(c.get("Season")));
        data.put("cardBackText", backText);

        data.put("faqHtml", generateFaqHtml(c));

        // FreeMarker Template füllen und in die Datei schreiben
        try {
            Template template = fmConfig.getTemplate("card-detail.ftlh");
            try (Writer out = new OutputStreamWriter(new FileOutputStream(path.toFile()), StandardCharsets.UTF_8)) {
                template.process(data, out);
            }
        } catch (Exception e) {
            log.error("Failed to process FreeMarker template for " + c.filename, e);
        }
    }

    // --- ENGINE 1: HOBBY SIGNIFICANCE (Sets, Parallels & History) ---
    private static String getHobbyTrivia(CardData c) {
        String brand = c.get("Brand").toLowerCase();
        String theme = c.get("Theme").toLowerCase();
        String variant = c.get("Variant").toLowerCase();
        String company = c.get("Company").toLowerCase();

        StringBuilder trivia = new StringBuilder();

        // 1. Skybox / Fleer Holy Grails
        if (variant.contains("pmg green") || variant.contains("precious metal gems green")) {
            trivia.append("The mythical PMG Green! Limited to just the first 10 copies of the 100-card print run, this is undeniably one of the most legendary and expensive parallel cards in the entire sports card hobby. ");
        } else if (variant.contains("pmg") || variant.contains("precious metal gems") || theme.contains("precious metal gems")) {
            trivia.append("SkyBox Metal Universe Precious Metal Gems (PMGs) are widely considered the holy grails of 1990s basketball. The print run was strictly limited to 100 copies. They are notorious for severe edge-chipping straight out of the pack, making highly-graded copies 'unicorns' at auction. ");
        }
        if (variant.contains("star ruby") || variant.contains("rubies")) {
            trivia.append("Skybox Premium 'Star Rubies' are among the most revered and aesthetically striking parallels of the late 90s. With brilliant red foil and extreme scarcity (often numbered to 50 or 45), they are a centerpiece that rivals PMGs in demand. ");
        }
        if (variant.contains("legacy") || theme.contains("legacy collection")) {
            trivia.append("Flair Showcase's 'Legacy Collection' revolutionized the hobby by introducing tiered rarity (Row 0, Row 1, Row 2) stamped with vibrant blue foil. These were some of the very first mainstream serial-numbered parallels, changing the chase dynamic forever. ");
        }
        if (variant.contains("rave") && !variant.contains("refractor")) {
            trivia.append("Z-Force 'Raves' (including Super Raves and Thunder Raves) featured vibrant, chaotic foil patterns matching the loud, aggressive aesthetic of 90s basketball. They were incredibly tough pack pulls. ");
        }

        // 2. E-X Series & Seating Tiers
        if (variant.contains("credentials")) {
            trivia.append("E-X Credentials (Now and Future) are among the most aesthetically stunning and condition-sensitive acetate cards ever produced. The print runs differed based on the player's card number, creating a complex and highly engaging chase for collectors. ");
        }
        if (variant.contains("general admission") || variant.contains("mezzanine") || variant.contains("balcony") || variant.contains("club box") || variant.contains("standing room") || variant.contains("loge level") || variant.contains("tier reserved")) {
            trivia.append("Modeled after stadium seating, these tiers represented different levels of scarcity in the set. Pulling 'Club Box' or 'Loge Level' equivalents meant you had secured the rarest variations in the product. ");
        }
        if (brand.contains("jambalaya") || theme.contains("jambalaya")) {
            trivia.append("E-X2001 Jambalaya inserts are legendary. Featuring a highly unique die-cut oval shape and falling at an incredibly tough ratio of 1 in 720 packs, their design and scarcity hold immense prestige today. ");
        }

        // 3. Upper Deck & High-End History
        if (brand.contains("exquisite")) {
            trivia.append("In 2003, Upper Deck gambled by releasing 'Exquisite Collection', packaged in wooden boxes at unprecedented price points. It single-handedly birthed the ultra-high-end market, introducing massive multi-color patches and on-card autographs. ");
        }
        if (variant.contains("electric court")) {
            trivia.append("Upper Deck's 'Electric Court' (along with Gold and Platinum versions) were among the earliest parallel chase cards in the hobby, giving collectors a premium foil-stamped upgrade over the standard base card. ");
        }
        if (variant.contains("diamond") && (variant.contains("double") || variant.contains("triple") || variant.contains("quadruple"))) {
            trivia.append("Upper Deck Black Diamond utilized a unique tiered rarity system based on the number of diamonds printed on the card. 'Quadruple Diamond' cards were the rarest base parallels in the set. ");
        }

        // 4. Medallions & Scripts
        if (variant.contains("medallion")) {
            trivia.append("Fleer Ultra's Medallion parallels (Gold and Platinum) offered a premium foil upgrade. Platinum Medallions, in particular, were heavily short-printed and remain highly collectible. ");
        }
        if (variant.contains("script") && (variant.contains("silver") || variant.contains("gold") || variant.contains("super"))) {
            trivia.append("Upper Deck's 'Script' parallels added elegant facsimile foil signatures to the cards, with 'Super Script' usually being severely limited and serial-numbered. ");
        }

        // 5. Panini Modern High-End
        if (brand.contains("flawless") || brand.contains("national treasures")) {
            trivia.append("Panini's Flawless and National Treasures lines represent the pinnacle of modern investment-grade basketball cards. Flawless is famous for embedding actual, certified diamonds and precious gems directly into the card. ");
        }

        // 6. Base / Entry Level
        if (variant.equals("base") || variant.equals("base set")) {
            trivia.append("As the foundational base card of the set, this piece represents the core of player registries. While not inherently rare, finding un-numbered base cards from the 90s in pristine Gem Mint condition has become incredibly difficult due to the card stock used at the time. ");
        }

        return trivia.toString();
    }

    // --- ENGINE 2: CARD TECHNOLOGY (Die-Cuts, Chromium, Plates, 1/1s) ---
    private static String getCardTechTrivia(CardData c) {
        String brand = c.get("Brand").toLowerCase();
        String variant = c.get("Variant").toLowerCase();
        String theme = c.get("Theme").toLowerCase();

        StringBuilder tech = new StringBuilder();

        // 1. One-of-Ones & Plates
        if (variant.contains("printing plate") || variant.contains("magenta") || variant.contains("cyan") || variant.contains("yellow") || variant.contains("black plate")) {
            tech.append("<strong>Printing Plate:</strong> This is an actual 1-of-1 metal plate used directly in the manufacturer's printing press to create the standard cards for this set. It is a true piece of production history. ");
        } else if (variant.contains("1 of 1") || variant.contains("1/1") || c.get("Serial").equals("1/1") || c.get("Serial/Print Run").contains("1/1") || variant.contains("masterpiece") || variant.contains("superfractor") || variant.contains("nebula")) {
            tech.append("<strong>One-of-One (1/1):</strong> This card is a true Masterpiece. Being the absolute only card of its exact kind ever manufactured makes it the ultimate crown jewel for any serious collector. ");
        } else if (variant.contains("pre production") || variant.contains("proof")) {
            tech.append("<strong>Production Proof:</strong> This is a rare pre-production proof or test print, originally used internally by the manufacturer to verify color and quality before the mass print run began. ");
        }

        // 2. The Refractor / Chromium Family
        if (variant.contains("refractor") || variant.contains("frozenfractor") || variant.contains("x-fractor") || variant.contains("atomic")) {
            if (variant.contains("atomic")) tech.append("<strong>Atomic Refractor:</strong> Features a stunning 'cracked ice' hyper-plaid holographic pattern. ");
            else if (variant.contains("x-fractor")) tech.append("<strong>X-Fractor:</strong> Famous for its distinct checkerboard holographic pattern. ");
            else if (variant.contains("frozenfractor")) tech.append("<strong>Frozenfractor:</strong> An incredibly rare, condition-sensitive parallel with a unique frosted ice aesthetic. ");
            else if (variant.contains("negative")) tech.append("<strong>Negative Refractor:</strong> Inverts the image colors for a striking, ghost-like appearance. ");
            else if (variant.contains("superfractor")) tech.append("<strong>Superfractor:</strong> The undisputed king of modern cards, featuring a mesmerizing golden swirl pattern, strictly limited to 1 copy worldwide. ");
            else tech.append("<strong>Chromium Refractor:</strong> Chromium technology revolutionized the hobby by adding a rainbow light-diffracting coating to the card surface. The specific color (Gold, Sapphire, Ruby, etc.) designates its exact scarcity tier in the set. ");
        }

        // 3. Modern Opti-Chrome / Prizm Tech
        if (variant.contains("mojo") || variant.contains("tie dye") || variant.contains("meta") || variant.contains("marble") || variant.contains("astral") || variant.contains("fractal") || variant.contains("pulsar") || variant.contains("holo")) {
            tech.append("<strong>Opti-Chrome Technology:</strong> This parallel utilizes hyper-refractive geometric foil patterns (like Mojo, Pulsar, or Tie-Dye) that react dynamically to light. These finishes are highly sought after by modern investors. ");
        }

        // 4. Physical Card Alterations (Die-Cut, Acetate, Embossed)
        if (variant.contains("die") && variant.contains("cut") || theme.contains("die-cut")) {
            tech.append("<strong>Die-Cut Technology:</strong> Manufacturers used custom stamping dies to cut intricate borders and patterns into the card stock. Because of the exposed, delicate extra corners, die-cut cards are notoriously condition-sensitive. ");
        }
        if (variant.contains("acetate") || variant.contains("plexiglass") || variant.contains("crystal")) {
            tech.append("<strong>Acetate / Plexiglass:</strong> Printed on clear, transparent, or semi-translucent plastic rather than traditional cardboard, creating a premium 'window' effect. ");
        }
        if (variant.contains("embossed") || theme.contains("embossed")) {
            tech.append("<strong>Embossing:</strong> Manufacturers used heavy presses to stamp raised, 3D textures into the card face, adding a premium tactile element that collectors could physically feel. ");
        }

        // 5. Foil & Finishes
        if (variant.contains("foil tech") || brand.contains("metal universe") || theme.contains("etched")) {
            tech.append("<strong>Etched Foil:</strong> Utilizes proprietary technology to create deep, grooved textures in the metallic background of the card, allowing light to catch the card in dynamic ways. ");
        }

        // 6. Memorabilia
        if (variant.contains("patch") || variant.contains("multicolor") || variant.contains("prime")) {
            tech.append("<strong>Prime Patch:</strong> Instead of a standard single-color jersey swatch, this card features a 'Prime' cut—usually containing multi-color stitching, numbers, or logos directly from the game-worn jersey. ");
        }

        return tech.toString();
    }

    // --- ENGINE 3: PLAYER PERFORMANCE & TEAMMATES ---
    private static String getSeasonHighlights(String season, String player) {
        if (!"Juwan Howard".equals(player)) return "";

        if (season.startsWith("1994")) return "Drafted 5th overall by Washington, Juwan Howard proved himself an elite talent during his 1994-95 rookie campaign. He earned NBA All-Rookie Second Team honors and played alongside former 'Fab Five' teammate Chris Webber, as well as the 7-foot-7 giant Gheorghe Mureșan.";
        if (season.startsWith("1995-96")) return "The 1995-96 season was Juwan's major breakout year. He became an NBA All-Star, was named to the All-NBA Third Team, and averaged a dominant 22.1 points per game. He anchored the Bullets alongside Chris Webber and sharpshooter Tracy Murray.";
        if (season.startsWith("1996") || season.startsWith("1997")) return "During this era, Juwan was the focal point of the Washington Wizards offense. He shared the court with prime Rod Strickland, who led the league in assists, and a young rookie named Richard Hamilton.";
        if (season.startsWith("2000-01") || season.startsWith("2001-02")) return "Traded to the Dallas Mavericks, Juwan provided crucial veteran scoring. He joined a highly explosive offensive roster featuring a young Dirk Nowitzki, MVP-caliber point guard Steve Nash, and Michael Finley.";
        if (season.startsWith("2003-04")) return "Playing for the Orlando Magic, Juwan Howard was a vital secondary scorer alongside the NBA's scoring champion, Tracy McGrady. He started 81 games and averaged 17 points per night.";
        if (season.startsWith("2004") || season.startsWith("2005") || season.startsWith("2006")) return "During his tenure with the Houston Rockets, Juwan was a seasoned veteran presence in the frontcourt, playing alongside Hall of Fame center Yao Ming, Tracy McGrady, and legendary shot-blocker Dikembe Mutombo.";
        if (season.startsWith("2010") || season.startsWith("2011") || season.startsWith("2012")) return "As a respected locker room leader for the Miami Heat, Juwan Howard achieved the ultimate goal. Playing alongside the 'Big Three' (LeBron James, Dwyane Wade, Chris Bosh), he won back-to-back NBA Championships in 2012 and 2013, capping off an incredible near-20-year career.";
        if (season.toLowerCase().contains("college")) return "At the University of Michigan, Juwan Howard was a cornerstone of the legendary 'Fab Five'. Alongside Chris Webber, Jalen Rose, Jimmy King, and Ray Jackson, they reached consecutive NCAA championship games and permanently altered basketball culture.";
        return "";
    }

    // --- ENGINE 4: NBA ERA & POP CULTURE CONTEXT ---
    private static String getNbaEraContext(String season) {
        if (season.startsWith("1996")) return "The 1996-97 season celebrated the NBA's 50th Anniversary (noted by gold NBA logos on many cards). It also marked the arrival of the legendary 1996 Draft Class (Kobe Bryant, Allen Iverson, Steve Nash), which drove the sports card hobby into a frenzy of innovation.";
        if (season.startsWith("1998")) return "The 1998-99 season was shortened to just 50 games due to a league-wide lockout. As a result, card manufacturers produced fewer sets and lower print runs, making specific inserts from this era surprisingly scarce today.";
        if (season.startsWith("1999")) return "Pop Culture crossover: During the 1999 television season, Juwan Howard made a famous cameo appearance in the hit political drama 'The West Wing' (Episode: 'The Crackpots and These Women'), playing a pickup basketball game against the President's staff.";
        if (season.startsWith("2003")) return "The 2003-04 season is historic for the arrival of LeBron James, Dwyane Wade, and Carmelo Anthony. The massive hype around this rookie class caused a boom in trading card investments, leading to the birth of ultra-high-end products like Exquisite Collection.";
        if (season.startsWith("2011") || season.startsWith("2012")) return "During the 'Heatles' era in Miami, the NBA was dominated by the polarizing Big Three. Cards produced during this period capture a highly significant dynasty that changed player empowerment and free agency forever.";
        if (season.toLowerCase().contains("college")) return "The early 90s college basketball scene was revolutionized by the Fab Five. They introduced baggy shorts, black socks, and a trash-talking swagger that heavily influenced the aesthetic of 90s hip-hop and sports pop culture.";
        return "";
    }

    private static String fileNameFromPath(String path) {
        return new File(path).getName();
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
        sb.append(c.get("Season")).append(" ");
        sb.append(c.get("Brand")).append(" ");
        sb.append(c.get("Player"));
        if (c.has("Theme") && !c.get("Brand").contains(c.get("Theme"))) sb.append(" ").append(c.get("Theme"));
        if (c.has("Variant")) sb.append(" ").append(c.get("Variant"));
        if (c.has("Number")) sb.append(" #").append(c.get("Number"));
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String generateBrowserTitle(CardData c, String overviewPage) {
        String player = c.get("Player");
        if (!isValid(player)) player = "Card";
        return generateH1(c) + " | " + player + " Collection";
    }

    private static String generateAltText(CardData c, String view) {
        String base = c.get("Player") + " " + c.get("Season") + " " + c.get("Brand") + " #" + c.get("Number");
        if (view.equals("front")) return "Front view of " + base + " - " + c.get("Variant") + " edition (" + c.get("Team") + ")";
        else return "Back view of " + base + " showing stats for " + c.get("Team");
    }

    private static String generateMetaDescription(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Details for the ").append(c.get("Season")).append(" ").append(c.get("Brand")).append(" ");
        sb.append(c.get("Player")).append(" card #").append(c.get("Number")).append(" (").append(c.get("Team")).append("). ");
        if (c.has("Variant")) sb.append("Variant: ").append(c.get("Variant")).append(". ");
        String combinedSerial = c.get("Serial/Print Run");
        if (isValid(combinedSerial)) sb.append("Numbered: ").append(combinedSerial).append(". ");
        else if (c.has("Serial")) sb.append("Numbered: ").append(c.get("Serial")).append("/").append(c.get("Print Run")).append(". ");
        sb.append("View high-res images and collector facts.");
        return sb.toString();
    }

    private static boolean isHolyGrail(CardData c) {
        String theme = c.get("Theme").toLowerCase();
        String brand = c.get("Brand").toLowerCase();
        String variant = c.get("Variant").toLowerCase();
        return theme.contains("pmg") || theme.contains("star rubies") || theme.contains("legacy collection") ||
                brand.contains("pmg") || variant.contains("pmg") || variant.contains("star rubies") || variant.contains("legacy collection");
    }

    private static String generateFaqHtml(CardData c) {
        StringBuilder sb = new StringBuilder();
        String season = c.get("Season");
        String company = c.get("Company");
        String player = c.get("Player");

        if (isHolyGrail(c)) {
            sb.append(createFaqItem("Is this a 'Holy Grail' card?", "Yes, this card belongs to one of the most prestigious parallel series in the hobby. These are extremely rare and heavily targeted by high-end investors."));
        }

        String combined = c.get("Serial/Print Run");
        if (isValid(combined)) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + escapeHtml(combined) + ", making it a strictly limited edition collectible."));
        } else if (c.has("Serial")) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + escapeHtml(c.get("Serial")) + " out of a total print run of " + escapeHtml(c.get("Print Run")) + "."));
        }

        if (c.has("Rookie")) {
            String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ?
                    "Yes, this is an official Rookie Card (RC) from " + escapeHtml(player) + "'s debut season, holding premium value for collectors." :
                    "No, this card was released during the " + escapeHtml(season) + " season, later in " + escapeHtml(player) + "'s career.";
            sb.append(createFaqItem("Is this a " + escapeHtml(player) + " Rookie Card?", rookieAns));
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(createFaqItem("Is the autograph authentic?", "Yes, this card features a manufacturer-certified autograph guaranteed by " + escapeHtml(company) + "."));
        }

        if (c.has("Grade")) {
            sb.append(createFaqItem("Is this card professionally graded?", "Yes, this card has been graded by " + escapeHtml(c.get("Grading Co.")) + " and received a condition score of " + escapeHtml(c.get("Grade")) + "."));
        }

        return sb.toString();
    }

    private static String createFaqItem(String question, String answer) {
        return "<details class=\"faq-details\" style=\"background: #fff; padding: 15px; border-bottom: 1px solid #ddd; cursor: pointer;\">" + "<summary class=\"faq-summary\" style=\"font-weight: bold; font-size: 1.1em; outline: none;\">" + question + "</summary>" + "<p class=\"faq-answer\" style=\"margin-top: 10px; color: #555;\">" + answer + "</p>" + "</details>";
    }

    private static String generateJsonLd(CardData c, String desc, String h1Title, String overviewPage, String imageBaseName) {
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-front.webp";
        String backImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-back.webp";

        String sb = "<script type=\"application/ld+json\">\n" +
                "{\n" +
                "  \"@context\": \"https://schema.org\",\n" +
                "  \"@graph\": [\n" +
                "    {\n" +
                "      \"@type\": \"VisualArtwork\",\n" +
                "      \"name\": \"" + escapeJson(h1Title) + "\",\n" +
                "      \"image\": [ \"" + frontImgUrl + "\", \"" + backImgUrl + "\" ],\n" +
                "      \"description\": \"" + escapeJson(desc) + "\",\n" +
                "      \"creator\": { \"@type\": \"Organization\", \"name\": \"" + escapeJson(c.get("Company")) + "\" },\n" +
                "      \"about\": {\n" +
                "        \"@type\": \"Person\",\n" +
                "        \"name\": \"" + escapeJson(c.get("Player")) + "\",\n" +
                "        \"jobTitle\": \"Professional Basketball Player\"\n" +
                "      },\n" +
                "      \"artMedium\": \"Trading Card\",\n" +
                "      \"artform\": \"Sports Memorabilia\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "</script>\n";
        return sb;
    }

    private static void addTableRow(StringBuilder sb, String title, String value) {
        sb.append("            <tr><th class=\"specs-th\" style=\"padding: 8px; border-bottom: 1px solid #ddd; width: 30%;\">").append(title).append("</th><td class=\"specs-td\" style=\"padding: 8px; border-bottom: 1px solid #ddd;\">").append(isValid(value) ? escapeHtml(value) : "-").append("</td></tr>\n");
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
        return text.replace("\"", "&quot;").replace("'", "&#39;");
    }
}