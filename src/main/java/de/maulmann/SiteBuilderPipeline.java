package de.maulmann;

import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths; // Java's file path utility
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SiteBuilderPipeline {

    private static final String OUTPUT_DIR = "output";
    private static final String IMAGES_DIR = "output/images"; // Pointing to the generated WebP folder
    private static final String BUCKET_NAME = "maulmann.de";
    private static final Region REGION = Region.EU_CENTRAL_1;

    // --- CLOUDFRONT CONFIG ---
    // IMPORTANT: Replace this with your actual CloudFront Distribution ID!
    private static final String CLOUDFRONT_DIST_ID = "E2R4RQKEX6C6Y6";

    // --- CACHE CONTROL CONSTANTS ---
    // 1 Year Cache for Static Assets (Images, CSS, JS)
    private static final String CACHE_LONG = "public, max-age=31536000, immutable";
    // 0 Seconds Cache for HTML & XML (Always force browser to check for new version)
    private static final String CACHE_SHORT = "max-age=0, must-revalidate";

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        System.out.println("==================================================");
        System.out.println("🚀 STARTING MASTER BUILD PIPELINE");
        System.out.println("==================================================");

        // --- UPGRADE: Custom Netty Client to handle massive concurrent uploads ---
        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(500) // Handles huge bursts of file uploads safely
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))
                )
                .build()) {

            // --- PHASE 1: Generate Site HTML ---
            System.out.println("\n[PHASE 1] Generating HTML files...");
            FileGenerator.main(new String[0]);

            // --- PHASE 2: Convert Images to WebP ---
            System.out.println("\n[PHASE 2] Converting images to WebP...");
            ImageConverter.main(new String[0]);

            // --- PHASE 3: Compress & Upload HTML/CSS/JS ---
            System.out.println("\n[PHASE 3] Minifying, Compressing, and Uploading Web Files...");
            processAndUploadWebFiles(s3AsyncClient);

            // --- PHASE 4: Upload Images (No GZIP) ---
            System.out.println("\n[PHASE 4] Syncing Images to S3...");
            processAndUploadImages(s3AsyncClient);

            // --- PHASE 5: Generate, Compress & Upload Sitemap ---
            System.out.println("\n[PHASE 5] Processing Sitemap...");
            processAndUploadSitemap(s3AsyncClient);

            // --- PHASE 6: Invalidate CDN Cache ---
            invalidateCloudFrontCache();

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

                // Strips "output/" so files land in the bucket root
                String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                try {
                    if (fileName.endsWith(".html")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(HTMLMinifier.minifyHTMLToBytes(file.toFile()), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "text/html", CACHE_SHORT, uploadCount));
                    } else if (fileName.endsWith(".css")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(CSSMinifier.minifyCSSToBytes(file.toFile()), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "text/css", CACHE_LONG, uploadCount));
                    } else if (fileName.endsWith(".js")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "application/javascript", CACHE_LONG, uploadCount));
                    } else if (fileName.endsWith(".json")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "application/json", CACHE_SHORT, uploadCount));
                    } else if (fileName.endsWith(".xml")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadFutures.add(uploadBytesAsync(s3Client, s3Key, gzippedData, "application/xml", CACHE_SHORT, uploadCount));
                    } else if (fileName.endsWith(".ico")) {
                        uploadFutures.add(uploadRawFileAsync(s3Client, file, s3Key, "image/x-icon", CACHE_LONG, uploadCount));
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process " + fileName + ": " + e.getMessage());
                }
            });
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        System.out.println("-> Successfully processed and uploaded " + uploadCount.get() + " web files.");
    }

    private static void processAndUploadImages(S3AsyncClient s3Client) throws Exception {
        Path imagesDir = Paths.get(IMAGES_DIR);
        Path outputDir = Paths.get(OUTPUT_DIR); // Needed to strip the prefix

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
                    // Strips "output/" so "output/images/card.webp" becomes "images/card.webp" in S3
                    String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                    uploadFutures.add(uploadRawFileAsync(s3Client, file, s3Key, contentType, CACHE_LONG, uploadCount));
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
                .cacheControl(CACHE_SHORT) // Sitemap must always be fresh for search engines
                .build();

        CompletableFuture<PutObjectResponse> future = s3Client.putObject(
                request,
                AsyncRequestBody.fromFile(sitemapGzFile)
        );

        future.join();
        System.out.println("-> Successfully uploaded sitemap.xml.gz to S3");
    }

    private static void invalidateCloudFrontCache() {
        System.out.println("\n[PHASE 6] Invalidating CloudFront Edge Caches...");

        // Skip if you haven't put your ID in yet
        if (CLOUDFRONT_DIST_ID.equals("YOUR_DISTRIBUTION_ID_HERE")) {
            System.out.println("-> WARNING: CloudFront ID not set. Skipping invalidation.");
            return;
        }

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL) // CloudFront requires the global region
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            // Fully qualified class name to avoid conflict with java.nio.file.Paths
            software.amazon.awssdk.services.cloudfront.model.Paths cfPaths =
                    software.amazon.awssdk.services.cloudfront.model.Paths.builder()
                            .quantity(1)
                            .items("/*")
                            .build();

            InvalidationBatch batch = InvalidationBatch.builder()
                    .paths(cfPaths)
                    .callerReference(String.valueOf(System.currentTimeMillis())) // Unique ID for this specific invalidation
                    .build();

            CreateInvalidationRequest request = CreateInvalidationRequest.builder()
                    .distributionId(CLOUDFRONT_DIST_ID)
                    .invalidationBatch(batch)
                    .build();

            cloudFrontClient.createInvalidation(request);
            System.out.println("-> Successfully requested CloudFront invalidation for '/*'");

        } catch (Exception e) {
            System.err.println("-> WARNING: Failed to invalidate CloudFront: " + e.getMessage());
        }
    }

    // --- Helper: Uploads GZIPPED data directly from RAM with Cache-Control ---
    private static CompletableFuture<Void> uploadBytesAsync(S3AsyncClient s3Client, String s3Key, byte[] data, String contentType, String cacheControl, AtomicInteger counter) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentEncoding("gzip")
                .contentLanguage("en-US")
                .cacheControl(cacheControl)
                .build();

        return s3Client.putObject(request, AsyncRequestBody.fromBytes(data))
                .thenAccept(response -> counter.incrementAndGet())
                .exceptionally(ex -> {
                    System.err.println("Failed to upload " + s3Key + ": " + ex.getMessage());
                    return null;
                });
    }

    // --- Helper: Uploads RAW BINARY files (like WebP) directly from Hard Drive with Cache-Control ---
    private static CompletableFuture<Void> uploadRawFileAsync(S3AsyncClient s3Client, Path localFile, String s3Key, String contentType, String cacheControl, AtomicInteger counter) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .cacheControl(cacheControl)
                .build();

        return s3Client.putObject(request, AsyncRequestBody.fromFile(localFile))
                .thenAccept(response -> counter.incrementAndGet())
                .exceptionally(ex -> {
                    System.err.println("Failed to upload file " + s3Key + ": " + ex.getMessage());
                    return null;
                });
    }

    private static String determineImageContentType(String fileName) {
        // JPGs explicitly ignored. Only modern/standard web formats allowed.
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return null;
    }
}