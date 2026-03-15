package de.maulmann;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MinifyCompressFolder {

    // --- CONFIGURATION ---
    private static final String SOURCE_PATH = "cards/";
    private static final String DEST_PATH = "output/cards/";

    // Thread-safe tracking
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        Path sourceRoot = Paths.get(SOURCE_PATH);
        Path destRoot = Paths.get(DEST_PATH);

        if (!Files.exists(sourceRoot)) {
            System.err.println("Source directory does not exist: " + sourceRoot.toAbsolutePath());
            return;
        }

        System.out.println("Starting high-speed multithreaded compression...");

        try {
            processDirectoryMultithreaded(sourceRoot, destRoot);

            long endTime = System.currentTimeMillis();
            System.out.println("\n--- Processing Summary ---");
            System.out.println("Successfully processed: " + successCount.get() + " files");
            System.out.println("Failed to process:      " + failureCount.get() + " files");
            System.out.println("Total execution time:   " + (endTime - startTime) + " ms");
            System.out.println("Output location:        " + destRoot.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("Critical error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processDirectoryMultithreaded(Path sourceRoot, Path destRoot) throws IOException, InterruptedException {
        // Since GZIP compression and minification are CPU-bound,
        // tying the thread pool to the exact number of CPU cores is highly efficient.
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Spinning up " + cores + " parallel compression threads...");
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Submit each file as an independent task
                executor.submit(() -> {
                    try {
                        processSingleFile(file, sourceRoot, destRoot);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        System.err.println("Failed to process " + file.getFileName() + ": " + e.getMessage());
                    }
                });
                return FileVisitResult.CONTINUE;
            }
        });

        // Prevent new tasks and wait for all compression threads to finish
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    private static void processSingleFile(Path sourceFile, Path sourceRoot, Path destRoot) throws IOException {
        // 1. Calculate destination path maintaining the subfolder structure
        Path relativePath = sourceRoot.relativize(sourceFile);
        Path destFile = destRoot.resolve(relativePath);

        // 2. Thread-safely ensure the parent directories exist
        Files.createDirectories(destFile.getParent());

        File srcFileObj = sourceFile.toFile();
        File dstFileObj = destFile.toFile();
        File tempMinified = null;

        try {
            boolean needsMinification = false;
            String fileName = srcFileObj.getName().toLowerCase();

            // 3. Identify if Minification is needed
            // File.createTempFile generates a unique name, preventing thread collisions
            if (fileName.endsWith(".html")) {
                tempMinified = File.createTempFile("min_html_", ".tmp");
                minifyHTML(srcFileObj, tempMinified);
                needsMinification = true;
            } else if (fileName.endsWith(".css")) {
                tempMinified = File.createTempFile("min_css_", ".tmp");
                minifyCSS(srcFileObj, tempMinified);
                needsMinification = true;
            }

            // 4. Select input for compression
            File inputForCompression = needsMinification ? tempMinified : srcFileObj;

            // 5. Compress to final destination
            compressFile(inputForCompression, dstFileObj);

            // System.out.println("Processed: " + fileName + " on " + Thread.currentThread().getName());

        } finally {
            // 6. Cleanup temp file to avoid leaving garbage on the hard drive
            if (tempMinified != null && tempMinified.exists()) {
                tempMinified.delete();
            }
        }
    }

    // --- Wrapper Methods ---
    // (Assuming your external minifier/compressor classes are thread-safe,
    // which they should be if they only rely on the file arguments passed to them).

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