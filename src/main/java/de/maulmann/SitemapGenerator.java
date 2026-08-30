package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Refactored XML Sitemap Generator.
 * Implements a high-performance hierarchical Sitemap Index (sitemap.xml)
 * with dynamic <lastmod> timestamps per URL and specialized sub-sitemaps.
 */
public class SitemapGenerator {

    private static final Logger log = LoggerFactory.getLogger(SitemapGenerator.class);
    private static final Configuration fmConfig = CardUtils.getFreeMarkerConfig();
    private static final String BASE_URL = CardUtils.BASE_URL;
    private static final String OUTPUT_DIR = "output";

    private static final java.util.regex.Pattern PATTERN_NON_DIGITS = java.util.regex.Pattern.compile("[^0-9]");
    private static final java.util.regex.Pattern PATTERN_NON_SLUG_CHARS = java.util.regex.Pattern.compile("[^a-z0-9\\-]");
    private static final java.util.regex.Pattern PATTERN_MULTI_HYPHENS = java.util.regex.Pattern.compile("-+");
    private static final java.util.regex.Pattern PATTERN_SLUG_EDGES = java.util.regex.Pattern.compile("^-|-$");
    private static final java.util.regex.Pattern PATTERN_HIGH_RES = java.util.regex.Pattern.compile("-\\d+w(\\.[a-zA-Z0-9]+)$");

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
        generate(null);
    }

    public static void generate(List<CardData> inMemoryCards) {
        AtomicInteger imagesAdded = new AtomicInteger(0);
        AtomicInteger imagesMissing = new AtomicInteger(0);

        List<Map<String, String>> coreLinks = new ArrayList<>();
        Map<String, List<Map<String, String>>> seasonGroups = new TreeMap<>();

        try {
            log.info("Generating best-in-class robots.txt...");
            generateRobotsTxt();

            log.info("Cleaning up old sitemap files...");
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

            log.info("Generating sitemaps...");

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String todayIso = isoFormat.format(new Date());

            List<SitemapUrlEntry> mainEntries = new ArrayList<>();
            List<SitemapUrlEntry> highlightEntries = new ArrayList<>();
            Map<String, List<SitemapUrlEntry>> cardGroupEntries = new TreeMap<>();

            // 1. Process Core HTML Files (always from disk, ~10 files)
            List<Path> corePaths = new ArrayList<>();
            if (Files.exists(outputDirPath)) {
                try (Stream<Path> paths = Files.list(outputDirPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".html"))
                            .sorted()
                            .forEach(corePaths::add);
                }
            }

            for (Path path : corePaths) {
                String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
                String loc = BASE_URL + "/" + relativePath;
                if (loc.endsWith("/index.html")) {
                    loc = loc.replace("/index.html", "/");
                }

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
                    coreLinks.add(linkMap);

                    // Extract core page images
                    Set<String> processedImageUrls = new HashSet<>();
                    for (Element img : doc.select("img")) {
                        String src = img.attr("src");
                        if (src.isEmpty() || src.startsWith("data:")) continue;
                        String absLoc = resolveImageLoc(relativePath, src);
                        String highRes = toHighResLoc(absLoc);
                        if (!highRes.isEmpty() && processedImageUrls.add(highRes)) {
                            String imgTitle = img.attr("title").trim();
                            images.add(new ImageEntry(highRes, imgTitle.isEmpty() ? pageTitle : imgTitle, pageTitle));
                            imagesAdded.incrementAndGet();
                        }
                    }
                } catch (IOException e) {
                    log.error("Could not parse core page {}: {}", path, e.getMessage());
                }

                mainEntries.add(new SitemapUrlEntry(relativePath, loc, lastModDate, "daily", "1.0", images));
            }

            // 2. Process Cards (In-Memory if available, fallback to Disk walk)
            if (inMemoryCards != null && !inMemoryCards.isEmpty()) {
                log.info("Processing {} card pages in memory for sitemaps...", inMemoryCards.size());
                for (CardData c : inMemoryCards) {
                    String relativePath = c.fullRelativePath;
                    String loc = BASE_URL + "/" + relativePath;

                    String lastModDate;
                    if (timestampTracker != null) {
                        lastModDate = timestampTracker.getIsoDate(relativePath);
                    } else {
                        lastModDate = todayIso;
                    }

                    String h1Title = CardPageGenerator.generateH1(c);
                    String rawImageBase = c.filenameBase.contains("-") ? c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-")) : c.filenameBase;
                    String resolvedImageBase = CardPageGenerator.resolveDiskImageBase(c.seasonFolder, rawImageBase, c);

                    String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + resolvedImageBase + "-front.avif";
                    String backImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + resolvedImageBase + "-back.avif";

                    List<ImageEntry> images = new ArrayList<>();
                    images.add(new ImageEntry(frontImgUrl, h1Title + " (Front Scan)", h1Title));
                    images.add(new ImageEntry(backImgUrl, h1Title + " (Back Scan)", h1Title));
                    imagesAdded.addAndGet(2);

                    // HTML Sitemap link
                    Map<String, String> linkMap = new HashMap<>();
                    linkMap.put("url", relativePath);
                    String player = c.get("Player");
                    String theme = c.get("Theme");
                    String variant = c.get("Variant");

                    linkMap.put("player", player);
                    linkMap.put("company", c.get("Company"));
                    linkMap.put("brand", c.get("Brand"));
                    linkMap.put("theme", theme);
                    linkMap.put("variant", variant);
                    linkMap.put("number", c.get("Number"));

                    linkMap.put("text", h1Title);

                    seasonGroups.computeIfAbsent(c.seasonFolder, k -> new ArrayList<>()).add(linkMap);

                    boolean isHighlight = isHighlightCard(c);
                    if (isHighlight) {
                        highlightEntries.add(new SitemapUrlEntry(relativePath, loc, lastModDate, "weekly", "0.9", images));
                    } else {
                        String groupName = c.seasonFolder;
                        cardGroupEntries.computeIfAbsent(groupName, k -> new ArrayList<>())
                                .add(new SitemapUrlEntry(relativePath, loc, lastModDate, "yearly", "0.5", images));
                    }
                }
            } else {
                // Fallback: Scan disk output/cards directory
                List<Path> allPaths = new ArrayList<>();
                if (Files.exists(outputDirPath)) {
                    try (Stream<Path> paths = Files.walk(outputDirPath)) {
                        paths.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".html"))
                                .forEach(allPaths::add);
                    }
                }

                for (Path path : allPaths) {
                    String relativePath = outputDirPath.relativize(path).toString().replace("\\", "/");
                    if (!relativePath.startsWith("cards/")) continue;
                    String loc = BASE_URL + "/" + relativePath;

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

                        // Extract images
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
                        log.error("Could not parse {}: {}", path, e.getMessage());
                    }

                    boolean isHighlight = isHighlightCard(doc, relativePath);
                    if (isHighlight) {
                        highlightEntries.add(new SitemapUrlEntry(relativePath, loc, lastModDate, "weekly", "0.9", images));
                    } else {
                        String groupName = extractGroupName(relativePath);
                        cardGroupEntries.computeIfAbsent(groupName, k -> new ArrayList<>())
                                .add(new SitemapUrlEntry(relativePath, loc, lastModDate, "yearly", "0.5", images));
                    }
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

            log.info("Sitemap Index & Sub-Sitemaps successfully generated!");
            log.info("   > Total Sub-Sitemaps: {}", childSitemaps.size());
            log.info("   > Images added: {}", imagesAdded.get());

            generateHtmlSitemap(coreLinks, seasonGroups, timestampTracker);
            generateLlmsTxt(inMemoryCards);
            generateLlmsFullTxt(inMemoryCards);
            generateRssFeed(inMemoryCards, timestampTracker);

        } catch (Exception e) {
            log.error("Failed to generate Sitemap: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean isHighlightCard(CardData c) {
        if (c == null) return false;
        String pathLower = c.fullRelativePath.toLowerCase();
        if (pathLower.contains("flawless")) return true;

        String printRunStr = c.get("Print Run");
        if (printRunStr.isEmpty()) printRunStr = c.get("Serial/Print Run");
        if (printRunStr.isEmpty()) printRunStr = c.get("Serial");

        String variant = c.get("Variant").toLowerCase();
        String auto = c.get("Autograph").toLowerCase();

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
                int run = Integer.parseInt(PATTERN_NON_DIGITS.matcher(cleanRun).replaceAll(""));
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
                int run = Integer.parseInt(PATTERN_NON_DIGITS.matcher(cleanRun).replaceAll(""));
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
        String step1 = PATTERN_NON_SLUG_CHARS.matcher(raw.toLowerCase()).replaceAll("-");
        String step2 = PATTERN_MULTI_HYPHENS.matcher(step1).replaceAll("-");
        return PATTERN_SLUG_EDGES.matcher(step2).replaceAll("");
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

    public static void generateLlmsTxt() {
        generateLlmsTxt(null);
    }

    public static void generateLlmsTxt(List<CardData> inMemoryCards) {
        log.info("Generating llms.txt standard index for AI/LLMs...");
        StringBuilder sb = new StringBuilder();
        sb.append("# maulmann.de - Private Sports Card Collection\n\n");
        sb.append("> Authority database and high-resolution visual archive of the Andreas Maulmann Private Collection, specializing in Juwan Howard (1994–present), 90s basketball insert grails, 1/1 Masterpieces, and certified memorabilia.\n\n");

        if (inMemoryCards != null && !inMemoryCards.isEmpty()) {
            int total = inMemoryCards.size();
            long count1of1 = 0;
            long countUltraSp = 0;
            long countSerialized = 0;
            long countAutos = 0;
            long countMem = 0;
            long countRookies = 0;
            long countGemMint = 0;
            Set<String> seasons = new HashSet<>();

            for (CardData c : inMemoryCards) {
                String season = c.get("Season");
                if (CardUtils.isValidForDisplay(season)) seasons.add(season);

                String printRunStr = c.get("Print Run");
                String serialStr = c.get("Serial");
                String variant = c.get("Variant").toLowerCase();
                String theme = c.get("Theme").toLowerCase();

                int pr = 0;
                if (CardUtils.isValidForDisplay(printRunStr)) {
                    try { pr = Integer.parseInt(printRunStr); } catch (Exception ignored) {}
                }

                boolean is1of1 = pr == 1 || "1/1".equalsIgnoreCase(serialStr) || variant.contains("1/1") || variant.contains("masterpiece");
                boolean isPlateOrProof = variant.contains("plate") || variant.contains("proof") || theme.contains("plate") || theme.contains("proof");
                if (is1of1 && !isPlateOrProof) {
                    count1of1++;
                }

                if (pr > 0 && pr <= 10) {
                    countUltraSp++;
                }
                if ((pr > 0 && pr <= 100) || (CardUtils.isValidForDisplay(serialStr) && !serialStr.equals("0"))) {
                    countSerialized++;
                }

                if ("Yes".equalsIgnoreCase(c.get("Autograph")) || variant.contains("auto")) {
                    countAutos++;
                }
                if ("Yes".equalsIgnoreCase(c.get("Memorabilia")) || variant.contains("jersey") || variant.contains("patch")) {
                    countMem++;
                }
                if ("Yes".equalsIgnoreCase(c.get("Rookie"))) {
                    countRookies++;
                }

                String grade = c.get("Grade");
                String gradingCo = c.get("Grading Co.");
                if (("PSA".equalsIgnoreCase(gradingCo) && "10".equals(grade)) ||
                    ("BGS".equalsIgnoreCase(gradingCo) && ("9.5".equals(grade) || "10".equals(grade))) ||
                    ("SGC".equalsIgnoreCase(gradingCo) && "10".equals(grade))) {
                    countGemMint++;
                }
            }

            sb.append("## Collection Statistics & Highlights\n");
            sb.append("- Total Unique Cards: ").append(String.format("%,d", total)).append(" indexed cards\n");
            sb.append("- Spanning Seasons: ").append(seasons.size()).append(" seasons (1994-95 to present)\n");
            sb.append("- 1/1 Masterpieces & Grails: ").append(count1of1).append(" true 1/1 cards\n");
            sb.append("- Ultra Short Prints (≤ 10): ").append(countUltraSp).append(" cards\n");
            sb.append("- Low-Numbered Serialized (≤ 100): ").append(countSerialized).append(" cards\n");
            sb.append("- Certified Autographs: ").append(countAutos).append(" cards\n");
            sb.append("- Game-Used Patches & Memorabilia: ").append(countMem).append(" cards\n");
            sb.append("- Official Rookie Cards (RC): ").append(countRookies).append(" cards\n");
            sb.append("- Gem Mint Graded (PSA 10 / BGS 9.5): ").append(countGemMint).append(" cards\n\n");
        } else {
            sb.append("## Collection Statistics\n");
            sb.append("- Total Unique Cards: 1,440+ cards\n");
            sb.append("- Subject: Juwan Howard (Michigan Fab Five, Washington Bullets/Wizards, Miami Heat)\n\n");
        }

        sb.append("## Core Vault Pages & Interactive Tools\n");
        sb.append("- [Juwan Howard Master Collection](").append(BASE_URL).append("/Juwan-Howard-Collection.html): Complete searchable vault of Juwan Howard trading cards with filters for PMGs, Rubies, Autographs, and Rookies.\n");
        sb.append("- [3D 9-Pocket Collector Binder](").append(BASE_URL).append("/binder.html): Interactive virtual 9-pocket trading card binder with 3D page flips and card inspection.\n");
        sb.append("- [Parallel Rainbow Tracker](").append(BASE_URL).append("/rainbows.html): Visual tracking system for completing parallel rainbows (Base, Refractor, Atomic, PMG, 1/1 Masterpiece).\n");
        sb.append("- [Flawless Collection](").append(BASE_URL).append("/Flawless.html): Ultra-high-end Panini Flawless diamond gems, ruby parallels, and game-worn patch cards.\n");
        sb.append("- [Panini Rarities](").append(BASE_URL).append("/Panini.html): Showcase of modern Panini National Treasures, Immaculate, Prizm, and Select cards.\n");
        sb.append("- [Baseball Grails](").append(BASE_URL).append("/Baseball.html): Certified MLB on-card autographs and game-used relics.\n");
        sb.append("- [Wantlist](").append(BASE_URL).append("/Wantlist.html): Actively sought-after holy grails and missing rainbow pieces.\n\n");

        sb.append("## Machine-Readable Endpoints & AI Feeds\n");
        sb.append("- [llms-full.txt](").append(BASE_URL).append("/llms-full.txt): Complete detailed text database of all cards, image URLs, and entity metadata for LLM ingestion.\n");
        sb.append("- [sitemap.xml](").append(BASE_URL).append("/sitemap.xml): Comprehensive XML sitemap index referencing all 35+ sub-sitemaps and 5,900+ high-res card scans.\n");
        sb.append("- [rss.xml](").append(BASE_URL).append("/rss.xml): RSS 2.0 discovery feed highlighting recent card additions and holy grail acquisitions.\n");
        sb.append("- [latest.json](").append(BASE_URL).append("/latest.json): Machine-readable JSON summary for offline PWA sync.\n");

        Path llmsPath = Paths.get(OUTPUT_DIR, "llms.txt");
        try {
            Files.writeString(llmsPath, sb.toString(), StandardCharsets.UTF_8);
            log.info("llms.txt successfully generated!");
        } catch (IOException e) {
            log.error("Failed to write llms.txt: {}", e.getMessage());
        }
    }

    private static void generateLlmsFullTxt(List<CardData> inMemoryCards) {
        log.info("Generating llms-full.txt for AI/LLM RAG Indexing...");
        StringBuilder sb = new StringBuilder();
        sb.append("# maulmann.de - Full Private Collection Knowledge Base for LLMs\n\n");
        sb.append("> Comprehensive dataset index and authority reference for the Andreas Maulmann Private Trading Card Collection.\n");
        sb.append("> Featuring extensive collections of Juwan Howard (1994–present), Flawless High-End Sets, Panini Rarities, and Baseball Grails.\n");
        sb.append("> Web: ").append(BASE_URL).append("/\n\n");

        sb.append("## Collector & Subject Profile: Juwan Howard\n");
        sb.append("- Background: Key member of the iconic Michigan 'Fab Five' (1991–1994), #5 Overall Pick in the 1994 NBA Draft by Washington Bullets.\n");
        sb.append("- Accolades: NBA All-Star (1996), All-NBA Third Team (1996), NBA All-Rookie First Team (1995), 2x NBA Champion (Miami Heat 2012, 2013).\n");
        sb.append("- Collection Scope: 1,440+ unique Juwan Howard trading cards spanning 1994 to present, including 1/1 Masterpieces, Superfractors, Precious Metal Gems (PMGs), Rubies, Platinum Medallions, and multi-piece game-worn patches.\n\n");

        sb.append("## Hobby Taxonomy & Parallel Tiers\n");
        sb.append("- Tier 1 (Ultra-Rare Grails): 1-of-1 Masterpieces, Superfractors, PMG Precious Metal Gems, Emeralds, Rubies (#/50 or less).\n");
        sb.append("- Tier 2 (High-End Numbered): Refractors, Atomic Refractors, Die-Cut Parallels, Credentials, Legacy Collection (#/100 or less).\n");
        sb.append("- Tier 3 (Certified Memorabilia & Autographs): On-Card Signatures, Game-Used Jersey Patches, Prime Tag Relics.\n");
        sb.append("- Tier 4 (Graded Condition Standards): PSA 10 Gem Mint, BGS 9.5 True Gem, SGC 10 Pristine specimens.\n\n");

        sb.append("## Core Overview & Feature Pages\n");
        sb.append("- Home & Hub: ").append(BASE_URL).append("/\n");
        sb.append("- Juwan Howard Master Collection: ").append(BASE_URL).append("/Juwan-Howard-Collection.html\n");
        sb.append("- 3D 9-Pocket Collector's Binder: ").append(BASE_URL).append("/binder.html\n");
        sb.append("- Parallel Rainbow Tracker: ").append(BASE_URL).append("/rainbows.html\n");
        sb.append("- Flawless Collection: ").append(BASE_URL).append("/Flawless.html\n");
        sb.append("- Panini Rarities: ").append(BASE_URL).append("/Panini.html\n");
        sb.append("- Baseball Collection: ").append(BASE_URL).append("/Baseball.html\n");
        sb.append("- Most Wanted Cards (Wantlist): ").append(BASE_URL).append("/Wantlist.html\n\n");

        sb.append("## Complete Card Index & Direct URLs\n\n");

        if (inMemoryCards != null && !inMemoryCards.isEmpty()) {
            for (CardData c : inMemoryCards) {
                String loc = BASE_URL + "/" + c.fullRelativePath;
                String title = CardPageGenerator.generateH1(c);
                String desc = CardPageGenerator.generateMetaDescription(c);

                sb.append("### ").append(title).append("\n");
                sb.append("- URL: ").append(loc).append("\n");
                if (!desc.isEmpty()) {
                    sb.append("- Description: ").append(desc).append("\n");
                }

                String player = c.get("Player");
                String season = c.get("Season");
                String company = c.get("Company");
                if (company.isEmpty()) company = c.get("Manufacturer");
                String brand = c.get("Brand");
                String variant = c.get("Variant");
                String number = c.get("Card Number");
                if (number.isEmpty()) number = c.get("Number");
                String printRun = c.get("Print Run");
                if (printRun.isEmpty()) printRun = c.get("Serial/Print Run");
                if (printRun.isEmpty()) printRun = c.get("Serial");
                String grading = c.get("Grading Co.");
                if (!grading.isEmpty()) {
                    String gVal = c.get("Grade");
                    if (!gVal.isEmpty()) grading += " " + gVal;
                } else {
                    grading = c.get("Grading");
                }

                List<String> specs = new ArrayList<>();
                if (!player.isEmpty()) specs.add("Player: " + player);
                if (!season.isEmpty()) specs.add("Season: " + season);
                if (!company.isEmpty()) specs.add("Company: " + company);
                if (!brand.isEmpty()) specs.add("Brand: " + brand);
                if (!variant.isEmpty() && !variant.equalsIgnoreCase("Base")) specs.add("Variant: " + variant);
                if (!number.isEmpty()) specs.add("Card #: " + number);
                if (!printRun.isEmpty() && !printRun.equals("-")) specs.add("Serial: " + printRun);
                if (!grading.isEmpty() && !grading.equals("-")) specs.add("Grading: " + grading);

                if (c.estimatedValue != null && c.estimatedValue > 0) {
                    specs.add("Est. Market Value: $" + String.format(Locale.US, "%.2f", c.estimatedValue));
                }
                if (c.lastSoldPrice != null && c.lastSoldPrice > 0) {
                    String compStr = "Last Comp: $" + String.format(Locale.US, "%.2f", c.lastSoldPrice);
                    if (c.lastSoldDate != null && !c.lastSoldDate.isBlank()) {
                        compStr += " (" + c.lastSoldDate + ")";
                    }
                    specs.add(compStr);
                }
                if (c.popTotal != null && c.popTotal > 0) {
                    specs.add("PSA Pop: " + c.popTotal + (c.popHigher != null ? " (Higher: " + c.popHigher + ")" : ""));
                }

                if (!specs.isEmpty()) {
                    sb.append("- Attributes: ").append(String.join(" | ", specs)).append("\n");
                }
                sb.append("\n");
            }
        } else {
            Path outputDirPath = Paths.get(OUTPUT_DIR);
            List<Path> allPaths = new ArrayList<>();
            if (Files.exists(outputDirPath)) {
                try (Stream<Path> paths = Files.walk(outputDirPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".html"))
                            .forEach(allPaths::add);
                } catch (IOException ignored) {}
            }
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

                    String player = getSpecValue(doc, "Player");
                    String season = getSpecValue(doc, "Season");
                    String company = getSpecValue(doc, "Company");
                    if (company.isEmpty()) company = getSpecValue(doc, "Manufacturer");
                    String brand = getSpecValue(doc, "Brand");
                    String variant = getSpecValue(doc, "Variant");
                    String number = getSpecValue(doc, "Card Number");
                    if (number.isEmpty()) number = getSpecValue(doc, "Number");
                    String printRun = getSpecValue(doc, "Print Run");
                    if (printRun.isEmpty()) printRun = getSpecValue(doc, "Serial/Print Run");
                    if (printRun.isEmpty()) printRun = getSpecValue(doc, "Serial");
                    String grading = getSpecValue(doc, "Grading Co.");
                    if (!grading.isEmpty()) {
                        String gVal = getSpecValue(doc, "Grade");
                        if (!gVal.isEmpty()) grading += " " + gVal;
                    } else {
                        grading = getSpecValue(doc, "Grading");
                    }

                    List<String> specs = new ArrayList<>();
                    if (!player.isEmpty()) specs.add("Player: " + player);
                    if (!season.isEmpty()) specs.add("Season: " + season);
                    if (!company.isEmpty()) specs.add("Company: " + company);
                    if (!brand.isEmpty()) specs.add("Brand: " + brand);
                    if (!variant.isEmpty() && !variant.equalsIgnoreCase("Base")) specs.add("Variant: " + variant);
                    if (!number.isEmpty()) specs.add("Card #: " + number);
                    if (!printRun.isEmpty() && !printRun.equals("-")) specs.add("Serial: " + printRun);
                    if (!grading.isEmpty() && !grading.equals("-")) specs.add("Grading: " + grading);

                    if (!specs.isEmpty()) {
                        sb.append("- Attributes: ").append(String.join(" | ", specs)).append("\n");
                    }
                    sb.append("\n");
                } catch (Exception ignored) {
                    sb.append("- ").append(fileName).append(": ").append(loc).append("\n");
                }
            }
        }

        Path llmsFullPath = Paths.get(OUTPUT_DIR, "llms-full.txt");
        try {
            Files.writeString(llmsFullPath, sb.toString(), StandardCharsets.UTF_8);
            log.info("llms-full.txt successfully generated!");
        } catch (IOException e) {
            log.error("Failed to write llms-full.txt: {}", e.getMessage());
        }
    }

    private static void generateRssFeed(List<CardData> inMemoryCards, TimestampTracker timestampTracker) {
        log.info("Generating RSS feed (rss.xml)...");
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

        String channelPubDate;
        if (timestampTracker != null) {
            String stableTime = timestampTracker.getStableTimestamp("rss.xml", "RSS_FEED_STATIC_MARKER");
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                LocalDateTime ldt = LocalDateTime.parse(stableTime, fmt);
                channelPubDate = rfc822.format(java.util.Date.from(ldt.atZone(java.time.ZoneId.of("UTC")).toInstant()));
            } catch (Exception e) {
                channelPubDate = rfc822.format(new Date());
            }
        } else {
            channelPubDate = rfc822.format(new Date());
        }

        rss.append("    <pubDate>").append(channelPubDate).append("</pubDate>\n");

        int itemCap = 50;
        int addedCount = 0;

        if (inMemoryCards != null && !inMemoryCards.isEmpty()) {
            for (CardData c : inMemoryCards) {
                String relativePath = c.fullRelativePath;
                String loc = BASE_URL + "/" + relativePath;
                String title = CardPageGenerator.generateH1(c);
                String desc = CardPageGenerator.generateMetaDescription(c);

                String pubDate;
                if (timestampTracker != null) {
                    String isoDate = timestampTracker.getIsoDate(relativePath);
                    try {
                        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                        Date d = isoFormat.parse(isoDate);
                        pubDate = rfc822.format(d);
                    } catch (Exception e) {
                        pubDate = channelPubDate;
                    }
                } else {
                    pubDate = channelPubDate;
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
            }
        } else {
            Path outputDirPath = Paths.get(OUTPUT_DIR);
            List<Path> allPaths = new ArrayList<>();
            if (Files.exists(outputDirPath)) {
                try (Stream<Path> paths = Files.walk(outputDirPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".html"))
                            .forEach(allPaths::add);
                } catch (IOException ignored) {}
            }

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
                    if (timestampTracker != null) {
                        String isoDate = timestampTracker.getIsoDate(relativePath);
                        try {
                            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                            Date d = isoFormat.parse(isoDate);
                            pubDate = rfc822.format(d);
                        } catch (Exception e) {
                            pubDate = channelPubDate;
                        }
                    } else {
                        pubDate = channelPubDate;
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
        }

        rss.append("  </channel>\n");
        rss.append("</rss>");

        Path rssPath = Paths.get(OUTPUT_DIR, "rss.xml");
        try {
            Files.writeString(rssPath, rss.toString(), StandardCharsets.UTF_8);
            log.info("rss.xml successfully generated! ({} items)", addedCount);
        } catch (IOException e) {
            log.error("Failed to write rss.xml: {}", e.getMessage());
        }
    }

    private static void generateHtmlSitemap(List<Map<String, String>> coreLinks, Map<String, List<Map<String, String>>> seasonGroups, TimestampTracker timestampTracker) {
        try {
            log.info("Generating sitemap.html...");

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
            log.info("Sitemap.html successfully generated!");
        } catch (Exception e) {
            log.error("Failed to generate HTML Sitemap: {}", e.getMessage());
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

        Path robotsPath = Paths.get(OUTPUT_DIR, "robots.txt");
        Files.writeString(robotsPath, robots.toString(), StandardCharsets.UTF_8);
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
        String highRes = PATTERN_HIGH_RES.matcher(absImageLoc).replaceAll("$1");

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
