package de.maulmann;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.w3c.dom.Element;

public class ImageConverter {

    // --- Configuration ---
    private static final int MAX_WIDTH = 1000;
    private static final int MAX_HEIGHT = 700;
    private static final int TARGET_DPI = 72;
    private static final int TARGET_JPG_KB = 140;

    // UPDATE THIS PATH based on the output of 'which cwebp' in your Mac Terminal
    private static final String CWEBP_PATH = "/opt/homebrew/bin/cwebp";

    // Atomic counters for thread-safe tracking
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
        System.out.println("Starting processing pool with " + cores + " threads...");
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
        // --- 1. Read Original Image for JPG processing & dimension calculation ---
        BufferedImage originalImage = ImageIO.read(sourceFile.toFile());
        if (originalImage == null) {
            throw new IOException("ImageIO returned null (unsupported or corrupted format).");
        }

        int origW = originalImage.getWidth();
        int origH = originalImage.getHeight();
        double ratio = Math.min((double) MAX_WIDTH / origW, (double) MAX_HEIGHT / origH);

        int newW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int newH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // --- 2. Setup paths safely to maintain subfolder structure ---
        Path relativePath = sourceDir.relativize(sourceFile);
        String baseName = getBaseName(relativePath.getFileName().toString());
        Path relativeParent = relativePath.getParent();

        Path currentJpgOutDir = relativeParent != null ? jpgOutDir.resolve(relativeParent) : jpgOutDir;
        Path currentWebpOutDir = relativeParent != null ? webpOutDir.resolve(relativeParent) : webpOutDir;

        Files.createDirectories(currentJpgOutDir);
        Files.createDirectories(currentWebpOutDir);

        File jpgOutputFile = currentJpgOutDir.resolve(baseName + ".jpg").toFile();
        File webpOutputFile = currentWebpOutDir.resolve(baseName + ".webp").toFile();

        // --- 3. Process JPG (Java Native) ---
        BufferedImage resizedImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(originalImage, 0, 0, newW, newH, null);
        g2d.dispose();

        writeJpegOptimized(resizedImage, jpgOutputFile, TARGET_JPG_KB);

        // --- 4. Process WebP (Mac CLI) ---
        // We pass the calculated newW and newH to let cwebp handle the resize natively
        writeWebpViaCLI(sourceFile, webpOutputFile, newW, newH);
    }

    private static void writeJpegOptimized(BufferedImage image, File outputFile, int maxFileSizeKb) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPG writer found");
        ImageWriter writer = writers.next();

        float quality = 0.85f;
        float minQuality = 0.40f;
        byte[] finalImageBytes = null;

        while (quality >= minQuality) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();

                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);

                // Make it interlaced/progressive
                if (param.canWriteProgressive()) {
                    param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
                }

                // Apply 72 DPI
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
                if (metadata != null && !metadata.isReadOnly()) {
                    setDpiMetadata(metadata);
                }

                writer.write(null, new IIOImage(image, null, metadata), param);
            }

            finalImageBytes = baos.toByteArray();
            if (finalImageBytes.length <= maxFileSizeKb * 1024) {
                break; // Target size reached!
            }
            quality -= 0.05f;
        }

        Files.write(outputFile.toPath(), finalImageBytes);
        writer.dispose();
    }

    private static void setDpiMetadata(IIOMetadata metadata) {
        try {
            String format = "javax_imageio_jpeg_image_1.0";
            if (metadata.getNativeMetadataFormatName().equals(format)) {
                Element tree = (Element) metadata.getAsTree(format);
                Element jfif = (Element) tree.getElementsByTagName("app0JFIF").item(0);
                if (jfif != null) {
                    jfif.setAttribute("resUnits", "1");
                    jfif.setAttribute("Xdensity", String.valueOf(TARGET_DPI));
                    jfif.setAttribute("Ydensity", String.valueOf(TARGET_DPI));
                    metadata.setFromTree(format, tree);
                }
            }
        } catch (Exception e) {
            // Silently ignore metadata errors
        }
    }

    private static void writeWebpViaCLI(Path sourceFile, File outputFile, int targetW, int targetH) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                CWEBP_PATH,
                "-q", "75", // 75% quality is excellent for WebP
                "-resize", String.valueOf(targetW), String.valueOf(targetH),
                "-mt", // Use multithreading internally in cwebp
                "-quiet", // Suppress console spam
                sourceFile.toAbsolutePath().toString(),
                "-o", outputFile.getAbsolutePath()
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("cwebp exited with error code: " + exitCode);
        }
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}