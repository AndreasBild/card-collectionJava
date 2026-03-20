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

import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SiteBuilderPipeline {

    private static final String OUTPUT_DIR = "output";
    private static final String IMAGES_DIR = "output/images";
    private static final String BUCKET_NAME = "maulmann.de";
    private static final Region REGION = Region.EU_CENTRAL_1;

    // --- CLOUDFRONT CONFIG ---
    private static final String CLOUDFRONT_DIST_ID = "E2R4RQKEX6C6Y6";

    // --- CACHE CONTROL CONSTANTS ---
    private static final String CACHE_LONG = "public, max-age=31536000, immutable";
    private static final String CACHE_SHORT = "max-age=0, must-revalidate";

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        System.out.println("==================================================");
        System.out.println("🚀 STARTING MASTER BUILD PIPELINE");
        System.out.println("==================================================");

        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(500)
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))
                )
                .build()) {

            // Initialisiere den Hash-Cache für Smart-Uploads
            FileTracker tracker = new FileTracker(OUTPUT_DIR + "/sync-hashes.properties");
            TimestampTracker timeTracker = new TimestampTracker(OUTPUT_DIR + "/generation-timestamps.properties");

            // Ensure stable CSS version for hash stability if content didn't change
            SharedTemplates.setBuildId("stable");

            // --- PHASE 1: Generate Site HTML & Sitemap ---
            System.out.println("\n[PHASE 1] Generating HTML files and Sitemap...");
            FileGenerator.setTimestampTracker(timeTracker);
            CardPageGenerator.setTimestampTracker(timeTracker);

            FileGenerator.copyResources();
            FileGenerator.buildCollectionOverview();
            FileGenerator.buildOtherCollections();
            FileGenerator.buildStaticPages();
            CardPageGenerator.run();

            timeTracker.save();
            SitemapGenerator.generate(); // Sitemap & robots.txt now ready for Phase 3

            // --- PHASE 2: Convert Images to WebP ---
            System.out.println("\n[PHASE 2] Converting images to WebP...");
            ImageConverter.main(new String[0]);

            // --- PHASE 3: Compress & Upload HTML/CSS/JS/XML ---
            System.out.println("\n[PHASE 3] Minifying, Compressing, and Uploading Web Files...");
            processAndUploadWebFiles(s3AsyncClient, tracker);

            // --- PHASE 4: Upload Images (No GZIP) ---
            System.out.println("\n[PHASE 4] Syncing Images to S3...");
            processAndUploadImages(s3AsyncClient, tracker);

            // Speichere die neuen Hashes, damit sie beim nächsten Build bekannt sind
            tracker.save();

            // --- PHASE 4.5: Clean up Orphaned Files on S3 ---
            System.out.println("\n[PHASE 4.5] Sweeping S3 for ghost files...");
            cleanOrphanedS3Files(s3AsyncClient);

            // --- PHASE 5: Compress & Upload Sitemap GZ ---
            System.out.println("\n[PHASE 5] Processing Sitemap GZ...");
            processAndUploadSitemapGz(s3AsyncClient, tracker);

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

    private static void processAndUploadWebFiles(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        Path outputDir = Paths.get(OUTPUT_DIR);
        List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString().toLowerCase();
                String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                // Pre-Check: Hat sich die Datei verändert? (Ignoriere sitemap-gz & hashes)
                // sitemap.xml wird in Phase 5 separat als .gz behandelt
                if (fileName.equals("sitemap.xml") || fileName.equals("sitemap.xml.gz") || fileName.equals("sync-hashes.properties")) {
                    return;
                }

                String currentHash = tracker.getHash(file);
                if (!tracker.hasChanged(file)) {
                    skipCount.incrementAndGet();
                    return;
                }

                try {
                    CompletableFuture<Void> uploadTask = null;

                    if (fileName.endsWith(".html")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(HTMLMinifier.minifyHTMLToBytes(file.toFile()), 9);
                        uploadTask = uploadBytesAsync(s3Client, s3Key, gzippedData, "text/html", CACHE_SHORT, uploadCount);
                    } else if (fileName.endsWith(".css")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(CSSMinifier.minifyCSSToBytes(file.toFile()), 9);
                        uploadTask = uploadBytesAsync(s3Client, s3Key, gzippedData, "text/css", CACHE_LONG, uploadCount);
                    } else if (fileName.endsWith(".js")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadTask = uploadBytesAsync(s3Client, s3Key, gzippedData, "application/javascript", CACHE_LONG, uploadCount);
                    } else if (fileName.endsWith(".json")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadTask = uploadBytesAsync(s3Client, s3Key, gzippedData, "application/json", CACHE_SHORT, uploadCount);
                    } else if (fileName.endsWith(".xml")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadTask = uploadBytesAsync(s3Client, s3Key, gzippedData, "application/xml", CACHE_SHORT, uploadCount);
                    } else if (fileName.endsWith(".ico")) {
                        uploadTask = uploadRawFileAsync(s3Client, file, s3Key, "image/x-icon", CACHE_LONG, uploadCount);
                    } else if (fileName.startsWith("robots")) {
                        byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                        uploadTask = uploadBytesAsync(s3Client, s3Key, gzippedData, "text/plain", CACHE_SHORT, uploadCount);
                    }

                    // Nach erfolgreichem Upload: Hash aktualisieren
                    if (uploadTask != null) {
                        uploadFutures.add(uploadTask.thenRun(() -> tracker.updateHash(file, currentHash)));
                    }

                } catch (Exception e) {
                    System.err.println("Failed to process " + fileName + ": " + e.getMessage());
                }
            });
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        System.out.println("-> Uploaded " + uploadCount.get() + " web files. (Skipped " + skipCount.get() + " unmodified files).");
    }

    private static void processAndUploadImages(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        Path imagesDir = Paths.get(IMAGES_DIR);
        Path outputDir = Paths.get(OUTPUT_DIR);

        if (!Files.exists(imagesDir)) {
            System.out.println("-> Images directory not found, skipping phase.");
            return;
        }

        List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        try (Stream<Path> paths = Files.walk(imagesDir)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString().toLowerCase();
                String contentType = determineImageContentType(fileName);

                if (contentType != null) {
                    // Pre-Check: Hat sich das Bild verändert?
                    if (!tracker.hasChanged(file)) {
                        skipCount.incrementAndGet();
                        return;
                    }

                    String s3Key = outputDir.relativize(file).toString().replace("\\", "/");
                    CompletableFuture<Void> uploadTask = uploadRawFileAsync(s3Client, file, s3Key, contentType, CACHE_LONG, uploadCount);

                    // Nach erfolgreichem Upload: Hash eintragen
                    uploadFutures.add(uploadTask.thenRun(() -> tracker.updateHash(file)));
                }
            });
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        System.out.println("-> Synced " + uploadCount.get() + " images. (Skipped " + skipCount.get() + " unmodified images).");
    }

    // ... (cleanOrphanedS3Files bleibt absolut unverändert) ...
    private static void cleanOrphanedS3Files(S3AsyncClient s3Client) {
        try {
            Path localOutputDir = Paths.get(OUTPUT_DIR);
            List<ObjectIdentifier> objectsToDelete = new ArrayList<>();

            System.out.println("    Scanning S3 bucket for pagination...");

            boolean isDone = false;
            String continuationToken = null;
            int totalS3FilesScanned = 0;

            while (!isDone) {
                ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                        .bucket(BUCKET_NAME);

                if (continuationToken != null) {
                    reqBuilder.continuationToken(continuationToken);
                }

                ListObjectsV2Response listRes = s3Client.listObjectsV2(reqBuilder.build()).join();

                for (S3Object s3Object : listRes.contents()) {
                    totalS3FilesScanned++;
                    String s3Key = s3Object.key();

                    if (s3Key.startsWith("cards/") || s3Key.startsWith("images/")) {
                        Path expectedLocalFile = localOutputDir.resolve(s3Key);
                        if (!Files.exists(expectedLocalFile)) {
                            objectsToDelete.add(ObjectIdentifier.builder().key(s3Key).build());
                        }
                    }
                }

                if (listRes.nextContinuationToken() == null) {
                    isDone = true;
                } else {
                    continuationToken = listRes.nextContinuationToken();
                }
            }

            System.out.println("    Finished scanning " + totalS3FilesScanned + " objects in S3.");

            if (!objectsToDelete.isEmpty()) {
                System.out.println("    -> Found " + objectsToDelete.size() + " orphaned files. Deleting from S3 in batches...");

                for (int i = 0; i < objectsToDelete.size(); i += 1000) {
                    int end = Math.min(objectsToDelete.size(), i + 1000);
                    List<ObjectIdentifier> batch = objectsToDelete.subList(i, end);

                    DeleteObjectsRequest deleteReq = DeleteObjectsRequest.builder()
                            .bucket(BUCKET_NAME)
                            .delete(Delete.builder().objects(batch).build())
                            .build();

                    s3Client.deleteObjects(deleteReq).join();
                    System.out.println("       Deleted batch of " + batch.size() + " files.");
                }
                System.out.println("    -> S3 Cleanup complete.");
            } else {
                System.out.println("    -> S3 is perfectly in sync. No ghost files found.");
            }

        } catch (Exception e) {
            System.err.println("-> WARNING: Failed to clean orphaned S3 files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processAndUploadSitemapGz(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
        File sitemapGzFile = new File(OUTPUT_DIR + "/sitemap.xml.gz");

        if (!sitemapFile.exists()) {
            System.err.println("-> WARNING: sitemap.xml not found. Skipping GZ upload.");
            return;
        }

        // Wir prüfen hier, ob die sitemap.xml sich geändert hat.
        // Falls ja, generieren wir die .gz neu und laden sie hoch.
        if (!tracker.hasChanged(sitemapFile.toPath()) && sitemapGzFile.exists()) {
            System.out.println("-> Sitemap unchanged. Skipping GZ upload.");
            return;
        }

        GZIPCompressor.compressFile(sitemapFile, sitemapGzFile, 9);
        System.out.println("-> Compressed sitemap to sitemap.xml.gz");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key("sitemap.xml.gz")
                .contentType("application/xml")
                .contentEncoding("gzip")
                .cacheControl(CACHE_SHORT)
                .build();

        s3Client.putObject(request, AsyncRequestBody.fromFile(sitemapGzFile)).join();

        tracker.updateHash(sitemapFile.toPath());
        System.out.println("-> Successfully uploaded sitemap.xml.gz to S3");
    }

    private static void invalidateCloudFrontCache() {
        System.out.println("\n[PHASE 6] Invalidating CloudFront Edge Caches...");

        if (CLOUDFRONT_DIST_ID.equals("YOUR_DISTRIBUTION_ID_HERE")) {
            System.out.println("-> WARNING: CloudFront ID not set. Skipping invalidation.");
            return;
        }

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            software.amazon.awssdk.services.cloudfront.model.Paths cfPaths =
                    software.amazon.awssdk.services.cloudfront.model.Paths.builder()
                            .quantity(1)
                            .items("/*")
                            .build();

            InvalidationBatch batch = InvalidationBatch.builder()
                    .paths(cfPaths)
                    .callerReference(String.valueOf(System.currentTimeMillis()))
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
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return null;
    }

}