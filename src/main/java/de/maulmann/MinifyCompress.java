package de.maulmann;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class MinifyCompress {

    private static final String PATH_SOURCE = "output";

    // Thread-safe tracking
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        Path directory = Paths.get(PATH_SOURCE);

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            System.err.println("Source directory does not exist or is not a folder: " + directory.toAbsolutePath());
            return;
        }

        System.out.println("Starting in-place high-speed compression...");

        try {
            processDirectoryInPlace(directory);

            long endTime = System.currentTimeMillis();
            System.out.println("\n--- Processing Summary ---");
            System.out.println("Successfully replaced:  " + successCount.get() + " files");
            System.out.println("Failed to process:      " + failureCount.get() + " files");
            System.out.println("Total execution time:   " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            System.err.println("Critical error during execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processDirectoryInPlace(Path directory) throws IOException, InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        // Using Files.list() to get only the files directly inside the 'output' folder
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                executor.submit(() -> {
                    try {
                        processSingleFile(file);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        System.err.println("Failed to process " + file.getFileName() + ": " + e.getMessage());
                    }
                });
            });
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    private static void processSingleFile(Path originalFile) throws IOException {
        String fileName = originalFile.getFileName().toString().toLowerCase();

        // Setup secure, hidden temporary files
        Path tempMinified = null;
        Path tempCompressed = Files.createTempFile("comp_", ".gz");

        try {
            boolean isMinified = false;

            // 1. Minify if applicable
            if (fileName.endsWith(".html")) {
                tempMinified = Files.createTempFile("min_", ".html");
                minifyHTML(originalFile.toFile(), tempMinified.toFile());
                isMinified = true;
            } else if (fileName.endsWith(".css")) {
                tempMinified = Files.createTempFile("min_", ".css");
                minifyCSS(originalFile.toFile(), tempMinified.toFile());
                isMinified = true;
            }

            // 2. Select the input for the compressor
            Path inputForCompression = isMinified ? tempMinified : originalFile;

            // 3. Compress to our temporary GZIP file
            compressFile(inputForCompression.toFile(), tempCompressed.toFile());

            // 4. Safely overwrite the original file with the new compressed data
            // This replaces the old File.renameTo() with a much safer atomic move
            Files.move(tempCompressed, originalFile, StandardCopyOption.REPLACE_EXISTING);

        } finally {
            // 5. Guaranteed cleanup of temp files, even if compression fails
            if (tempMinified != null) {
                Files.deleteIfExists(tempMinified);
            }
            Files.deleteIfExists(tempCompressed);
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
        GZIPCompressor.compressFile(inputFile, outputFile, 9);
    }


}