package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.BufferedWriter;
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

/**
 * Refactored XML Sitemap Generator.
 * Implements a high-performance hierarchical Sitemap Index (sitemap.xml)
 * with dynamic <lastmod> timestamps per URL and specialized sub-sitemaps.
 */
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

    public record ImageEntry(String loc, String title, String caption) {}

    public record SitemapUrlEntry(
            String relativePath,
            String loc,
            String lastModDate,
            String changeFreq,
            String priority,
            List<ImageEntry> images
    ) {}

    public record ChildSitemapInfo(String fileName, String maxLastMod) {}

    public static void generate() {
        AtomicInteger imagesAdded = new AtomicInteger(0);
        AtomicInteger imagesMissing = new AtomicInteger(0);

        List<Map<String, String>> coreLinks = new ArrayList<>();
        Map<String, List<Map<String, String>>> seasonGroups = new TreeMap<>();

        try {
            System.out.println("-> Generating best-in-class robots.txt...");
            generateRobotsTxt();

            System.out.println("-> Cleaning up old sitemap files...");
            Path outputDirPath = Paths.get(OUTPUT_DIR);
            if (Files.exists(outputDirPath)) {
                try (Stream<Path> sitemapFiles = Files.list(outputDirPath)) {
                    sitemapFiles.filter(p -> p.getFileName().toString().startsWith("sitemap") &&
                                            (p.getFileName().toString().endsWith(".gz") || p.getFileName().toString().endsWith(".xml")))
                                .forEach(p -> {
                                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                                });
                }
            }

            System.out.println("-> Scanning output directory for sitemaps...");

            // Collect all HTML paths
            List<Path> allPaths = new ArrayList<>();
            if (Files.exists(outputDirPath)) {
                try (Stream<Path> paths = Files.walk(outputDirPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".html"))
                            .forEach(allPaths::add);
                }
            }

            // Sort paths: Core files first, then cards alphabetically
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
            String todayIso = isoFormat.format(new Date());

            List<SitemapUrlEntry> mainEntries = new ArrayList<>();
            List<SitemapUrlEntry> highlightEntries = new ArrayList<>();
            Map<String, List<SitemapUrlEntry>> cardGroupEntries = new TreeMap<>();

            for (Path path : allPaths) {
                String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
                String loc = BASE_URL + "/" + relativePath;

                if (loc.endsWith("/index.html")) {
                    loc = loc.replace("/index.html", "/");
                }

                // Dynamic lastmod: read content-tracked timestamp if available
                String lastModDate;
                if (timestampTracker != null) {
                    lastModDate = timestampTracker.getIsoDate(relativePath);
                } else {
                    try {
                        lastModDate = isoFormat.format(new Date(Files.getLastModifiedTime(path).toMillis()));
                    } catch (Exception e) {
                        lastModDate = todayIso;
                    }
                }

                List<ImageEntry> images = new ArrayList<>();
                Document doc = null;
                try {
                    doc = Jsoup.parse(path.toFile(), "UTF-8");
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

                    // Extract images for sitemap
                    Set<String> processedImageUrls = new HashSet<>();
                    Elements pictureElements = doc.select("picture");
                    for (Element picture : pictureElements) {
                        Element img = picture.selectFirst("img");
                        String imgTitle = (img != null) ? img.attr("title").trim() : "";
                        String imgAlt = (img != null) ? img.attr("alt").trim() : "";

                        Element figcaption = picture.parents().select("figcaption").first();
                        String figText = (figcaption != null) ? figcaption.text().trim() : "";

                        String imageTitle = !imgTitle.isEmpty() ? imgTitle : pageTitle;
                        String imageCaption = !figText.isEmpty() ? figText : (!imgAlt.isEmpty() ? imgAlt : pageTitle);

                        String bestCandidate = "";
                        Elements sources = picture.select("source[srcset]");
                        for (Element source : sources) {
                            String candidate = extractHighestResCandidate(source.attr("srcset"));
                            if (!candidate.isEmpty()) {
                                bestCandidate = candidate;
                                break;
                            }
                        }
                        if (bestCandidate.isEmpty() && img != null) {
                            String srcset = img.attr("srcset");
                            if (!srcset.isEmpty()) {
                                bestCandidate = extractHighestResCandidate(srcset);
                            } else {
                                bestCandidate = img.attr("src");
                            }
                        }

                        if (!bestCandidate.isEmpty() && !bestCandidate.startsWith("data:")) {
                            String absLoc = resolveImageLoc(relativePath, bestCandidate);
                            String highResLoc = toHighResLoc(absLoc);
                            if (!highResLoc.isEmpty() && processedImageUrls.add(highResLoc)) {
                                images.add(new ImageEntry(highResLoc, imageTitle, imageCaption));
                                imagesAdded.incrementAndGet();
                            }
                        }
                    }

                    // Fallback for standalone <img> elements (not inside <picture>)
                    Elements standaloneImgs = doc.select("img");
                    for (Element img : standaloneImgs) {
                        if (img.parents().is("picture")) continue;

                        String src = img.attr("src");
                        String srcset = img.attr("srcset");
                        String bestCandidate = !srcset.isEmpty() ? extractHighestResCandidate(srcset) : src;
                        if (bestCandidate.isEmpty() || bestCandidate.startsWith("data:")) continue;

                        String absImageLoc = resolveImageLoc(relativePath, bestCandidate);
                        String highResLoc = toHighResLoc(absImageLoc);
                        if (highResLoc.isEmpty() || !processedImageUrls.add(highResLoc)) continue;

                        String imgTitle = img.attr("title").trim();
                        String imgAlt = img.attr("alt").trim();
                        String imageTitle = !imgTitle.isEmpty() ? imgTitle : pageTitle;
                        String imageCaption = !imgAlt.isEmpty() ? imgAlt : pageTitle;

                        images.add(new ImageEntry(highResLoc, imageTitle, imageCaption));
                        imagesAdded.incrementAndGet();
                    }
                } catch (IOException e) {
                    System.err.println("Could not parse " + path + ": " + e.getMessage());
                }

                // Categorize URL into correct sitemap bucket
                if (!relativePath.contains("/") || relativePath.equalsIgnoreCase("sitemap.html")) {
                    // sitemap-main.xml: Priority 1.0, Changefreq daily
                    mainEntries.add(new SitemapUrlEntry(relativePath, loc, lastModDate, "daily", "1.0", images));
                } else if (relativePath.startsWith("cards/")) {
                    boolean isHighlight = isHighlightCard(doc, relativePath);
                    if (isHighlight) {
                        // sitemap-highlights.xml: Priority 0.9, Changefreq weekly
                        highlightEntries.add(new SitemapUrlEntry(relativePath, loc, lastModDate, "weekly", "0.9", images));
                    } else {
                        // sitemap-cards-[group].xml: Priority 0.5, Changefreq yearly
                        String groupName = extractGroupName(relativePath);
                        cardGroupEntries.computeIfAbsent(groupName, k -> new ArrayList<>())
                                .add(new SitemapUrlEntry(relativePath, loc, lastModDate, "yearly", "0.5", images));
                    }
                } else {
                    // Other static pages: Priority 0.8, Changefreq weekly
                    mainEntries.add(new SitemapUrlEntry(relativePath, loc, lastModDate, "weekly", "0.8", images));
                }
            }

            List<ChildSitemapInfo> childSitemaps = new ArrayList<>();

            // 1. Generate sitemap-main.xml
            if (!mainEntries.isEmpty()) {
                ChildSitemapInfo mainInfo = writeSubSitemap("sitemap-main.xml", mainEntries);
                childSitemaps.add(mainInfo);
            }

            // 2. Generate sitemap-highlights.xml
            if (!highlightEntries.isEmpty()) {
                ChildSitemapInfo hInfo = writeSubSitemap("sitemap-highlights.xml", highlightEntries);
                childSitemaps.add(hInfo);
            }

            // 3. Generate sitemap-cards-[group].xml
            for (Map.Entry<String, List<SitemapUrlEntry>> entry : cardGroupEntries.entrySet()) {
                String fileName = "sitemap-cards-" + sanitizeFilename(entry.getKey()) + ".xml";
                ChildSitemapInfo cInfo = writeSubSitemap(fileName, entry.getValue());
                childSitemaps.add(cInfo);
            }

            // 4. Generate Root sitemap.xml (Sitemap Index)
            writeSitemapIndex("sitemap.xml", childSitemaps);

            System.out.println("-> Sitemap Index & Sub-Sitemaps successfully generated!");
            System.out.println("   > Total Sub-Sitemaps: " + childSitemaps.size());
            System.out.println("   > Images added: " + imagesAdded.get());
            if (imagesMissing.get() > 0) {
                System.out.println("   > Images missing: " + imagesMissing.get());
            }

            generateHtmlSitemap(coreLinks, seasonGroups);
            generateLlmsTxt();
            generateLlmsFullTxt(allPaths);
            generateRssFeed(allPaths);

        } catch (Exception e) {
            System.err.println("Failed to generate Sitemap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean isHighlightCard(Document doc, String relativePath) {
        if (doc == null) return false;
        String pathLower = relativePath.toLowerCase();
        if (pathLower.contains("flawless")) return true;

        String printRunStr = getSpecValue(doc, "Print Run");
        if (printRunStr.isEmpty()) printRunStr = getSpecValue(doc, "Serial/Print Run");
        if (printRunStr.isEmpty()) printRunStr = getSpecValue(doc, "Serial");

        String variant = getSpecValue(doc, "Variant").toLowerCase();
        String auto = getSpecValue(doc, "Autograph").toLowerCase();

        if (variant.contains("1/1") || variant.contains("superfractor") || variant.contains("masterpiece") ||
            variant.contains("logoman") || variant.contains("frozenfractor")) {
            return true;
        }

        if (!printRunStr.isEmpty()) {
            try {
                String cleanRun = printRunStr;
                if (cleanRun.contains("/")) {
                    cleanRun = cleanRun.substring(cleanRun.lastIndexOf("/") + 1).trim();
                }
                int run = Integer.parseInt(cleanRun.replaceAll("[^0-9]", ""));
                if (run > 0 && run <= 5) {
                    return true;
                }
                if (auto.equalsIgnoreCase("yes") && run <= 25) {
                    return true;
                }
            } catch (NumberFormatException ignored) {}
        }

        return auto.equalsIgnoreCase("yes") && (variant.contains("patch") || variant.contains("ruby") || variant.contains("pmg"));
    }

    private static String extractGroupName(String relativePath) {
        String[] parts = relativePath.split("/");
        if (parts.length >= 3) {
            return parts[1];
        }
        return "general";
    }

    private static String sanitizeFilename(String raw) {
        return raw.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private static ChildSitemapInfo writeSubSitemap(String fileName, List<SitemapUrlEntry> entries) throws IOException {
        File file = new File(OUTPUT_DIR, fileName);
        String maxLastMod = "1970-01-01";

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
            writer.write("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");

            for (SitemapUrlEntry entry : entries) {
                if (entry.lastModDate().compareTo(maxLastMod) > 0) {
                    maxLastMod = entry.lastModDate();
                }

                writer.write("  <url>\n");
                writer.write("    <loc>" + escapeXml(entry.loc()) + "</loc>\n");
                writer.write("    <lastmod>" + entry.lastModDate() + "</lastmod>\n");
                writer.write("    <changefreq>" + entry.changeFreq() + "</changefreq>\n");
                writer.write("    <priority>" + entry.priority() + "</priority>\n");

                for (ImageEntry img : entry.images()) {
                    writer.write("    <image:image>\n");
                    writer.write("      <image:loc>" + escapeXml(img.loc()) + "</image:loc>\n");
                    if (img.title() != null && !img.title().trim().isEmpty()) {
                        writer.write("      <image:title>" + escapeXml(img.title().trim()) + "</image:title>\n");
                    }
                    if (img.caption() != null && !img.caption().trim().isEmpty()) {
                        writer.write("      <image:caption>" + escapeXml(img.caption().trim()) + "</image:caption>\n");
                    }
                    writer.write("    </image:image>\n");
                }

                writer.write("  </url>\n");
            }

            writer.write("</urlset>");
        }

        return new ChildSitemapInfo(fileName, maxLastMod);
    }

    private static void writeSitemapIndex(String indexFileName, List<ChildSitemapInfo> childSitemaps) throws IOException {
        File indexFile = new File(OUTPUT_DIR, indexFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(indexFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

            for (ChildSitemapInfo info : childSitemaps) {
                writer.write("  <sitemap>\n");
                writer.write("    <loc>" + BASE_URL + "/" + info.fileName() + "</loc>\n");
                writer.write("    <lastmod>" + info.maxLastMod() + "</lastmod>\n");
                writer.write("  </sitemap>\n");
            }

            writer.write("</sitemapindex>");
        }
    }

    private static void generateLlmsTxt() {
        System.out.println("-> Generating llms.txt standard index for AI/LLMs...");
        StringBuilder sb = new StringBuilder();
        sb.append("# maulmann.de\n\n");
        sb.append("> Maulmann Private Collection: High-end sports card database featuring Juwan Howard, rare 1/1 Masterpieces, and low-numbered serial cards.\n\n");
        sb.append("## Core Pages\n");
        sb.append("- [Collection Overview](").append(BASE_URL).append("/Juwan-Howard-Collection.html): Main private collection index with 1,300+ cards.\n");
        sb.append("- [Rainbow Tracker](").append(BASE_URL).append("/rainbows.html): Interactive tracker for full parallel rainbow sets.\n");
        sb.append("- [Flawless Collection](").append(BASE_URL).append("/Flawless.html): Ultra-high-end Panini Flawless autographs and patches.\n");
        sb.append("- [Panini Showcase](").append(BASE_URL).append("/Panini.html): Panini basketball card release highlights.\n");
        sb.append("- [Baseball Collection](").append(BASE_URL).append("/Baseball.html): Certified MLB autograph and game-used memorabilia cards.\n");
        sb.append("- [Wantlist](").append(BASE_URL).append("/Wantlist.html): Cards actively sought after for the collection.\n\n");
        sb.append("## Full Database Index\n");
        sb.append("- [llms-full.txt](").append(BASE_URL).append("/llms-full.txt): Complete detailed text index of all 1,300+ card pages with metadata.\n");

        File llmsFile = new File(OUTPUT_DIR + "/llms.txt");
        try (FileWriter writer = new FileWriter(llmsFile, StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
            System.out.println("-> llms.txt successfully generated!");
        } catch (IOException e) {
            System.err.println("Failed to write llms.txt: " + e.getMessage());
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

    private static void generateRobotsTxt() throws IOException {
        StringBuilder robots = new StringBuilder();

        robots.append("User-agent: *\n");
        robots.append("Allow: /\n\n");

        robots.append("User-agent: Googlebot-Image\n");
        robots.append("Allow: /images/\n\n");

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

        robots.append("Sitemap: ").append(BASE_URL).append("/sitemap.xml\n");
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

        try {
            Path pagePath = Paths.get(pageRelativePath);
            Path parent = pagePath.getParent();

            String resultPath;
            if (parent == null) {
                resultPath = imgSrc;
            } else {
                resultPath = parent.resolve(imgSrc).normalize().toString().replace("\\", "/");
            }

            while (resultPath.startsWith("/")) resultPath = resultPath.substring(1);
            while (resultPath.startsWith("./")) resultPath = resultPath.substring(2);

            return baseUrlStripped + "/" + resultPath;
        } catch (Exception _) {
            return baseUrlStripped + "/" + imgSrc;
        }
    }

    public static String extractHighestResCandidate(String srcset) {
        if (srcset == null || srcset.isBlank()) return "";
        String[] candidates = srcset.split(",");
        String bestUrl = "";
        double maxScore = -1.0;

        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\s+");
            String url = parts[0];
            double score = 0.0;

            if (parts.length > 1) {
                String descriptor = parts[1].toLowerCase();
                if (descriptor.endsWith("w")) {
                    try {
                        score = Double.parseDouble(descriptor.substring(0, descriptor.length() - 1));
                    } catch (NumberFormatException ignored) {}
                } else if (descriptor.endsWith("x")) {
                    try {
                        score = Double.parseDouble(descriptor.substring(0, descriptor.length() - 1)) * 1000.0;
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                if (!url.matches(".*-\\d+w\\.[a-zA-Z0-9]+$")) {
                    score = 1000.0;
                } else {
                    score = 1.0;
                }
            }

            if (score > maxScore) {
                maxScore = score;
                bestUrl = url;
            }
        }
        return bestUrl;
    }

    private static String toHighResLoc(String absImageLoc) {
        if (absImageLoc == null || absImageLoc.isEmpty()) return "";
        String highRes = absImageLoc.replaceAll("-\\d+w(\\.[a-zA-Z0-9]+)$", "$1");

        if (highRes.startsWith(BASE_URL + "/")) {
            String relPath = highRes.substring((BASE_URL + "/").length());
            if (Files.exists(Paths.get(OUTPUT_DIR, relPath))) {
                return highRes;
            }
            if (relPath.endsWith(".avif")) {
                String jpgRel = relPath.substring(0, relPath.length() - 5) + ".jpg";
                if (Files.exists(Paths.get(OUTPUT_DIR, jpgRel))) {
                    return BASE_URL + "/" + jpgRel;
                }
                String pngRel = relPath.substring(0, relPath.length() - 5) + ".png";
                if (Files.exists(Paths.get(OUTPUT_DIR, pngRel))) {
                    return BASE_URL + "/" + pngRel;
                }
            }
        }
        return highRes;
    }

    private static String getSpecValue(Document doc, String specName) {
        if (doc == null) return "";
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