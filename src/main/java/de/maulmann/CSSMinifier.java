package de.maulmann;

import com.yahoo.platform.yui.compressor.CssCompressor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CSSMinifier {

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