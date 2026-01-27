package de.maulmann;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;

public class MinifyCompressFolder {

    // Define source and destination paths
    private static final String SOURCE_PATH = "cards/";
    private static final String DEST_PATH = "output/cards/";

    public static void main(String[] args) {
        File sourceRoot = new File(SOURCE_PATH);
        File destRoot = new File(DEST_PATH);

        if (!sourceRoot.exists()) {
            System.err.println("Source directory does not exist: " + sourceRoot.getAbsolutePath());
            return;
        }

        System.out.println("Starting recursive compression...");
        processDirectory(sourceRoot, destRoot);
        System.out.println("Done! Compressed files are in: " + destRoot.getAbsolutePath());
    }

    /**
     * Recursively walks through the source directory and replicates structure in destination.
     */
    private static void processDirectory(File sourceDir, File destDir) {
        // Create destination directory if it doesn't exist
        if (!destDir.exists() && !destDir.mkdirs()) {
            System.err.println("Failed to create directory: " + destDir.getAbsolutePath());
            return;
        }

        // Get all files in current directory
        File[] files = sourceDir.listFiles();
        if (files == null) return;

        for (File sourceFile : files) {
            // Determine the corresponding destination file/folder path
            File destFile = new File(destDir, sourceFile.getName());

            if (sourceFile.isDirectory()) {
                // RECURSION: If it's a folder, go deeper
                processDirectory(sourceFile, destFile);
            } else {
                // If it's a file, process it
                processFile(sourceFile, destFile);
            }
        }
    }

    /**
     * Handles the logic for Minification -> Compression -> Output
     */
    private static void processFile(File sourceFile, File destFile) {
        File tempMinified = null;

        try {
            boolean needsMinification = false;

            // 1. Identify if Minification is needed
            if (sourceFile.getName().endsWith(".html")) {
                tempMinified = File.createTempFile("min_html_", ".tmp");
                minifyHTML(sourceFile, tempMinified);
                needsMinification = true;
            } else if (sourceFile.getName().endsWith(".css")) {
                tempMinified = File.createTempFile("min_css_", ".tmp");
                minifyCSS(sourceFile, tempMinified);
                needsMinification = true;
            }

            // 2. Select input for compression (either the minified temp file or the original)
            File inputForCompression = needsMinification ? tempMinified : sourceFile;

            // 3. Compress to final destination
            // Note: If you want to append .gz to the filename, change destFile below to:
            // new File(destFile.getAbsolutePath() + ".gz")
            compressFile(inputForCompression, destFile);

            System.out.println("Compressed: " + sourceFile.getName() + " -> " + destFile.getName());

        } catch (IOException e) {
            System.err.println("Error processing file: " + sourceFile.getAbsolutePath());
            e.printStackTrace();
        } finally {
            // 4. Cleanup temp file
            if (tempMinified != null && tempMinified.exists()) {
                tempMinified.delete();
            }
        }
    }

    // --- Wrapper Methods ---

    public static void minifyHTML(File inputFile, File outputFile) throws IOException {
        HTMLMinifier.minifyHTML(inputFile, outputFile);
    }

    public static void minifyCSS(File inputFile, File outputFile) throws IOException {
        CSSMinifier.minifyCSS(inputFile, outputFile);
    }

    public static void compressFile(File inputFile, File outputFile) throws IOException {
        // Compresses input and writes it to the destination file
        GZIPCompressor.compressFile(inputFile, outputFile, 9);
    }
}