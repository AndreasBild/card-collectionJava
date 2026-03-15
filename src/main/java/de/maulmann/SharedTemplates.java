package de.maulmann;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SharedTemplates {

    // 1. Thread-safe in-memory cache for all templates
    private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    // 2. Pre-compiled, highly efficient, thread-safe date formatter
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private static String loadResource(String path) {
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
            System.err.println("Could not find resource: " + path);
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            System.err.println("Error loading resource " + path + ": " + e.getMessage());
            return "";
        }
    }

    public static String getAnalytics() {
        return loadResource("/templates/analytics.html");
    }

    public static String getFavicon(String root) {
        String template = loadResource("/templates/favicon.html");
        return template.replace("{{ROOT}}", root);
    }

    public static String getOpenGraph(String page, String title, String description, String imageURL) {
        String template = loadResource("/templates/opengraph.html");

        return template.replace("{{PAGE}}", page)
                .replace("{{TITLE}}", title)
                .replace("{{IMAGE}}", imageURL)
                .replace("{{DESCRIPTION}}", description);
    }

    public static String getSeo(String page, String description) {
        String template = loadResource("/templates/seo.html");
        return template.replace("{{PAGE}}", page)
                .replace("{{DESCRIPTION}}", description);
    }

    public static String getHead(String title, String description, String root, String page, String image) {
        String template = loadResource("/templates/head.html");
        if (template.isEmpty()) {
            return "<title>" + title + "</title><meta name=\"description\" content=\"" + description + "\">";
        }
        return template.replace("{{TITLE}}", title)
                .replace("{{DESCRIPTION}}", description)
                .replace("{{ROOT}}", root)
                .replace("{{ANALYTICS}}", getAnalytics())
                .replace("{{SEO}}", getSeo(page, description))
                .replace("{{OPENGRAPH}}", getOpenGraph(page, title, description, image))
                .replace("{{FAVICON}}", getFavicon(root));
    }

    public static String getTopNav(String root, String activePage) {
        String template = loadResource("/templates/topnav.html");
        if (template.isEmpty()) {
            return "<nav><a href=\"" + root + "index.html\" title=\"Home\">Home</a></nav>";
        }
        return template.replace("{{ROOT}}", root)
                .replace("{{ACTIVE_INDEX}}", activePage.equals("index") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_COLLECTION}}", activePage.equals("collection") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_BASEBALL}}", activePage.equals("baseball") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_FLAWLESS}}", activePage.equals("flawless") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_WANTLIST}}", activePage.equals("wantlist") ? "class=\"active\"" : "")
                .replace("{{ACTIVE_PANINI}}", activePage.equals("panini") ? "class=\"active\"" : "");
    }

    public static String getFooterNav(String root) {
        String template = loadResource("/templates/footer_nav.html");
        if (template.isEmpty()) return "";
        return template.replace("{{ROOT}}", root);
    }

    public static String getFooter(String root) {
        String template = loadResource("/templates/footer.html");
        return template.replace("{{ROOT}}", root).replace("{{TIME}}", getTimestamp());
    }

    public static String getErrorPage(String root) {
        String template = loadResource("/templates/error.html");
        if (template.isEmpty()) {
            return "<html><body><h1>404 - Page Not Found</h1><p>Sorry, the page you are looking for does not exist.</p><a href=\"" + root + "index.html\">Back to Home</a></body></html>";
        }

        String title = "Error Page | Maulmann Trading Cards";
        String description = "The page you are looking for does not exist in the Maulmann Trading Cards collection.";
        String page = "error.html";
        String image = root + "images/logo.png";

        return template.replace("{{HEAD}}", getHead(title, description, root, page, image))
                .replace("{{TOPNAV}}", getTopNav(root, "error"))
                .replace("{{FOOTER}}", getFooter(root))
                .replace("{{ROOT}}", root);
    }

    public static String getTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }
}