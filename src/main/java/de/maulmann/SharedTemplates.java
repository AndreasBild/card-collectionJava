package de.maulmann;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

public class SharedTemplates {

    private static String loadResource(String path) {
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

        try (InputStream effectivelyFinalIs = is;
             BufferedReader reader = new BufferedReader(new InputStreamReader(effectivelyFinalIs, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            System.err.println("Error loading resource " + path + ": " + e.getMessage());
            return "";
        }
    }

    public static String getAnalytics() {
        return loadResource("/templates/analytics.html");
    }

    public static String getHead(String title, String description, String root) {
        String template = loadResource("/templates/head.html");
        if (template.isEmpty()) {
            return "<title>" + title + "</title><meta name=\"description\" content=\"" + description + "\">";
        }
        return template.replace("{{TITLE}}", title)
                       .replace("{{DESCRIPTION}}", description)
                       .replace("{{ROOT}}", root)
                       .replace("{{ANALYTICS}}", getAnalytics());
    }

    public static String getTopNav(String root, String activePage) {
        String template = loadResource("/templates/topnav.html");
        if (template.isEmpty()) {
            return "<nav><a href=\"" + root + "index.html\">Home</a></nav>";
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

    public static String getFooter() {
        return loadResource("/templates/footer.html");
    }

    public static String getTimestamp() {
        return "<p>List Created: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()) + "</p>";
    }
}
