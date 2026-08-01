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
        fmConfig = new Configuration(Configuration.VERSION_2_3_34);
        fmConfig.setClassForTemplateLoading(SitemapGenerator.class, "/templates");
        fmConfig.setDefaultEncoding("UTF-8");
        fmConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }
    private static final String BASE_URL = "https://www.maulmann.de";
    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) {
        generate();
    }

    private static TimestampTracker timestampTracker;

    public static void setTimestampTracker(TimestampTracker tracker) {
        timestampTracker = tracker;
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
            xml.append("<?xml-stylesheet type=\"text/xsl\" href=\"sitemap.xsl\"?>\n");
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

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            for (Path path : allPaths) {
                String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
                String loc = BASE_URL + "/" + relativePath;

                // SEO Best Practice: index.html auf die reine Root-Domain leiten
                if (loc.endsWith("/index.html")) {
                    loc = loc.replace("/index.html", "/");
                }

                // File-based lastmod timestamp for Search Engine accuracy
                String lastModDate;
                try {
                    lastModDate = isoFormat.format(new Date(Files.getLastModifiedTime(path).toMillis()));
                } catch (Exception e) {
                    lastModDate = isoFormat.format(new Date());
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
                xml.append("    <lastmod>").append(lastModDate).append("</lastmod>\n");
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
                        String theme = getSpecValue(doc, "Theme");
                        String variant = getSpecValue(doc, "Variant");

                        linkMap.put("player", pageTitle);
                        linkMap.put("company", getSpecValue(doc, "Manufacturer"));
                        linkMap.put("brand", getSpecValue(doc, "Brand"));
                        linkMap.put("theme", theme);
                        linkMap.put("variant", variant);
                        linkMap.put("number", getSpecValue(doc, "Card Number"));

                        // Build unique anchor text including Theme and Variant without "Theme:" / "Variant:" prefixes
                        StringBuilder anchorText = new StringBuilder(pageTitle);
                        List<String> extra = new ArrayList<>();
                        if (!theme.isEmpty() && !theme.equals("-") && !pageTitle.toLowerCase().contains(theme.toLowerCase())) {
                            extra.add(theme);
                        }
                        if (!variant.isEmpty() && !variant.equals("-") && !variant.equalsIgnoreCase("Base") && !pageTitle.toLowerCase().contains(variant.toLowerCase())) {
                            extra.add(variant);
                        }
                        if (!extra.isEmpty()) {
                            anchorText.append(" - ").append(String.join(" - ", extra));
                        }
                        linkMap.put("text", anchorText.toString());

                        String[] parts = relativePath.split("/");
                        if (parts.length >= 3) {
                            String season = parts[1];
                            seasonGroups.computeIfAbsent(season, k -> new ArrayList<>()).add(linkMap);
                        }
                    }

                    // Extract Images from <img> and <picture>/<source> elements for Image Sitemap
                    Set<String> processedImageUrls = new HashSet<>();
                    Elements pictureSources = doc.select("picture source[srcset]");
                    for (Element source : pictureSources) {
                        String srcset = source.attr("srcset");
                        if (!srcset.isEmpty()) {
                            String[] candidates = srcset.split(",");
                            String firstCandidate = candidates[0].trim().split("\\s+")[0];
                            String absLoc = resolveImageLoc(relativePath, firstCandidate);
                            if (!absLoc.isEmpty() && processedImageUrls.add(absLoc)) {
                                addImageToXml(xml, absLoc, pageTitle);
                                imagesAdded.incrementAndGet();
                            }
                        }
                    }

                    Elements imgs = doc.select("img");
                    for (Element img : imgs) {
                        String src = img.attr("src");
                        if (src.isEmpty() || src.startsWith("data:")) continue;

                        String absImageLoc = resolveImageLoc(relativePath, src);
                        if (absImageLoc.isEmpty() || !processedImageUrls.add(absImageLoc)) continue;

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
                            if (alt.isEmpty()) alt = pageTitle;
                            addImageToXml(xml, absImageLoc, alt);
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
            try (FileWriter writer = new FileWriter(sitemapFile, StandardCharsets.UTF_8)) {
                writer.write(xml.toString());
            }

            System.out.println("-> Sitemap.xml successfully generated!");
            System.out.println("   > Images added: " + imagesAdded.get());
            if (imagesMissing.get() > 0) {
                System.out.println("   > Images missing: " + imagesMissing.get());
            }

            generateHtmlSitemap(coreLinks, seasonGroups);
            generateLlmsFullTxt(allPaths);
            generateRssFeed(allPaths);

        } catch (Exception e) {
            System.err.println("Failed to generate Sitemap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateLlmsFullTxt(List<Path> allPaths) {
        System.out.println("-> Generating llms-full.txt for AI/LLM RAG Indexing...");
        StringBuilder sb = new StringBuilder();
        sb.append("# maulmann.de - Full Private Collection Knowledge Base for LLMs\n\n");
        sb.append("> Full dataset index for the Juwan Howard Basketball Card Private Collection.\n");
        sb.append("> Web: ").append(BASE_URL).append("/\n\n");

        sb.append("## Core Overview Pages\n");
        sb.append("- Home: ").append(BASE_URL).append("/\n");
        sb.append("- Juwan Howard Collection: ").append(BASE_URL).append("/Juwan-Howard-Collection.html\n");
        sb.append("- Baseball Collection: ").append(BASE_URL).append("/Baseball.html\n");
        sb.append("- Flawless Collection: ").append(BASE_URL).append("/Flawless.html\n");
        sb.append("- Panini Collection: ").append(BASE_URL).append("/Panini.html\n");
        sb.append("- Wantlist: ").append(BASE_URL).append("/Wantlist.html\n\n");

        sb.append("## Complete Card Index & Direct URLs\n\n");

        Path outputDirPath = Paths.get(OUTPUT_DIR);
        for (Path path : allPaths) {
            String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
            if (!relativePath.startsWith("cards/")) continue;

            String fileName = path.getFileName().toString().replace(".html", "");
            String loc = BASE_URL + "/" + relativePath;

            try {
                Document doc = Jsoup.parse(path.toFile(), "UTF-8");
                String title = doc.select("h1").text();
                String desc = doc.select("meta[name=description]").attr("content");
                if (title.isEmpty()) title = fileName;

                sb.append("### ").append(title).append("\n");
                sb.append("- URL: ").append(loc).append("\n");
                if (!desc.isEmpty()) {
                    sb.append("- Description: ").append(desc).append("\n");
                }
                sb.append("\n");
            } catch (Exception ignored) {
                sb.append("- ").append(fileName).append(": ").append(loc).append("\n");
            }
        }

        File llmsFullFile = new File(OUTPUT_DIR + "/llms-full.txt");
        try (FileWriter writer = new FileWriter(llmsFullFile, StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
            System.out.println("-> llms-full.txt successfully generated!");
        } catch (IOException e) {
            System.err.println("Failed to write llms-full.txt: " + e.getMessage());
        }
    }

    private static void generateRssFeed(List<Path> allPaths) {
        System.out.println("-> Generating RSS feed (rss.xml)...");
        StringBuilder rss = new StringBuilder();
        rss.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        rss.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
        rss.append("  <channel>\n");
        rss.append("    <title>Maulmann Trading Cards - Private Collection Updates</title>\n");
        rss.append("    <link>").append(BASE_URL).append("/</link>\n");
        rss.append("    <description>Latest additions and rare card highlights from the Maulmann Private Card Vault.</description>\n");
        rss.append("    <language>en-us</language>\n");
        rss.append("    <atom:link href=\"").append(BASE_URL).append("/rss.xml\" rel=\"self\" type=\"application/rss+xml\" />\n");

        SimpleDateFormat rfc822 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        rfc822.setTimeZone(TimeZone.getTimeZone("UTC"));
        rss.append("    <pubDate>").append(rfc822.format(new Date())).append("</pubDate>\n");

        Path outputDirPath = Paths.get(OUTPUT_DIR);
        int itemCap = 50;
        int addedCount = 0;

        for (Path path : allPaths) {
            String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
            if (!relativePath.startsWith("cards/")) continue;

            String loc = BASE_URL + "/" + relativePath;

            try {
                Document doc = Jsoup.parse(path.toFile(), "UTF-8");
                String title = doc.select("h1").text();
                String desc = doc.select("meta[name=description]").attr("content");
                if (title.isEmpty()) title = path.getFileName().toString().replace(".html", "");

                String pubDate;
                try {
                    pubDate = rfc822.format(new Date(Files.getLastModifiedTime(path).toMillis()));
                } catch (Exception e) {
                    pubDate = rfc822.format(new Date());
                }

                rss.append("    <item>\n");
                rss.append("      <title>").append(escapeXml(title)).append("</title>\n");
                rss.append("      <link>").append(escapeXml(loc)).append("</link>\n");
                rss.append("      <guid isPermaLink=\"true\">").append(escapeXml(loc)).append("</guid>\n");
                rss.append("      <pubDate>").append(pubDate).append("</pubDate>\n");
                if (!desc.isEmpty()) {
                    rss.append("      <description>").append(escapeXml(desc)).append("</description>\n");
                }
                rss.append("    </item>\n");

                addedCount++;
                if (addedCount >= itemCap) break;
            } catch (Exception ignored) {
            }
        }

        rss.append("  </channel>\n");
        rss.append("</rss>");

        File rssFile = new File(OUTPUT_DIR + "/rss.xml");
        try (FileWriter writer = new FileWriter(rssFile, StandardCharsets.UTF_8)) {
            writer.write(rss.toString());
            System.out.println("-> rss.xml successfully generated! (" + addedCount + " items)");
        } catch (IOException e) {
            System.err.println("Failed to write rss.xml: " + e.getMessage());
        }
    }

    private static void generateHtmlSitemap(List<Map<String, String>> coreLinks, Map<String, List<Map<String, String>>> seasonGroups) {
        try {
            System.out.println("-> Generating sitemap.html...");

            Map<String, Object> data = new HashMap<>();

            String title = "HTML Sitemap | Juwan Howard Private Collection";
            String description = "An organized overview of all pages in the Juwan Howard Super Collector private collection, including over 1,000 unique trading cards.";

            String headHtml = SharedTemplates.getHead(title, description, "", "sitemap.html", FileGenerator.DEFAULT_IMAGE);
            String topNavHtml = SharedTemplates.getTopNav("", "sitemap");

            List<Map<String, String>> breadcrumbItems = new ArrayList<>();
            breadcrumbItems.add(Map.of("name", "Home", "link", "index.html"));
            breadcrumbItems.add(Map.of("name", "Sitemap", "link", ""));
            data.put("breadcrumbHtml", SharedTemplates.getBreadcrumb(breadcrumbItems));

            String footerHtml = SharedTemplates.getFooter("");

            // Generate JSON-LD for Sitemap
            List<Map<String, String>> bcItems = new ArrayList<>();
            bcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
            bcItems.add(Map.of("name", "Sitemap", "link", BASE_URL + "/sitemap.html"));

            String jsonLd = "<script type=\"application/ld+json\">\n" +
                    "{\n" +
                    "  \"@context\": \"https://schema.org\",\n" +
                    "  \"@graph\": [\n" +
                    "    " + SharedTemplates.getBreadcrumbJsonLd(bcItems, BASE_URL + "/sitemap.html#breadcrumb") + ",\n" +
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

            StringWriter sw = new StringWriter();
            template.process(data, sw);
            String finalHtml = sw.toString();

            if (timestampTracker != null && finalHtml.contains("[[STABLE_TIME]]")) {
                String stableTime = timestampTracker.getStableTimestamp("sitemap.html", finalHtml);
                finalHtml = finalHtml.replace("[[STABLE_TIME]]", stableTime);
            } else {
                finalHtml = finalHtml.replace("[[STABLE_TIME]]", SharedTemplates.getTimestamp());
            }

            Files.writeString(outFile.toPath(), finalHtml, StandardCharsets.UTF_8);
            System.out.println("-> Sitemap.html successfully generated!");
        } catch (Exception e) {
            System.err.println("Failed to generate HTML Sitemap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void addImageToXml(StringBuilder xml, String imageLoc, String caption) {
        xml.append("    <image:image>\n");
        xml.append("      <image:loc>").append(escapeXml(imageLoc)).append("</image:loc>\n");
        if (caption != null && !caption.trim().isEmpty()) {
            xml.append("      <image:caption>").append(escapeXml(caption.trim())).append("</image:caption>\n");
        }
        xml.append("    </image:image>\n");
    }

    private static void generateRobotsTxt() throws IOException {
        StringBuilder robots = new StringBuilder();

        // Standard Search Engine Crawling
        robots.append("User-agent: *\n");
        robots.append("Allow: /\n\n");

        // High Priority Image Indexing for Collector Websites
        robots.append("User-agent: Googlebot-Image\n");
        robots.append("Allow: /images/\n\n");

        // AI Search Discovery Bots & LLM Crawlers
        robots.append("User-agent: GPTBot\nAllow: /\n\n");
        robots.append("User-agent: ChatGPT-User\nAllow: /\n\n");
        robots.append("User-agent: ClaudeBot\nAllow: /\n\n");
        robots.append("User-agent: Claude-Web\nAllow: /\n\n");
        robots.append("User-agent: PerplexityBot\nAllow: /\n\n");
        robots.append("User-agent: Google-Extended\nAllow: /\n\n");
        robots.append("User-agent: Applebot\nAllow: /\n\n");
        robots.append("User-agent: Meta-ExternalAgent\nAllow: /\n\n");
        robots.append("User-agent: Amazonbot\nAllow: /\n\n");
        robots.append("User-agent: ByteDance\nAllow: /\n\n");

        // Dual Sitemap Indexing & LLM Manifests
        robots.append("Sitemap: ").append(BASE_URL).append("/sitemap.xml\n");
        robots.append("Sitemap: ").append(BASE_URL).append("/sitemap.xml.gz\n");
        robots.append("Sitemap: ").append(BASE_URL).append("/llms.txt\n");
        robots.append("Sitemap: ").append(BASE_URL).append("/llms-full.txt\n");

        File robotsFile = new File(OUTPUT_DIR + "/robots.txt");
        try (FileWriter writer = new FileWriter(robotsFile, StandardCharsets.UTF_8)) {
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
        } catch (Exception _) {
            return baseUrlStripped + "/" + imgSrc;
        }
    }

    private static String getSpecValue(Document doc, String specName) {
        Elements rows = doc.select("tr");
        for (Element row : rows) {
            Element th = row.selectFirst("th.specs-th");
            if (th != null && th.text().trim().equalsIgnoreCase(specName)) {
                Element td = row.selectFirst("td.specs-td");
                if (td != null) {
                    return td.text().trim();
                }
            }
        }
        return "";
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