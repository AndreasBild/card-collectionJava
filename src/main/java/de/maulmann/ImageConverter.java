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

    private static final int MAX_WIDTH = 1000;
    private static final int MAX_HEIGHT = 700;
    private static final int TARGET_DPI = 72;
    private static final int TARGET_JPG_KB = 140;

    // Atomic counters for thread-safe tracking
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) {
        Path sourceDir = Paths.get("Test");
        Path jpgOutDir = Paths.get("output/output_jpg");
        // Path webpOutDir = Paths.get("path/to/output_webp"); // WEBP DISABLED

        long startTime = System.currentTimeMillis();

        try {
            // processImages(sourceDir, jpgOutDir, webpOutDir); // WEBP DISABLED
            processImages(sourceDir, jpgOutDir);
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

    // public static void processImages(Path sourceDir, Path jpgOutDir, Path webpOutDir) throws IOException, InterruptedException { // WEBP DISABLED
    public static void processImages(Path sourceDir, Path jpgOutDir) throws IOException, InterruptedException {
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
                            // convertAndSaveImage(file, sourceDir, jpgOutDir, webpOutDir); // WEBP DISABLED
                            convertAndSaveImage(file, sourceDir, jpgOutDir);
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

    // private static void convertAndSaveImage(Path sourceFile, Path sourceDir, Path jpgOutDir, Path webpOutDir) throws IOException { // WEBP DISABLED
    private static void convertAndSaveImage(Path sourceFile, Path sourceDir, Path jpgOutDir) throws IOException {
        BufferedImage originalImage = ImageIO.read(sourceFile.toFile());
        if (originalImage == null) {
            throw new IOException("ImageIO returned null (unsupported or corrupted format).");
        }

        // 1. Calculate dimensions
        int origW = originalImage.getWidth();
        int origH = originalImage.getHeight();
        double ratio = Math.min((double) MAX_WIDTH / origW, (double) MAX_HEIGHT / origH);

        int newW = ratio < 1.0 ? (int) (origW * ratio) : origW;
        int newH = ratio < 1.0 ? (int) (origH * ratio) : origH;

        // 2. Resize
        BufferedImage resizedImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(originalImage, 0, 0, newW, newH, null);
        g2d.dispose();

        // 3. Setup paths safely
        Path relativePath = sourceDir.relativize(sourceFile);
        String baseName = getBaseName(relativePath.getFileName().toString());
        Path relativeParent = relativePath.getParent();

        Path currentJpgOutDir = relativeParent != null ? jpgOutDir.resolve(relativeParent) : jpgOutDir;
        // Path currentWebpOutDir = relativeParent != null ? webpOutDir.resolve(relativeParent) : webpOutDir; // WEBP DISABLED

        Files.createDirectories(currentJpgOutDir);
        // Files.createDirectories(currentWebpOutDir); // WEBP DISABLED

        File jpgOutputFile = currentJpgOutDir.resolve(baseName + ".jpg").toFile();
        // File webpOutputFile = currentWebpOutDir.resolve(baseName + ".webp").toFile(); // WEBP DISABLED

        // 4. Write Optimized JPG
        writeJpegOptimized(resizedImage, jpgOutputFile, TARGET_JPG_KB);

        // 5. Write Optimized WebP
        // writeWebpOptimized(resizedImage, webpOutputFile); // WEBP DISABLED
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

                if (param.canWriteProgressive()) {
                    param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
                }

                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
                if (metadata != null && !metadata.isReadOnly()) {
                    setDpiMetadata(metadata);
                }

                writer.write(null, new IIOImage(image, null, metadata), param);
            }

            finalImageBytes = baos.toByteArray();
            if (finalImageBytes.length <= maxFileSizeKb * 1024) {
                break;
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

    /* WEBP DISABLED
    private static void writeWebpOptimized(BufferedImage image, File outputFile) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) throw new IllegalStateException("No WebP writer found.");
        ImageWriter writer = writers.next();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionType(param.getCompressionTypes()[0]);
                param.setCompressionQuality(0.75f);
            }

            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
    */

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}