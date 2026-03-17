package de.maulmann;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class FileGenerator {

    static final String DEFAULT_IMAGE = "https://www.maulmann.de/images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front.webp";

    public static void main(String[] args) {
        System.out.println("-> Generiere Startseite (index.html)...");
        createLandingPage();

        System.out.println("-> Verarbeite statische Zusatzseiten...");
        processStaticPage("Flawless.html", "2008 Upper Deck Exquisite Flawless Basketball | Private Collection", "Discover a stunning private collection of 2008 Upper Deck Exquisite Flawless basketball autographs.");
        processStaticPage("Baseball.html", "Upper Deck Baseball Cards | Private Collection", "Explore our premium selection of rare Upper Deck Baseball cards and inscriptions.");
        processStaticPage("Panini.html", "Panini Flawless Basketball | Private Collection", "A showcase of the ultra-high-end Panini Flawless basketball card collection.");
        processStaticPage("Wantlist.html", "Juwan Howard Wantlist | Cards I'm Searching For", "A detailed list of rare Juwan Howard basketball cards needed to complete the ultimate private collection.");

        // --- NEU: Die gesplitteten Saison-Dateien zusammenfügen ---
        buildCollectionOverview();

        System.out.println("-> HTML-Framework (Phase 1) abgeschlossen.");
    }

    public static void buildCollectionOverview() {
        try {
            System.out.println("-> Baue Juwan-Howard-Collection.html aus Saison-Dateien...");

            String headHtml = SharedTemplates.getHead(
                    "Juwan Howard Private Collection | Maulmann Trading Cards",
                    "Complete overview of the Juwan Howard trading card collection, featuring 1/1s and rare inserts.",
                    "",
                    "Juwan-Howard-Collection.html",
                    DEFAULT_IMAGE
            );

            String topnav = SharedTemplates.loadResource("/templates/topnav.html").replace("{{ROOT}}", "");
            topnav = topnav.replace("href=\"Juwan-Howard-Collection.html\"", "href=\"Juwan-Howard-Collection.html\" class=\"active\"");
            String footer = SharedTemplates.loadResource("/templates/footer.html").replace("{{ROOT}}", "");

            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n");
            sb.append(headHtml).append("\n<body>\n").append(topnav).append("\n");
            sb.append("<main class=\"detail-main\">\n");
            sb.append("    <header class=\"detail-header\">\n");
            sb.append("        <h1>Juwan Howard Private Collection</h1>\n");
            sb.append("        <p class=\"sub-title\">Complete Career Overview</p>\n");
            sb.append("    </header>\n");

            // --- Alle HTML-Dateien im content/ Ordner finden, die eine Zahl enthalten (z.B. 94-95.html) ---
            File contentDir = new File("content");
            File[] seasonFiles = contentDir.listFiles((dir, name) -> name.endsWith(".html") && name.matches(".*\\d.*"));

            if (seasonFiles != null && seasonFiles.length > 0) {
                Arrays.sort(seasonFiles); // Sortiert chronologisch (94 vor 95)
                for (File file : seasonFiles) {
                    String tableContent = new String(Files.readAllBytes(file.toPath()));
                    sb.append(tableContent).append("\n<br>\n");
                }
            } else {
                System.out.println("   WARNUNG: Keine Saison-Dateien (z.B. 94-95.html) im Ordner 'content' gefunden!");
            }

            sb.append("</main>\n").append(footer).append("\n</body>\n</html>");

            File outputDir = new File("output");
            if (!outputDir.exists()) outputDir.mkdirs();

            Files.writeString(new File(outputDir, "Juwan-Howard-Collection.html").toPath(), sb.toString(), StandardCharsets.UTF_8);
            System.out.println("-> Juwan-Howard-Collection.html erfolgreich in /output erstellt!");

        } catch (Exception e) {
            System.err.println("Fehler beim Bauen der Collection-Übersicht: " + e.getMessage());
        }
    }

    public static void processStaticPage(String filename, String title, String description) {
        try {
            File sourceFile = new File("content/other/" + filename);
            if (!sourceFile.exists()) sourceFile = new File(filename);
            if (!sourceFile.exists()) return;

            String content = new String(Files.readAllBytes(sourceFile.toPath()));

            String headHtml = SharedTemplates.getHead(title, description, "", filename, DEFAULT_IMAGE);
            String topnavHtml = SharedTemplates.loadResource("/templates/topnav.html").replace("{{ROOT}}", "");
            topnavHtml = topnavHtml.replace("href=\"" + filename + "\"", "href=\"" + filename + "\" class=\"active\"");
            String footerHtml = SharedTemplates.loadResource("/templates/footer.html").replace("{{ROOT}}", "");

            content = content.replace("{{HEAD}}", headHtml)
                    .replace("{{TOPNAV}}", topnavHtml)
                    .replace("{{FOOTER}}", footerHtml)
                    .replace("{{BUILD_ID}}", SharedTemplates.BUILD_ID);

            File outputDir = new File("output");
            if (!outputDir.exists()) outputDir.mkdirs();

            try (FileWriter writer = new FileWriter(new File(outputDir, filename))) {
                writer.write(content);
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Verarbeiten von " + filename + ": " + e.getMessage());
        }
    }

    public static void createLandingPage() {
        // ... (Füge hier exakt deinen alten Inhalt der Methode createLandingPage() ein!)
    }
}