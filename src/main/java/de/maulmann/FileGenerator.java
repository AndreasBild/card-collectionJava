package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

// --- NEU: Jsoup Imports für die Tabellen-Analyse ---
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FileGenerator {

    private static final String BASE_URL = "https://www.maulmann.de";
    public static final String DEFAULT_IMAGE = "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-front.webp";
    static String pathSource = "content/";
    static String pathOutput = "output/";

    private static TimestampTracker timestampTracker;

    public static void setTimestampTracker(TimestampTracker tracker) {
        timestampTracker = tracker;
    }

    private static final Configuration fmConfig;
    static {
        fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        fmConfig.setClassForTemplateLoading(FileGenerator.class, "/templates");
        fmConfig.setDefaultEncoding("UTF-8");
        fmConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    // --- 0. LATEST METADATA FÜR PWA ---
    public static void generateLatestMetadata(int totalCardCount) {
        try {
            System.out.println("-> Generiere latest.json...");
            String json = "{\n" +
                    "  \"cardCount\": " + totalCardCount + ",\n" +
                    "  \"lastUpdate\": \"" + System.currentTimeMillis() + "\"\n" +
                    "}";
            Files.writeString(Paths.get(pathOutput, "latest.json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Fehler bei latest.json: " + e.getMessage());
        }
    }

    // --- 1. HAUPT-SAMMLUNG BAUEN ---
    public static void buildCollectionOverview() {
        try {
            System.out.println("-> Baue Juwan-Howard-Collection.html...");
            Map<String, Object> data = createBaseData("Juwan Howard Private Collection | Juwan Howard Super Collector", "Explore the Juwan Howard Masterpiece Collection. A massive private collection featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.", "Juwan-Howard-Collection.html", "collection", "");

            // Schema.org Breadcrumb & CollectionPage
            String jsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    {\n" +
                    "      \"@type\": \"BreadcrumbList\",\n" +
                    "      \"name\": \"Breadcrumbs\",\n" +
                    "      \"itemListElement\": [\n" +
                    "        { \"@type\": \"ListItem\", \"position\": 1, \"name\": \"Home\", \"item\": \"" + BASE_URL + "/index.html\" },\n" +
                    "        { \"@type\": \"ListItem\", \"position\": 2, \"name\": \"Collection\", \"item\": \"" + BASE_URL + "/Juwan-Howard-Collection.html\" }\n" +
                    "      ]\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"CollectionPage\",\n" +
                    "      \"@id\": \"" + BASE_URL + "/Juwan-Howard-Collection.html\",\n" +
                    "      \"name\": \"Juwan Howard Private Collection\",\n" +
                    "      \"description\": \"A massive private collection featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.\",\n" +
                    "      \"publisher\": { \"@type\": \"Person\", \"name\": \"Mauli Maulmann\" }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "</script>";
            data.put("jsonLd", jsonLd);

            File contentDir = new File(pathSource);

            // NEU: Lässt alle Jahreszahlen ZUSÄTZLICH zur College.html durch!
            File[] seasonFiles = contentDir.listFiles((dir, name) -> name.endsWith(".html") && (name.matches(".*\\d.*") || name.equalsIgnoreCase("College.html")));

            List<Map<String, String>> seasons = new ArrayList<>();
            int cumulativeTotal = 0; // Der laufende Zähler für die Gesamtanzahl!

            if (seasonFiles != null) {

                // NEU: Sortiert chronologisch, aber zwingt "College.html" an die allererste Position
                Arrays.sort(seasonFiles, (f1, f2) -> {
                    if (f1.getName().equalsIgnoreCase("College.html")) return -1;
                    if (f2.getName().equalsIgnoreCase("College.html")) return 1;
                    return f1.getName().compareTo(f2.getName());
                });

                for (File file : seasonFiles) {
                    Map<String, String> seasonMap = new HashMap<>();
                    String rawName = file.getName().replace(".html", "");
                    seasonMap.put("id", rawName.toLowerCase());

                    if (rawName.equalsIgnoreCase("College")) {
                        seasonMap.put("name", "College");
                    } else {
                        seasonMap.put("name", "Season " + rawName);
                    }

                    String rawContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);

                    // NEU: Analysiert das HTML, isoliert NUR die Tabelle und zählt die Reihen
                    Document doc = Jsoup.parse(rawContent, "UTF-8");
                    Element table = doc.selectFirst("table");

                    int seasonCardCount = 0;
                    if (table != null) {
                        // Alle Reihen (tr) zählen minus 1 (für die Kopfzeile)
                        seasonCardCount = Math.max(0, table.select("tr").size() - 1);
                        seasonMap.put("content", cleanOldPlaceholders(table.outerHtml()));
                    } else {
                        seasonMap.put("content", "<p>No cards found.</p>");
                    }

                    cumulativeTotal += seasonCardCount;

                    // Schreibt die errechneten Werte in die Map für FreeMarker
                    seasonMap.put("seasonCount", String.valueOf(seasonCardCount));
                    seasonMap.put("cumulativeTotal", String.valueOf(cumulativeTotal));

                    seasons.add(seasonMap);
                }
            }
            data.put("seasons", seasons);
            processTemplate("collection-overview.ftlh", data, pathOutput + "Juwan-Howard-Collection.html");

            // Metadaten für PWA generieren
            generateLatestMetadata(cumulativeTotal);

        } catch (Exception e) { System.err.println("Fehler bei Haupt-Collection: " + e.getMessage()); }
    }

    // --- 2. NEBEN-SAMMLUNGEN BAUEN (Baseball, Panini, etc.) ---
    public static void buildOtherCollections() {
        Map<String, String[]> collectionMetas = new HashMap<>();
        collectionMetas.put("Baseball", new String[]{
                "Ultimate Signature Edition Baseball Private Collection",
                "A curated gallery from a private collection of 2005 Upper Deck Ultimate Signature Edition baseball cards. Featuring 'Immortal Inscriptions' and rare Ken Griffey Jr. autographs."
        });
        collectionMetas.put("Flawless", new String[]{
                "2008 Upper Deck Exquisite Flawless Basketball Private Collection",
                "The peak of Upper Deck's Exquisite era. View rare 2008 Flawless 1/1s and autographs of Michael Jordan, Bill Russell, and Kobe Bryant in this private collection."
        });
        collectionMetas.put("Panini", new String[]{
                "2012-13 Panini Flawless Basketball Private Collection",
                "A showcase of the historic 2012-13 Panini Flawless Basketball set from a private collector. Features 1/1 Masterpiece cards and rare gemstone-embedded NBA legends."
        });
        collectionMetas.put("Wantlist", new String[]{
                "Juwan Howard Wantlist | Super Collector Searching for 1/1, PMG, Ruby",
                "Help a Juwan Howard Super Collector complete the master collection. We are searching for rare 1/1 Masterpieces, Precious Metal Gems (PMG), SkyBox Premium Rubies, and Legacy Collection parallels."
        });
        collectionMetas.put("privacy", new String[]{
                "Privacy Policy | Maulmann Trading Cards",
                "Privacy policy for Maulmann Trading Cards. Information on data collection and usage."
        });
        collectionMetas.put("imprint", new String[]{
                "Imprint | Maulmann Trading Cards",
                "Legal notice and contact information for Maulmann Trading Cards."
        });

        for (Map.Entry<String, String[]> entry : collectionMetas.entrySet()) {
            String coll = entry.getKey();
            String title = entry.getValue()[0];
            String description = entry.getValue()[1];

            try {
                System.out.println("-> Baue " + coll + ".html...");
                Map<String, Object> data = createBaseData(title, description, coll + ".html", coll.toLowerCase(), "");

                // Initial Breadcrumb & CollectionPage
                String jsonLd = "<script type=\"application/ld+json\">\n" +
                        "{\n" +
                        "  \"@context\": \"https://schema.org\",\n" +
                        "  \"@graph\": [\n" +
                        "    {\n" +
                        "      \"@type\": \"BreadcrumbList\",\n" +
                        "      \"name\": \"Breadcrumbs\",\n" +
                        "      \"itemListElement\": [\n" +
                        "        { \"@type\": \"ListItem\", \"position\": 1, \"name\": \"Home\", \"item\": \"" + BASE_URL + "/index.html\" },\n" +
                        "        { \"@type\": \"ListItem\", \"position\": 2, \"name\": \"" + coll + "\", \"item\": \"" + BASE_URL + "/" + coll + ".html\" }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    {\n" +
                        "      \"@type\": \"CollectionPage\",\n" +
                        "      \"@id\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                        "      \"name\": \"" + title.split("\\|")[0].trim() + "\",\n" +
                        "      \"description\": \"" + description + "\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n" +
                        "</script>";
                data.put("jsonLd", jsonLd);

                Path sourcePath = Paths.get(pathSource, "other", coll + ".html");
                if (Files.exists(sourcePath)) {
                    String rawContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
                    Document doc = Jsoup.parse(rawContent);

                    // Extrahiere FAQ aus dem Content für das JSON-LD (falls vorhanden)
                    if (rawContent.contains("application/ld+json") && rawContent.contains("FAQPage")) {
                        try {
                            Element faqScript = doc.selectFirst("script[type=application/ld+json]");
                            if (faqScript != null) {
                                String faqJson = faqScript.data().trim();
                                // Wir bauen ein @graph-basiertes JSON-LD, das Breadcrumb, CollectionPage UND FAQ enthält
                                jsonLd = "<script type=\"application/ld+json\">\n" +
                                        "{\n" +
                                        "  \"@context\": \"https://schema.org\",\n" +
                                        "  \"@graph\": [\n" +
                                        "    {\n" +
                                        "      \"@type\": \"BreadcrumbList\",\n" +
                                        "      \"name\": \"Breadcrumbs\",\n" +
                                        "      \"itemListElement\": [\n" +
                                        "        { \"@type\": \"ListItem\", \"position\": 1, \"name\": \"Home\", \"item\": \"" + BASE_URL + "/index.html\" },\n" +
                                        "        { \"@type\": \"ListItem\", \"position\": 2, \"name\": \"" + coll + "\", \"item\": \"" + BASE_URL + "/" + coll + ".html\" }\n" +
                                        "      ]\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"@type\": \"CollectionPage\",\n" +
                                        "      \"@id\": \"" + BASE_URL + "/" + coll + ".html\",\n" +
                                        "      \"name\": \"" + title.split("\\|")[0].trim() + "\",\n" +
                                        "      \"description\": \"" + description + "\"\n" +
                                        "    },\n" +
                                        "    " + faqJson + "\n" +
                                        "  ]\n" +
                                        "}\n" +
                                        "</script>";
                                data.put("jsonLd", jsonLd);
                            }
                        } catch (Exception e) {
                            System.err.println("FAQ Extraction failed for " + coll);
                        }
                    }

                    // NEU: Isoliere den Inhalt des <main>-Tags, um doppelte Verschachtelung zu vermeiden
                    Element mainElement = doc.selectFirst("main");
                    String processedContent;
                    if (mainElement != null) {
                        // Alle Tabellen automatisch in responsive Container einpacken, falls noch nicht geschehen
                        for (Element table : mainElement.select("table")) {
                            if (table.parent() == null || !table.parent().hasClass("table-responsive")) {
                                table.wrap("<div class=\"table-responsive card-container-box\"></div>");
                            }
                        }
                        processedContent = mainElement.html();
                    } else {
                        processedContent = rawContent;
                    }

                    data.put("pageContent", cleanOldPlaceholders(processedContent));
                } else {
                    data.put("pageContent", "<p>No data found for this collection yet.</p>");
                }

                processTemplate("generic-collection.ftlh", data, pathOutput + coll + ".html");
            } catch (Exception e) { System.err.println("Fehler bei " + coll + ": " + e.getMessage()); }
        }
    }

    public static void copyResources() {
        try {
            // 1. Copy CSS
            Path cssDir = Paths.get(pathOutput, "css");
            Files.createDirectories(cssDir);
            Path cssSource = Paths.get("src/main/resources/css/main.css");
            if (Files.exists(cssSource)) {
                Files.copy(cssSource, cssDir.resolve("main.css"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Copy llms.txt from root
            Path llmsSource = Paths.get("llms.txt");
            if (Files.exists(llmsSource)) {
                Files.copy(llmsSource, Paths.get(pathOutput, "llms.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 3. Copy PWA assets
            Path pwaSourceDir = Paths.get("src/main/resources/pwa");
            if (Files.exists(pwaSourceDir)) {
                try (var stream = Files.walk(pwaSourceDir)) {
                    stream.filter(Files::isRegularFile).forEach(source -> {
                        try {
                            Path target = Paths.get(pathOutput).resolve(pwaSourceDir.relativize(source));
                            Files.createDirectories(target.getParent());

                            if (source.getFileName().toString().equals("serviceWorker.js")) {
                                String content = Files.readString(source, StandardCharsets.UTF_8);
                                content = content.replace("[[BUILD_ID]]", SharedTemplates.BUILD_ID);
                                Files.writeString(target, content, StandardCharsets.UTF_8);
                            } else {
                                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            System.err.println("Error copying PWA asset " + source + ": " + e.getMessage());
                        }
                    });
                }
            }

            // 4. Copy Favicon assets
            Path faviconSourceDir = Paths.get("src/main/resources/favicon");
            if (Files.exists(faviconSourceDir)) {
                try (var stream = Files.walk(faviconSourceDir)) {
                    stream.filter(Files::isRegularFile).forEach(source -> {
                        try {
                            Path target = Paths.get(pathOutput, "favicon").resolve(faviconSourceDir.relativize(source));
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            System.err.println("Error copying Favicon asset " + source + ": " + e.getMessage());
                        }
                    });
                }
            }
        } catch (IOException e) {
            System.err.println("Error copying resources: " + e.getMessage());
        }
    }

    // --- 3. STATISCHE SEITEN BAUEN (Index, Error) ---
    public static void buildStaticPages() {
        try {
            System.out.println("-> Baue index.html & error.html...");

            // Index (Navigations-Highlight für "index.html")
            Map<String, Object> indexData = createBaseData(
                    "Juwan Howard Super Collector | Private Collection",
                    "Welcome to the ultimate Juwan Howard Private Collection. A Super Collector showcase featuring 1,000+ unique cards, including 1/1 Masterpieces, PMGs, Rubies, and rare 90s basketball inserts.",
                    "index.html", "index", "");

            // Complex JSON-LD for Index (WebSite, Person, Collection, Breadcrumbs)
            String indexJsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    {\n" +
                    "      \"@type\": \"BreadcrumbList\",\n" +
                    "      \"name\": \"Breadcrumbs\",\n" +
                    "      \"itemListElement\": [\n" +
                    "        { \"@type\": \"ListItem\", \"position\": 1, \"name\": \"Home\", \"item\": \"" + BASE_URL + "/index.html\" }\n" +
                    "      ]\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"WebSite\",\n" +
                    "      \"name\": \"Maulmann Trading Cards\",\n" +
                    "      \"url\": \"" + BASE_URL + "\",\n" +
                    "      \"description\": \"Private collection of the Juwan Howard Super Collector\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"Person\",\n" +
                    "      \"name\": \"Mauli Maulmann\",\n" +
                    "      \"jobTitle\": \"Juwan Howard Super Collector\",\n" +
                    "      \"url\": \"" + BASE_URL + "\",\n" +
                    "      \"sameAs\": [\n" +
                    "        \"https://www.instagram.com/maulmann_cards/\"\n" +
                    "      ]\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"CollectionPage\",\n" +
                    "      \"name\": \"Juwan Howard Masterpiece Collection\",\n" +
                    "      \"description\": \"A massive private collection of rare Juwan Howard basketball cards.\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "</script>";
            indexData.put("jsonLd", indexJsonLd);

            processTemplate("index.ftlh", indexData, pathOutput + "index.html");

            // Error 404 (Kein Navigations-Highlight)
            // Hier nutzen wir "/" als Root, damit Links auch bei tiefen Pfaden funktionieren (404-Handling im Server)
            Map<String, Object> errorData = createBaseData("Error Page| Maulmann Trading Cards", "The page you are looking for does not exist in the Maulmann Trading Cards collection.", "error.html", "", "/");
            processTemplate("error.ftlh", errorData, pathOutput + "error.html");

        } catch (Exception e) { System.err.println("Fehler bei statischen Seiten: " + e.getMessage()); }
    }

    // --- HILFSMETHODEN ---
    private static Map<String, Object> createBaseData(String title, String subTitle, String filename, String navTargetUrl, String root) throws Exception {
        Map<String, Object> data = new HashMap<>();

        String headHtml = SharedTemplates.getHead(title, subTitle, root, filename, root + DEFAULT_IMAGE);

        String topnav = SharedTemplates.getTopNav(root, navTargetUrl.replace(".html", "").toLowerCase());

        String footerHtml = SharedTemplates.getFooter(root);

        data.put("headHtml", headHtml);
        data.put("topNavHtml", topnav);
        data.put("footerHtml", footerHtml);
        data.put("pageTitle", title);
        data.put("subTitle", subTitle);
        data.put("root", root);

        return data;
    }

    private static String cleanOldPlaceholders(String content) {
        // Entfernt auch vorhandene ld+json Blöcke aus dem Content, da diese nun im Head via FreeMarker landen
        String cleaned = content.replaceAll("(?s)<script type=\"application/ld\\+json\">.*?</script>", "");

        return cleaned.replace("{{HEAD}}", "")
                .replace("{{TOP_NAV}}", "")
                .replace("{{FOOTER_NAV}}", "")
                .replace("{{FOOTER}}", "")
                .replace("{{TIME}}", "");
    }

    private static void processTemplate(String templateName, Map<String, Object> data, String outputPath) throws Exception {
        File out = new File(outputPath);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();

        Template template = fmConfig.getTemplate(templateName);

        StringWriter stringWriter = new StringWriter();
        template.process(data, stringWriter);
        String finalHtml = stringWriter.toString();

        if (timestampTracker != null && finalHtml.contains("[[STABLE_TIME]]")) {
            String relativeOutputPath = new File(pathOutput).toURI().relativize(out.toURI()).getPath();
            String stableTime = timestampTracker.getStableTimestamp(relativeOutputPath, finalHtml);
            finalHtml = finalHtml.replace("[[STABLE_TIME]]", stableTime);
        }

        if (finalHtml.contains("{{CONSENT_BANNER}}")) {
            String root = (String) data.getOrDefault("root", "");
            finalHtml = finalHtml.replace("{{CONSENT_BANNER}}", SharedTemplates.getConsentBanner(root));
        }

        if (finalHtml.contains("{{FOOTER_NAV}}")) {
            try {
                String root = (String) data.getOrDefault("root", "");
                String footerNav = SharedTemplates.getFooterNav(root);
                finalHtml = finalHtml.replace("{{FOOTER_NAV}}", footerNav);
            } catch (Exception e) {
                finalHtml = finalHtml.replace("{{FOOTER_NAV}}", "");
            }
        }
        if (finalHtml.contains("{{FOOTER}}")) {
            finalHtml = finalHtml.replace("{{FOOTER}}", "");
        }

        Files.writeString(out.toPath(), finalHtml, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        copyResources();
        buildCollectionOverview();
        buildOtherCollections();
        buildStaticPages();
        System.out.println("-> Alle statischen Seiten erfolgreich generiert!");
    }
}