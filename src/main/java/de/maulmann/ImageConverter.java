package de.maulmann;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ImageConverter {

    // --- Configuration ---
    private static final int MAX_WIDTH = 1200;
    private static final int MAX_HEIGHT = 1680;

    // Definition der Responsive-Breiten für das srcset
    private static final int[] RESPONSIVE_WIDTHS = {400, 600, 900};

    // Dynamic discovery of cwebp
    private static final String CWEBP_PATH = findCwebp();

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

    public static void processImages(Path sourceDir, Path webpOutDir) throws IOException {
        System.out.println("Starting image processing with virtual threads on: " + sourceDir.toAbsolutePath());
        System.out.println("AVIFENC_PATH: " + AVIFENC_PATH);

        // Initialisierung des Hash-Checkers
        FileTracker tracker = new FileTracker("output/image-build-hashes.properties");

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Path> allFiles;
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                allFiles = stream.filter(Files::isRegularFile).toList();
            }
            for (Path file : allFiles) {
                String fileName = file.toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp")) {

                    Path relativePath = sourceDir.relativize(file);
                    String baseName = getBaseName(relativePath.getFileName().toString());
                    Path relativeParent = relativePath.getParent();
                    Path currentWebpOutDir = relativeParent != null ? webpOutDir.resolve(relativeParent) : webpOutDir;
                    File mainWebpFile = currentWebpOutDir.resolve(baseName + ".webp").toFile();
                    File mainAvifFile = currentWebpOutDir.resolve(baseName + ".avif").toFile();
                    boolean mainAvifMissing = (AVIFENC_PATH != null && !mainAvifFile.exists());

                    if (mainWebpFile.exists() && !mainAvifMissing && !tracker.hasChanged(file)) {
                        skippedCount.incrementAndGet();
                        continue;
                    }

                    futures.add(CompletableFuture.runAsync(() -> {
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
                    }, executor));
                }
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            System.err.println("Critical error during parallel image processing: " + e.getMessage());
        }

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

        File mainAvifFile = currentWebpOutDir.resolve(baseName + ".avif").toFile();
        boolean mainAvifMissing = (AVIFENC_PATH != null && !mainAvifFile.exists());

        // 1. PRE-CHECK: Müssen wir dieses Bild-Set neu generieren?
        if (mainWebpFile.exists() && !mainAvifMissing && !tracker.hasChanged(sourceFile)) {
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
        double ratio = Math.min((double) MAX_WIDTH / origW, (double) MAX_HEIGHT / origH);
        int mainW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int mainH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // --- CLI GENERIERUNG ---

        // A) Hauptbild (z.B. jordan.webp & jordan.avif)
        writeWebpViaCLI(sourceFile, mainWebpFile, mainW, mainH, 78);
        if (AVIFENC_PATH != null) {
            writeAvifViaCLI(sourceFile, mainAvifFile, mainW, mainH, 48);
        }

        // B) Responsive Varianten (z.B. jordan-400w.webp & jordan-400w.avif)
        for (int targetW : RESPONSIVE_WIDTHS) {
            if (targetW < mainW) {
                int targetH = (int) (mainH * ((double) targetW / mainW));
                File respFile = currentWebpOutDir.resolve(baseName + "-" + targetW + "w.webp").toFile();
                int quality = (targetW <= 400) ? 70 : 75;
                writeWebpViaCLI(sourceFile, respFile, targetW, targetH, quality);

                if (AVIFENC_PATH != null) {
                    File respAvifFile = currentWebpOutDir.resolve(baseName + "-" + targetW + "w.avif").toFile();
                    int avifQuality = (targetW <= 400) ? 38 : (targetW <= 600 ? 40 : 44);
                    writeAvifViaCLI(sourceFile, respAvifFile, targetW, targetH, avifQuality);
                }
            }
        }

        // Hash aktualisieren
        tracker.updateHash(sourceFile);
        return true;
    }

    private static final String AVIFENC_PATH = findAvifenc();

    private static void writeWebpViaCLI(Path sourceFile, File outputFile, int targetW, int targetH, int quality) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                CWEBP_PATH,
                "-q", String.valueOf(quality),
                "-m", "6",
                "-sharp_yuv",
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

    private static void writeAvifViaCLI(Path sourceFile, File outputFile, int targetW, int targetH, int quality) {
        if (AVIFENC_PATH == null) return;
        try {
            File tempPng = File.createTempFile("avif_resize_", ".png");
            try {
                java.awt.image.BufferedImage orig = ImageIO.read(sourceFile.toFile());
                if (orig != null) {
                    java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(targetW, targetH, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = resized.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(orig, 0, 0, targetW, targetH, null);
                    g.dispose();
                    ImageIO.write(resized, "png", tempPng);

                    ProcessBuilder pb = new ProcessBuilder(
                            AVIFENC_PATH,
                            "-s", "6",
                            "-q", String.valueOf(quality),
                            "--yuv", "420",
                            "-j", "8",
                            tempPng.getAbsolutePath(),
                            outputFile.getAbsolutePath()
                    );
                    Process p = pb.start();
                    p.waitFor();
                }
            } finally {
                if (tempPng.exists()) tempPng.delete();
            }
        } catch (Exception e) {
            System.err.println("AVIF conversion error for " + outputFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String findCwebp() {
        String[] paths = {
            "cwebp",
            "/usr/bin/cwebp",
            "/usr/local/bin/cwebp",
            "/opt/homebrew/bin/cwebp",
            "/usr/sbin/cwebp",
            "/bin/cwebp"
        };
        for (String path : paths) {
            try {
                Process p = new ProcessBuilder(path, "-version").start();
                if (p.waitFor() == 0) return path;
            } catch (Exception ignored) {}
        }
        return "cwebp"; // Fallback to PATH
    }

    private static String findAvifenc() {
        String[] paths = {
            "avifenc",
            "/opt/homebrew/bin/avifenc",
            "/usr/local/bin/avifenc",
            "/usr/bin/avifenc"
        };
        for (String path : paths) {
            try {
                Process p = new ProcessBuilder(path, "--version").start();
                if (p.waitFor() == 0) return path;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}