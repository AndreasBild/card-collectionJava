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

public class CardPageGenerator {

    // --- KONFIGURATION ---
    private static final String INPUT_FILE = "output/Juwan-Howard-Collection.html";
    private static final String OUTPUT_INDEX = "newIndex/Juwan-Howard-Collection.html";
    private static final String BASE_FOLDER = "cards";
    // Pfade von der Unterseite aus gesehen:
    private static final String RELATIVE_CSS_PATH = "../../css/main.css";
    private static final String RELATIVE_IMAGES_PATH = "../../images";
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final Logger log = LoggerFactory.getLogger(CardPageGenerator.class);
    // ---------------------

    // --- INTERNE KLASSE: KARTENDATEN ---
    static class CardData {
        Map<String, String> attributes;
        String filenameBase;
        String filename;
        String seasonFolder;
        String fullRelativePath;

        public CardData(Map<String, String> attributes) {
            this.attributes = new HashMap<>(attributes);

            // LOGIK 1: TEAM ERMITTELN
            String currentTeam = this.attributes.get("Team");
            if (!isValid(currentTeam)) {
                System.out.println(this.attributes.get("Season") + " " +this.filename +this.attributes.get("Number") + currentTeam);
                String calculatedTeam = getTeamBySeason(this.attributes.get("Season"));
                this.attributes.put("Team", calculatedTeam);
            }

            calculatePaths();
        }

        private void calculatePaths() {
            List<String> filenameTokens = new ArrayList<>();

            addIfPresent(filenameTokens, attributes.get("Player"));
            addIfPresent(filenameTokens, attributes.get("Team"));
            addIfPresent(filenameTokens, attributes.get("Season"));
            addIfPresent(filenameTokens, attributes.get("Company"));
            addIfPresent(filenameTokens, attributes.get("Brand"));
            addIfPresent(filenameTokens, attributes.get("Theme"));
            addIfPresent(filenameTokens, attributes.get("Variant"));
            addIfPresent(filenameTokens, attributes.get("Number"));

            // LOGIK 3: SERIAL NUMBER INTEGRATION
            String serial = attributes.get("Serial");
            if (isValid(serial) && !serial.equals("0")) {
                filenameTokens.add("sn" + serial);
            }

            // LOGIK 4: GRADING
            String grade = attributes.get("Grade");
            if (isValid(grade)) filenameTokens.add(grade);

            this.filenameBase = cleanFilename(String.join("-", filenameTokens));
            this.filename = this.filenameBase + ".html";

            String seasonRaw = attributes.get("Season");
            this.seasonFolder = isValid(seasonRaw) ? cleanFilename(seasonRaw) : "Unknown_Season";
            this.fullRelativePath = BASE_FOLDER + "/" + this.seasonFolder + "/" + this.filename;
        }

        public String get(String key) {
            return attributes.getOrDefault(key, "").trim();
        }

        public boolean has(String key) {
            String val = get(key);
            return isValid(val) && !val.equalsIgnoreCase("No") && !val.equalsIgnoreCase("None");
        }
    }

    // --- MAIN METHODE ---
    public static void main(String[] args) {
        try {
            System.out.println("Reading " + INPUT_FILE + "...");
            File input = new File(INPUT_FILE);
            Document doc = Jsoup.parse(input, "UTF-8");

            Elements tables = doc.select("table");
            if (tables.isEmpty()) {
                System.err.println("Error: No tables found!");
                return;
            }

            System.out.println("Found " + tables.size() + " tables. Processing...");

            int tableCount = 1;
            for (Element table : tables) {
                System.out.println("Processing Table #" + tableCount + "...");
                processTable(table);
                tableCount++;
            }

            Files.writeString(Paths.get(OUTPUT_INDEX), doc.outerHtml(), StandardCharsets.UTF_8);
            System.out.println("DONE! Index updated.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processTable(Element table) throws IOException {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return;

        Elements headerCols = rows.get(0).select("th");
        if (headerCols.isEmpty()) return;

        String[] headers = new String[headerCols.size()];
        for (int i = 0; i < headerCols.size(); i++) {
            headers[i] = headerCols.get(i).text().trim();
        }

        List<CardData> allCardsInTable = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.select("td");
            if (cols.isEmpty()) continue;

            Map<String, String> dataMap = new HashMap<>();
            for (int j = 0; j < cols.size() && j < headers.length; j++) {
                dataMap.put(headers[j], cols.get(j).text().trim());
            }
            allCardsInTable.add(new CardData(dataMap));
        }

        for (int i = 0; i < allCardsInTable.size(); i++) {
            CardData currentCard = allCardsInTable.get(i);
            CardData prevCard = (i > 0) ? allCardsInTable.get(i - 1) : null;
            CardData nextCard = (i < allCardsInTable.size() - 1) ? allCardsInTable.get(i + 1) : null;

            Path folderPath = Paths.get(BASE_FOLDER, currentCard.seasonFolder);
            Files.createDirectories(folderPath);
            Path filePath = folderPath.resolve(currentCard.filename);

            createSubPage(currentCard, filePath, prevCard, nextCard, allCardsInTable);

            // Link Update in der Index-Tabelle
            Element row = rows.get(i + 1);
            Elements cols = row.select("td");
            if (!cols.isEmpty()) {
                Element playerCell = cols.get(0);
                String originalText = playerCell.text();
                playerCell.empty();
                playerCell.appendElement("a")
                        .attr("href", currentCard.fullRelativePath)
                        .attr("title", "View details for " + currentCard.get("Season") + " " + currentCard.get("Brand") + " #" + currentCard.get("Number"))
                        .text(originalText);
            }
            System.out.println(" -> Generated: " + currentCard.filename);
        }
    }

    // --- TEMPLATE HEADER ---
    private static final String TEMPLATE_HEAD_SCRIPTS = """
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <meta http-equiv="X-UA-Compatible" content="IE=edge">
                        <meta name="theme-color" content="#317EFB">
                        <link rel="preload" href="../../css/main.css" as="style">
                        <link href="../../css/main.css" rel="stylesheet" type="text/css">
                        <link rel="preconnect" href="https://www.googletagmanager.com">
                        <script async src="https://www.googletagmanager.com/gtag/js?id=G-535TKYRZTR"></script>
                        <script>
                               function loadAnalytics() {
                                 var script = document.createElement('script');
                                 script.src = "https://www.googletagmanager.com/gtag/js?id=G-535TKYRZTR";
                                 script.async = true;
                                 document.head.appendChild(script);
                                 window.dataLayer = window.dataLayer || [];
                                 function gtag(){dataLayer.push(arguments);}
                                 gtag('js', new Date());
                                 gtag('config', 'G-535TKYRZTR');
                               }
                               window.addEventListener('scroll', loadAnalytics, {once: true});
                               window.addEventListener('mousemove', loadAnalytics, {once: true});
                               window.addEventListener('touchstart', loadAnalytics, {once: true});
                        </script>
                        <meta name="author" content="Mauli Maulmann - Content Creator">
                        <meta name="publisher" content="Mauli Maulmann - Card Collector">
                        <meta name="robots" content="index, follow, max-snippet:-1, max-image-preview:large">
                        <link href="../../favicon/favicon.ico" rel="icon" sizes="32x32">
                        <link href="../../favicon/apple-touch-icon.png" rel="apple-touch-icon">
                        <link rel="apple-touch-icon" sizes="180x180" href="../../favicon/apple-touch-icon.png">
                        <link rel="icon" type="image/png" sizes="32x32" href="../../favicon/favicon-32x32.png">
                        <link rel="icon" type="image/png" sizes="194x194" href="../../favicon/favicon-194x194.png">
                        <link rel="icon" type="image/png" sizes="192x192" href="../../favicon/android-chrome-192x192.png">
                        <link rel="icon" type="image/png" sizes="16x16" href="../../favicon/favicon-16x16.png">
                        <link rel="manifest" href="../../manifest.json">
                        <link rel="mask-icon" href="../../favicon/safari-pinned-tab.svg" color="#317EFB">
                        <meta name="apple-mobile-web-app-title" content="Maulmann.de">
                        <meta name="application-name" content="Maulmann.de">
                        <meta name="msapplication-TileColor" content="#317EFB">
                        <link rel="preload" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
                                  as="style">
                        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
            """;

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards) throws IOException {
        StringBuilder sb = new StringBuilder();

        String h1Title = generateH1(c);
        String browserTitle = generateBrowserTitle(c);
        String metaDesc = generateMetaDescription(c);

        String seasonImgFolder = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder;
        String frontImgPath = seasonImgFolder + "/" + c.filenameBase + "-front.jpg";
        String backImgPath = seasonImgFolder + "/" + c.filenameBase + "-back.jpg";

        String frontAlt = generateAltText(c, "front");
        String backAlt = generateAltText(c, "back");
        String frontImgTitle = "Front scan of " + c.get("Player") + " " + c.get("Brand") + " (" + c.get("Season") + ")";
        String backImgTitle = "Back scan of " + c.get("Player") + " " + c.get("Brand") + " (" + c.get("Season") + ")";

        // HTML START
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append("    <title>").append(browserTitle).append("</title>\n");
        sb.append("    <meta name=\"description\" content=\"").append(metaDesc).append("\">\n");

        // Open Graph Tags & LCP Preload
        sb.append("    <meta property=\"og:title\" content=\"").append(escapeHtml(browserTitle)).append("\">\n");
        sb.append("    <meta property=\"og:description\" content=\"").append(escapeHtml(metaDesc)).append("\">\n");
        sb.append("    <meta property=\"og:image\" content=\"").append(frontImgPath).append("\">\n");
        sb.append("    <meta property=\"og:type\" content=\"website\">\n");
        sb.append("    <link rel=\"preload\" as=\"image\" href=\"").append(frontImgPath).append("\">\n");

        sb.append(TEMPLATE_HEAD_SCRIPTS).append("\n");

        // Schema.org
        sb.append(generateJsonLd(c, metaDesc, h1Title));

        sb.append("</head>\n<body>\n");

        // NAVIGATION (Responsive topnav)
        sb.append("<div class=\"topnav\" id=\"myTopnav\">\n");
        sb.append("    <a href=\"../../index.html\">Home</a>\n");
        sb.append("    <a href=\"../../collection.html\">Juwan Howard PC</a>\n");
        sb.append("    <a href=\"../../Baseball.html\">Baseball</a>\n");
        sb.append("    <a href=\"../../Flawless.html\">Flawless</a>\n");
        sb.append("    <a href=\"../../Wantlist.html\">Wantlist</a>\n");
        sb.append("    <a href=\"../../Panini.html\">Panini</a>\n");
        sb.append("    <a href=\"javascript:void(0);\" class=\"icon\" onclick=\"myFunction()\">\n");
        sb.append("        <i class=\"fa fa-bars\"></i>\n");
        sb.append("    </a>\n");
        sb.append("</div>\n");

        sb.append("<script>\n");
        sb.append("function myFunction() {\n");
        sb.append("  var x = document.getElementById(\"myTopnav\");\n");
        sb.append("  if (x.className === \"topnav\") {\n");
        sb.append("    x.className += \" responsive\";\n");
        sb.append("  } else {\n");
        sb.append("    x.className = \"topnav\";\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("</script>\n");

        // SUB-NAV (Overview, Prev, Next)
        sb.append("<nav class=\"detail-nav\" style=\"display: flex; justify-content: space-between; align-items: center; width: 100%; border: none; background: transparent;\">\n");
        sb.append("    <a href=\"../../collection.html\" class=\"modern-button\" style=\"text-decoration:none;\" title=\"Return to the complete card collection overview\">&larr; Overview</a>\n");
        sb.append("    <div>\n");
        if (prev != null) {
            String prevTitle = "Go to previous card: " + prev.get("Season") + " " + prev.get("Brand");
            sb.append("        <a id=\"prevCardLink\" href=\"").append(prev.filename).append("\" title=\"").append(prevTitle).append("\" style=\"margin-right:10px; text-decoration:none;\">&laquo; Prev</a>\n");
        }
        if (next != null) {
            String nextTitle = "Go to next card: " + next.get("Season") + " " + next.get("Brand");
            sb.append("        <a id=\"nextCardLink\" href=\"").append(next.filename).append("\" title=\"").append(nextTitle).append("\" style=\"text-decoration:none;\">Next &raquo;</a>\n");
        }
        sb.append("    </div>\n");
        sb.append("</nav>\n");

        // MAIN CONTENT
        sb.append("<main class=\"detail-main\">\n");

        // HEADER
        sb.append("    <header class=\"detail-header\">\n");
        sb.append("        <h1>").append(h1Title).append("</h1>\n");
        sb.append("        <p class=\"sub-title\">").append(c.get("Season")).append(" ").append(c.get("Company")).append(" ").append(c.get("Brand")).append("</p>\n");
        sb.append("        <p class=\"meta-info\">").append(c.get("Theme")).append(" &bull; ").append(c.get("Variant")).append(" &bull; #").append(c.get("Number")).append("</p>\n");
        sb.append("    </header>\n");

        // SEO TEXT
        sb.append("    <article class=\"seo-box\">\n");
        sb.append("        <h3>About this Card</h3>\n");
        sb.append("        <p>").append(generateSeoText(c)).append("</p>\n");
        sb.append("    </article>\n");

        // --- IMAGES SECTION ---
        sb.append("    <div class=\"card-images-container\">\n");

        // Front Image (Kein Lazy-Loading!)
        sb.append("        <div class=\"card-image-wrapper\">\n");
        sb.append("            <img src=\"").append(frontImgPath).append("\" ")
                .append("alt=\"").append(frontAlt).append("\" ")
                .append("title=\"").append(frontImgTitle).append("\" ")
                .append("onclick=\"openModal('").append(frontImgPath).append("', '").append(backImgPath).append("')\">\n");
        sb.append("            <p>Front View (Click to Zoom)</p>\n");
        sb.append("        </div>\n");

        // Back Image (Mit Lazy-Loading)
        sb.append("        <div class=\"card-image-wrapper\">\n");
        sb.append("            <img src=\"").append(backImgPath).append("\" ")
                .append("alt=\"").append(backAlt).append("\" ")
                .append("title=\"").append(backImgTitle).append("\" ")
                .append("loading=\"lazy\" ")
                .append("onclick=\"openModal('").append(backImgPath).append("', '").append(frontImgPath).append("')\">\n");
        sb.append("            <p>Back View (Click to Zoom)</p>\n");
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

        String serialInfo = c.get("Serial") + " / " + c.get("Print Run");
        if (c.get("Serial").equals("0")) serialInfo = "Not numbered";
        addTableRow(sb, "Serial / Print Run", serialInfo);

        addTableRow(sb, "Rookie Card", c.get("Rookie"));
        addTableRow(sb, "Memorabilia", c.get("Game Used"));
        addTableRow(sb, "Autograph", c.get("Autograph"));

        String grading = c.get("Grading Co.") + " " + c.get("Grade");
        if (grading.trim().length() > 1 && !grading.trim().equals("null null")) {
            addTableRow(sb, "Grading", grading);
        }

        sb.append("        </table>\n");
        sb.append("    </div>\n");

        // FAQ SECTION
        sb.append("    <section style=\"margin-top: 50px;\">\n");
        sb.append("        <h2>Frequently Asked Questions about this Card</h2>\n");
        sb.append(generateFaqHtml(c));
        sb.append("    </section>\n");

        // RELATED CARDS
        sb.append("    <section class=\"related-cards-section\">\n");
        sb.append("        <h3>More from the ").append(c.get("Season")).append(" Collection</h3>\n");
        sb.append("        <ul class=\"related-cards-list\">\n");

        int count = 0;
        for (CardData other : allCards) {
            if (other == c) continue;
            if (count >= 6) break;

            String linkTitle = "View card details: " + other.get("Season") + " " + other.get("Brand") + " " + other.get("Variant");

            sb.append("            <li class=\"related-card-item\">\n");
            sb.append("                <a href=\"").append(other.filename).append("\" title=\"").append(linkTitle).append("\" style=\"color:#317EFB; text-decoration:none;\">")
                    .append(other.get("Brand")).append(" #").append(other.get("Number")).append(" ").append(other.get("Variant"))
                    .append("</a>\n");
            sb.append("            </li>\n");
            count++;
        }
        sb.append("        </ul>\n");
        sb.append("    </section>\n");

        sb.append("</main>\n");


        // --- MODAL & SCRIPT LOGIC (inkl. Tastatur und Aria) ---
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
                // Keyboard Navigation
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

    // --- HELPER METHODS ---

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

        if (c.has("Theme") && !c.get("Brand").contains(c.get("Theme"))) {
            sb.append(" ").append(c.get("Theme"));
        }
        sb.append(" ").append(c.get("Variant"));
        if (c.has("Number")) {
            sb.append(" #").append(c.get("Number"));
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String generateBrowserTitle(CardData c) {
        return generateH1(c) + " | Juwan Howard Collection";
    }

    private static String generateAltText(CardData c, String view) {
        String base = "Juwan Howard " + c.get("Season") + " " + c.get("Brand") + " #" + c.get("Number");
        if (view.equals("front")) {
            return "Front view of " + base + " basketball card - " + c.get("Variant") + " edition (" + c.get("Team") + ")";
        } else {
            return "Back view of " + base + " showing stats for " + c.get("Team");
        }
    }

    private static String generateMetaDescription(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Details for the ").append(c.get("Season")).append(" ").append(c.get("Brand")).append(" ");
        sb.append(c.get("Player")).append(" card #").append(c.get("Number")).append(" (").append(c.get("Team")).append("). ");
        sb.append("Variant: ").append(c.get("Variant")).append(". ");

        if (c.has("Serial")) {
            sb.append("Numbered: ").append(c.get("Serial")).append("/").append(c.get("Print Run")).append(". ");
        }
        if (c.has("Grade")) {
            sb.append("Graded: ").append(c.get("Grading Co.")).append(" ").append(c.get("Grade")).append(". ");
        }
        sb.append("View high-res images and specs.");
        return sb.toString();
    }

    private static String generateSeoText(CardData c) {
        StringBuilder sb = new StringBuilder();
        sb.append("This unique <strong>").append(c.get("Player")).append("</strong> basketball card is a highlight of the ");
        sb.append("<strong>").append(c.get("Season")).append("</strong> season. Produced by ").append(c.get("Company"));
        sb.append(" as part of the <strong>").append(c.get("Brand")).append("</strong> set, this specific card features the ");
        sb.append("<strong>").append(c.get("Theme")).append("</strong> theme. ");

        if (c.get("Variant").toLowerCase().contains("refractor") || c.get("Variant").toLowerCase().contains("gold")) {
            sb.append("Collectors particularly appreciate the ").append(c.get("Variant")).append(" finish, making it a standout piece. ");
        } else {
            sb.append("It is the ").append(c.get("Variant")).append(" version. ");
        }

        if (c.has("Serial")) {
            sb.append("This is a limited edition card, serial numbered <strong>").append(c.get("Serial")).append("</strong> ");
            sb.append("out of a total print run of <strong>").append(c.get("Print Run")).append("</strong>. ");
            if (c.get("Serial").equals("1/1") || c.get("Print Run").equals("1")) {
                sb.append("It is a true <strong>One of One</strong> masterpiece. ");
            }
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append("Notably, this card features an authentic <strong>Autograph</strong>, significantly adding to its rarity and value. ");
        }

        if (c.has("Game Used") && c.get("Game Used").equalsIgnoreCase("Yes")) {
            sb.append("It also contains a piece of <strong>Game Used Memorabilia</strong> (Jersey/Patch). ");
        }

        sb.append("It captures Juwan Howard during his time with the <strong> ").append(c.get("Team")).append("</strong>.");
        return sb.toString();
    }

    private static String generateFaqHtml(CardData c) {
        StringBuilder sb = new StringBuilder();

        if (c.has("Serial")) {
            sb.append(createFaqItem("How rare is this specific card?",
                    "This card is serially numbered " + c.get("Serial") + " out of a total print run of " + c.get("Print Run") + "."));
        } else {
            sb.append(createFaqItem("Is this card numbered?",
                    "No, this version of the card was not serial numbered by the manufacturer."));
        }

        String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes")
                ? "Yes, this is an official Rookie Card (RC) from the " + c.get("Season") + " class!"
                : "No, this is a veteran card released during the " + c.get("Season") + " season.";
        sb.append(createFaqItem("Is this a Rookie Card?", rookieAns));

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(createFaqItem("Is the autograph authentic?",
                    "Yes, this card features a manufacturer-certified autograph guaranteed by " + c.get("Company") + "."));
        }

        if (c.has("Grade")) {
            sb.append(createFaqItem("What is the condition of this card?",
                    "This card has been professionally graded by " + c.get("Grading Co.") + " and received a grade of " + c.get("Grade") + "."));
        }

        sb.append(createFaqItem("Which team did Juwan Howard play for on this card?",
                "This card features Juwan Howard in a " + c.get("Team") + " uniform."));

        return sb.toString();
    }

    private static String createFaqItem(String question, String answer) {
        return "<details class=\"faq-details\">" +
                "<summary class=\"faq-summary\">" + question + "</summary>" +
                "<p class=\"faq-answer\">" + answer + "</p>" +
                "</details>";
    }

    // UPDATE: Neues sauberes Schema (VisualArtwork + FAQPage) anstatt "SoldOut"-Produkte
    private static String generateJsonLd(CardData c, String desc, String h1Title) {
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + c.filenameBase + "-front.jpg";
        String backImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + c.filenameBase + "-back.jpg";

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
        sb.append("        { \"@type\": \"ListItem\", \"position\": 2, \"name\": \"Collection\", \"item\": \"").append(BASE_URL).append("/index.html\" },\n");
        sb.append("        { \"@type\": \"ListItem\", \"position\": 3, \"name\": \"").append(c.get("Season")).append("\", \"item\": \"").append(BASE_URL).append("/").append(c.seasonFolder).append("/index.html#").append(c.get("Number")).append("\" },\n");
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

        // FAQ Page Dynamic Generation
        sb.append("    {\n");
        sb.append("      \"@type\": \"FAQPage\",\n");
        sb.append("      \"mainEntity\": [\n");

        List<String> faqItems = new ArrayList<>();

        // Q1 Rarity
        if (c.has("Serial")) {
            faqItems.add(createJsonLdQuestion("How rare is this specific card?", "This card is serially numbered " + c.get("Serial") + " out of a total print run of " + c.get("Print Run") + "."));
        } else {
            faqItems.add(createJsonLdQuestion("Is this card numbered?", "No, this version of the card was not serial numbered by the manufacturer."));
        }

        // Q2 Rookie
        String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ? "Yes, this is an official Rookie Card (RC) from the " + c.get("Season") + " class!" : "No, this is a veteran card released during the " + c.get("Season") + " season.";
        faqItems.add(createJsonLdQuestion("Is this a Rookie Card?", rookieAns));

        // Q3 Auto
        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            faqItems.add(createJsonLdQuestion("Is the autograph authentic?", "Yes, this card features a manufacturer-certified autograph guaranteed by " + c.get("Company") + "."));
        }

        // Q4 Grade
        if (c.has("Grade")) {
            faqItems.add(createJsonLdQuestion("What is the condition of this card?", "This card has been professionally graded by " + c.get("Grading Co.") + " and received a grade of " + c.get("Grade") + "."));
        }

        // Q5 Team
        faqItems.add(createJsonLdQuestion("Which team did Juwan Howard play for on this card?", "This card features Juwan Howard in a " + c.get("Team") + " uniform."));

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
        sb.append("            <tr><th class=\"specs-th\">")
                .append(title)
                .append("</th><td class=\"specs-td\">")
                .append(isValid(value) ? value : "-")
                .append("</td></tr>\n");
    }

    private static void addIfPresent(List<String> list, String value) {
        if (isValid(value)) list.add(value);
    }

    private static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty() && !value.equals("0");
    }

    private static String cleanFilename(String text) {
        if (text == null) return "";
        String clean = text.replace("/", "-").replace("\\", "-");
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