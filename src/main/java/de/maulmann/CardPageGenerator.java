package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CardPageGenerator {

    // --- KONFIGURATION ---
    private static final String INPUT_FILE = "output/index.html";
    private static final String OUTPUT_INDEX = "newIndex/index.html";
    private static final String BASE_FOLDER = "cards";
    // Pfade von der Unterseite aus gesehen:
    private static final String RELATIVE_CSS_PATH = "../../css/main.css";
    private static final String RELATIVE_IMAGES_PATH = "../../images";
    private static final String BASE_URL = "https://www.maulmann.de";
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
                String calculatedTeam = getTeamBySeason(this.attributes.get("Season"));
                this.attributes.put("Team", calculatedTeam);
            }

            calculatePaths();
        }

        private void calculatePaths() {
            List<String> filenameTokens = new ArrayList<>();

            // LOGIK 2: DATEINAMEN BAUEN
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

            // Basisnamen bereinigen
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

            // Link Update
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
            """;

    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards) throws IOException {
        StringBuilder sb = new StringBuilder();

        String h1Title = generateH1(c);
        String browserTitle = generateBrowserTitle(c);
        String metaDesc = generateMetaDescription(c);
        String frontAlt = generateAltText(c, "front");
        String backAlt = generateAltText(c, "back");
        String frontImgTitle = "Front scan of " + c.get("Player") + " " + c.get("Brand") + " (" + c.get("Season") + ")";
        String backImgTitle = "Back scan of " + c.get("Player") + " " + c.get("Brand") + " (" + c.get("Season") + ")";

        // HTML START
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append("    <title>").append(browserTitle).append("</title>\n");
        sb.append("    <meta name=\"description\" content=\"").append(metaDesc).append("\">\n");
        sb.append(TEMPLATE_HEAD_SCRIPTS).append("\n");

        sb.append(generateJsonLd(c, metaDesc, h1Title));

        sb.append("</head>\n<body>\n");

        // NAVIGATION (CSS class: detail-nav)
        sb.append("<nav class=\"detail-nav\">\n");
        sb.append("    <a href=\"../../index.html\" class=\"modern-button\" style=\"text-decoration:none;\" title=\"Return to the complete card collection overview\">&larr; Overview</a>\n");

        sb.append("    <div>\n");
        if (prev != null) {
            String prevTitle = "Go to previous card: " + prev.get("Season") + " " + prev.get("Brand");
            sb.append("        <a href=\"").append(prev.filename).append("\" title=\"").append(prevTitle).append("\" style=\"margin-right:10px; text-decoration:none;\">&laquo; Prev</a>\n");
        }
        if (next != null) {
            String nextTitle = "Go to next card: " + next.get("Season") + " " + next.get("Brand");
            sb.append("        <a href=\"").append(next.filename).append("\" title=\"").append(nextTitle).append("\" style=\"text-decoration:none;\">Next &raquo;</a>\n");
        }
        sb.append("    </div>\n");
        sb.append("</nav>\n");

        // MAIN CONTENT (CSS class: detail-main)
        sb.append("<main class=\"detail-main\">\n");

        // HEADER (CSS class: detail-header)
        sb.append("    <header class=\"detail-header\">\n");
        sb.append("        <h1>").append(h1Title).append("</h1>\n");
        sb.append("        <p class=\"sub-title\">").append(c.get("Season")).append(" ").append(c.get("Company")).append(" ").append(c.get("Brand")).append("</p>\n");
        sb.append("        <p class=\"meta-info\">").append(c.get("Theme")).append(" &bull; ").append(c.get("Variant")).append(" &bull; #").append(c.get("Number")).append("</p>\n");
        sb.append("    </header>\n");

        // SEO TEXT (CSS class: seo-box)
        sb.append("    <article class=\"seo-box\">\n");
        sb.append("        <h3>About this Card</h3>\n");
        sb.append("        <p>").append(generateSeoText(c)).append("</p>\n");
        sb.append("    </article>\n");

        // --- IMAGES SECTION (CSS class: card-images-container) ---
        sb.append("    <div class=\"card-images-container\">\n");

        String seasonImgFolder = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder;
        String frontImgPath = seasonImgFolder + "/" + c.filenameBase + "-front.jpg";
        String backImgPath = seasonImgFolder + "/" + c.filenameBase + "-back.jpg";

        // Front Image (CSS class: card-image-wrapper)
        sb.append("        <div class=\"card-image-wrapper\">\n");
        sb.append("            <img src=\"").append(frontImgPath).append("\" ")
                .append("alt=\"").append(frontAlt).append("\" ")
                .append("title=\"").append(frontImgTitle).append("\" ")
                .append("loading=\"lazy\" ")
                .append("onclick=\"openModal('").append(frontImgPath).append("', '").append(backImgPath).append("')\">\n");
        sb.append("            <p>Front View (Click to Zoom)</p>\n");
        sb.append("        </div>\n");

        // Back Image (CSS class: card-image-wrapper)
        sb.append("        <div class=\"card-image-wrapper\">\n");
        sb.append("            <img src=\"").append(backImgPath).append("\" ")
                .append("alt=\"").append(backAlt).append("\" ")
                .append("title=\"").append(backImgTitle).append("\" ")
                .append("loading=\"lazy\" ")
                .append("onclick=\"openModal('").append(backImgPath).append("', '").append(frontImgPath).append("')\">\n");
        sb.append("            <p>Back View (Click to Zoom)</p>\n");
        sb.append("        </div>\n");

        sb.append("    </div>\n");
        // --- END IMAGES SECTION ---

        // DATA TABLE
        sb.append("    <div class=\"card-data\">\n");
        sb.append("        <table style=\"width: 100%; border-collapse: collapse; margin-top: 20px;\">\n");
        // Table Header Class
        sb.append("            <tr class=\"specs-table-header\"><th colspan=\"2\" style=\"padding: 10px; text-align: left;\">Technical Specifications</th></tr>\n");
        addTableRow(sb, "Season", c.get("Season"));
        addTableRow(sb, "Team", c.get("Team"));
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

        // RELATED CARDS (CSS class: related-cards-section)
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

        // FOOTER (CSS class: detail-footer)
        sb.append("<footer class=\"detail-footer\">\n");
        sb.append("    Juwan Howard Collection &copy; 2026\n");
        sb.append("</footer>\n");

        // --- MODAL HTML STRUCTURE UND JAVASCRIPT ---
        sb.append("""
            <div id="cardModal" class="modal">
              <span class="close-modal" onclick="closeModal()">&times;</span>
              <button class="flip-modal-btn" onclick="flipCard()">&#8644; Flip Card</button>
              <img class="modal-content" id="img01">
            </div>

            <script>
                var modal = document.getElementById("cardModal");
                var modalImg = document.getElementById("img01");
                
                // Variablen um Flip-Status zu speichern
                var currentModalSrc = "";
                var alternateModalSrc = "";

                function openModal(src, altSrc) {
                  modal.style.display = "flex";
                  modal.style.alignItems = "center";
                  modal.style.justifyContent = "center";
                  
                  modalImg.src = src;
                  currentModalSrc = src;
                  alternateModalSrc = altSrc;
                }

                function closeModal() {
                  modal.style.display = "none";
                }
                
                function flipCard() {
                    // Tausche die Bilder
                    var temp = currentModalSrc;
                    currentModalSrc = alternateModalSrc;
                    alternateModalSrc = temp;
                    
                    modalImg.src = currentModalSrc;
                }

                // Schließen wenn man neben das Bild klickt
                window.onclick = function(event) {
                  if (event.target == modal) {
                    closeModal();
                  }
                }
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

        sb.append("It captures Juwan Howard during his time with the ").append(c.get("Team")).append(".");
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

    // UPDATE: Verwende jetzt CSS Klassen statt Inline Styles
    private static String createFaqItem(String question, String answer) {
        return "<details class=\"faq-details\">" +
                "<summary class=\"faq-summary\">" + question + "</summary>" +
                "<p class=\"faq-answer\">" + answer + "</p>" +
                "</details>";
    }

    private static String generateJsonLd(CardData c, String desc, String name) {
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + c.filenameBase + "-front.jpg";

        StringBuilder faqSchema = new StringBuilder();
        faqSchema.append("    {\n      \"@type\": \"Question\",\n      \"name\": \"What is the serial number?\",\n      \"acceptedAnswer\": { \"@type\": \"Answer\", \"text\": \"")
                .append(c.get("Serial")).append(" / ").append(c.get("Print Run")).append("\" }\n    }");

        if (c.has("Grade")) {
            faqSchema.append(",\n    {\n      \"@type\": \"Question\",\n      \"name\": \"Is this card graded?\",\n      \"acceptedAnswer\": { \"@type\": \"Answer\", \"text\": \"Yes, ")
                    .append(c.get("Grading Co.")).append(" ").append(c.get("Grade")).append("\" }\n    }");
        }

        return """
        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Product",
              "image": "%s",
              "name": "%s",
              "description": "%s",
              "brand": { "@type": "Brand", "name": "%s" },
              "manufacturer": "%s",
              "sku": "%s-%s",
              "category": "Sports Card",
              "offers": { "@type": "Offer", "availability": "https://schema.org/SoldOut", "price": "0", "priceCurrency": "EUR", "description": "Private Collection (NFS)" }
            },
            {
              "@type": "FAQPage",
              "mainEntity": [
                %s
              ]
            }
          ]
        }
        </script>
        """.formatted(
                frontImgUrl,
                name,
                desc,
                c.get("Brand"),
                c.get("Company"),
                c.get("Number"), c.get("Variant").replaceAll("[^a-zA-Z0-9]", ""),
                faqSchema.toString()
        );
    }

    // UPDATE: Verwende jetzt CSS Klassen statt Inline Styles
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
}