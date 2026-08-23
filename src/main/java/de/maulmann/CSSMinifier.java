package de.maulmann;

import com.yahoo.platform.yui.compressor.CssCompressor;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class CSSMinifier {

    private static final Pattern LEADING_ZERO_PATTERN = Pattern.compile("\\b0(\\.\\d+)");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#([0-9a-fA-F])\\1([0-9a-fA-F])\\2([0-9a-fA-F])\\3\\b");
    private static final Pattern ZERO_UNIT_PATTERN = Pattern.compile("\\b0(px|em|rem|%|pt|in|cm|mm)\\b");

    public static byte[] minifyCSSToBytes(File inputFile) throws IOException {
        try (Reader in = new InputStreamReader(Files.newInputStream(inputFile.toPath()), StandardCharsets.UTF_8);
             StringWriter out = new StringWriter()) {

            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, -1);

            String minified = out.toString();

            // Additional post-processing passes for extra byte savings
            minified = LEADING_ZERO_PATTERN.matcher(minified).replaceAll("$1");
            minified = HEX_COLOR_PATTERN.matcher(minified).replaceAll("#$1$2$3");
            minified = ZERO_UNIT_PATTERN.matcher(minified).replaceAll("0");
            minified = minified.replace(";}", "}");

            return minified.getBytes(StandardCharsets.UTF_8);
        }
    }
}
