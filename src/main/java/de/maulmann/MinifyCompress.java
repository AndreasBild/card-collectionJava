package de.maulmann;

import java.io.*;

import java.util.Objects;


public class MinifyCompress {
    private static final String pathSource = "../card-CollectionJava/output";

    public static void main(String[] args) {
        File directory = new File(pathSource);

        for (File file : Objects.<File[]>requireNonNull(directory.listFiles())) {
            if (file.isFile()) {
                try {
                    // Temp file for minification
                    File tempFile = new File(file.getPath() + ".min");

                    // Minify HTML or CSS
                    if (file.getName().endsWith(".html")) {
                        minifyHTML(file, tempFile);
                    } else if (file.getName().endsWith(".css")) {
                        minifyCSS(file, tempFile);
                    } else {
                        tempFile = file; // No minification, use the original file
                    }

                    // Compress with GZIP
                    File compressedFile = new File(tempFile.getPath() + ".gz");
                    compressFile(tempFile, compressedFile);

                    // Rename file to original name
                    File finalFile = new File(file.getPath());
                    compressedFile.renameTo(finalFile);

                    // Delete the original uncompressed or minified file
                    if (!tempFile.equals(file)) {
                        tempFile.delete();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Minify HTML
    public static void minifyHTML(File inputFile, File outputFile) throws IOException {
        HTMLMinifier.minifyHTML(inputFile, outputFile);
    }

    // Minify CSS
    public static void minifyCSS(File inputFile, File outputFile) throws IOException {

        CSSMinifier.minifyCSS(inputFile, outputFile);
    }

    // Compress with GZIP
    public static void compressFile(File inputFile, File outputFile) throws IOException {
        GZIPCompressor.compressFile(inputFile, outputFile);

    }
}
