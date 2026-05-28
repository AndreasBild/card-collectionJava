package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SitemapGenerator {

    private static final Configuration fmConfig;
    static {
        fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        fmConfig.setClassForTemplateLoading(SitemapGenerator.class, "/templates");
        fmConfig.setDefaultEncoding("UTF-8");
        fmConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) {
        generate();
    }

    public static void generate() {
        AtomicInteger imagesAdded = new AtomicInteger(0);
        AtomicInteger imagesMissing = new AtomicInteger(0);

        List<Map<String, String>> coreLinks = new ArrayList<>();
        Map<String, List<Map<String, String>>> seasonGroups = new TreeMap<>();

        try {
            System.out.println("-> Generating best-in-class robots.txt...");
            generateRobotsTxt();

            System.out.println("-> Scanning output directory for sitemap.xml and sitemap.html...");
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"https://www.maulmann.de/sitemap.xsl\"?>\n");
            xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
            xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");

            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            Path outputDirPath = Paths.get(OUTPUT_DIR);

            // Collect paths first to sort them
            List<Path> allPaths = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(outputDirPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".html"))
                        .forEach(allPaths::add);
            }

            // Sort paths: Core files first, then cards by season
            allPaths.sort((p1, p2) -> {
                String s1 = outputDirPath.relativize(p1).toString().replace("\\", "/");
                String s2 = outputDirPath.relativize(p2).toString().replace("\\", "/");
                boolean p1IsCore = !s1.contains("/");
                boolean p2IsCore = !s2.contains("/");
                if (p1IsCore && !p2IsCore) return -1;
                if (!p1IsCore && p2IsCore) return 1;
                return s1.compareTo(s2);
            });

            for (Path path : allPaths) {
                String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
                String loc = BASE_URL + "/" + relativePath;

                // SEO Best Practice: index.html auf die reine Root-Domain leiten
                if (loc.endsWith("/index.html")) {
                    loc = loc.replace("/index.html", "/");
                }

                // Smarte SEO Prioritäten und Crawl-Frequenzen
                String priority = "0.6";
                String changeFreq = "yearly";

                if (relativePath.equals("index.html")) {
                    priority = "1.0";
                    changeFreq = "weekly";
                } else if (relativePath.equals("Juwan-Howard-Collection.html")) {
                    priority = "0.9";
                    changeFreq = "daily";
                } else if (!relativePath.contains("/")) {
                    priority = "0.8";
                    changeFreq = "weekly";
                }

                xml.append("  <url>\n");
                xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
                xml.append("    <lastmod>").append(today).append("</lastmod>\n");
                xml.append("    <changefreq>").append(changeFreq).append("</changefreq>\n");
                xml.append("    <priority>").append(priority).append("</priority>\n");

                try {
                    Document doc = Jsoup.parse(path.toFile(), "UTF-8");
                    String pageTitle = doc.title();
                    if (pageTitle.contains("|")) {
                        pageTitle = pageTitle.split("\\|")[0].trim();
                    }
                    if (pageTitle.isEmpty()) pageTitle = relativePath;

                    Map<String, String> linkMap = new HashMap<>();
                    linkMap.put("url", relativePath);
                    linkMap.put("text", pageTitle);

                    if (!relativePath.contains("/")) {
                        coreLinks.add(linkMap);
                    } else if (relativePath.startsWith("cards/")) {
                        String[] parts = relativePath.split("/");
                        if (parts.length >= 3) {
                            String season = parts[1];
                            seasonGroups.computeIfAbsent(season, k -> new ArrayList<>()).add(linkMap);
                        }
                    }

                    Elements imgs = doc.select("img");
                    for (Element img : imgs) {
                        String src = img.attr("src");
                        if (src.isEmpty() || src.startsWith("data:")) continue;

                        String absImageLoc = resolveImageLoc(relativePath, src);
                        if (absImageLoc.isEmpty()) continue;

                        boolean exists = false;
                        if (src.startsWith("http") || src.startsWith("//")) {
                            exists = true;
                        } else {
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
                    System.err.println("Could not parse " + path + ": " + e.getMessage());
                }

                xml.append("  </url>\n");
            }

            xml.append("</urlset>");

            File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
            try (FileWriter writer = new FileWriter(sitemapFile)) {
                writer.write(xml.toString());
            }

            System.out.println("-> Sitemap.xml successfully generated!");
            System.out.println("   > Images added: " + imagesAdded.get());
            if (imagesMissing.get() > 0) {
                System.out.println("   > Images missing: " + imagesMissing.get());
            }

            generateHtmlSitemap(coreLinks, seasonGroups);

        } catch (Exception e) {
            System.err.println("Failed to generate Sitemap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateHtmlSitemap(List<Map<String, String>> coreLinks, Map<String, List<Map<String, String>>> seasonGroups) {
        try {
            System.out.println("-> Generating sitemap.html...");

            Map<String, Object> data = new HashMap<>();

            String title = "HTML Sitemap | Juwan Howard Private Collection";
            String description = "An organized overview of all pages in the Juwan Howard Super Collector private collection, including over 1,000 unique trading cards.";

            // Note: We need to use SharedTemplates but SitemapGenerator might run in a context
            // where it doesn't have easy access to all createBaseData logic from FileGenerator.
            // However, they are in the same package.

            String headHtml = SharedTemplates.getHead(title, description, "", "sitemap.html", FileGenerator.IMAGE_PATH);
            String topNavHtml = SharedTemplates.getTopNav("", "sitemap");
            String footerHtml = SharedTemplates.getFooter("");

            // Generate JSON-LD for Sitemap
            String jsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    {\n" +
                    "      \"@type\": \"BreadcrumbList\",\n" +
                    "      \"name\": \"Breadcrumbs\",\n" +
                    "      \"itemListElement\": [\n" +
                    "        { \"@type\": \"ListItem\", \"position\": 1, \"name\": \"Home\", \"item\": \"" + BASE_URL + "/index.html\" },\n" +
                    "        { \"@type\": \"ListItem\", \"position\": 2, \"name\": \"Sitemap\", \"item\": \"" + BASE_URL + "/sitemap.html\" }\n" +
                    "      ]\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"@type\": \"WebPage\",\n" +
                    "      \"@id\": \"" + BASE_URL + "/sitemap.html\",\n" +
                    "      \"url\": \"" + BASE_URL + "/sitemap.html\",\n" +
                    "      \"name\": \"" + title + "\",\n" +
                    "      \"description\": \"" + description + "\",\n" +
                    "      \"publisher\": { \"@type\": \"Person\", \"name\": \"Mauli Maulmann\" }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "</script>";

            data.put("headHtml", headHtml);
            data.put("jsonLd", jsonLd);
            data.put("topNavHtml", topNavHtml);
            data.put("footerHtml", footerHtml);

            data.put("coreLinks", coreLinks);

            List<Map<String, Object>> cardSeasons = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, String>>> entry : seasonGroups.entrySet()) {
                Map<String, Object> seasonMap = new HashMap<>();
                seasonMap.put("name", "Season " + entry.getKey());
                seasonMap.put("links", entry.getValue());
                cardSeasons.add(seasonMap);
            }
            data.put("cardSeasons", cardSeasons);

            Template template = fmConfig.getTemplate("sitemap.ftlh");
            File outFile = new File(OUTPUT_DIR + "/sitemap.html");

            try (FileWriter writer = new FileWriter(outFile)) {
                template.process(data, writer);
            }

            // Post-process for stable time if needed?
            // SitemapGenerator is called in Phase 1 of SiteBuilderPipeline after FileGenerator.
            // We might need to handle the [[STABLE_TIME]] here as well if we want it to be consistent.
            // But let's see if the current implementation in FileGenerator/CardPageGenerator is enough.
            // Actually, processTemplate in FileGenerator handles it.
            // SitemapGenerator.generate() is called directly.

            String finalHtml = Files.readString(outFile.toPath(), StandardCharsets.UTF_8);
            if (finalHtml.contains("[[STABLE_TIME]]")) {
                // We don't have easy access to the timestampTracker here unless we pass it.
                // For now, let's just use the current timestamp if not handled.
                // But wait, SharedTemplates.getFooter("") uses [[STABLE_TIME]].
                finalHtml = finalHtml.replace("[[STABLE_TIME]]", SharedTemplates.getTimestamp());
                Files.writeString(outFile.toPath(), finalHtml, StandardCharsets.UTF_8);
            }

            System.out.println("-> Sitemap.html successfully generated!");
        } catch (Exception e) {
            System.err.println("Failed to generate HTML Sitemap: " + e.getMessage());
            e.printStackTrace();
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