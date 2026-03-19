package de.maulmann;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageConverter {

    // --- Configuration ---
    private static final int MAX_WIDTH = 1000;
    private static final int MAX_HEIGHT = 700;

    // Mac CLI Paths
    private static final String CWEBP_PATH = "/opt/homebrew/bin/cwebp";

    // Thread-safe counters
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger skippedCount = new AtomicInteger(0); // NEU: Zähler für übersprungene Bilder

    public static void main(String[] args) {
        Path sourceDir = Paths.get("images");
        Path webpOutDir = Paths.get("output/images"); // Drops right into your pipeline

        long startTime = System.currentTimeMillis();

        try {
            processImages(sourceDir, webpOutDir);
            long endTime = System.currentTimeMillis();

            System.out.println("\n--- Image Processing Summary ---");
            System.out.println("Successfully converted: " + successCount.get() + " images");
            System.out.println("Skipped (unchanged):    " + skippedCount.get() + " images");
            System.out.println("Failed to convert:      " + failureCount.get() + " images");
            System.out.println("Total execution time:   " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            System.err.println("Critical error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processImages(Path sourceDir, Path webpOutDir) throws IOException, InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Starting high-performance pool with " + cores + " threads...");
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        // Lade die bisherigen Hashes für den Build-Prozess
        ImageHashCache hashCache = new ImageHashCache("output/image-build-hashes.properties");

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp")) {

                    executor.submit(() -> {
                        try {
                            boolean wasConverted = convertAndSaveImage(file, sourceDir, webpOutDir, hashCache);
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

        // Speichere die aktualisierten Hashes am Ende des gesamten Prozesses
        hashCache.save();
    }

    // Gibt true zurück, wenn das Bild konvertiert wurde, und false, wenn es übersprungen wurde
    private static boolean convertAndSaveImage(Path sourceFile, Path sourceDir, Path webpOutDir, ImageHashCache hashCache) throws Exception {

        // --- 1. Setup paths to maintain subfolder structure ---
        Path relativePath = sourceDir.relativize(sourceFile);
        String baseName = getBaseName(relativePath.getFileName().toString());
        Path relativeParent = relativePath.getParent();

        Path currentWebpOutDir = relativeParent != null ? webpOutDir.resolve(relativeParent) : webpOutDir;
        Files.createDirectories(currentWebpOutDir);

        File webpOutputFile = currentWebpOutDir.resolve(baseName + ".webp").toFile();

        // --- 2. PRE-CHECK: Müssen wir dieses Bild überhaupt neu generieren? ---
        if (webpOutputFile.exists() && hashCache.isUnchanged(sourceFile)) {
            return false; // Überspringen! Datei existiert und Original hat sich nicht verändert.
        }

        // --- 3. Fast Dimension Calculation (Reads header only, saves RAM) ---
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

        // --- 4. Smart Orientation Detection ---
        boolean isPortrait = origH > origW;

        // If portrait, swap the bounding box so height gets the larger limit (1000)
        int currentMaxWidth = isPortrait ? MAX_HEIGHT : MAX_WIDTH;   // Portrait: 700, Landscape: 1000
        int currentMaxHeight = isPortrait ? MAX_WIDTH : MAX_HEIGHT;  // Portrait: 1000, Landscape: 700

        double ratio = Math.min((double) currentMaxWidth / origW, (double) currentMaxHeight / origH);
        int newW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int newH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // --- 5. Delegate to Native CLI Tools ---
        writeWebpViaCLI(sourceFile, webpOutputFile, newW, newH);

        // Nach erfolgreicher Konvertierung: Hash speichern, damit es beim nächsten Mal übersprungen wird
        hashCache.updateHash(sourceFile);

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
            throw new IOException("cwebp exited with code " + exitCode);
        }
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    // =========================================================================
    // HILFSKLASSE FÜR DEN BUILD-HASH-CHECK
    // =========================================================================
    static class ImageHashCache {
        private final File storeFile;
        private final Properties hashes = new Properties();

        public ImageHashCache(String filePath) {
            this.storeFile = new File(filePath);
            if (storeFile.exists()) {
                try (InputStream in = Files.newInputStream(storeFile.toPath())) {
                    hashes.load(in);
                } catch (Exception e) {
                    System.err.println("Konnte Image-Hash-Datei nicht laden: " + e.getMessage());
                }
            }
        }

        public boolean isUnchanged(Path file) {
            try {
                String currentHash = calculateMD5(file);
                String storedHash = hashes.getProperty(file.toString());
                return currentHash.equals(storedHash);
            } catch (Exception e) {
                return false; // Bei Fehlern lieber neu generieren
            }
        }

        public void updateHash(Path file) {
            try {
                hashes.setProperty(file.toString(), calculateMD5(file));
            } catch (Exception ignored) {
            }
        }

        public void save() {
            try {
                storeFile.getParentFile().mkdirs();
                try (OutputStream out = Files.newOutputStream(storeFile.toPath())) {
                    hashes.store(out, "Automatisierter Image Build Hash Cache");
                }
            } catch (Exception e) {
                System.err.println("Konnte Image-Hash-Datei nicht speichern: " + e.getMessage());
            }
        }

        private String calculateMD5(Path file) throws Exception {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
}