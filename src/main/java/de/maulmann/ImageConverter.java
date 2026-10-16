package de.maulmann;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Path avifOutDir = Paths.get("output/images");

        boolean skipReport = false;
        if (args != null) {
            for (String arg : args) {
                if ("--no-report".equalsIgnoreCase(arg)) {
                    skipReport = true;
                    break;
                }
            }
        }

        long startTime = System.currentTimeMillis();

        try {
            processImages(sourceDir, avifOutDir);
            long endTime = System.currentTimeMillis();

            log.info("\n--- Image Processing Summary ---");
            log.info("Successfully converted sets: {}", successCount.get());
            log.info("Skipped (unchanged):         {}", skippedCount.get());
            log.info("Failed to convert:           {}", failureCount.get());
            log.info("Total execution time:        {} ms", endTime - startTime);

            if (!skipReport) {
                updateMissingImagesReportIfCardsAvailable();
            }

        } catch (Exception e) {
            log.error("Critical error during processing: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateMissingImagesReportIfCardsAvailable() {
        try {
            Path cardsPath = Paths.get("content/json/cards.json");
            if (Files.exists(cardsPath)) {
                List<CardJson> cards = CardDataLoader.loadCardsFromJson(cardsPath.toString());
                if (cards != null && !cards.isEmpty()) {
                    List<CardData> cardDataList = new ArrayList<>(cards.size());
                    for (CardJson c : cards) {
                        cardDataList.add(CardPageGenerator.computeCardData(c));
                    }
                    CardPageGenerator.refreshExistingImageKeys();
                    CardPageGenerator.generateMissingImagesReport(cardDataList);
                    log.info("Updated MissingImages.txt following image conversion.");
                }
            }
        } catch (Exception e) {
            log.warn("Could not update MissingImages.txt after image conversion: {}", e.getMessage());
        }
    }

    public static void processImages(Path sourceDir, Path avifOutDir) throws IOException {
        log.info("Starting image processing with virtual threads on: {}", sourceDir.toAbsolutePath());
        log.info("AVIFENC_PATH: {}", AVIFENC_PATH);

        // Initialisierung des Hash-Checkers
        FileTracker tracker = new FileTracker("output/image-build-hashes.properties");

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
                    Path currentAvifOutDir = relativeParent != null ? avifOutDir.resolve(relativeParent) : avifOutDir;

                    executor.submit(() -> {
                        try {
                            File mainAvifFile = currentAvifOutDir.resolve(baseName + ".avif").toFile();
                            File f200 = currentAvifOutDir.resolve(baseName + "-200w.avif").toFile();
                            File f400 = currentAvifOutDir.resolve(baseName + "-400w.avif").toFile();
                            File f600 = currentAvifOutDir.resolve(baseName + "-600w.avif").toFile();
                            File f900 = currentAvifOutDir.resolve(baseName + "-900w.avif").toFile();
                            boolean avifMissing = (AVIFENC_PATH != null && (!mainAvifFile.exists() || !f200.exists() || !f400.exists() || !f600.exists() || !f900.exists()));

                            if (!avifMissing && !tracker.hasChanged(file)) {
                                skippedCount.incrementAndGet();
                                return;
                            }

                            boolean wasConverted = convertAndSaveImageSet(file, sourceDir, avifOutDir, tracker);
                            if (wasConverted) {
                                successCount.incrementAndGet();
                            } else {
                                skippedCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("Failed to process {}: {}", file, e.getMessage());
                            failureCount.incrementAndGet();
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Critical error during parallel image processing: {}", e.getMessage());
        }

        // Bereinigung verwaister AVIF-Bilder in output/images
        cleanOrphanedAvifImages(sourceDir, avifOutDir);

        // Speichern der aktualisierten Hashes
        tracker.save();
    }

    /**
     * Scans the AVIF output directory and removes orphaned AVIF files whose original source scans no longer exist.
     * @param sourceDir Root source images directory
     * @param avifOutDir Root AVIF output directory
     * @return Count of orphaned files removed
     */
    public static int cleanOrphanedAvifImages(Path sourceDir, Path avifOutDir) {
        if (!Files.exists(avifOutDir) || !Files.exists(sourceDir)) {
            return 0;
        }
        int removedCount = 0;
        try (Stream<Path> stream = Files.walk(avifOutDir)) {
            List<Path> avifFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".avif"))
                    .toList();

            for (Path avifFile : avifFiles) {
                Path relative = avifOutDir.relativize(avifFile);
                String fileName = relative.getFileName().toString();
                String baseName = fileName.replaceFirst("-(200|400|600|900)w\\.avif$", "").replaceFirst("\\.avif$", "");
                Path parent = relative.getParent();

                boolean sourceExists = false;
                String[] possibleExts = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".JPG", ".JPEG", ".PNG"};
                for (String ext : possibleExts) {
                    Path candidate = parent != null
                            ? sourceDir.resolve(parent).resolve(baseName + ext)
                            : sourceDir.resolve(baseName + ext);
                    if (Files.exists(candidate)) {
                        sourceExists = true;
                        break;
                    }
                }

                if (!sourceExists) {
                    try {
                        Files.deleteIfExists(avifFile);
                        removedCount++;
                        log.debug("Removed orphaned AVIF file: {}", avifFile);
                    } catch (IOException e) {
                        log.warn("Could not remove orphaned AVIF file {}: {}", avifFile, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error during orphaned AVIF cleanup: {}", e.getMessage());
        }
        if (removedCount > 0) {
            log.info("Cleaned {} orphaned AVIF image(s) from output.", removedCount);
        }
        return removedCount;
    }

    private static boolean convertAndSaveImageSet(Path sourceFile, Path sourceDir, Path avifOutDir, FileTracker tracker) throws Exception {

        Path relativePath = sourceDir.relativize(sourceFile);
        String baseName = getBaseName(relativePath.getFileName().toString());
        Path relativeParent = relativePath.getParent();

        Path currentAvifOutDir = relativeParent != null ? avifOutDir.resolve(relativeParent) : avifOutDir;
        Files.createDirectories(currentAvifOutDir);

        File mainAvifFile = currentAvifOutDir.resolve(baseName + ".avif").toFile();
        File f200 = currentAvifOutDir.resolve(baseName + "-200w.avif").toFile();
        File f400 = currentAvifOutDir.resolve(baseName + "-400w.avif").toFile();
        File f600 = currentAvifOutDir.resolve(baseName + "-600w.avif").toFile();
        File f900 = currentAvifOutDir.resolve(baseName + "-900w.avif").toFile();
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

            File respAvifFile = currentAvifOutDir.resolve(baseName + "-" + targetW + "w.avif").toFile();
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


    /** Caps concurrent avifenc processes (each is already multi-threaded) to avoid process/memory exhaustion. */
    private static final int MAX_CONCURRENT_ENCODES = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final java.util.concurrent.Semaphore ENCODE_PERMITS =
            new java.util.concurrent.Semaphore(MAX_CONCURRENT_ENCODES);
    private static final int AVIFENC_TIMEOUT_SECONDS = 120;

    private static void writeAvifViaCLI(java.awt.image.BufferedImage orig, File outputFile, int targetW, int targetH, int quality) {
        if (AVIFENC_PATH == null || orig == null) return;
        // Temp file keeps the .avif extension (avifenc infers format from it) and lives next to the target
        // so the final move is atomic. Leftovers are removed by cleanOrphanedAvifImages.
        Path finalPath = outputFile.toPath();
        Path tmpPath = finalPath.resolveSibling(".part-" + outputFile.getName());
        boolean acquired = false;
        Process p = null;
        try {
            ENCODE_PERMITS.acquire();
            acquired = true;

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
                    "-j", "2",
                    tmpPath.toAbsolutePath().toString()
            );
            // Never let an unread output pipe block the encoder.
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            p = pb.start();
            try (java.io.OutputStream os = p.getOutputStream()) {
                ImageIO.write(resized, "png", os);
                os.flush();
            }
            if (!p.waitFor(AVIFENC_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.error("avifenc timed out after {}s for {}", AVIFENC_TIMEOUT_SECONDS, outputFile.getName());
                Files.deleteIfExists(tmpPath);
                return;
            }
            int exitCode = p.exitValue();
            if (exitCode != 0) {
                log.warn("avifenc exited with code {} for {}", exitCode, outputFile.getName());
                Files.deleteIfExists(tmpPath);
                return;
            }
            try {
                Files.move(tmpPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (p != null) p.destroyForcibly();
            log.error("AVIF conversion interrupted for {}: {}", outputFile.getName(), ie.getMessage());
        } catch (Exception e) {
            if (p != null && p.isAlive()) p.destroyForcibly();
            log.error("AVIF conversion error for {}: {}", outputFile.getName(), e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {
                // best effort; orphan cleanup removes stale .part files
            }
            if (acquired) ENCODE_PERMITS.release();
        }
    }


    private static String findAvifenc() {
        String[] paths = {
            "avifenc",
            "avifenc.exe",
            "/opt/homebrew/bin/avifenc",
            "/usr/local/bin/avifenc",
            "/usr/bin/avifenc",
            "C:\\Program Files\\libavif\\avifenc.exe"
        };
        for (String path : paths) {
            try {
                Process p = new ProcessBuilder(path, "--version").start();
                if (p.waitFor() == 0) return path;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }
        return null;
    }

    static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}
