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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class SitemapGenerator {

    private static final String BASE_URL = "https://www.maulmann.de";
    private static final String OUTPUT_SITEMAP = "output/sitemap.xml";
    private static final String IMAGE_PATH_LOCAL = "images/";
    private static final String DATE_TODAY = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

    // In-memory cache for ultra-fast file existence checks
    private static final Set<String> availableImages = new HashSet<>();

    public static void main(String[] args) {
        generate();
    }

    public static void generate() {
        try {
            System.out.println("Generating sitemap.xml for: " + BASE_URL);

            // 1. Build Image Cache
            buildImageCache();

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"https://www.maulmann.de/sitemap.xsl\"?>\n");
            xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
            xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\"\n");
            xml.append("         xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

            // 2. Static and Collection Pages
            addUrl(xml, BASE_URL + "/index.html", "1.0", "weekly", getLastModifiedDate("output/index.html"));

            String[] collectionFiles = {
                    "Juwan-Howard-Collection.html",
                    "Baseball.html",
                    "Flawless.html",
                    "Wantlist.html",
                    "Panini.html"
            };

            for (String fileName : collectionFiles) {
                String filePath = "output/" + fileName;
                addUrl(xml, BASE_URL + "/" + fileName, "0.9", "weekly", getLastModifiedDate(filePath));
                processCollectionCards(xml, filePath);
            }

            xml.append("</urlset>");

            File outputFile = new File(OUTPUT_SITEMAP);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            Files.writeString(outputFile.toPath(), xml.toString(), StandardCharsets.UTF_8);

            System.out.println("--------------------------------------------------");
            System.out.println("✅ Sitemap successfully generated at " + OUTPUT_SITEMAP);
            System.out.println("--------------------------------------------------");

        } catch (Exception e) {
            System.err.println("❌ Error generating sitemap:");
            e.printStackTrace();
        }
    }

    private static void processCollectionCards(StringBuilder xml, String inputPath) throws IOException {
        File input = new File(inputPath);
        if (!input.exists()) return;

        Document doc = Jsoup.parse(input, "UTF-8");
        Elements tables = doc.select("table");

        for (Element table : tables) {
            Elements rows = table.select("tr");
            if (rows.isEmpty()) continue;

            int headerRowIndex = -1;
            String[] headers = null;

            for (int i = 0; i < rows.size(); i++) {
                Elements ths = rows.get(i).select("th");
                if (ths.isEmpty()) ths = rows.get(i).select("td");

                if (!ths.isEmpty()) {
                    headers = new String[ths.size()];
                    for (int j = 0; j < ths.size(); j++) {
                        headers[j] = ths.get(j).text().trim();
                    }
                    headerRowIndex = i;
                    break;
                }
            }

            if (headerRowIndex == -1 || headers == null) continue;

            for (int i = headerRowIndex + 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cols = row.select("td");
                if (cols.isEmpty()) continue;

                Map<String, String> data = new HashMap<>();
                for (int j = 0; j < cols.size() && j < headers.length; j++) {
                    data.put(headers[j], cols.get(j).text().trim());
                }

                // Path generation logic (Keep in sync with CardPageGenerator)
                String player = data.getOrDefault("Player", "");
                String team = data.getOrDefault("Team", "");
                String season = data.getOrDefault("Season", "");

                if (!isValid(team) && "Juwan Howard".equals(player)) {
                    team = getTeamBySeason(season);
                }

                List<String> tokens = new ArrayList<>();
                addIfPresent(tokens, player);
                addIfPresent(tokens, team);
                addIfPresent(tokens, season);
                addIfPresent(tokens, data.get("Company"));
                addIfPresent(tokens, data.get("Brand"));
                addIfPresent(tokens, data.get("Theme"));
                addIfPresent(tokens, data.get("Variant"));
                addIfPresent(tokens, data.get("Number"));

                String serial = data.get("Serial");
                if (!isValid(serial)) serial = data.get("Serial/Print Run");
                if (isValid(serial) && !serial.equals("0")) {
                    tokens.add("sn" + serial.replace("#", "").replace("/", "-"));
                }

                String grade = data.get("Grade");
                if (isValid(grade)) tokens.add(grade);

                String filenameBase = cleanFilename(String.join("-", tokens));
                String seasonFolder = isValid(season) ? cleanFilename(season) : "Unknown_Season";

                String pageUrl = BASE_URL + "/cards/" + seasonFolder + "/" + filenameBase + ".html";
                String pageLocalPath = "output/cards/" + seasonFolder + "/" + filenameBase + ".html";

                // Dynamic Captions
                String brand = data.getOrDefault("Brand", "");
                String number = data.getOrDefault("Number", "");
                String variant = data.getOrDefault("Variant", "");

                String baseTitle = player + " " + season + " " + brand + " #" + number;
                String frontCaption = "Front view of " + baseTitle + " basketball card - " + variant + " edition (" + team + ")";
                String backCaption = "Back view of " + baseTitle + " showing stats for " + team;

                // Image checks
                String frontRelPath = IMAGE_PATH_LOCAL + seasonFolder + "/" + filenameBase + "-front.jpg";
                String backRelPath = IMAGE_PATH_LOCAL + seasonFolder + "/" + filenameBase + "-back.jpg";

                String frontUrl = availableImages.contains(frontRelPath) ? BASE_URL + "/" + frontRelPath : null;
                String backUrl = availableImages.contains(backRelPath) ? BASE_URL + "/" + backRelPath : null;

                String lastMod = getLastModifiedDate(pageLocalPath);
                addUrlWithImages(xml, pageUrl, lastMod, frontUrl, frontCaption, backUrl, backCaption);
            }
        }
    }

    private static void buildImageCache() {
        Path startPath = Paths.get(IMAGE_PATH_LOCAL);
        if (!Files.exists(startPath)) return;

        try (Stream<Path> stream = Files.walk(startPath)) {
            stream.filter(Files::isRegularFile)
                    .forEach(p -> availableImages.add(p.toString().replace("\\", "/")));
        } catch (Exception e) {
            System.err.println("Warning: Could not build image cache: " + e.getMessage());
        }
    }

    private static String getLastModifiedDate(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return Instant.ofEpochMilli(file.lastModified())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_DATE);
        }
        return DATE_TODAY;
    }

    private static void addUrlWithImages(StringBuilder sb, String loc, String lastmod, String imgFront, String captionFront, String imgBack, String captionBack) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(imgFront == null ? "weekly" : "yearly").append("</changefreq>\n");
        sb.append("    <priority>0.6</priority>\n");

        if (imgFront != null) appendImg(sb, imgFront, captionFront);
        if (imgBack != null) appendImg(sb, imgBack, captionBack);

        sb.append("  </url>\n");
    }

    private static void addUrl(StringBuilder sb, String loc, String prio, String freq, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(freq).append("</changefreq>\n");
        sb.append("    <priority>").append(prio).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private static void appendImg(StringBuilder sb, String url, String title) {
        sb.append("    <image:image>\n");
        sb.append("      <image:loc>").append(escapeXml(url)).append("</image:loc>\n");
        sb.append("      <image:title>").append(escapeXml(title)).append("</image:title>\n");
        sb.append("    </image:image>\n");
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

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
}