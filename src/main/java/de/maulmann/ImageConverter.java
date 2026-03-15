package de.maulmann;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageConverter {

    // --- Configuration ---
    private static final int MAX_WIDTH = 1000;
    private static final int MAX_HEIGHT = 700;
    private static final int TARGET_DPI = 72;
    private static final int TARGET_JPG_KB = 130;

    // Mac CLI Paths (Update to /usr/local/bin/... if on Intel Mac)
    private static final String MAGICK_PATH = "/opt/homebrew/bin/magick";
    private static final String CWEBP_PATH = "/opt/homebrew/bin/cwebp";

    // Thread-safe counters
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) {
        // Define your directories here
        Path sourceDir = Paths.get("Test");
        Path jpgOutDir = Paths.get("output/output_jpg");
        Path webpOutDir = Paths.get("output/output_webp");

        long startTime = System.currentTimeMillis();

        try {
            processImages(sourceDir, jpgOutDir, webpOutDir);
            long endTime = System.currentTimeMillis();

            System.out.println("\n--- Processing Summary ---");
            System.out.println("Successfully converted: " + successCount.get() + " images");
            System.out.println("Failed to convert:      " + failureCount.get() + " images");
            System.out.println("Total execution time:   " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            System.err.println("Critical error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processImages(Path sourceDir, Path jpgOutDir, Path webpOutDir) throws IOException, InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Starting high-performance pool with " + cores + " threads...");
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp")) {

                    executor.submit(() -> {
                        try {
                            convertAndSaveImage(file, sourceDir, jpgOutDir, webpOutDir);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Failed to process " + file + ": " + e.getMessage());
                            failureCount.incrementAndGet();
                        }
                    });
                }
                return FileVisitResult.CONTINUE;
            }
        });

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    private static void convertAndSaveImage(Path sourceFile, Path sourceDir, Path jpgOutDir, Path webpOutDir) throws IOException, InterruptedException {
        // --- 1. Fast Dimension Calculation (Reads header only, saves RAM) ---
        int origW = 0;
        int origH = 0;
        try (ImageInputStream in = ImageIO.createImageInputStream(sourceFile.toFile())) {
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    origW = reader.getWidth(0);
                    origH = reader.getHeight(0);
                } finally {
                    reader.dispose();
                }
            } else {
                throw new IOException("Could not read image header.");
            }
        }

        double ratio = Math.min((double) MAX_WIDTH / origW, (double) MAX_HEIGHT / origH);
        int newW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int newH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // --- 2. Setup paths to maintain subfolder structure ---
        Path relativePath = sourceDir.relativize(sourceFile);
        String baseName = getBaseName(relativePath.getFileName().toString());
        Path relativeParent = relativePath.getParent();

        Path currentJpgOutDir = relativeParent != null ? jpgOutDir.resolve(relativeParent) : jpgOutDir;
        Path currentWebpOutDir = relativeParent != null ? webpOutDir.resolve(relativeParent) : webpOutDir;

        Files.createDirectories(currentJpgOutDir);
        Files.createDirectories(currentWebpOutDir);

        File jpgOutputFile = currentJpgOutDir.resolve(baseName + ".jpg").toFile();
        File webpOutputFile = currentWebpOutDir.resolve(baseName + ".webp").toFile();

        // --- 3. Delegate to Native CLI Tools ---
        writeJpegViaCLI(sourceFile, jpgOutputFile);
        writeWebpViaCLI(sourceFile, webpOutputFile, newW, newH);
    }

    private static void writeJpegViaCLI(Path sourceFile, File outputFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                MAGICK_PATH,
                sourceFile.toAbsolutePath().toString(),
                "-resize", MAX_WIDTH + "x" + MAX_HEIGHT + ">", // Shrink to fit bounding box only if larger
                "-density", String.valueOf(TARGET_DPI),
                "-units", "PixelsPerInch",
                "-interlace", "Plane", // Progressive JPG
                "-define", "jpeg:extent=" + TARGET_JPG_KB + "kb", // The magic target size flag
                outputFile.getAbsolutePath()
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("ImageMagick exited with code " + exitCode);
        }
    }

    private static void writeWebpViaCLI(Path sourceFile, File outputFile, int targetW, int targetH) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                CWEBP_PATH,
                "-q", "75",
                "-resize", String.valueOf(targetW), String.valueOf(targetH),
                "-mt",
                "-quiet",
                sourceFile.toAbsolutePath().toString(),
                "-o", outputFile.getAbsolutePath()
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("cwebp exited with code " + exitCode);
        }
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}