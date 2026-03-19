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

    // Definition der Responsive-Breiten für das srcset
    private static final int[] RESPONSIVE_WIDTHS = {400, 600};

    // Mac CLI Path zu Homebrew cwebp
    private static final String CWEBP_PATH = "/opt/homebrew/bin/cwebp";

    // Zähler für die Zusammenfassung
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger skippedCount = new AtomicInteger(0);

    public static void main(String[] args) {
        Path sourceDir = Paths.get("images");
        Path webpOutDir = Paths.get("output/images");

        long startTime = System.currentTimeMillis();

        try {
            processImages(sourceDir, webpOutDir);
            long endTime = System.currentTimeMillis();

            System.out.println("\n--- Image Processing Summary ---");
            System.out.println("Successfully converted sets: " + successCount.get());
            System.out.println("Skipped (unchanged):         " + skippedCount.get());
            System.out.println("Failed to convert:           " + failureCount.get());
            System.out.println("Total execution time:        " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            System.err.println("Critical error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processImages(Path sourceDir, Path webpOutDir) throws IOException, InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Starting high-performance pool with " + cores + " threads...");
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        // Initialisierung des Hash-Checkers
        FileTracker tracker = new FileTracker("output/image-build-hashes.properties");

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp")) {

                    executor.submit(() -> {
                        try {
                            boolean wasConverted = convertAndSaveImageSet(file, sourceDir, webpOutDir, tracker);
                            if (wasConverted) {
                                successCount.incrementAndGet();
                            } else {
                                skippedCount.incrementAndGet();
                            }
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

        // Speichern der aktualisierten Hashes
        tracker.save();
    }

    private static boolean convertAndSaveImageSet(Path sourceFile, Path sourceDir, Path webpOutDir, FileTracker tracker) throws Exception {

        Path relativePath = sourceDir.relativize(sourceFile);
        String baseName = getBaseName(relativePath.getFileName().toString());
        Path relativeParent = relativePath.getParent();

        Path currentWebpOutDir = relativeParent != null ? webpOutDir.resolve(relativeParent) : webpOutDir;
        Files.createDirectories(currentWebpOutDir);

        File mainWebpFile = currentWebpOutDir.resolve(baseName + ".webp").toFile();

        // 1. PRE-CHECK: Müssen wir dieses Bild-Set neu generieren?
        if (mainWebpFile.exists() && !tracker.hasChanged(sourceFile)) {
            return false;
        }

        // 2. Original-Dimensionen auslesen (nur Header-Scan)
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
            }
        }

        // 3. Smart Scaling für das Hauptbild
        boolean isPortrait = origH > origW;
        int currentMaxWidth = isPortrait ? MAX_HEIGHT : MAX_WIDTH;
        int currentMaxHeight = isPortrait ? MAX_WIDTH : MAX_HEIGHT;

        double ratio = Math.min((double) currentMaxWidth / origW, (double) currentMaxHeight / origH);
        int mainW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int mainH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // --- CLI GENERIERUNG ---

        // A) Hauptbild (z.B. jordan.webp)
        writeWebpViaCLI(sourceFile, mainWebpFile, mainW, mainH);

        // B) Responsive Varianten (z.B. jordan-400w.webp)
        for (int targetW : RESPONSIVE_WIDTHS) {
            if (targetW < mainW) {
                int targetH = (int) (mainH * ((double) targetW / mainW));
                File respFile = currentWebpOutDir.resolve(baseName + "-" + targetW + "w.webp").toFile();
                writeWebpViaCLI(sourceFile, respFile, targetW, targetH);
            }
        }

        // Hash aktualisieren
        tracker.updateHash(sourceFile);
        return true;
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
            throw new IOException("cwebp fehlerhaft mit Code " + exitCode);
        }
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}