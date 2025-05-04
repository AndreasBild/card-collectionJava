package de.maulmann;
import com.yahoo.platform.yui.compressor.CssCompressor;

import java.io.*;

public class CSSMinifier {
    public static void minifyCSS(File inputFile, File outputFile) throws IOException {
        try (Reader in = new BufferedReader(new FileReader(inputFile));
             Writer out = new BufferedWriter(new FileWriter(outputFile))) {
            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(out, -1);
        }
    }
}