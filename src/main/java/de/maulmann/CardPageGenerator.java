package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // --- NEU: Globale Liste für alle gefundenen Duplikate ---
    private static final List<String> duplicateLog = new ArrayList<>();

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

        // Duplikat-Log initialisieren
        duplicateLog.clear();
        duplicateLog.add("FILTERED DUPLICATES LOG");
        duplicateLog.add("=======================");
        duplicateLog.add("These un-numbered base/insert cards were skipped during HTML generation because a duplicate already exists in the collection.\n");

        // --- WIPE LOCAL GHOST FILES BEFORE GENERATING ---
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

        // --- LESE AB JETZT ALLES AUS DEM OUTPUT ORDNER ---
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

        // --- NEU: Speichere die Duplikate-Datei am Ende des Durchlaufs ---
        try {
            File dupFile = new File("output/Duplicates.txt");
            Files.write(dupFile.toPath(), duplicateLog, StandardCharsets.UTF_8);
            log.info("Saved Duplicates.txt with {} entries.", duplicateLog.size() - 4); // -4 wegen der Header-Zeilen
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
            if (!input.exists()) {
                log.warn("Input file not found: {}", inputPath);
                return;
            }

            Document doc = Jsoup.parse(input, "UTF-8");
            Elements tables = doc.select("table");
            if (tables.isEmpty()) return;

            log.info("Processing {} tables in {}...", tables.size(), fileNameFromPath(inputPath));

            List<CardData> rawCards = new ArrayList<>();
            int globalCardCounter = 0;

            for (Element table : tables) {
                globalCardCounter = extractTableDataAndUpdateDom(table, rawCards, globalCardCounter);
            }

            if (rawCards.isEmpty()) {
                log.warn("No valid card rows found in {}", inputPath);
                return;
            }

            // ==========================================
            // DEDUPLICATION LOGIC
            // ==========================================
            List<CardData> filteredCards = new ArrayList<>();
            Set<String> seenFingerprints = new HashSet<>();

            duplicateLog.add("\n--- From " + overviewPage + " ---");

            for (CardData card : rawCards) {
                // --- UPDATE: Der Fingerprint berücksichtigt jetzt auch Grading Co. und Grade! ---
                String fingerprint = (card.get("Season") + "|" + card.get("Company") + "|" +
                        card.get("Brand") + "|" + card.get("Theme") + "|" +
                        card.get("Variant") + "|" + card.get("Number") + "|" +
                        card.get("Grading Co.") + "|" + card.get("Grade")).toLowerCase();

                String serial = card.get("Serial");
                if (!isValid(serial)) serial = card.get("Serial/Print Run");

                boolean hasSerial = isValid(serial) && !serial.equals("0");

                if (seenFingerprints.contains(fingerprint)) {
                    if (!hasSerial) {
                        // --- NEU: Duplikat protokollieren ---
                        String dupInfo = card.get("Season") + " " + card.get("Company") + " " +
                                card.get("Brand") + " " + card.get("Theme") + " " +
                                card.get("Variant") + " #" + card.get("Number") + " - " + card.get("Player");
                        duplicateLog.add("[SKIPPED] " + dupInfo.replaceAll("\\s+", " "));
                        continue;
                    } else {
                        // It's a duplicate, BUT it has a serial number (e.g. 2/10 and 7/10). Keep it!
                        filteredCards.add(card);
                    }
                } else {
                    // First time seeing this card
                    seenFingerprints.add(fingerprint);
                    filteredCards.add(card);
                }
            }

            log.info("Deduplication complete: Kept {} unique/numbered cards out of {}.", filteredCards.size(), rawCards.size());

            // Update main HTML DOM
            updateDomLinks(tables, filteredCards);

            // Write the updated main HTML file back to disk
            File outIndex = new File(outputPath);
            if (outIndex.getParentFile() != null) outIndex.getParentFile().mkdirs();
            Files.writeString(outIndex.toPath(), doc.outerHtml(), StandardCharsets.UTF_8);

            // Multithread the generation of the subpages using ONLY the filtered list
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

            // Create card with unique ID
            CardData currentCard = new CardData(dataMap, counter++);
            globalCardList.add(currentCard);

            // We temporarily store the card reference directly in the row element for the next step
            row.attr("data-card-id", String.valueOf(currentCard.hashCode()));
        }
        return counter;
    }

    private static void updateDomLinks(Elements tables, List<CardData> filteredCards) {
        // Create a fast lookup map of approved cards
        Set<String> approvedCardIds = new HashSet<>();
        for (CardData card : filteredCards) {
            approvedCardIds.add(String.valueOf(card.hashCode()));
        }

        for (Element table : tables) {
            Elements rows = table.select("tr");
            int playerColIndex = 0; // Default

            // Find Player column
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
                if (rowId.isEmpty()) continue; // Header row

                // Wurde die Karte behalten?
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
                    // Clean up temporary attribute
                    row.removeAttr("data-card-id");
                } else {
                    // --- DER FIX: LÖSCHE DIE ZEILE KOMPLETT AUS DER TABELLE ---
                    row.remove();
                }
            }
        }
    }

    private static void generateSubPagesMultithreaded(List<CardData> allCards, String overviewPage) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        AtomicInteger successCount = new AtomicInteger(0);

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
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to generate subpage for card at index " + index, e);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        log.info("Successfully generated {} subpages for {}.", successCount.get(), overviewPage);
    }

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards, String overviewPage) throws IOException {
        StringBuilder sb = new StringBuilder(4096);

        String h1Title = generateH1(c);
        String browserTitle = generateBrowserTitle(c, overviewPage);
        String metaDesc = generateMetaDescription(c);

        String seasonImgFolder = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder;

        // Remove the "-<uniqueId>" suffix for the IMAGE lookup so it still finds the original file
        String imageBaseName = c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-"));

        String frontImgPath = seasonImgFolder + "/" + imageBaseName + "-front.webp";
        String backImgPath = seasonImgFolder + "/" + imageBaseName + "-back.webp";

        String frontAlt = generateAltText(c, "front");
        String backAlt = generateAltText(c, "back");
        String frontImgTitle = "Front scan of " + c.get("Player") + " " + c.get("Brand") + " (" + c.get("Season") + ")";
        String backImgTitle = "Back scan of " + c.get("Player") + " " + c.get("Brand") + " (" + c.get("Season") + ")";

        // HTML START
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append(SharedTemplates.getHead(escapeHtml(browserTitle), escapeHtml(metaDesc), ROOT, overviewPage, frontImgPath));

        // LCP Preload
        sb.append("    <link rel=\"preload\" as=\"image\" href=\"").append(frontImgPath).append("\" fetchpriority=\"high\">\n");

        // Schema.org
        sb.append(generateJsonLd(c, metaDesc, h1Title, overviewPage, imageBaseName));

        sb.append("</head>\n<body>\n");

        // NAVIGATION
        String activeNav = overviewPage.replace(".html", "").toLowerCase();
        if (activeNav.equals("juwan-howard-collection")) activeNav = "collection";
        sb.append(SharedTemplates.getTopNav(ROOT, activeNav));

        // SUB-NAV (Overview, Prev, Next)
        sb.append("<nav class=\"detail-nav\" style=\"border: none; background: transparent;\">\n");
        sb.append("    <a href=\"../../").append(overviewPage).append("\" class=\"modern-button\" style=\"text-decoration:none;\" title=\"Return to the complete card collection overview\">&larr; Overview</a>\n");
        sb.append("    <div style=\"display: flex; gap: 10px;\">\n");
        if (prev != null) {
            String prevTitle = "Go to previous card: " + prev.get("Season") + " " + prev.get("Brand");
            String prevLink = "../" + prev.seasonFolder + "/" + prev.filename;
            sb.append("        <a id=\"prevCardLink\" href=\"").append(prevLink).append("\" title=\"").append(escapeHtml(prevTitle)).append("\" style=\"text-decoration:none;\">&laquo; Prev</a>\n");
        }
        if (next != null) {
            String nextTitle = "Go to next card: " + next.get("Season") + " " + next.get("Brand");
            String nextLink = "../" + next.seasonFolder + "/" + next.filename;
            sb.append("        <a id=\"nextCardLink\" href=\"").append(nextLink).append("\" title=\"").append(escapeHtml(nextTitle)).append("\" style=\"text-decoration:none;\">Next &raquo;</a>\n");
        }
        sb.append("    </div>\n");
        sb.append("</nav>\n");

        // MAIN CONTENT
        sb.append("<main class=\"detail-main\">\n");

        // HEADER
        sb.append("    <header class=\"detail-header\">\n");
        sb.append("        <h1>").append(escapeHtml(h1Title)).append("</h1>\n");
        sb.append("    </header>\n");

        // SEO TEXT
        sb.append("    <article class=\"seo-box\">\n");
        sb.append("        <h2>About this Card</h2>\n");
        sb.append("        <p>").append(generateSeoText(c)).append("</p>\n");
        sb.append("    </article>\n");

        // IMAGES SECTION
        sb.append("    <div class=\"card-images-container\" style=\"display: flex; gap: 20px; flex-wrap: wrap;\">\n");
        sb.append("        <div class=\"card-image-wrapper\" style=\"display: flex; flex-direction: column; align-items: center;\">\n");
        sb.append("            <img src=\"").append(frontImgPath).append("\" ")
                .append("alt=\"").append(escapeHtml(frontAlt)).append("\" ")
                .append("title=\"").append(escapeHtml(frontImgTitle)).append("\" ")
                .append("width=\"400\" height=\"550\" fetchpriority=\"high\" ")
                .append("style=\"aspect-ratio: 400 / 550; width: 100%; max-width: 400px; height: auto; display: block; object-fit: contain;\" ")
                .append("onclick=\"openModal('").append(frontImgPath).append("', '").append(backImgPath).append("')\">\n");
        sb.append("            <p style=\"margin-top: 10px; min-height: 24px;\">Front View (Click to Zoom)</p>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"card-image-wrapper\" style=\"display: flex; flex-direction: column; align-items: center;\">\n");
        sb.append("            <img src=\"").append(backImgPath).append("\" ")
                .append("alt=\"").append(escapeHtml(backAlt)).append("\" ")
                .append("title=\"").append(escapeHtml(backImgTitle)).append("\" ")
                .append("width=\"400\" height=\"550\" loading=\"lazy\" ")
                .append("style=\"aspect-ratio: 400 / 550; width: 100%; max-width: 400px; height: auto; display: block; object-fit: contain;\" ")
                .append("onclick=\"openModal('").append(backImgPath).append("', '").append(frontImgPath).append("')\">\n");
        sb.append("            <p style=\"margin-top: 10px; min-height: 24px;\">Back View (Click to Zoom)</p>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");

        // DATA TABLE
        sb.append("    <div class=\"card-data\">\n");
        sb.append("        <table style=\"width: 100%; border-collapse: collapse; margin-top: 20px;\">\n");
        sb.append("            <tr class=\"specs-table-header\"><th colspan=\"2\" style=\"padding: 10px; text-align: left;\">Technical Specifications</th></tr>\n");
        addTableRow(sb, "Season", c.get("Season"));
        addTableRow(sb, "Team", c.get("Team"));
        addTableRow(sb, "Sport", c.get("Sport"));
        addTableRow(sb, "Manufacturer", c.get("Company"));
        addTableRow(sb, "Brand", c.get("Brand"));
        addTableRow(sb, "Theme", c.get("Theme"));
        addTableRow(sb, "Variant", c.get("Variant"));
        addTableRow(sb, "Card Number", c.get("Number"));

        String serial = c.get("Serial");
        String printRun = c.get("Print Run");
        String combined = c.get("Serial/Print Run");
        String serialDisplay = "-";
        if (isValid(combined)) {
            serialDisplay = combined;
        } else if (isValid(serial) || isValid(printRun)) {
            serialDisplay = (isValid(serial) ? serial : "?") + " / " + (isValid(printRun) ? printRun : "?");
        }
        addTableRow(sb, "Serial / Print Run", serialDisplay);

        addTableRow(sb, "Rookie Card", c.get("Rookie"));
        addTableRow(sb, "Memorabilia", c.get("Game Used"));
        addTableRow(sb, "Autograph", c.get("Autograph"));

        String grading = c.get("Grading Co.") + " " + c.get("Grade");
        if (grading.trim().length() > 1 && !grading.trim().equals("null null")) {
            addTableRow(sb, "Grading", grading);
        }

        sb.append("        </table>\n");
        sb.append("    </div>\n");

        // SEASON / CAREER CONTEXT
        if ("Juwan Howard".equals(c.get("Player"))) {
            String highlights = getSeasonHighlights(c.get("Season"));
            String seasonTeammates = getNotableTeammates(c.get("Season"));
            if (!highlights.isEmpty() || !seasonTeammates.isEmpty()) {
                sb.append("    <div class=\"career-context\" style=\"margin-top: 30px; padding: 15px; background: #f9f9f9; border-left: 5px solid #317EFB;\">\n");
                sb.append("        <h3>Career & Season Context</h3>\n");
                if (!highlights.isEmpty()) {
                    sb.append("        <p><strong>Highlights:</strong> ").append(escapeHtml(highlights)).append("</p>\n");
                }
                if (!seasonTeammates.isEmpty()) {
                    sb.append("        <p><strong>Notable Teammates:</strong> ").append(escapeHtml(seasonTeammates)).append("</p>\n");
                }
                sb.append("    </div>\n");
            }
        }

        // FAQ SECTION
        sb.append("    <section style=\"margin-top: 50px;\">\n");
        sb.append("        <h2>Frequently Asked Questions about this Card</h2>\n");
        sb.append(generateFaqHtml(c));
        sb.append("    </section>\n");

        // RELATED CARDS
        sb.append("    <section style=\"display: flex; flex-wrap: wrap; gap: 20px; justify-content: left;\">\n");
        sb.append("        <h3>More from the ").append(escapeHtml(c.get("Season"))).append(" ").append(escapeHtml(c.get("Player"))).append(" Collection</h3>\n");
        sb.append("        <ul class=\"related-cards-list\">\n");

        int count = 0;
        for (CardData other : allCards) {
            if (other == c) continue;
            if (count >= 6) break;

            String linkTitle = "View card details: " + other.get("Season") + " " + other.get("Brand") + " " + other.get("Variant");
            String otherLink = "../" + other.seasonFolder + "/" + other.filename;

            sb.append("            <li>\n");
            sb.append("                <a href=\"").append(otherLink).append("\" title=\"").append(escapeHtml(linkTitle)).append("\" class=\"modern-button modern-button-footer\" style=\"width: 300px;text-decoration:none;\">").append(escapeHtml(other.get("Brand"))).append(" #").append(escapeHtml(other.get("Number"))).append(" ").append(escapeHtml(other.get("Variant"))).append("</a>\n");
            sb.append("            </li>\n");
            count++;
        }
        sb.append("        </ul>\n");
        sb.append("    </section>\n");
        sb.append(SharedTemplates.getFooter(ROOT));
        sb.append("</main>\n");

        // --- MODAL & SCRIPT LOGIC ---
        sb.append("""
                    <div id="cardModal" class="modal" aria-hidden="true" style="display:none;">
                      <span class="close-modal" aria-label="Close zoomed image" onclick="closeModal()">&times;</span>
                      <button class="flip-modal-btn" aria-label="Flip card to other side" onclick="flipCard()">&#8644; Flip Card</button>
                      <img class="modal-content" id="img01" alt="Zoomed card view">
                    </div>
                
                    <script>
                        var modal = document.getElementById("cardModal");
                        var modalImg = document.getElementById("img01");
                        var currentModalSrc = "";
                        var alternateModalSrc = "";
                
                        function openModal(src, altSrc) {
                          modal.style.display = "flex";
                          modal.style.alignItems = "center";
                          modal.style.justifyContent = "center";
                          modal.setAttribute("aria-hidden", "false");
                          modalImg.src = src;
                          currentModalSrc = src;
                          alternateModalSrc = altSrc;
                        }
                
                        function closeModal() {
                          modal.style.display = "none";
                          modal.setAttribute("aria-hidden", "true");
                        }
                        function flipCard() {
                            var temp = currentModalSrc;
                            currentModalSrc = alternateModalSrc;
                            alternateModalSrc = temp;
                            modalImg.src = currentModalSrc;
                        }
                
                        window.onclick = function(event) {
                          if (event.target == modal) {
                            closeModal();
                          }
                        }
                        document.addEventListener('keydown', function(event) {
                            if (event.key === "Escape") {
                                closeModal();
                            }
                            if (event.key === "ArrowLeft") {
                                var prevLink = document.getElementById('prevCardLink');
                                if (prevLink) window.location.href = prevLink.href;
                            }
                            if (event.key === "ArrowRight") {
                                var nextLink = document.getElementById('nextCardLink');
                                if (nextLink) window.location.href = nextLink.href;
                            }
                        });
                    </script>
                """);

        sb.append("</body>\n</html>");

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String fileNameFromPath(String path) {
        return new File(path).getName();
    }

    private static String getTeamBySeason(String season) {
        if (season == null || season.isEmpty()) return "Unknown Team";
        String s = season.trim();

        if (s.startsWith("1994") || s.startsWith("1995") || s.startsWith("1996")) return "Washington Bullets";
        if (s.startsWith("1997") || s.startsWith("1998") || s.startsWith("1999") || s.startsWith("2000"))
            return "Washington Wizards";
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

        if (c.has("Theme") && !c.get("Brand").contains(c.get("Theme"))) {
            sb.append(" ").append(c.get("Theme"));
        }
        if (c.has("Variant")) {
            sb.append(" ").append(c.get("Variant"));
        }
        if (c.has("Number")) {
            sb.append(" #").append(c.get("Number"));
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String generateBrowserTitle(CardData c, String overviewPage) {
        String player = c.get("Player");
        if (!isValid(player)) player = "Card";
        return generateH1(c) + " | " + player + " Collection";
    }

    private static String generateAltText(CardData c, String view) {
        String base = c.get("Player") + " " + c.get("Season") + " " + c.get("Brand") + " #" + c.get("Number");
        if (view.equals("front")) {
            return "Front view of " + base + " - " + c.get("Variant") + " edition (" + c.get("Team") + ")";
        } else {
            return "Back view of " + base + " showing stats for " + c.get("Team");
        }
    }

    private static String generateMetaDescription(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Details for the ").append(c.get("Season")).append(" ").append(c.get("Brand")).append(" ");
        sb.append(c.get("Player")).append(" card #").append(c.get("Number")).append(" (").append(c.get("Team")).append("). ");
        if (c.has("Variant")) {
            sb.append("Variant: ").append(c.get("Variant")).append(". ");
        }

        String combinedSerial = c.get("Serial/Print Run");
        if (isValid(combinedSerial)) {
            sb.append("Numbered: ").append(combinedSerial).append(". ");
        } else {
            if (c.has("Serial")) {
                sb.append("Numbered: ").append(c.get("Serial")).append("/").append(c.get("Print Run")).append(". ");
            }
        }
        sb.append("View high-res images and specs.");
        return sb.toString();
    }

    private static boolean isHolyGrail(CardData c) {
        String theme = c.get("Theme").toLowerCase();
        String brand = c.get("Brand").toLowerCase();
        String variant = c.get("Variant").toLowerCase();

        return theme.contains("precious metal gems") || theme.contains("pmg") ||
                theme.contains("star rubies") || theme.contains("star ruby") ||
                theme.contains("legacy collection") ||
                brand.contains("precious metal gems") || brand.contains("pmg") ||
                variant.contains("precious metal gems") || variant.contains("pmg") ||
                variant.contains("star rubies") || variant.contains("star ruby") ||
                variant.contains("legacy collection");
    }

    private static boolean isChampionshipSeason(String season) {
        return season.startsWith("2011-12") || season.startsWith("2012-13");
    }

    private static boolean isCollegeEra(CardData c) {
        String season = c.get("Season");
        String team = c.get("Team");
        return "College".equalsIgnoreCase(season) || team.toLowerCase().contains("michigan") || team.toLowerCase().contains("wolverines");
    }

    private static String getSeasonHighlights(String season) {
        if (season.startsWith("1994")) return "The 1994-95 season was Juwan Howard's rookie year, earning him NBA All-Rookie Second Team honors.";
        if (season.startsWith("1995-96")) return "In 1996, Juwan Howard was an NBA All-Star and was named to the All-NBA Third Team.";
        if (season.startsWith("2011-12")) return "Juwan Howard won his first NBA Championship with the Miami Heat in 2012.";
        if (season.startsWith("2012-13")) return "Juwan Howard won his second consecutive NBA Championship with the Miami Heat in 2013.";
        if ("College".equalsIgnoreCase(season)) return "As a member of the legendary 'Fab Five' at the University of Michigan, Juwan Howard reached two NCAA championship games.";
        return "";
    }

    private static String getNotableTeammates(String season) {
        if (season.startsWith("1994") || season.startsWith("1995")) return "Chris Webber, Gheorghe Mureșan, and Calbert Cheaney.";
        if (season.startsWith("1996") || season.startsWith("1997") || season.startsWith("1998")) return "Chris Webber, Rod Strickland, and Tracy Murray.";
        if (season.startsWith("1999") || season.startsWith("2000")) return "Mitch Richmond, Rod Strickland, and Richard Hamilton.";
        if (season.startsWith("2001")) return "Dirk Nowitzki, Steve Nash, and Michael Finley.";
        if (season.startsWith("2003")) return "Tracy McGrady, Tyronn Lue, and Drew Gooden.";
        if (season.startsWith("2004") || season.startsWith("2005") || season.startsWith("2006")) return "Tracy McGrady, Yao Ming, and Dikembe Mutombo.";
        if (season.startsWith("2010") || season.startsWith("2011") || season.startsWith("2012")) return "LeBron James, Dwyane Wade, and Chris Bosh.";
        if ("College".equalsIgnoreCase(season)) return "Chris Webber, Jalen Rose, Jimmy King, and Ray Jackson (The Fab Five).";
        return "";
    }

    private static String generateSeoText(CardData c) {
        StringBuilder sb = new StringBuilder();
        String brand = c.get("Brand");
        String company = c.get("Company");
        String season = c.get("Season");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String team = c.get("Team");
        String player = c.get("Player");

        sb.append("This unique <strong>").append(escapeHtml(player)).append("</strong> card is a highlight of the ");
        sb.append("<strong>").append(escapeHtml(season)).append("</strong> season. Produced by ").append(escapeHtml(company));
        sb.append(" as part of the <strong>").append(escapeHtml(brand)).append("</strong> set");
        if (isValid(theme)) {
            sb.append(", this specific card features the <strong>").append(escapeHtml(theme)).append("</strong> theme");
        }
        sb.append(". ");

        if (brand.toLowerCase().contains("metal universe")) {
            sb.append("SkyBox Metal Universe cards are legendary for their futuristic designs and groundbreaking etching technology. ");
        } else if (brand.toLowerCase().contains("topps chrome")) {
            sb.append("Topps Chrome is one of the most collected brands in the hobby, known for its premium chromium finish and iconic refractors. ");
        } else if (brand.toLowerCase().contains("flawless") || brand.toLowerCase().contains("national treasures")) {
            sb.append("As a high-end release, this card represents the pinnacle of luxury in sports card collecting. ");
        }

        if (theme.toLowerCase().contains("precious metal gems") || theme.toLowerCase().contains("pmg")) {
            sb.append("The <strong>Precious Metal Gems (PMG)</strong> inserts are among the most coveted parallels in the entire world of sports card collecting. ");
        }

        if (isHolyGrail(c)) {
            sb.append("This specific card is considered a <strong>'Holy Grail'</strong> for collectors. Whether it's a PMG, a Star Ruby, or a Legacy Collection, these versions represent the absolute peak of sports card rarity and design innovation. ");
        }

        if (variant.toLowerCase().contains("refractor") || variant.toLowerCase().contains("gold")) {
            sb.append("Collectors particularly appreciate the <strong>").append(escapeHtml(variant)).append("</strong> finish, which adds a beautiful shine and significant value to the card. ");
        } else if (!variant.equalsIgnoreCase("Base") && !variant.isEmpty()) {
            sb.append("It is the <strong>").append(escapeHtml(variant)).append("</strong> version, offering a distinct look compared to the base set. ");
        } else {
            sb.append("It is the classic <strong>Base</strong> version, a fundamental part of any complete collection. ");
        }

        String serial = c.get("Serial");
        String printRun = c.get("Print Run");
        String combined = c.get("Serial/Print Run");

        if (isValid(combined)) {
            sb.append("This is a limited edition card, serial numbered <strong>").append(escapeHtml(combined)).append("</strong>. ");
            if (combined.contains("1/1") || combined.contains("/1")) {
                sb.append("It is a true <strong>One of One</strong> masterpiece, the only one of its kind in existence. ");
            }
        } else if (c.has("Serial")) {
            sb.append("This is a limited edition card, serial numbered <strong>").append(escapeHtml(serial)).append("</strong> ");
            sb.append("out of a total print run of <strong>").append(escapeHtml(printRun)).append("</strong>. ");
            if (serial.equals("1/1") || printRun.equals("1")) {
                sb.append("It is a true <strong>One of One</strong> masterpiece, the only one of its kind in existence. ");
            } else if (isValid(printRun) && printRun.replaceAll("[^0-9]", "").length() > 0 && Integer.parseInt(printRun.replaceAll("[^0-9]", "")) <= 100) {
                sb.append("With such a low print run, it is considered a very rare 'short print' (SP). ");
            }
        }

        if (c.has("Rookie") && c.get("Rookie").equalsIgnoreCase("Yes")) {
            sb.append("As an official <strong>Rookie Card</strong>, it captures ").append(escapeHtml(player)).append(" at the very beginning of their professional career. ");
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append("Notably, this card features an authentic <strong>Autograph</strong>, providing a direct link to the player's legacy. ");
        }

        if (c.has("Game Used") && c.get("Game Used").equalsIgnoreCase("Yes")) {
            sb.append("It also contains a piece of <strong>Game Used Memorabilia</strong>, making it a tangible piece of sports history. ");
        }

        if (isValid(team)) {
            sb.append("It captures ").append(escapeHtml(player)).append(" during their time with the <strong>").append(escapeHtml(team)).append("</strong>. ");

            if ("Juwan Howard".equals(c.get("Player"))) {
                if (isChampionshipSeason(c.get("Season"))) {
                    sb.append("This card hails from one of Juwan Howard's <strong>NBA Championship</strong> winning seasons with the Miami Heat, adding historical significance to the piece. ");
                }
                if (isCollegeEra(c)) {
                    sb.append("This card commemorates Juwan Howard's legendary time as part of the <strong>'Fab Five'</strong> at the University of Michigan, one of the most influential groups in college basketball history. ");
                }
            }
        }

        return sb.toString();
    }

    private static String generateFaqHtml(CardData c) {
        StringBuilder sb = new StringBuilder();

        String season = c.get("Season");
        String brand = c.get("Brand");
        String company = c.get("Company");
        String variant = c.get("Variant");
        String player = c.get("Player");

        if (isHolyGrail(c)) {
            sb.append(createFaqItem("Is this a 'Holy Grail' card for collectors?", "Yes, this card belongs to one of the most prestigious series in the hobby (like PMG, Legacy, or Star Rubies). These are extremely rare and highly sought after by high-end collectors worldwide."));
        }

        String combined = c.get("Serial/Print Run");
        if (isValid(combined)) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + escapeHtml(combined) + ", making it a limited edition collectible."));
        } else if (c.has("Serial")) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + escapeHtml(c.get("Serial")) + " out of a total print run of " + escapeHtml(c.get("Print Run")) + ", making it a limited edition collectible."));
        } else {
            sb.append(createFaqItem("Is this card numbered?", "No, this version of the card was not individually serial numbered by " + escapeHtml(company) + ". These are often referred to as 'pack-pulled' or 'un-numbered' versions."));
        }

        if (c.has("Rookie")) {
            String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ?
                    "Yes, this is an official Rookie Card (RC) from " + escapeHtml(player) + "'s debut " + escapeHtml(season) + " season, which is highly sought after by collectors." :
                    "No, this card was released during the " + escapeHtml(season) + " season, which was part of " + escapeHtml(player) + "'s established career.";
            sb.append(createFaqItem("Is this a " + escapeHtml(player) + " Rookie Card?", rookieAns));
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(createFaqItem("Is the autograph on this card authentic?", "Yes, this card features a manufacturer-certified autograph. " + escapeHtml(company) + " guarantees the authenticity of the signature on the card."));
        }

        if (c.has("Game Used") && c.get("Game Used").equalsIgnoreCase("Yes")) {
            sb.append(createFaqItem("What kind of memorabilia is on this card?", "This card contains a piece of game-used memorabilia, typically a jersey or patch worn by the player in a game."));
        }

        if (brand.toLowerCase().contains("metal universe")) {
            sb.append(createFaqItem("What makes SkyBox Metal Universe cards special?", "Metal Universe cards from the late 90s are famous for their unique 'galactic' backgrounds and high-quality etching, with the PMG parallels being some of the most expensive cards in the hobby."));
        }

        if (isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(createFaqItem("What is the '" + escapeHtml(variant) + "' variant?", "The '" + escapeHtml(variant) + "' is a parallel version of the base card. Parallels usually have different colors, finishes, or lower print runs than the standard version."));
        }

        sb.append(createFaqItem("Which team is " + escapeHtml(player) + " representing on this card?", "On this " + escapeHtml(season) + " " + escapeHtml(brand) + " card, " + escapeHtml(player) + " is shown as a member of the " + escapeHtml(c.get("Team")) + "."));

        if ("Juwan Howard".equals(c.get("Player"))) {
            if (isChampionshipSeason(c.get("Season"))) {
                sb.append(createFaqItem("Did Juwan Howard win a championship this season?", "Yes! This card is from the " + escapeHtml(season) + " season when Juwan Howard won an NBA Championship with the Miami Heat."));
            }

            String teammates = getNotableTeammates(c.get("Season"));
            if (!teammates.isEmpty()) {
                sb.append(createFaqItem("Who were some of Juwan Howard's notable teammates during this season?", "During the " + escapeHtml(season) + " season with the " + escapeHtml(c.get("Team")) + ", Juwan played alongside players like " + escapeHtml(teammates)));
            }

            if (isCollegeEra(c)) {
                sb.append(createFaqItem("What is Juwan Howard's college legacy?", "Juwan Howard was a key member of the 'Fab Five' at the University of Michigan, widely considered one of the most iconic and influential teams in college basketball history."));
            }
        }

        if (c.has("Grade")) {
            sb.append(createFaqItem("Is this card professionally graded?", "Yes, this card has been graded by " + escapeHtml(c.get("Grading Co.")) + " and received a score of " + escapeHtml(c.get("Grade")) + ". Professional grading helps verify the condition and authenticity of high-value cards."));
        }

        return sb.toString();
    }

    private static String createFaqItem(String question, String answer) {
        return "<details class=\"faq-details\">" + "<summary class=\"faq-summary\">" + question + "</summary>" + "<p class=\"faq-answer\">" + answer + "</p>" + "</details>";
    }

    private static String generateJsonLd(CardData c, String desc, String h1Title, String overviewPage, String imageBaseName) {
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-front.webp";
        String backImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-back.webp";

        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"application/ld+json\">\n");
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@graph\": [\n");

        // Breadcrumbs
        sb.append("    {\n");
        sb.append("      \"@type\": \"BreadcrumbList\",\n");
        sb.append("      \"itemListElement\": [\n");
        sb.append("        { \"@type\": \"ListItem\", \"position\": 1, \"name\": \"Home\", \"item\": \"").append(BASE_URL).append("\" },\n");
        sb.append("        { \"@type\": \"ListItem\", \"position\": 2, \"name\": \"Collection\", \"item\": \"").append(BASE_URL).append("/").append(overviewPage).append("\" },\n");
        sb.append("        { \"@type\": \"ListItem\", \"position\": 3, \"name\": \"").append(escapeJson(c.get("Season"))).append("\", \"item\": \"").append(BASE_URL).append("/").append(overviewPage).append("#").append(escapeJson(c.get("Number"))).append("\" },\n");
        sb.append("        { \"@type\": \"ListItem\", \"position\": 4, \"name\": \"").append(escapeJson(h1Title)).append("\" }\n");
        sb.append("      ]\n");
        sb.append("    },\n");

        // Visual Artwork
        sb.append("    {\n");
        sb.append("      \"@type\": \"VisualArtwork\",\n");
        sb.append("      \"name\": \"").append(escapeJson(h1Title)).append("\",\n");
        sb.append("      \"image\": [ \"").append(frontImgUrl).append("\", \"").append(backImgUrl).append("\" ],\n");
        sb.append("      \"description\": \"").append(escapeJson(desc)).append("\",\n");
        sb.append("      \"creator\": { \"@type\": \"Organization\", \"name\": \"").append(escapeJson(c.get("Company"))).append("\" },\n");
        sb.append("      \"artMedium\": \"Trading Card\",\n");
        sb.append("      \"artform\": \"Sports Memorabilia\"\n");
        sb.append("    },\n");

        // FAQ Page
        sb.append("    {\n");
        sb.append("      \"@type\": \"FAQPage\",\n");
        sb.append("      \"mainEntity\": [\n");

        List<String> faqItems = new ArrayList<>();
        String season = c.get("Season");
        String brand = c.get("Brand");
        String company = c.get("Company");
        String player = c.get("Player");

        String combined = c.get("Serial/Print Run");
        if (isValid(combined)) {
            faqItems.add(createJsonLdQuestion("How rare is this specific card?", "This card is serially numbered " + combined + ", making it a limited edition collectible."));
        } else if (c.has("Serial")) {
            faqItems.add(createJsonLdQuestion("How rare is this specific card?", "This card is serially numbered " + c.get("Serial") + " out of a total print run of " + c.get("Print Run") + ", making it a limited edition collectible."));
        } else {
            faqItems.add(createJsonLdQuestion("Is this card numbered?", "No, this version of the card was not individually serial numbered by " + company + ". These are often referred to as 'pack-pulled' or 'un-numbered' versions."));
        }

        if (c.has("Rookie")) {
            String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ?
                    "Yes, this is an official Rookie Card (RC) from " + player + "'s debut " + season + " season, which is highly sought after by collectors." :
                    "No, this card was released during the " + season + " season, which was part of " + player + "'s established career.";
            faqItems.add(createJsonLdQuestion("Is this a " + player + " Rookie Card?", rookieAns));
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            faqItems.add(createJsonLdQuestion("Is the autograph on this card authentic?", "Yes, this card features a manufacturer-certified autograph. " + company + " guarantees the authenticity of the signature on the card."));
        }

        if (c.has("Game Used") && c.get("Game Used").equalsIgnoreCase("Yes")) {
            faqItems.add(createJsonLdQuestion("What kind of memorabilia is on this card?", "This card contains a piece of game-used memorabilia, typically a jersey or patch worn by the player in a game."));
        }

        if (brand.toLowerCase().contains("metal universe")) {
            faqItems.add(createJsonLdQuestion("What makes SkyBox Metal Universe cards special?", "Metal Universe cards from the late 90s are famous for their unique 'galactic' backgrounds and high-quality etching, with the PMG parallels being some of the most expensive cards in the hobby."));
        }

        faqItems.add(createJsonLdQuestion("Which team is " + player + " representing on this card?", "On this " + season + " " + brand + " card, " + player + " is shown as a member of the " + c.get("Team") + "."));

        if (c.has("Grade")) {
            faqItems.add(createJsonLdQuestion("Is this card professionally graded?", "Yes, this card has been graded by " + c.get("Grading Co.") + " and received a score of " + c.get("Grade") + ". Professional grading helps verify the condition and authenticity of high-value cards."));
        }

        sb.append(String.join(",\n", faqItems)).append("\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    private static String createJsonLdQuestion(String q, String a) {
        return "        { \"@type\": \"Question\", \"name\": \"" + escapeJson(q) + "\", \"acceptedAnswer\": { \"@type\": \"Answer\", \"text\": \"" + escapeJson(a) + "\" } }";
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
        return text.replace("\"", "&quot;").replace("'", "&#39;");
    }
}