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

    private static final java.util.regex.Pattern COMMA_PATTERN = java.util.regex.Pattern.compile("\\s*,\\s*");

    /**
     * Replaces comma-separated values with " / " delimiters for display.
     * Used by CardPageGenerator and CardSchemaGenerator.
     */
    public static String formatMulti(String val) {
        if (val == null) return "";
        return COMMA_PATTERN.matcher(val).replaceAll(" / ");
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

    private static final java.util.regex.Pattern CARD_NUMBER_PATTERN = java.util.regex.Pattern.compile("#([A-Za-z0-9\\-]+)");
    private static final java.util.regex.Pattern YEAR_PATTERN = java.util.regex.Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b");

    /**
     * Extracts card number from title or text with '#' prefix (e.g. "#33" -> "33").
     */
    public static String extractCardNumber(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = CARD_NUMBER_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extracts 4-digit year from title or text (e.g. "1997-98 Fleer" -> "1997").
     */
    public static String extractYear(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = YEAR_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
