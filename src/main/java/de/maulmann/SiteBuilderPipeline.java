package de.maulmann;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SiteBuilderPipeline {

    private static final String OUTPUT_DIR = "output";
    private static final String IMAGES_DIR = "images"; // Your local images folder
    private static final String BUCKET_NAME = "maulmann.de";
    private static final Region REGION = Region.EU_CENTRAL_1;

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        System.out.println("==================================================");
        System.out.println("🚀 STARTING MASTER BUILD PIPELINE");
        System.out.println("==================================================");

        // Using DefaultCredentialsProvider for GitHub Actions compatibility
        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            // --- PHASE 1: Generate Site ---
            System.out.println("\n[PHASE 1] Generating HTML files...");
            FileGenerator.main(new String[0]);

            // --- PHASE 2: Compress & Upload HTML/CSS ---
            System.out.println("\n[PHASE 2] Minifying, Compressing, and Uploading HTML/CSS...");
            processAndUploadWebFiles(s3AsyncClient);

            // --- PHASE 3: Upload Images (No GZIP) ---
            System.out.println("\n[PHASE 3] Syncing Images to S3...");
            processAndUploadImages(s3AsyncClient);

            // --- PHASE 4: Generate, Compress & Upload Sitemap ---
            System.out.println("\n[PHASE 4] Processing Sitemap...");
            processAndUploadSitemap(s3AsyncClient);

            long pipelineEnd = System.currentTimeMillis();
            System.out.println("\n==================================================");
            System.out.println("✅ PIPELINE COMPLETE IN " + (pipelineEnd - pipelineStart) + " ms");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("\n❌ PIPELINE FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processAndUploadWebFiles(S3AsyncClient s3Client) throws Exception {
        Path outputDir = Paths.get(OUTPUT_DIR);
        List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);

        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString().toLowerCase();

                // We strip "output/" from the beginning of the S3 key so it sits at the root of the bucket
                String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                try {
                    if (fileName.endsWith(".html")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(HTMLMinifier.minifyHTMLToBytes(file.toFile()), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "text/html", uploadCount));
                    } else if (fileName.endsWith(".css")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(CSSMinifier.minifyCSSToBytes(file.toFile()), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "text/css", uploadCount));
                    } else if (fileName.endsWith(".js")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "application/javascript", uploadCount));
                    } else if (fileName.endsWith(".json")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "application/json", uploadCount));
                    } else if (fileName.endsWith(".xml")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "application/xml", uploadCount));
                    } else if (fileName.endsWith(".ico")) {
                        uploadFutures.add(uploadRawFileAsync(s3Client, file, s3Key, "image/x-icon", uploadCount));
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process " + fileName + ": " + e.getMessage());
                }
            });
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        System.out.println("-> Successfully processed and uploaded " + uploadCount.get() + " HTML/CSS files.");
    }

    private static void processAndUploadImages(S3AsyncClient s3Client) throws Exception {
        Path imagesDir = Paths.get(IMAGES_DIR);
        if (!Files.exists(imagesDir)) {
            System.out.println("-> Images directory not found, skipping phase.");
            return;
        }

        List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);

        try (Stream<Path> paths = Files.walk(imagesDir)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString().toLowerCase();
                String contentType = determineImageContentType(fileName);

                if (contentType != null) {
                    // For images, the S3 key should include the "images/" folder prefix
                    String s3Key = file.toString().replace("\\", "/");
                    uploadFutures.add(uploadRawFileAsync(s3Client, file, s3Key, contentType, uploadCount));
                }
            });
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        System.out.println("-> Successfully synced " + uploadCount.get() + " images.");
    }

    private static void processAndUploadSitemap(S3AsyncClient s3Client) throws Exception {
        SitemapGenerator.generate();

        File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
        File sitemapGzFile = new File(OUTPUT_DIR + "/sitemap.xml.gz");

        if (!sitemapFile.exists()) throw new Exception("Sitemap was not generated properly.");

        GZIPCompressor.compressFile(sitemapFile, sitemapGzFile, 9);
        System.out.println("-> Compressed sitemap to sitemap.xml.gz");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key("sitemap.xml.gz")
                .contentType("application/xml")
                .contentEncoding("gzip")
                .build();

        CompletableFuture<PutObjectResponse> future = s3Client.putObject(
                request,
                AsyncRequestBody.fromFile(sitemapGzFile)
        );

        future.join();
        System.out.println("-> Successfully uploaded sitemap.xml.gz to S3");
    }

    // --- Helper: Uploads GZIPPED data directly from RAM ---
    private static CompletableFuture<Void> uploadBytesAsync(S3AsyncClient s3Client, String s3Key, byte[] data, String contentType, AtomicInteger counter) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentEncoding("gzip") // CRITICAL: Tells browser to decompress
                .contentLanguage("en-US")
                .build();

        return s3Client.putObject(request, AsyncRequestBody.fromBytes(data))
                .thenAccept(response -> counter.incrementAndGet())
                .exceptionally(ex -> {
                    System.err.println("Failed to upload " + s3Key + ": " + ex.getMessage());
                    return null;
                });
    }

    // --- Helper: Uploads RAW BINARY files directly from the Hard Drive ---
    private static CompletableFuture<Void> uploadRawFileAsync(S3AsyncClient s3Client, Path localFile, String s3Key, String contentType, AtomicInteger counter) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                // NOTICE: No contentEncoding("gzip") here!
                .build();

        return s3Client.putObject(request, AsyncRequestBody.fromFile(localFile))
                .thenAccept(response -> counter.incrementAndGet())
                .exceptionally(ex -> {
                    System.err.println("Failed to upload image " + s3Key + ": " + ex.getMessage());
                    return null;
                });
    }

    private static String determineImageContentType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return null; // Ignore unknown files (like hidden OS files)
    }
}