package de.maulmann;

/**
 * Centralized utility methods for HTML/JSON escaping and common string operations.
 * Eliminates duplication across FileGenerator, SharedTemplates, and CardSchemaGenerator.
 */
public final class CardUtils {

    private CardUtils() {}

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
}
