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

    // Interne Klasse um Kartendaten + Dateinamen zu speichern
    static class CardData {
        Map<String, String> attributes;
        String filenameBase; // Der Teil vor .html (für Bilder wichtig)
        String filename;     // Der komplette Dateiname mit .html
        String seasonFolder;
        String fullRelativePath; // z.B. cards/2021-22/card.html

        public CardData(Map<String, String> attributes) {
            this.attributes = attributes;
            calculatePaths();
        }

        private void calculatePaths() {
            List<String> filenameTokens = new ArrayList<>();
            // Reihenfolge für den Dateinamen
            addIfPresent(filenameTokens, attributes.get("Player"));
            addIfPresent(filenameTokens, attributes.get("Team"));
            addIfPresent(filenameTokens, attributes.get("Season"));
            addIfPresent(filenameTokens, attributes.get("Company"));
            addIfPresent(filenameTokens, attributes.get("Brand"));
            addIfPresent(filenameTokens, attributes.get("Theme"));
            addIfPresent(filenameTokens, attributes.get("Variant"));
            addIfPresent(filenameTokens, attributes.get("Number"));


            String grade = attributes.get("Grade");
            if (isValid(grade)) filenameTokens.add(grade);

            // Basisnamen bereinigen und speichern
            this.filenameBase = cleanFilename(String.join("-", filenameTokens));
            this.filename = this.filenameBase + ".html";

            String seasonRaw = attributes.get("Season");
            this.seasonFolder = isValid(seasonRaw) ? cleanFilename(seasonRaw) : "Unknown_Season";

            // Pfad von index.html aus gesehen
            this.fullRelativePath = BASE_FOLDER + "/" + this.seasonFolder + "/" + this.filename;
        }

        public String get(String key) {
            return attributes.getOrDefault(key, "");
        }
    }

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

        // SCHRITT 1: Erst alle Karten einlesen
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

        // SCHRITT 2: Seiten generieren
        for (int i = 0; i < allCardsInTable.size(); i++) {
            CardData currentCard = allCardsInTable.get(i);

            // Links zu vorheriger/nächster Karte finden
            CardData prevCard = (i > 0) ? allCardsInTable.get(i - 1) : null;
            CardData nextCard = (i < allCardsInTable.size() - 1) ? allCardsInTable.get(i + 1) : null;

            // Pfade vorbereiten
            Path folderPath = Paths.get(BASE_FOLDER, currentCard.seasonFolder);
            Files.createDirectories(folderPath);
            Path filePath = folderPath.resolve(currentCard.filename);

            // Seite erstellen
            createSubPage(currentCard, filePath, prevCard, nextCard, allCardsInTable);

            // Link in der Haupttabelle aktualisieren
            Element row = rows.get(i + 1);
            Elements cols = row.select("td");
            if (!cols.isEmpty()) {
                Element playerCell = cols.get(0);
                String originalText = playerCell.text();
                playerCell.empty();
                playerCell.appendElement("a")
                        .attr("href", currentCard.fullRelativePath)
                        .attr("title", "View details for " + currentCard.get("Brand") + " #" + currentCard.get("Number"))
                        .text(originalText);
            }
            System.out.println(" -> Generated: " + currentCard.filename);
        }
    }
    private static final String templateBegin = """
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
                        <link rel="icon" type="image/png" sizes="192x192" href="../..//favicon/android-chrome-192x192.png">
                        <link rel="icon" type="image/png" sizes="16x16" href="../..//favicon/favicon-16x16.png">
                        <link rel="manifest" href="../..//manifest.json">
                        <link rel="mask-icon" href="../..//favicon/safari-pinned-tab.svg" color="#317EFB">
                        <meta name="apple-mobile-web-app-title" content="Maulmann.de">
                        <meta name="application-name" content="Maulmann.de">
                        <meta name="msapplication-TileColor" content="#317EFB">
            """;



    private static void createSubPage(CardData c, Path path, CardData prev, CardData next, List<CardData> allCards) throws IOException {
        StringBuilder sb = new StringBuilder();

        String titleStr = c.get("Player") + " | " + c.get("Season") + " " + c.get("Brand") + " " + c.get("Theme") + " " + c.get("Variant");
        String h1 = c.get("Player") + " - " + c.get("Season") + " - " + c.get("Brand") + " " + c.get("Theme") + " " + c.get("Variant");

        String metaDesc = generateMetaDescription(c);

        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append("    <title>").append(titleStr).append(" | Card Details</title>\n");
        sb.append("    <meta name=\"description\" content=\"").append(metaDesc).append("\">\n");
        sb.append(templateBegin+"\n");


        // JSON-LD SCHEMA (FAQ & Product)
        sb.append(generateJsonLd(c));

        sb.append("</head>\n<body>\n");

        // NAVIGATION
        sb.append("<nav style=\"padding: 20px; background: #f8f9fa; border-bottom: 1px solid #e9ecef; display:flex; justify-content:space-between; align-items:center;\">\n");
        sb.append("    <a href=\"../../index.html\" class=\"modern-button\" style=\"text-decoration:none;\">&larr; Overview</a>\n");

        sb.append("    <div>\n");
        if (prev != null) sb.append("        <a href=\"").append(prev.filename).append("\" title=\"Previous: ").append(prev.get("Brand")).append("\" style=\"margin-right:10px; text-decoration:none;\">&laquo; Prev</a>\n");
        if (next != null) sb.append("        <a href=\"").append(next.filename).append("\" title=\"Next: ").append(next.get("Brand")).append("\" style=\"text-decoration:none;\">Next &raquo;</a>\n");
        sb.append("    </div>\n");
        sb.append("</nav>\n");

        sb.append("<main style=\"max-width: 900px; margin: 0 auto; padding: 20px; font-family: sans-serif;\">\n");

        // HEADER
        sb.append("    <header style=\"text-align:center; margin-bottom:40px;\">\n");
        sb.append("        <h1 style=\"margin-bottom:5px;\">").append(h1).append("</h1>\n");
        sb.append("        <p style=\"font-size:1.2em; color:#555; margin:0;\">").append(c.get("Season")).append(" ").append(c.get("Company")).append(" ").append(c.get("Brand")).append("</p>\n");
        sb.append("        <p style=\"font-size:1em; color:#777;\">").append(c.get("Theme")).append(" &bull; ").append(c.get("Variant")).append(" &bull; #").append(c.get("Number")).append("</p>\n");
        sb.append("    </header>\n");

        // SEO TEXT BLOCK
        sb.append("    <article style=\"background:#fff; padding:20px; border-radius:8px; box-shadow:0 2px 5px rgba(0,0,0,0.05); margin-bottom:30px; line-height:1.6;\">\n");
        sb.append("        <h3>About this Card</h3>\n");
        sb.append("        <p>").append(generateSeoText(c)).append("</p>\n");
        sb.append("    </article>\n");

        // --- IMAGES SECTION (NEU) ---
        sb.append("    <div class=\"card-images-container\" style=\"display: flex; flex-wrap: wrap; gap: 30px; justify-content: center; margin: 40px 0;\">\n");

        // Pfade berechnen: ../../images/SAISON/BASISNAME-front.jpg
        String seasonImgFolder = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder;
        String frontImgPath = seasonImgFolder + "/" + c.filenameBase + "-front.jpg";
        String backImgPath = seasonImgFolder + "/" + c.filenameBase + "-back.jpg";

        // Basis für Alt-Texte und Titel
        String baseAltText = c.get("Player") + " " + c.get("Season") + " " + c.get("Company") +" " + c.get("Brand") + " " + c.get("Theme") +  " " + c.get("Variant") + " #" + c.get("Number");

        // Front Image Wrapper
        sb.append("        <div class=\"card-image-wrapper\" style=\"width: 300px; text-align:center;\">\n");
        // Anchor mit Title Metadata für SEO, öffnet im neuen Tab
        sb.append("            <a href=\"").append(frontImgPath).append("\" title=\"View high-resolution front image of ").append(baseAltText).append("\" target=\"_blank\" rel=\"noopener\">\n");
        // Image mit Alt Metadata für SEO. CSS macht es responsiv statt fixer Höhe.
        sb.append("                <img src=\"").append(frontImgPath).append("\" alt=\"Front view of ").append(baseAltText).append(" basketball card\" style=\"max-width:100%; height:auto; border:1px solid #eee; box-shadow: 0 2px 5px rgba(0,0,0,0.1);\">\n");
        sb.append("            </a>\n");
        sb.append("            <p style=\"color: #888; margin-top:10px; font-weight:bold;\">Front View</p>\n");
        sb.append("        </div>\n");

        // Back Image Wrapper
        sb.append("        <div class=\"card-image-wrapper\" style=\"width: 300px; text-align:center;\">\n");
        // Anchor mit Title Metadata
        sb.append("            <a href=\"").append(backImgPath).append("\" title=\"View high-resolution back image showing stats for ").append(baseAltText).append("\" target=\"_blank\" rel=\"noopener\">\n");
        // Image mit Alt Metadata
        sb.append("                <img src=\"").append(backImgPath).append("\" alt=\"Back view of ").append(baseAltText).append(" basketball card\" style=\"max-width:100%; height:auto; border:1px solid #eee; box-shadow: 0 2px 5px rgba(0,0,0,0.1);\">\n");
        sb.append("            </a>\n");
        sb.append("            <p style=\"color: #888; margin-top:10px; font-weight:bold;\">Back View</p>\n");
        sb.append("        </div>\n");

        sb.append("    </div>\n");
        // --- END IMAGES SECTION ---


        // DATA TABLE
        sb.append("    <div class=\"card-data\">\n");
        sb.append("        <table style=\"width: 100%; border-collapse: collapse; margin-top: 20px;\">\n");
        sb.append("            <tr style=\"background-color: #317EFB; color: white;\"><th colspan=\"2\" style=\"padding: 10px; text-align: left;\">Technical Specifications</th></tr>\n");
        addTableRow(sb, "Season", c.get("Season"));
        addTableRow(sb, "Team", "Washington Wizards");
        addTableRow(sb, "Manufacturer", c.get("Company"));
        addTableRow(sb, "Brand", c.get("Brand"));
        addTableRow(sb, "Theme", c.get("Theme"));
        addTableRow(sb, "Variant", c.get("Variant"));
        addTableRow(sb, "Card Number", c.get("Number"));
        addTableRow(sb, "Serial / Print Run", c.get("Serial") + " / " + c.get("Print Run"));
        addTableRow(sb, "Rookie Card", c.get("Rookie"));
        addTableRow(sb, "Memorabilia", c.get("Game Used"));
        addTableRow(sb, "Autograph", c.get("Autograph"));

        String grading = c.get("Grading Co.") + " " + c.get("Grade");
        if (grading.trim().length() > 1) addTableRow(sb, "Grading", grading);

        sb.append("        </table>\n");
        sb.append("    </div>\n");

        // FAQ SECTION
        sb.append("    <section style=\"margin-top: 50px;\">\n");
        sb.append("        <h2>Frequently Asked Questions</h2>\n");
        sb.append(generateFaqHtml(c));
        sb.append("    </section>\n");

        // INTERNAL LINKING FOOTER (SEO)
        sb.append("    <section style=\"margin-top: 60px; padding-top: 20px; border-top: 1px solid #ddd;\">\n");
        sb.append("        <h3>More from the ").append(c.get("Season")).append(" Collection</h3>\n");
        sb.append("        <ul style=\"list-style:none; padding:0; display:flex; flex-wrap:wrap; gap:10px;\">\n");

        int count = 0;
        for (CardData other : allCards) {
            if (other == c) continue;
            if (count >= 6) break;
            sb.append("            <li style=\"flex: 1 1 30%; min-width:200px; margin-bottom:10px;\">\n");
            sb.append("                <a href=\"").append(other.filename).append("\" style=\"color:#317EFB; text-decoration:none;\">")
                    .append(other.get("Brand")).append(" #").append(other.get("Number")).append(" ").append(other.get("Variant"))
                    .append("</a>\n");
            sb.append("            </li>\n");
            count++;
        }
        sb.append("        </ul>\n");
        sb.append("    </section>\n");

        sb.append("</main>\n");
        sb.append("<footer style=\"text-align: center; margin-top: 50px; padding: 20px; font-size: 0.9em; color: #666; background:#f9f9f9;\">\n");
        sb.append("    Juwan Howard Collection &copy; 2026\n");
        sb.append("</footer>\n");
        sb.append("</body>\n</html>");

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    // --- HELPER METHODS ---

    private static String generateMetaDescription(CardData c) {
        return "Details for " + c.get("Player") + " " + c.get("Season") + " " + c.get("Brand") + " " + c.get("Theme") + " card." +
                "Variant: " + c.get("Variant") + ". Serial Number: " + c.get("Serial") + "/" + c.get("Print Run") + ". " +
                "Part of the private collection.";
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

        if (!c.get("Serial").equals("0")) {
            sb.append("This is a limited edition card, serial numbered <strong>").append(c.get("Serial")).append("</strong> ");
            sb.append("out of a total print run of <strong>").append(c.get("Print Run")).append("</strong>. ");
        }

        if (c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append("Notably, this card features an authentic <strong>Autograph</strong>, significantly adding to its rarity and value. ");
        }

        sb.append("It captures Juwan Howard during his time with the Washington Bulltes/Wizards").append(".");
        return sb.toString();
    }

    private static String generateFaqHtml(CardData c) {
        StringBuilder sb = new StringBuilder();
        // FAQ Q1
        sb.append("<details style=\"margin-bottom:10px; border:1px solid #ddd; padding:10px; border-radius:5px;\">");
        sb.append("<summary style=\"cursor:pointer; font-weight:bold;\">What is the print run of this card?</summary>");
        sb.append("<p style=\"margin-top:5px; color:#555;\">This specific ").append(c.get("Variant")).append(" card has a total print run of ").append(c.get("Print Run")).append(". Serial Number: ").append(c.get("Serial")).append(".</p>");
        sb.append("</details>");
        // FAQ Q2
        sb.append("<details style=\"margin-bottom:10px; border:1px solid #ddd; padding:10px; border-radius:5px;\">");
        sb.append("<summary style=\"cursor:pointer; font-weight:bold;\">Is this a Rookie Card?</summary>");
        String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ? "Yes, this is a highly generated Rookie Card (RC)!" : "No, this is not a rookie card. It was released during the " + c.get("Season") + " season.";
        sb.append("<p style=\"margin-top:5px; color:#555;\">").append(rookieAns).append("</p>");
        sb.append("</details>");
        // FAQ Q3
        sb.append("<details style=\"margin-bottom:10px; border:1px solid #ddd; padding:10px; border-radius:5px;\">");
        sb.append("<summary style=\"cursor:pointer; font-weight:bold;\">Does this card have an autograph?</summary>");
        String autoAns = c.get("Autograph").equalsIgnoreCase("Yes") ? "Yes, this card features a certified autograph." : "No, this card does not feature an autograph.";
        sb.append("<p style=\"margin-top:5px; color:#555;\">").append(autoAns).append("</p>");
        sb.append("</details>");
        return sb.toString();
    }

    private static String generateJsonLd(CardData c) {
        // Wir bauen die absolute URL zum Vorderseiten-Bild
        // Schema: https://www.maulmann.de/images/SAISON/DATEINAME-front.jpg
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + c.filenameBase + "-front.jpg";

        return """
        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Product",
              "image": "%s",
              "name": "%s %s %s - %s",
              "description": "%s",
              "brand": { "@type": "Brand", "name": "%s" },
              "sku": "%s-%s",
              "offers": { "@type": "Offer", "availability": "https://schema.org/SoldOut", "price": "0", "priceCurrency": "EUR", "description": "Private Collection" }
            },
            {
              "@type": "FAQPage",
              "mainEntity": [
                {
                  "@type": "Question",
                  "name": "What is the serial number?",
                  "acceptedAnswer": { "@type": "Answer", "text": "%s / %s" }
                },
                {
                  "@type": "Question",
                  "name": "Is this card autographed?",
                  "acceptedAnswer": { "@type": "Answer", "text": "%s" }
                }
              ]
            }
          ]
        }
        </script>
        """.formatted(
                // 1. Das neue Bild-Argument an erster Stelle (für "image": "%s")
                frontImgUrl,

                // 2. Die restlichen Argumente wie bisher
                c.get("Season"), c.get("Brand"), c.get("Player"), c.get("Variant"), // Name
                generateMetaDescription(c), // Description
                c.get("Company"), // Brand
                c.get("Number"), c.get("Variant").replaceAll(" ", ""), // SKU
                c.get("Serial"), c.get("Print Run"), // FAQ Answer 1
                c.get("Autograph") // FAQ Answer 2
        );
    }

    private static void addTableRow(StringBuilder sb, String title, String value) {
        sb.append("            <tr><th style=\"padding: 8px; border-bottom: 1px solid #ddd; text-align:left; width: 200px; color:#333;\">")
                .append(title)
                .append("</th><td style=\"padding: 8px; border-bottom: 1px solid #ddd; color:#555;\">")
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