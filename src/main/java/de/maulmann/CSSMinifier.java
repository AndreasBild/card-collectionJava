package de.maulmann;

import com.yahoo.platform.yui.compressor.CssCompressor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CSSMinifier {

    // Standard File-to-File method (Upgraded to strict UTF-8)
    public static void minifyCSS(File inputFile, File outputFile) throws IOException {
        // Using Files.newInputStream and Explicit UTF-8 prevents character corruption
        try (Reader in = new InputStreamReader(Files.newInputStream(inputFile.toPath()), StandardCharsets.UTF_8);
             Writer out = new OutputStreamWriter(Files.newOutputStream(outputFile.toPath()), StandardCharsets.UTF_8)) {

            CssCompressor compressor = new CssCompressor(in);
            // The '-1' means no line breaks are inserted, maximizing compression
            compressor.compress(out, -1);
        }
    }

    // --- NEW: IN-MEMORY METHOD ---
    // Use this to pass data directly to your Brotli compressor without touching the disk!
    public static byte[] minifyCSSToBytes(File inputFile) throws IOException {
        try (Reader in = new InputStreamReader(Files.newInputStream(inputFile.toPath()), StandardCharsets.UTF_8);
             StringWriter out = new StringWriter()) {

            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, -1);

            // Extract the minified string from RAM and convert it safely to UTF-8 bytes
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}