package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SitemapGenerator {

    // --- KONFIGURATION ---
    // HIER DEINE ECHTE DOMAIN EINTRAGEN (ohne Slash am Ende):
    private static final String BASE_URL = "https://www.maulmann.de";

    private static final String INPUT_FILE = "newIndex/index.html";
    private static final String OUTPUT_SITEMAP = "output/sitemap.xml";
    private static final String DATE_TODAY = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    // ---------------------

    public static void main(String[] args) {
        try {
            System.out.println("Generating sitemap.xml for: " + BASE_URL);

            StringBuilder xml = new StringBuilder();

            // XML Header und Namespaces (inklusive Image Extension für SEO)
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"https://www.maulmann.de/sitemap.xsl\"?>\n");
            xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
            xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");

            // 1. Statische Hauptseiten hinzufügen
            addUrl(xml, BASE_URL + "/index.html", "1.0", "weekly");
            addUrl(xml, BASE_URL + "/Wantlist.html", "0.8", "monthly");
            addUrl(xml, BASE_URL + "/Baseball.html", "0.8", "monthly");
            addUrl(xml, BASE_URL + "/Flawless.html", "0.8", "monthly");
            addUrl(xml, BASE_URL + "/Panini.html", "0.8", "monthly");

            // 2. Alle generierten Kartenseiten aus der index.html auslesen
            File input = new File(INPUT_FILE);
            Document doc = Jsoup.parse(input, "UTF-8");
            Elements tables = doc.select("table");

            int cardCount = 0;

            for (Element table : tables) {
                Elements rows = table.select("tr");
                if (rows.isEmpty()) continue;

                // Header lesen
                Elements headerCols = rows.get(0).select("th");
                String[] headers = new String[headerCols.size()];
                for (int i = 0; i < headerCols.size(); i++) {
                    headers[i] = headerCols.get(i).text().trim();
                }

                // Datenzeilen verarbeiten
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cols = row.select("td");
                    if (cols.isEmpty()) continue;

                    Map<String, String> data = new HashMap<>();
                    for (int j = 0; j < cols.size() && j < headers.length; j++) {
                        data.put(headers[j], cols.get(j).text().trim());
                    }

                    // Pfade berechnen (Exakt gleiche Logik wie im Generator!)
                    List<String> tokens = new ArrayList<>();
                    addIfPresent(tokens, data.get("Player"));
                    addIfPresent(tokens, data.get("Team"));
                    addIfPresent(tokens, data.get("Season"));
                    addIfPresent(tokens, data.get("Company"));
                    addIfPresent(tokens, data.get("Brand"));
                    addIfPresent(tokens, data.get("Theme"));
                    addIfPresent(tokens, data.get("Variant"));
                    addIfPresent(tokens, data.get("Number"));

                    String gradingCo = data.get("Grading Co.");
                    String grade = data.get("Grade");
                    if (isValid(gradingCo)) tokens.add(gradingCo);
                    if (isValid(grade)) tokens.add(grade);

                    String filenameBase = cleanFilename(String.join("-", tokens));
                    String filename = filenameBase + ".html";
                    String seasonFolder = isValid(data.get("Season")) ? cleanFilename(data.get("Season")) : "Unknown_Season";

                    // URLs zusammenbauen
                    String pageUrl = BASE_URL + "/cards/" + seasonFolder + "/" + filename;

                    // Bild URLs
                    String imgFrontUrl = BASE_URL + "/images/" + seasonFolder + "/" + filenameBase + "-front.jpg";
                    String imgBackUrl = BASE_URL + "/images/" + seasonFolder + "/" + filenameBase + "-back.jpg";
                    String imgTitle = data.get("Player") + " " + data.get("Season") + " " + data.get("Brand") + " " + data.get("Variant");

                    // Eintrag zur Sitemap hinzufügen (mit Bildern)
                    addUrlWithImages(xml, pageUrl, imgFrontUrl, imgBackUrl, imgTitle);
                    cardCount++;
                }
            }

            xml.append("</urlset>");

            Files.writeString(Paths.get(OUTPUT_SITEMAP), xml.toString(), StandardCharsets.UTF_8);

            System.out.println("--------------------------------------------------");
            System.out.println("SUCCESS! Generated sitemap with " + (cardCount + 5) + " URLs.");
            System.out.println("Saved to: " + OUTPUT_SITEMAP);
            System.out.println("Don't forget to upload this file to your root directory!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fügt eine normale URL hinzu (für statische Seiten)
     */
    private static void addUrl(StringBuilder sb, String loc, String priority, String freq) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(DATE_TODAY).append("</lastmod>\n");
        sb.append("    <changefreq>").append(freq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    /**
     * Fügt eine Karten-URL inklusive Bild-Referenzen hinzu (Google Image Sitemap)
     */
    private static void addUrlWithImages(StringBuilder sb, String loc, String imgFront, String imgBack, String title) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(DATE_TODAY).append("</lastmod>\n");
        sb.append("    <changefreq>never</changefreq>\n"); // Sammelkarten ändern sich selten
        sb.append("    <priority>0.6</priority>\n");

        // Image Extension: Front
        sb.append("    <image:image>\n");
        sb.append("      <image:loc>").append(imgFront).append("</image:loc>\n");
        sb.append("      <image:title>Front view: ").append(title).append("</image:title>\n");
        sb.append("    </image:image>\n");

        // Image Extension: Back
        sb.append("    <image:image>\n");
        sb.append("      <image:loc>").append(imgBack).append("</image:loc>\n");
        sb.append("      <image:title>Back view: ").append(title).append("</image:title>\n");
        sb.append("    </image:image>\n");

        sb.append("  </url>\n");
    }

    // --- Hilfsmethoden (Kopiert vom PageGenerator für Konsistenz) ---

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