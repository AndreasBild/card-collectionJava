package de.maulmann;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;

/**
 * Centralized utility methods for HTML/JSON escaping and common string operations.
 * Eliminates duplication across FileGenerator, SharedTemplates, CardPageGenerator, and CardSchemaGenerator.
 */
public final class CardUtils {

    /** Shared base URL — used by CardPageGenerator, CardSchemaGenerator, FileGenerator, SitemapGenerator. */
    public static final String BASE_URL = "https://www.maulmann.de";

    private static final SimpleLazyConstant<Configuration> FM_CONFIG = SimpleLazyConstant.of(() -> {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setClassForTemplateLoading(CardUtils.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    });

    private CardUtils() {}

    /** Returns the shared FreeMarker Configuration singleton. */
    public static Configuration getFreeMarkerConfig() {
        return FM_CONFIG.get();
    }

    /**
     * Escapes a string for safe inclusion in HTML content and attributes.
     * Handles already-escaped input by first unescaping, then re-escaping cleanly.
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        // First unescape any already-escaped entities to avoid double-encoding
        String unescaped = text.replace("&quot;", "\"")
                               .replace("&amp;", "&")
                               .replace("&#39;", "'")
                               .replace("&lt;", "<")
                               .replace("&gt;", ">");
        // Then escape cleanly
        return unescaped.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;");
    }

    /**
     * Escapes a string for safe inclusion in JSON string values.
     */
    public static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Replaces comma-separated values with " / " delimiters for display.
     * Used by CardPageGenerator and CardSchemaGenerator.
     */
    public static String formatMulti(String val) {
        if (val == null) return "";
        return val.replaceAll("\\s*,\\s*", " / ");
    }

    /**
     * Validates a string for HTML display purposes.
     * Rejects null, blank, "0", "-", and "—" (em-dash).
     */
    public static boolean isValidForDisplay(String value) {
        return value != null && !value.trim().isEmpty()
                && !value.equals("0") && !value.equals("-") && !value.equals("—");
    }

    /**
     * Validates a string for JSON-LD schema purposes.
     * Rejects null, blank, the literal string "null" (case-insensitive), and "-".
     */
    public static boolean isValidForSchema(String str) {
        return str != null && !str.trim().isEmpty()
                && !str.equalsIgnoreCase("null") && !str.equals("-");
    }
}
