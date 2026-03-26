package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SitemapGenerator {
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) {
        generate();
    }

    public static void generate() {
        AtomicInteger imagesAdded = new AtomicInteger(0);
        AtomicInteger imagesMissing = new AtomicInteger(0);

        try {
            System.out.println("-> Generating best-in-class robots.txt...");
            generateRobotsTxt();

            System.out.println("-> Scanning output directory for sitemap.xml...");
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"https://www.maulmann.de/sitemap.xsl\"?>\n");
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

                            // Best-in-class Image SEO: Extrahiere Bilder aus der HTML
                            try {
                                Document doc = Jsoup.parse(path.toFile(), "UTF-8");
                                Elements imgs = doc.select("img");
                                for (Element img : imgs) {
                                    String src = img.attr("src");
                                    if (src.isEmpty() || src.startsWith("data:")) continue;

                                    // Auflösen relativer Pfade (z.B. ../../images/...)
                                    String absImageLoc = resolveImageLoc(relativePath, src);
                                    if (absImageLoc.isEmpty()) continue;

                                    // Check existence
                                    boolean exists = false;
                                    if (src.startsWith("http") || src.startsWith("//")) {
                                        exists = true;
                                    } else {
                                        // For local images, resolve absolute URL gives us the path after BASE_URL
                                        if (absImageLoc.startsWith(BASE_URL + "/")) {
                                            String relPath = absImageLoc.substring((BASE_URL + "/").length());
                                            if (Files.exists(Paths.get(OUTPUT_DIR, relPath))) {
                                                exists = true;
                                            }
                                        }
                                    }

                                    if (exists) {
                                        String alt = img.attr("alt");
                                        if (alt.isEmpty()) alt = img.attr("title");

                                        xml.append("    <image:image>\n");
                                        xml.append("      <image:loc>").append(escapeXml(absImageLoc)).append("</image:loc>\n");
                                        if (!alt.isEmpty()) {
                                            xml.append("      <image:caption>").append(escapeXml(alt)).append("</image:caption>\n");
                                        }
                                        xml.append("    </image:image>\n");
                                        imagesAdded.incrementAndGet();
                                    } else {
                                        imagesMissing.incrementAndGet();
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Could not parse " + path + " for images: " + e.getMessage());
                            }

                            xml.append("  </url>\n");
                        });
            }

            xml.append("</urlset>");

            File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
            try (FileWriter writer = new FileWriter(sitemapFile)) {
                writer.write(xml.toString());
            }

            System.out.println("-> Sitemap successfully generated based on actual output files!");
            System.out.println("   > Images added to sitemap: " + imagesAdded.get());
            if (imagesMissing.get() > 0) {
                System.out.println("   > Images missing (skipped): " + imagesMissing.get());
            }

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

    private static String resolveImageLoc(String pageRelativePath, String imgSrc) {
        if (imgSrc == null || imgSrc.isEmpty() || imgSrc.startsWith("data:")) return "";
        if (imgSrc.startsWith("http")) return imgSrc;
        if (imgSrc.startsWith("//")) return "https:" + imgSrc;

        String baseUrlStripped = BASE_URL;

        if (imgSrc.startsWith("/")) {
            return baseUrlStripped + imgSrc;
        }

        // Wir gehen davon aus, dass alle Pfade im Output-Verzeichnis relativ zueinander sind.
        // pageRelativePath ist z.B. "cards/2005/some-card.html"
        // imgSrc ist z.B. "../../images/2005/some-card-front.webp"

        try {
            Path pagePath = Paths.get(pageRelativePath);
            Path parent = pagePath.getParent();

            String resultPath;
            if (parent == null) {
                // Datei liegt im Root, z.B. "index.html"
                resultPath = imgSrc;
            } else {
                // Normalisiere den Pfad relativ zur aktuellen Seite
                resultPath = parent.resolve(imgSrc).normalize().toString().replace("\\", "/");
            }

            // Bereinige führende ./ oder /
            while (resultPath.startsWith("/")) resultPath = resultPath.substring(1);
            while (resultPath.startsWith("./")) resultPath = resultPath.substring(2);

            return baseUrlStripped + "/" + resultPath;
        } catch (Exception e) {
            return baseUrlStripped + "/" + imgSrc;
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