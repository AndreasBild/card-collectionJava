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

    public static final String IMAGE_PATH = "images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-front.webp";
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

    // --- 1. HAUPT-SAMMLUNG BAUEN ---
    public static void buildCollectionOverview() {
        try {
            System.out.println("-> Baue Juwan-Howard-Collection.html...");
            Map<String, Object> data = createBaseData("Juwan Howard Private Collection | Maulmann Trading Cards", "Complete Career Overview of the Juwan Howard Master Collection.", "Juwan-Howard-Collection.html", "collection", "");

            File contentDir = new File(pathSource);

            // NEU: Lässt alle Jahreszahlen ZUSÄTZLICH zur College.html durch!
            File[] seasonFiles = contentDir.listFiles((dir, name) -> name.endsWith(".html") && (name.matches(".*\\d.*") || name.equalsIgnoreCase("College.html")));

            List<Map<String, String>> seasons = new ArrayList<>();
            if (seasonFiles != null) {

                // NEU: Sortiert chronologisch, aber zwingt "College.html" an die allererste Position
                Arrays.sort(seasonFiles, (f1, f2) -> {
                    if (f1.getName().equalsIgnoreCase("College.html")) return -1;
                    if (f2.getName().equalsIgnoreCase("College.html")) return 1;
                    return f1.getName().compareTo(f2.getName());
                });

                int cumulativeTotal = 0; // Der laufende Zähler für die Gesamtanzahl!

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

        } catch (Exception e) { System.err.println("Fehler bei Haupt-Collection: " + e.getMessage()); }
    }

    // --- 2. NEBEN-SAMMLUNGEN BAUEN (Baseball, Panini, etc.) ---
    public static void buildOtherCollections() {
        Map<String, String[]> collectionMetas = new HashMap<>();
        collectionMetas.put("Baseball", new String[]{
                "Ultimate Signature Edition Baseball Collection | Maulmann Trading Cards",
                "A curated gallery of 2005 Upper Deck Ultimate Signature Edition baseball cards. Featuring 'Immortal Inscriptions' with hand-written nicknames and rare Ken Griffey Jr. buy-back autographs."
        });
        collectionMetas.put("Flawless", new String[]{
                "2008 Upper Deck Exquisite Flawless Basketball | Maulmann Trading Cards",
                "The peak of Upper Deck's Exquisite era. View rare 2008 Flawless autographs of Michael Jordan, Bill Russell, LeBron James, and Kobe Bryant in this curated private collection."
        });
        collectionMetas.put("Panini", new String[]{
                "2012-13 Panini Flawless Basketball | Maulmann Trading Cards",
                "A showcase of the historic 2012-13 Panini Flawless Basketball set. Features premium on-card autographs and rare gemstone-embedded cards from NBA legends like Julius Erving and Hakeem Olajuwon."
        });
        collectionMetas.put("Wantlist", new String[]{
                "Juwan Howard Card Wantlist | Rare 90s Parallels Wanted | Maulmann Trading Cards",
                "Help us complete the Juwan Howard master collection. We are searching for rare 90s parallels, SPx Finite 1/1s, and SkyBox Premium Rubies. View our full basketball card wantlist."
        });

        for (Map.Entry<String, String[]> entry : collectionMetas.entrySet()) {
            String coll = entry.getKey();
            String title = entry.getValue()[0];
            String description = entry.getValue()[1];

            try {
                System.out.println("-> Baue " + coll + ".html...");
                Map<String, Object> data = createBaseData(title, description, coll + ".html", coll.toLowerCase(), "");

                Path sourcePath = Paths.get(pathSource, "other", coll + ".html");
                if (Files.exists(sourcePath)) {
                    String rawContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
                    data.put("pageContent", cleanOldPlaceholders(rawContent));
                } else {
                    data.put("pageContent", "<p>No data found for this collection yet.</p>");
                }

                processTemplate("generic-collection.ftlh", data, pathOutput + coll + ".html");
            } catch (Exception e) { System.err.println("Fehler bei " + coll + ": " + e.getMessage()); }
        }
    }

    public static void copyResources() {
        try {
            Path cssDir = Paths.get(pathOutput, "css");
            Files.createDirectories(cssDir);
            Path cssSource = Paths.get("src/main/resources/css/main.css");
            if (Files.exists(cssSource)) {
                Files.copy(cssSource, cssDir.resolve("main.css"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
                    "Maulmann Trading Cards | Juwan Howard Private Collection & Sports Card Archive",
                    "Explore the ultimate Juwan Howard private collection with over 1,000 unique cards. Featuring rare 90s inserts, 1-of-1s, and high-end basketball, baseball, and football collectibles.",
                    "index.html", "index", "");
            processTemplate("index.ftlh", indexData, pathOutput + "index.html");

            // Error 404 (Kein Navigations-Highlight)
            // Hier nutzen wir "/" als Root, damit Links auch bei tiefen Pfaden funktionieren (404-Handling im Server)
            Map<String, Object> errorData = createBaseData("404 Not Found | Maulmann Trading Cards", "The page you are looking for does not exist in the Maulmann Trading Cards collection.", "error.html", "", "/");
            processTemplate("error.ftlh", errorData, pathOutput + "error.html");

        } catch (Exception e) { System.err.println("Fehler bei statischen Seiten: " + e.getMessage()); }
    }

    // --- HILFSMETHODEN ---
    private static Map<String, Object> createBaseData(String title, String subTitle, String filename, String navTargetUrl, String root) throws Exception {
        Map<String, Object> data = new HashMap<>();

        String headHtml = SharedTemplates.getHead(title, subTitle, root, filename, root + IMAGE_PATH);

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
        return content.replace("{{HEAD}}", "")
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