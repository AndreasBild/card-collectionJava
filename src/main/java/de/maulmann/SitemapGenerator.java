package de.maulmann;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;

public class SitemapGenerator {
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final String OUTPUT_DIR = "output";

    public static void generate() {
        try {
            System.out.println("-> Generating best-in-class robots.txt...");
            generateRobotsTxt();

            System.out.println("-> Scanning output directory for sitemap.xml...");
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
            // Standard Image Sitemap Erweiterung (optional, aber Best Practice)
            xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");

            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            Path outputDirPath = Paths.get(OUTPUT_DIR);

            // Scanne den kompletten Output-Ordner auf alle existierenden HTML-Dateien
            try (Stream<Path> paths = Files.walk(outputDirPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".html"))
                        .forEach(path -> {
                            String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");

                            String loc = BASE_URL + "/" + relativePath;

                            // SEO Best Practice: index.html auf die reine Root-Domain leiten
                            if (loc.endsWith("/index.html")) {
                                loc = loc.replace("/index.html", "/");
                            }

                            // Smarte SEO Prioritäten und Crawl-Frequenzen
                            String priority = "0.6";
                            String changeFreq = "yearly"; // Standard für alte Einzelkarten

                            if (relativePath.equals("index.html")) {
                                priority = "1.0";
                                changeFreq = "weekly";
                            } else if (relativePath.equals("Juwan-Howard-Collection.html")) {
                                priority = "0.9";
                                changeFreq = "daily"; // Die Hauptsammlung wächst ständig
                            } else if (!relativePath.contains("/")) {
                                // Andere statische Root-Dateien wie Baseball.html, Flawless.html
                                priority = "0.8";
                                changeFreq = "weekly";
                            }

                            xml.append("  <url>\n");
                            xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
                            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
                            xml.append("    <changefreq>").append(changeFreq).append("</changefreq>\n");
                            xml.append("    <priority>").append(priority).append("</priority>\n");
                            xml.append("  </url>\n");
                        });
            }

            xml.append("</urlset>");

            File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
            try (FileWriter writer = new FileWriter(sitemapFile)) {
                writer.write(xml.toString());
            }

            System.out.println("-> Sitemap successfully generated based on actual output files!");

        } catch (Exception e) {
            System.err.println("Failed to generate Sitemap: " + e.getMessage());
        }
    }

    private static void generateRobotsTxt() throws IOException {
        StringBuilder robots = new StringBuilder();

        // Erlaube allen Suchmaschinen das Crawlen der gesamten Seite
        robots.append("User-agent: *\n");
        robots.append("Allow: /\n\n");

        // Explizit Bilder-Bots erlauben (extrem wichtig für Sammler-Websites!)
        robots.append("User-agent: Googlebot-Image\n");
        robots.append("Allow: /images/\n\n");

        // Zeige den Crawlern sofort an, wo die komprimierte Sitemap liegt
        robots.append("Sitemap: ").append(BASE_URL).append("/sitemap.xml.gz\n");

        File robotsFile = new File(OUTPUT_DIR + "/robots.txt");
        try (FileWriter writer = new FileWriter(robotsFile)) {
            writer.write(robots.toString());
        }
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}