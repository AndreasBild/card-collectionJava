package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ImageConverter {

    private static final Logger log = LoggerFactory.getLogger(ImageConverter.class);

    // --- Configuration ---
    private static final int MAX_WIDTH = 1200;
    private static final int MAX_HEIGHT = 1680;

    // Definition der Responsive-Breiten für das srcset
    private static final int[] RESPONSIVE_WIDTHS = {200, 400, 600, 900};


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

            log.info("\n--- Image Processing Summary ---");
            log.info("Successfully converted sets: {}", successCount.get());
            log.info("Skipped (unchanged):         {}", skippedCount.get());
            log.info("Failed to convert:           {}", failureCount.get());
            log.info("Total execution time:        {} ms", endTime - startTime);

        } catch (Exception e) {
            log.error("Critical error during processing: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processImages(Path sourceDir, Path webpOutDir) throws IOException {
        log.info("Starting image processing with virtual threads on: {}", sourceDir.toAbsolutePath());
        log.info("AVIFENC_PATH: {}", AVIFENC_PATH);

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
                    File mainAvifFile = currentWebpOutDir.resolve(baseName + ".avif").toFile();
                    File f200 = currentWebpOutDir.resolve(baseName + "-200w.avif").toFile();
                    File f400 = currentWebpOutDir.resolve(baseName + "-400w.avif").toFile();
                    File f600 = currentWebpOutDir.resolve(baseName + "-600w.avif").toFile();
                    File f900 = currentWebpOutDir.resolve(baseName + "-900w.avif").toFile();
                    boolean avifMissing = (AVIFENC_PATH != null && (!mainAvifFile.exists() || !f200.exists() || !f400.exists() || !f600.exists() || !f900.exists()));

                    if (!avifMissing && !tracker.hasChanged(file)) {
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
                            log.error("Failed to process {}: {}", file, e.getMessage());
                            failureCount.incrementAndGet();
                        }
                    }, executor));
                }
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            log.error("Critical error during parallel image processing: {}", e.getMessage());
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

        File mainAvifFile = currentWebpOutDir.resolve(baseName + ".avif").toFile();
        File f200 = currentWebpOutDir.resolve(baseName + "-200w.avif").toFile();
        File f400 = currentWebpOutDir.resolve(baseName + "-400w.avif").toFile();
        File f600 = currentWebpOutDir.resolve(baseName + "-600w.avif").toFile();
        File f900 = currentWebpOutDir.resolve(baseName + "-900w.avif").toFile();
        boolean avifMissing = (AVIFENC_PATH != null && (!mainAvifFile.exists() || !f200.exists() || !f400.exists() || !f600.exists() || !f900.exists()));

        // 1. PRE-CHECK: Müssen wir dieses Bild-Set neu generieren?
        if (!avifMissing && !tracker.hasChanged(sourceFile)) {
            return false;
        }

        // 2. Original-Bild einmalig laden
        java.awt.image.BufferedImage orig = ImageIO.read(sourceFile.toFile());
        if (orig == null) {
            return false;
        }
        int origW = orig.getWidth();
        int origH = orig.getHeight();

        // 3. Smart Scaling für das Hauptbild
        double ratio = Math.min((double) MAX_WIDTH / origW, (double) MAX_HEIGHT / origH);
        int mainW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int mainH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // --- CLI GENERIERUNG ---

        // A) Hauptbild (z.B. jordan.avif)
        if (AVIFENC_PATH != null) {
            writeAvifViaCLI(orig, mainAvifFile, mainW, mainH, 48);
        }

        // B) Responsive Varianten (z.B. jordan-200w.avif, jordan-400w.avif, jordan-600w.avif, jordan-900w.avif)
        for (int targetW : RESPONSIVE_WIDTHS) {
            int w = Math.min(targetW, mainW);
            int h = (w == mainW) ? mainH : (int) (mainH * ((double) w / mainW));
            
            File respAvifFile = currentWebpOutDir.resolve(baseName + "-" + targetW + "w.avif").toFile();
            int avifQuality = (targetW <= 200) ? 36 : (targetW <= 400 ? 38 : (targetW <= 600 ? 40 : 44));
            if (w == mainW && mainAvifFile.exists()) {
                Files.copy(mainAvifFile.toPath(), respAvifFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (AVIFENC_PATH != null) {
                writeAvifViaCLI(orig, respAvifFile, w, h, avifQuality);
            }
        }

        // Hash aktualisieren
        tracker.updateHash(sourceFile);
        return true;
    }

    private static final String AVIFENC_PATH = findAvifenc();


    private static void writeAvifViaCLI(java.awt.image.BufferedImage orig, File outputFile, int targetW, int targetH, int quality) {
        if (AVIFENC_PATH == null || orig == null) return;
        try {
            java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(targetW, targetH, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = resized.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(orig, 0, 0, targetW, targetH, null);
            g.dispose();

            ProcessBuilder pb = new ProcessBuilder(
                    AVIFENC_PATH,
                    "--stdin",
                    "--input-format", "png",
                    "-s", "6",
                    "-q", String.valueOf(quality),
                    "--yuv", "420",
                    "-j", "8",
                    outputFile.getAbsolutePath()
            );
            Process p = pb.start();
            try (java.io.OutputStream os = p.getOutputStream()) {
                ImageIO.write(resized, "png", os);
                os.flush();
            }
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                log.warn("avifenc exited with code {} for {}", exitCode, outputFile.getName());
            }
        } catch (Exception e) {
            log.error("AVIF conversion error for {}: {}", outputFile.getName(), e.getMessage());
        }
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