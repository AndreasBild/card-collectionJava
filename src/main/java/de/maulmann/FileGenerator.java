package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FileGenerator {

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
            Map<String, Object> data = createBaseData("Juwan Howard Private Collection", "Complete Career Overview", "Juwan-Howard-Collection.html", "Juwan-Howard-Collection.html");

            File contentDir = new File("content");
            File[] seasonFiles = contentDir.listFiles((dir, name) -> name.endsWith(".html") && name.matches(".*\\d.*"));

            List<Map<String, String>> seasons = new ArrayList<>();
            if (seasonFiles != null) {
                Arrays.sort(seasonFiles);
                for (File file : seasonFiles) {
                    Map<String, String> seasonMap = new HashMap<>();
                    String name = file.getName().replace(".html", "");
                    seasonMap.put("id", name.toLowerCase());
                    seasonMap.put("name", name);

                    // Bereinigt die Tabellen-Dateien vorsichtshalber von alten Platzhaltern
                    String rawContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    seasonMap.put("content", cleanOldPlaceholders(rawContent));

                    seasons.add(seasonMap);
                }
            }
            data.put("seasons", seasons);
            processTemplate("collection-overview.ftlh", data, "output/Juwan-Howard-Collection.html");

        } catch (Exception e) { System.err.println("Fehler bei Haupt-Collection: " + e.getMessage()); }
    }

    // --- 2. NEBEN-SAMMLUNGEN BAUEN (Baseball, Panini, etc.) ---
    public static void buildOtherCollections() {
        String[] collections = {"Baseball", "Flawless", "Panini", "Wantlist"};

        for (String coll : collections) {
            try {
                System.out.println("-> Baue " + coll + ".html...");
                // Wichtig: Groß- und Kleinschreibung für das Nav-Highlight genau beachten (z.B. "Baseball.html")
                Map<String, Object> data = createBaseData(coll + " Collection", "Premium " + coll + " Cards", coll + ".html", coll + ".html");

                Path sourcePath = Paths.get("content/other", coll + ".html");
                if (Files.exists(sourcePath)) {
                    String rawContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
                    // Bereinigt die importierte Datei von alten Skript-Tags
                    data.put("pageContent", cleanOldPlaceholders(rawContent));
                } else {
                    data.put("pageContent", "<p>No data found for this collection yet.</p>");
                }

                processTemplate("generic-collection.ftlh", data, "output/" + coll + ".html");
            } catch (Exception e) { System.err.println("Fehler bei " + coll + ": " + e.getMessage()); }
        }
    }

    // --- 3. STATISCHE SEITEN BAUEN (Index, Error) ---
    public static void buildStaticPages() {
        try {
            System.out.println("-> Baue index.html & error.html...");

            // Index (Navigations-Highlight für "index.html")
            Map<String, Object> indexData = createBaseData("Maulmann Trading Cards", "Digital Archive", "index.html", "index.html");
            processTemplate("index.ftlh", indexData, "output/index.html");

            // Error 404 (Kein Navigations-Highlight)
            Map<String, Object> errorData = createBaseData("404 Not Found", "Page missing", "error.html", "");
            processTemplate("error.ftlh", errorData, "output/error.html");

        } catch (Exception e) { System.err.println("Fehler bei statischen Seiten: " + e.getMessage()); }
    }

    // --- HILFSMETHODEN ---
    private static Map<String, Object> createBaseData(String title, String subTitle, String filename, String navTargetUrl) throws Exception {
        Map<String, Object> data = new HashMap<>();

        String headHtml = SharedTemplates.getHead(title + " | Maulmann Trading Cards", subTitle, "", filename, "images/default-share.jpg");

        // --- NAVIGATIONS-HIGHLIGHT FIX ---
        String topnav = SharedTemplates.loadResource("/templates/topnav.html").replace("{{ROOT}}", "");
        if (!navTargetUrl.isEmpty()) {
            topnav = topnav.replace("href=\"" + navTargetUrl + "\"", "href=\"" + navTargetUrl + "\" class=\"active\"");
        }

        // Holt den Standard-Footer (inklusive aktueller Uhrzeit!)
        String footerHtml = SharedTemplates.getFooter("");

        data.put("headHtml", headHtml);
        data.put("topNavHtml", topnav);
        data.put("footerHtml", footerHtml);
        data.put("pageTitle", title);
        data.put("subTitle", subTitle);

        return data;
    }

    // Löscht alte Tags aus Dateien, die noch aus der Python-Skript-Zeit stammen
    private static String cleanOldPlaceholders(String content) {
        return content.replace("{{HEAD}}", "")
                .replace("{{TOP_NAV}}", "")
                .replace("{{FOOTER_NAV}}", "")
                .replace("{{FOOTER}}", "")
                .replace("{{TIME}}", "");
    }

    // Der wichtigste Teil: Hier wird gerendert und am Schluss alles global gescannt
    private static void processTemplate(String templateName, Map<String, Object> data, String outputPath) throws Exception {
        File out = new File(outputPath);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();

        Template template = fmConfig.getTemplate(templateName);

        // 1. Rendere das komplette HTML zuerst in einen String (Arbeitsspeicher)
        StringWriter stringWriter = new StringWriter();
        template.process(data, stringWriter);
        String finalHtml = stringWriter.toString();

        // 2. GLOBALE PLATZHALTER-AUFLÖSUNG (Fängt alles ab, selbst wenn es tief im Code versteckt ist)
        if (finalHtml.contains("{{FOOTER_NAV}}")) {
            try {
                String footerNav = SharedTemplates.loadResource("/templates/footer_nav.html").replace("{{ROOT}}", "");
                finalHtml = finalHtml.replace("{{FOOTER_NAV}}", footerNav);
            } catch (Exception e) {
                finalHtml = finalHtml.replace("{{FOOTER_NAV}}", ""); // Löschen, falls Datei fehlt
            }
        }
        if (finalHtml.contains("{{FOOTER}}")) {
            finalHtml = finalHtml.replace("{{FOOTER}}", "");
        }

        // 3. Erst jetzt die blitzsaubere Datei auf die Festplatte schreiben
        Files.writeString(out.toPath(), finalHtml, StandardCharsets.UTF_8);
    }

    // --- STARTPUNKT ---
    public static void main(String[] args) {
        buildCollectionOverview();
        buildOtherCollections();
        buildStaticPages();
        System.out.println("-> Alle statischen Seiten erfolgreich generiert!");
    }
}