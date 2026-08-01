package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;

import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.StorageClass;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final Logger log = LoggerFactory.getLogger(SiteBuilderPipeline.class);

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        log.info("==================================================");
        log.info("🚀 STARTING MASTER BUILD PIPELINE");
        log.info("==================================================");

        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(500)
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))
                )
                .build()) {

            // Initialisiere den Hash-Cache für Smart-Uploads
            FileTracker tracker = new FileTracker(OUTPUT_DIR + "/sync-hashes.properties");
            TimestampTracker timeTracker = new TimestampTracker(OUTPUT_DIR + "/generation-timestamps.properties");

            // Ensure stable CSS version for hash stability if content didn't change
            String cssHash = tracker.getHash(Paths.get("src/main/resources/css/main.css"));
            if (cssHash != null && cssHash.length() >= 8) {
                SharedTemplates.setBuildId(cssHash.substring(0, 8));
            } else {
                SharedTemplates.setBuildId("stable");
            }

            // --- PARALLEL PHASES: HTML Generation & Image WebP Conversion ---
            log.info("\n[PHASE 1 & 2] Launching HTML Generation and Image WebP Conversion in parallel...");

            try (ExecutorService phaseExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                CompletableFuture<Void> htmlTask = CompletableFuture.runAsync(() -> {
                    log.info("  -> [PHASE 1] Generating HTML files and Sitemap...");
                    FileGenerator.setTimestampTracker(timeTracker);
                    CardPageGenerator.setTimestampTracker(timeTracker);
                    SitemapGenerator.setTimestampTracker(timeTracker);

                    FileGenerator.copyResources();
                    FileGenerator.buildCollectionOverview();
                    FileGenerator.buildOtherCollections();
                    FileGenerator.buildStaticPages();
                    CardPageGenerator.run();

                    timeTracker.save();
                    SitemapGenerator.generate(); // Sitemap & robots.txt now ready for Phase 3

                    // --- PHASE 1.5: Inject Firestore Ratings ---
                    log.info("  -> [PHASE 1.5] Injecting Firestore ratings...");
                    String firebaseCreds = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
                    File firebaseFile = new File("firebase/maulmann-3f90d-firebase-adminsdk-fbsvc-78c9f10838");

                    if ((firebaseCreds == null || firebaseCreds.isEmpty()) && !firebaseFile.exists()) {
                        log.error("⚠️ WARNING: Firebase credentials (env or file) are missing! Ratings will NOT be injected.");
                    } else {
                        FirestoreRatingInjector.main(new String[0]);
                    }
                }, phaseExecutor);

                CompletableFuture<Void> imageTask = CompletableFuture.runAsync(() -> {
                    log.info("  -> [PHASE 2] Converting images to WebP...");
                    ImageConverter.main(new String[0]);
                }, phaseExecutor);

                // Wait for both tasks to complete concurrently
                CompletableFuture.allOf(htmlTask, imageTask).join();
            }

            try {
                // --- PHASE 3: Compress & Upload HTML/CSS/JS/XML ---
                log.info("\n[PHASE 3] Minifying, Compressing, and Uploading Web Files...");
                processAndUploadWebFiles(s3AsyncClient, tracker);

                // --- PHASE 4: Upload Images (No Compression) ---
                log.info("\n[PHASE 4] Syncing Images to S3...");
                processAndUploadImages(s3AsyncClient, tracker);

                // Speichere die neuen Hashes, damit sie beim nächsten Build bekannt sind
                tracker.save();

                // --- PHASE 4.5: Clean up Orphaned Files on S3 ---
                log.info("\n[PHASE 4.5] Sweeping S3 for ghost files...");
                cleanOrphanedS3Files(s3AsyncClient);

                // --- PHASE 5: Compress & Upload Sitemap GZ ---
                log.info("\n[PHASE 5] Processing Sitemap GZ...");
                processAndUploadSitemapGz(s3AsyncClient, tracker);

                // --- PHASE 6: Invalidate CDN Cache ---
                invalidateCloudFrontCache();
            } catch (Exception e) {
                if (e.toString().contains("SdkClientException") || (e.getCause() != null && e.getCause().toString().contains("SdkClientException"))) {
                    log.info("ℹ️ Local build: AWS credentials not found. Skipping S3 upload phases.");
                } else {
                    throw e;
                }
            }

            long pipelineEnd = System.currentTimeMillis();
            log.info("==================================================");
            log.info("✅ PIPELINE COMPLETE IN {} ms", pipelineEnd - pipelineStart);
            log.info("==================================================");

        } catch (Exception e) {
            log.error("\n❌ PIPELINE FAILED: {}", e.getMessage());
        }
    }

    private static void processAndUploadWebFiles(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        Path outputDir = Paths.get(OUTPUT_DIR);
        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        final int BROTLI_FAST_QUALITY = 9;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try (Stream<Path> paths = Files.walk(outputDir)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                    if (fileName.equals("sitemap.xml") || fileName.equals("sitemap.xml.gz") || fileName.endsWith(".properties")) {
                        return;
                    }

                    String currentHash = tracker.getHash(file);
                    String storedHash = tracker.getStoredHash(file);
                    if (currentHash != null && currentHash.equals(storedHash)) {
                        skipCount.incrementAndGet();
                        return;
                    }

                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";
                            switch (ext) {
                                case "html" -> {
                                    byte[] brData = BrotliCompressor.compressBytes(HTMLMinifier.minifyHTMLToBytes(file.toFile()), BROTLI_FAST_QUALITY);
                                    uploadBytes(s3Client, s3Key, brData, "text/html", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                case "css" -> {
                                    byte[] brData = BrotliCompressor.compressBytes(CSSMinifier.minifyCSSToBytes(file.toFile()), BROTLI_FAST_QUALITY);
                                    uploadBytes(s3Client, s3Key, brData, "text/css", "br", CACHE_LONG, uploadCount, tracker, file, currentHash);
                                }
                                case "js" -> {
                                    String cacheControl = fileName.equalsIgnoreCase("serviceworker.js") ? CACHE_SHORT : CACHE_LONG;
                                    byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BROTLI_FAST_QUALITY);
                                    uploadBytes(s3Client, s3Key, brData, "application/javascript", "br", cacheControl, uploadCount, tracker, file, currentHash);
                                }
                                case "json" -> {
                                    byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BROTLI_FAST_QUALITY);
                                    uploadBytes(s3Client, s3Key, brData, "application/json", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                case "xml", "xsl" -> {
                                    byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BROTLI_FAST_QUALITY);
                                    uploadBytes(s3Client, s3Key, brData, "application/xml", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                case "ico" -> uploadRawFile(s3Client, file, s3Key, "image/x-icon", CACHE_LONG, uploadCount, tracker, currentHash);
                                case "txt" -> {
                                    byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BROTLI_FAST_QUALITY);
                                    uploadBytes(s3Client, s3Key, brData, "text/plain", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                default -> {}
                            }
                        } catch (Exception e) {
                            if (!e.toString().contains("SdkClientException") && (e.getCause() == null || !e.getCause().toString().contains("SdkClientException"))) {
                                log.error("Failed to process {}: {}", fileName, e.getMessage());
                            }
                        }
                    }, executor));
                });
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        log.info("-> Uploaded {} web files. (Skipped {} unmodified files).", uploadCount.get(), skipCount.get());
    }

    private static void processAndUploadImages(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        Path imagesDir = Paths.get(IMAGES_DIR);
        Path outputDir = Paths.get(OUTPUT_DIR);

        if (!Files.exists(imagesDir)) {
            log.info("-> Images directory not found, skipping phase.");
            return;
        }

        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try (Stream<Path> paths = Files.walk(imagesDir)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    String contentType = determineImageContentType(fileName);

                    if (contentType != null) {
                        String currentHash = tracker.getHash(file);
                        String storedHash = tracker.getStoredHash(file);
                        if (currentHash != null && currentHash.equals(storedHash)) {
                            skipCount.incrementAndGet();
                            return;
                        }

                        String s3Key = outputDir.relativize(file).toString().replace("\\", "/");
                        futures.add(CompletableFuture.runAsync(() -> {
                            try {
                                uploadRawFile(s3Client, file, s3Key, contentType, CACHE_LONG, uploadCount, tracker, currentHash);
                            } catch (Exception e) {
                                if (!e.toString().contains("SdkClientException") && (e.getCause() == null || !e.getCause().toString().contains("SdkClientException"))) {
                                    log.error("Failed to upload image {}: {}", fileName, e.getMessage());
                                }
                            }
                        }, executor));
                    }
                });
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        log.info("-> Synced {} images. (Skipped {} unmodified images).", uploadCount.get(), skipCount.get());
    }

    private static void cleanOrphanedS3Files(S3AsyncClient s3Client) {
        try {
            Path localOutputDir = Paths.get(OUTPUT_DIR);
            List<ObjectIdentifier> objectsToDelete = new ArrayList<>();

            log.info("-> Scanning S3 bucket for pagination...");

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

                    if (s3Key.endsWith("-hashes.properties") || s3Key.endsWith("-timestamps.properties")) {
                        continue;
                    }

                    Path expectedLocalFile = localOutputDir.resolve(s3Key);
                    if (!Files.exists(expectedLocalFile)) {
                        objectsToDelete.add(ObjectIdentifier.builder().key(s3Key).build());
                    }
                }

                if (listRes.nextContinuationToken() == null) {
                    isDone = true;
                } else {
                    continuationToken = listRes.nextContinuationToken();
                }
            }

            log.info("-> Finished scanning {} objects in S3.", totalS3FilesScanned);

            if (!objectsToDelete.isEmpty()) {
                log.info("-> Found {} orphaned files. Deleting from S3 in batches...", objectsToDelete.size());

                objectsToDelete.stream()
                        .gather(java.util.stream.Gatherers.windowFixed(1000))
                        .forEach(batch -> {
                            DeleteObjectsRequest deleteReq = DeleteObjectsRequest.builder()
                                    .bucket(BUCKET_NAME)
                                    .delete(Delete.builder().objects(batch).build())
                                    .build();

                            s3Client.deleteObjects(deleteReq).join();
                            log.info("       Deleted batch of {} files.", batch.size());
                        });
                log.info("-> S3 Cleanup complete.");
            } else {
                log.info("-> S3 is perfectly in sync. No ghost files found.");
            }

        } catch (Exception e) {
            log.error("-> WARNING: Failed to clean orphaned S3 files: {}", e.getMessage());
        }
    }

    private static void processAndUploadSitemapGz(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
        File sitemapGzFile = new File(OUTPUT_DIR + "/sitemap.xml.gz");

        if (!sitemapFile.exists()) {
            log.error("-> WARNING: sitemap.xml not found. Skipping Sitemap upload.");
            return;
        }

        if (!tracker.hasChanged(sitemapFile.toPath()) && sitemapGzFile.exists()) {
            log.info("-> Sitemap unchanged. Skipping upload.");
            return;
        }

        // 1. Upload uncompressed sitemap.xml
        PutObjectRequest xmlReq = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key("sitemap.xml")
                .contentType("application/xml; charset=utf-8")
                .cacheControl(CACHE_SHORT)
                .build();
        s3Client.putObject(xmlReq, AsyncRequestBody.fromFile(sitemapFile)).join();
        log.info("-> Successfully uploaded sitemap.xml to S3");

        // 2. Compress & upload sitemap.xml.gz
        GZIPCompressor.compressFile(sitemapFile, sitemapGzFile, 9);
        log.info("-> Compressed sitemap to sitemap.xml.gz");

        PutObjectRequest gzReq = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key("sitemap.xml.gz")
                .contentType("application/xml")
                .contentEncoding("gzip")
                .cacheControl(CACHE_SHORT)
                .build();

        s3Client.putObject(gzReq, AsyncRequestBody.fromFile(sitemapGzFile)).join();
        log.info("-> Successfully uploaded sitemap.xml.gz to S3");

        tracker.updateHash(sitemapFile.toPath());
    }

    private static void invalidateCloudFrontCache() {
        log.info("\n[PHASE 6] Invalidating CloudFront Edge Caches...");

        if (CLOUDFRONT_DIST_ID.equals("YOUR_DISTRIBUTION_ID_HERE")) {
            log.info("-> WARNING: CloudFront ID not set. Skipping invalidation.");
            return;
        }

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
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
            log.info("-> Successfully requested CloudFront invalidation for '/*'");

        } catch (Exception e) {
            log.error("-> WARNING: Failed to invalidate CloudFront: {}", e.getMessage());
        }
    }

    private static void uploadBytes(S3AsyncClient s3Client, String s3Key, byte[] data, String contentType, String contentEncoding, String cacheControl, AtomicInteger counter, FileTracker tracker, Path localFile, String preCalculatedHash) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentEncoding(contentEncoding)
                .contentLanguage("en-US")
                .cacheControl(cacheControl)
                .build();

        s3Client.putObject(request, AsyncRequestBody.fromBytes(data)).join();
        counter.incrementAndGet();
        if (tracker != null && localFile != null) {
            tracker.updateHash(localFile, preCalculatedHash);
        }
    }

    private static void uploadRawFile(S3AsyncClient s3Client, Path localFile, String s3Key, String contentType, String cacheControl, AtomicInteger counter, FileTracker tracker, String preCalculatedHash) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .cacheControl(cacheControl)
                .storageClass(StorageClass.INTELLIGENT_TIERING)
                .build();

        s3Client.putObject(request, AsyncRequestBody.fromFile(localFile)).join();
        counter.incrementAndGet();
        if (tracker != null && localFile != null) {
            tracker.updateHash(localFile, preCalculatedHash);
        }
    }

    private static String determineImageContentType(String fileName) {
        if (fileName.endsWith(".avif")) return "image/avif";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return null;
    }

}