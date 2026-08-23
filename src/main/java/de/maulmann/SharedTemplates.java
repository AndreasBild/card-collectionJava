package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SharedTemplates {

    private static final Logger log = LoggerFactory.getLogger(SharedTemplates.class);

    // 1. Thread-safe in-memory cache for all templates
    private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    // 2. Pre-compiled, highly efficient, thread-safe date formatter
    private static final SimpleLazyConstant<DateTimeFormatter> TIMESTAMP_FORMATTER =
            SimpleLazyConstant.of(() -> DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

    // 3. Generate a stable cache buster ID derived from css/main.css content hash
    static String BUILD_ID = calculateBuildId();

    public static void setBuildId(String id) {
        BUILD_ID = id;
    }

    private static String calculateBuildId() {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            String[] resourcePaths = {
                    "/css/main.css",
                    "/pwa/collector-features.js",
                    "/pwa/serviceWorker.js",
                    "/templates/head.html",
                    "/templates/card-detail.ftlh",
                    "/templates/collection-overview.ftlh"
            };
            for (String path : resourcePaths) {
                String content = loadResource(path);
                if (content != null && !content.isEmpty()) {
                    md.update(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 8);
        } catch (Exception e) {
            return "1.0";
        }
    }
    static String loadResource(String path) {
        // If the template is already in RAM, return it instantly (0 Disk I/O)
        return TEMPLATE_CACHE.computeIfAbsent(path, SharedTemplates::readResourceFromDisk);
    }

    private static String readResourceFromDisk(String path) {
        String resourcePath = path.startsWith("/") ? path : "/" + path;
        InputStream is = SharedTemplates.class.getResourceAsStream(resourcePath);

        if (is == null) {
            String noSlashPath = resourcePath.substring(1);
            is = SharedTemplates.class.getClassLoader().getResourceAsStream(noSlashPath);
        }

        if (is == null) {
            log.warn("Could not find resource: {}", path);
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.error("Error loading resource {}: {}", path, e.getMessage());
            return "";
        }
    }

    public static String getAnalytics() {
        return loadResource("/templates/analytics.html");
    }

    public static String getConsentBanner(String root) {
        String template = loadResource("/templates/consent_banner.html");
        return template.replace("{{ROOT}}", root);
    }

    public static String getFavicon(String root) {
        String template = loadResource("/templates/favicon.html");
        return template.replace("{{ROOT}}", root);
    }

    public static String getOpenGraph(String page, String title, String description, String imageURL) {
        return getOpenGraph(page, title, description, imageURL, 1200, 1680, title);
    }

    public static String getOpenGraph(String page, String title, String description, String imageURL, int width, int height, String imageAlt) {
        String template = loadResource("/templates/opengraph.html");

        String pageUrl;
        if (page.startsWith("http://") || page.startsWith("https://")) {
            pageUrl = page;
        } else {
            String cleanPage = page.startsWith("/") ? page.substring(1) : page;
            pageUrl = CardUtils.BASE_URL + "/" + cleanPage;
        }

        String absImageUrl;
        if (imageURL == null || imageURL.isBlank()) {
            absImageUrl = CardUtils.BASE_URL + "/" + FileGenerator.DEFAULT_IMAGE;
        } else if (imageURL.startsWith("http://") || imageURL.startsWith("https://")) {
            absImageUrl = imageURL;
        } else {
            String cleanImg = imageURL.replace("../", "").replace("./", "");
            cleanImg = cleanImg.startsWith("/") ? cleanImg.substring(1) : cleanImg;
            absImageUrl = CardUtils.BASE_URL + "/" + cleanImg;
        }

        String mimeType = "image/avif";
        String lowerImg = absImageUrl.toLowerCase();
        if (lowerImg.endsWith(".jpg") || lowerImg.endsWith(".jpeg")) {
            mimeType = "image/jpeg";
        } else if (lowerImg.endsWith(".png")) {
            mimeType = "image/png";
        } else if (lowerImg.endsWith(".webp")) {
            mimeType = "image/webp";
        } else if (lowerImg.endsWith(".avif")) {
            mimeType = "image/avif";
        }

        String secureImageUrl = absImageUrl.startsWith("http://") ? absImageUrl.replace("http://", "https://") : absImageUrl;

        return template.replace("{{PAGE_URL}}", pageUrl)
                .replace("{{PAGE}}", page)
                .replace("{{TITLE}}", escapeHtml(title))
                .replace("{{DESCRIPTION}}", escapeHtml(description))
                .replace("{{IMAGE}}", absImageUrl)
                .replace("{{SECURE_IMAGE}}", secureImageUrl)
                .replace("{{IMAGE_WIDTH}}", String.valueOf(width))
                .replace("{{IMAGE_HEIGHT}}", String.valueOf(height))
                .replace("{{IMAGE_TYPE}}", mimeType)
                .replace("{{IMAGE_ALT}}", escapeHtml(imageAlt != null ? imageAlt : title));
    }

    public static String getSeo(String page, String description) {
        String template = loadResource("/templates/seo.html");
        return template.replace("{{PAGE}}", page)
                .replace("{{DESCRIPTION}}", escapeHtml(description));
    }

    public static String getHead(String title, String description, String root, String page, String image) {
        return getHead(title, description, root, page, image, "");
    }

    public static String getHead(String title, String description, String root, String page, String image, String extraHead) {
        return getHead(title, description, root, page, image, extraHead, 1200, 1680, title);
    }

    public static String getHead(String title, String description, String root, String page, String image, String extraHead, int imageWidth, int imageHeight, String imageAlt) {
        String template = loadResource("/templates/head.html");
        if (template.isEmpty()) {
            return "<title>" + escapeHtml(title) + "</title><meta name=\"description\" content=\"" + escapeHtml(description) + "\">";
        }
        return template.replace("{{TITLE}}", escapeHtml(title))
                .replace("{{DESCRIPTION}}", escapeHtml(description))
                .replace("{{ROOT}}", root)
                .replace("{{ANALYTICS}}", getAnalytics())
                .replace("{{SEO}}", getSeo(page, description))
                .replace("{{OPENGRAPH}}", getOpenGraph(page, title, description, image, imageWidth, imageHeight, imageAlt))
                .replace("{{EXTRA_HEAD}}", extraHead != null ? extraHead : "")
                .replace("{{FAVICON}}", getFavicon(root))
                .replace("{{BUILD_ID}}", BUILD_ID);
    }
    public static String getBreadcrumb(List<Map<String, String>> items) {
        String template = loadResource("/templates/breadcrumb.html");
        if (template.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String name = escapeHtml(item.get("name"));
            String link = item.get("link");
            boolean isLast = (i == items.size() - 1);

            sb.append("            <li class=\"breadcrumb-item\"");
            if (isLast) {
                sb.append(" aria-current=\"page\">").append(name).append("</li>\n");
            } else {
                sb.append("><a href=\"").append(link).append("\" class=\"plain\" title=\"").append(name).append("\">").append(name).append("</a></li>\n");
            }
        }
        return template.replace("{{ITEMS}}", sb.toString().trim());
    }

    public static String getBreadcrumbJsonLd(List<Map<String, String>> items) {
        return getBreadcrumbJsonLd(items, null);
    }

    public static String getBreadcrumbJsonLd(List<Map<String, String>> items, String id) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"@type\": \"BreadcrumbList\",\n");
        if (id != null && !id.isEmpty()) {
            sb.append("      \"@id\": \"").append(escapeJson(id)).append("\",\n");
        }
        sb.append("      \"name\": \"Breadcrumbs\",\n");
        sb.append("      \"itemListElement\": [\n");

        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String rawName = item.get("name");
            String name = escapeJson(rawName);
            String link = item.get("link");

            sb.append("        { \"@type\": \"ListItem\", \"position\": ").append(i + 1).append(", \"name\": \"").append(name).append("\"");
            if (link != null && !link.isEmpty()) {
                sb.append(", \"item\": \"").append(escapeJson(link)).append("\"");
            }
            sb.append(" }");
            if (i < items.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("      ]\n");
        sb.append("    }");
        return sb.toString();
    }

    public static String escapeJson(String text) {
        return CardUtils.escapeJson(text);
    }

    public static String getTopNav(String root, String activePage) {
        String template = loadResource("/templates/topnav.html");
        if (template.isEmpty()) {
            return "<nav><a href=\"" + root + "index.html\" title=\"Home\">Home</a></nav>";
        }
        boolean isJhActive = activePage.equals("collection") || activePage.equals("juwan-howard-collection")
                || activePage.equals("binder") || activePage.equals("rainbows") || activePage.equals("wantlist");
        boolean isBballActive = activePage.equals("panini") || activePage.equals("flawless");

        return template.replace("{{ROOT}}", root)
                .replace("{{ACTIVE_INDEX}}", activePage.equals("index") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_JH_GROUP}}", isJhActive ? "is-active-group" : "")
                .replace("{{ACTIVE_JH_BTN}}", isJhActive ? "active" : "")
                .replace("{{ACTIVE_COLLECTION}}", (activePage.equals("collection") || activePage.equals("juwan-howard-collection")) ? "class=\"active\"" : "")
                .replace("{{ACTIVE_BINDER}}", activePage.equals("binder") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_RAINBOWS}}", activePage.equals("rainbows") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_WANTLIST}}", activePage.equals("wantlist") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_BBALL_GROUP}}", isBballActive ? "is-active-group" : "")
                .replace("{{ACTIVE_BBALL_BTN}}", isBballActive ? "active" : "")
                .replace("{{ACTIVE_PANINI}}", activePage.equals("panini") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_FLAWLESS}}", activePage.equals("flawless") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_BASEBALL}}", activePage.equals("baseball") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_SITEMAP}}", activePage.equals("sitemap") ? "class=\"active\"" : "");
    }

    public static String getFooter(String root) {
        String template = loadResource("/templates/footer.html");
        // Using a placeholder for stable timestamps that can be replaced after generation
        return template.replace("{{ROOT}}", root).replace("{{TIME}}", "[[STABLE_TIME]]");
    }

    public static String getTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER.get());
    }

    public static Map<String, Object> createBaseData(String title, String subTitle, String filename, String navTargetUrl, String root) {
        Map<String, Object> data = new java.util.HashMap<>();

        String absImageUrl = CardUtils.BASE_URL + "/" + FileGenerator.DEFAULT_IMAGE;
        String headHtml = getHead(title, subTitle, root, filename, absImageUrl, "", 1200, 1680, title);
        String topnav = getTopNav(root, navTargetUrl.replace(".html", "").toLowerCase());
        String footerHtml = getFooter(root);

        data.put("headHtml", headHtml);
        data.put("topNavHtml", topnav);
        data.put("footerHtml", footerHtml);
        data.put("pageTitle", title);
        data.put("subTitle", subTitle);
        data.put("root", root);

        return data;
    }

    private static String escapeHtml(String text) {
        return CardUtils.escapeHtml(text);
    }
}
