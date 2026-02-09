package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SitemapGenerator {

    private static final String BASE_URL = "https://www.maulmann.de";
    private static final String INPUT_FILE = "newIndex/index.html";
    private static final String OUTPUT_SITEMAP = "output/sitemap.xml";
    private static final String IMAGE_PATH_LOCAL = "images/";
    private static final String DATE_TODAY = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

    public static void main(String[] args) {
        try {
            System.out.println("Generating sitemap.xml for: " + BASE_URL);

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"https://www.maulmann.de/sitemap.xsl\"?>\n");
            xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
            xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\"\n");
            xml.append("         xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

            // 1. Statische Seiten
            addUrl(xml, BASE_URL + "/index.html", "1.0", "weekly");
            addUrl(xml, BASE_URL + "/Wantlist.html", "0.8", "monthly");

            // 2. Karten verarbeiten
            File input = new File(INPUT_FILE);
            Document doc = Jsoup.parse(input, "UTF-8");
            Elements tables = doc.select("table");

            int totalCards = 0;
            int missingFronts = 0;
            int missingBacks = 0;

            for (Element table : tables) {
                Elements rows = table.select("tr");
                if (rows.size() < 2) continue;

                Elements headerCols = rows.get(0).select("th");
                String[] headers = new String[headerCols.size()];
                for (int i = 0; i < headerCols.size(); i++) headers[i] = headerCols.get(i).text().trim();

                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cols = row.select("td");
                    if (cols.isEmpty()) continue;

                    Map<String, String> data = new HashMap<>();
                    for (int j = 0; j < cols.size() && j < headers.length; j++) {
                        data.put(headers[j], cols.get(j).text().trim());
                    }

                    // --- LOGIK START ---

                    // 1. Team Logik (identisch zu CardPageGenerator)
                    String currentTeam = data.get("Team");
                    if (!isValid(currentTeam)) {
                        currentTeam = getTeamBySeason(data.get("Season"));
                    }

                    // 2. Dateinamen Tokens sammeln
                    List<String> tokens = new ArrayList<>();
                    addIfPresent(tokens, data.get("Player"));
                    addIfPresent(tokens, currentTeam);
                    addIfPresent(tokens, data.get("Season"));
                    addIfPresent(tokens, data.get("Company"));
                    addIfPresent(tokens, data.get("Brand"));
                    addIfPresent(tokens, data.get("Theme"));
                    addIfPresent(tokens, data.get("Variant"));
                    addIfPresent(tokens, data.get("Number"));

                    // 3. Serial Number Logic
                    String serial = data.get("Serial");
                    if (isValid(serial) && !serial.equals("0")) {
                        tokens.add("sn" + serial);
                    }

                    // 4. Grading Logic
                    String grade = data.get("Grade");
                    if (isValid(grade)) tokens.add(grade);

                    // Pfade generieren
                    String filenameBase = cleanFilename(String.join("-", tokens));
                    String seasonFolder = isValid(data.get("Season")) ? cleanFilename(data.get("Season")) : "Unknown_Season";

                    // HTML URL
                    String pageUrl = BASE_URL + "/cards/" + seasonFolder + "/" + filenameBase + ".html";

                    // --- NEU: DYNAMISCHE BILD-TITEL (identisch zu Alt-Texten) ---
                    String baseTitle = "Juwan Howard " + data.getOrDefault("Season", "") + " " + data.getOrDefault("Brand", "") + " #" + data.getOrDefault("Number", "");

                    String frontCaption = "Front view of " + baseTitle + " basketball card - " + data.getOrDefault("Variant", "") + " edition (" + currentTeam + ")";
                    String backCaption = "Back view of " + baseTitle + " showing stats for " + currentTeam;

                    // Bilder-Check
                    String relDir = IMAGE_PATH_LOCAL + seasonFolder + "/";
                    File fFile = new File(relDir + filenameBase + "-front.jpg");
                    File bFile = new File(relDir + filenameBase + "-back.jpg");
                    // Debug-Ausgabe: Wo sucht das Programm?
                    if (i == 1) { // Nur für die erste Karte, um die Konsole nicht zu fluten
                        System.out.println("Suche Bild unter: " + fFile.getAbsolutePath());
                    }
                    String frontUrl = fFile.exists() ? BASE_URL + "/images/" + seasonFolder + "/" + filenameBase + "-front.jpg" : null;
                    String backUrl = bFile.exists() ? BASE_URL + "/images/" + seasonFolder + "/" + filenameBase + "-back.jpg" : null;

                    if (frontUrl == null) missingFronts++;
                    if (backUrl == null) missingBacks++;

                    // Hier übergeben wir jetzt die spezifischen Captions statt eines generischen Titels
                    addUrlWithImages(xml, pageUrl, frontUrl, frontCaption, backUrl, backCaption);

                    totalCards++;
                }
            }

            xml.append("</urlset>");
            Files.writeString(Paths.get(OUTPUT_SITEMAP), xml.toString(), StandardCharsets.UTF_8);

            System.out.println("--------------------------------------------------");
            System.out.println("Sitemap erfolgreich erstellt!");
            System.out.println("Karten: " + totalCards + " | Front-Bilder: " + (totalCards - missingFronts) + " | Back-Bilder: " + (totalCards - missingBacks));
            System.out.println("--------------------------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
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

    // Angepasste Methode: Nimmt jetzt separate Captions für Front und Back entgegen
    private static void addUrlWithImages(StringBuilder sb, String loc, String imgFront, String captionFront, String imgBack, String captionBack) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(DATE_TODAY).append("</lastmod>\n");
        sb.append("    <changefreq>").append(imgFront == null ? "weekly" : "monthly").append("</changefreq>\n");
        sb.append("    <priority>0.6</priority>\n");

        if (imgFront != null) appendImg(sb, imgFront, captionFront);
        if (imgBack != null) appendImg(sb, imgBack, captionBack);

        sb.append("  </url>\n");
    }

    private static void appendImg(StringBuilder sb, String url, String title) {
        sb.append("    <image:image>\n");
        sb.append("      <image:loc>").append(url).append("</image:loc>\n");
        // image:title ist wichtig für Google Images SEO
        sb.append("      <image:title>").append(escapeXml(title)).append("</image:title>\n");
        sb.append("    </image:image>\n");
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void addUrl(StringBuilder sb, String loc, String prio, String freq) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(DATE_TODAY).append("</lastmod>\n");
        sb.append("    <changefreq>").append(freq).append("</changefreq>\n");
        sb.append("    <priority>").append(prio).append("</priority>\n");
        sb.append("  </url>\n");
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