package de.maulmann;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance in-memory HTML minifier.
 * Strips comments and collapses redundant whitespace without building heavy DOM trees.
 */
public class HTMLMinifier {

    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--(?!\\[if)[\\s\\S]*?-->");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("[ \\t\\f]+");
    private static final Pattern INTER_TAG_WHITESPACE_PATTERN = Pattern.compile(">\\s+<");
    private static final Pattern SCRIPT_PRESERVE_PATTERN = Pattern.compile("(<script\\b[^>]*>[\\s\\S]*?<\\/script>|<style\\b[^>]*>[\\s\\S]*?<\\/style>|<pre\\b[^>]*>[\\s\\S]*?<\\/pre>|<textarea\\b[^>]*>[\\s\\S]*?<\\/textarea>)", Pattern.CASE_INSENSITIVE);

    /**
     * Minifies HTML string in-memory.
     */
    public static String minifyHTML(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // Fast path: if no script/pre/style tags, perform direct full regex cleanup
        Matcher preservedMatcher = SCRIPT_PRESERVE_PATTERN.matcher(html);
        if (!preservedMatcher.find()) {
            return fastMinifySimpleHtml(html);
        }

        // Process chunk-by-chunk to preserve whitespace inside <pre>, <textarea>, <script>, <style>
        StringBuilder sb = new StringBuilder(html.length());
        int lastIndex = 0;
        preservedMatcher.reset();

        while (preservedMatcher.find()) {
            String nonPreserved = html.substring(lastIndex, preservedMatcher.start());
            sb.append(fastMinifySimpleHtml(nonPreserved, true));

            String preserved = preservedMatcher.group(1);
            sb.append(preserved);
            lastIndex = preservedMatcher.end();
        }

        if (lastIndex < html.length()) {
            sb.append(fastMinifySimpleHtml(html.substring(lastIndex), false));
        }

        return sb.toString().trim();
    }

    private static final Pattern TRAILING_TAG_SPACE = Pattern.compile(">\\s+$");
    private static final Pattern LEADING_TAG_SPACE = Pattern.compile("^\\s+<");

    private static String fastMinifySimpleHtml(String html) {
        return fastMinifySimpleHtml(html, false);
    }

    private static String fastMinifySimpleHtml(String html, boolean endsBeforePreservedBlock) {
        if (html.isEmpty()) return "";
        // 1. Remove comments
        String stripped = HTML_COMMENT_PATTERN.matcher(html).replaceAll("");
        // 2. Collapse horizontal whitespace
        stripped = MULTI_WHITESPACE_PATTERN.matcher(stripped).replaceAll(" ");
        // 3. Collapse whitespace between tags
        stripped = INTER_TAG_WHITESPACE_PATTERN.matcher(stripped).replaceAll("><");
        // 4. Clean tag boundaries
        if (endsBeforePreservedBlock) {
            stripped = TRAILING_TAG_SPACE.matcher(stripped).replaceAll(">");
        }
        stripped = LEADING_TAG_SPACE.matcher(stripped).replaceAll("<");
        return stripped;
    }

    /**
     * Minifies HTML file to UTF-8 bytes directly in-memory.
     */
    public static byte[] minifyHTMLToBytes(File inputFile) throws IOException {
        String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);
        return minifyHTML(content).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Minifies HTML bytes to UTF-8 bytes directly in-memory.
     */
    public static byte[] minifyHTMLToBytes(byte[] inputBytes) {
        String content = new String(inputBytes, StandardCharsets.UTF_8);
        return minifyHTML(content).getBytes(StandardCharsets.UTF_8);
    }
}
