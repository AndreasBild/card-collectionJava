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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

    // --- BEST-PRACTICE SECURITY HEADERS ---
    private static final Map<String, String> SECURITY_METADATA = Map.of(
            "x-content-type-options", "nosniff",
            "x-frame-options", "SAMEORIGIN",
            "referrer-policy", "strict-origin-when-cross-origin",
            "strict-transport-security", "max-age=63072000; includeSubDomains; preload"
    );

    public static class DeploymentMetrics {
        public long phase1_2Ms = 0;
        public long phase3Ms = 0;
        public long phase4Ms = 0;
        public long phase45Ms = 0;
        public long phase5Ms = 0;
        public long phase6Ms = 0;
        public long phase7Ms = 0;

        public final AtomicInteger totalCards = new AtomicInteger(0);
        public final AtomicInteger webFilesUploaded = new AtomicInteger(0);
        public final AtomicInteger webFilesSkipped = new AtomicInteger(0);
        public final AtomicLong rawWebBytes = new AtomicLong(0);
        public final AtomicLong compressedWebBytes = new AtomicLong(0);

        public final AtomicInteger imagesUploaded = new AtomicInteger(0);
        public final AtomicInteger imagesSkipped = new AtomicInteger(0);
        public final AtomicLong imageBytes = new AtomicLong(0);

        public final AtomicInteger sitemapsUploaded = new AtomicInteger(0);
        public final AtomicInteger orphansSwept = new AtomicInteger(0);
    }

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        log.info("==================================================");
        log.info("🚀 STARTING MASTER BUILD PIPELINE");
        log.info("==================================================");

        DeploymentMetrics metrics = new DeploymentMetrics();

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

            long p1_2Start = System.currentTimeMillis();
            List<CardPageGenerator.CardData> cards = buildLocalArtifacts(timeTracker, tracker);
            metrics.phase1_2Ms = System.currentTimeMillis() - p1_2Start;
            if (cards != null) {
                metrics.totalCards.set(cards.size());
            }

            boolean hasAwsCredentials = false;
            try {
                DefaultCredentialsProvider.builder().build().resolveCredentials();
                hasAwsCredentials = true;
            } catch (Exception _) {
                log.info("ℹ️ Local build: AWS credentials not found. Skipping S3 upload & compression phases.");
            }

            if (hasAwsCredentials) {
                try {
                    // --- PHASE 3: Compress & Upload HTML/CSS/JS/XML ---
                    log.info("\n[PHASE 3] Minifying, Compressing, and Uploading Web Files...");
                    long p3Start = System.currentTimeMillis();
                    processAndUploadWebFiles(s3AsyncClient, tracker, metrics);
                    metrics.phase3Ms = System.currentTimeMillis() - p3Start;

                    // --- PHASE 4: Upload Images (No Compression) ---
                    log.info("\n[PHASE 4] Syncing Images to S3...");
                    long p4Start = System.currentTimeMillis();
                    processAndUploadImages(s3AsyncClient, tracker, metrics);
                    metrics.phase4Ms = System.currentTimeMillis() - p4Start;

                    // Speichere die neuen Hashes, damit sie beim nächsten Build bekannt sind
                    tracker.save();

                    // --- PHASE 4.5: Clean up Orphaned Files on S3 ---
                    log.info("\n[PHASE 4.5] Sweeping S3 for ghost files...");
                    long p45Start = System.currentTimeMillis();
                    cleanOrphanedS3Files(s3AsyncClient, metrics);
                    metrics.phase45Ms = System.currentTimeMillis() - p45Start;

                    // --- PHASE 5: Compress & Upload Sitemap GZ ---
                    log.info("\n[PHASE 5] Processing Sitemap GZ...");
                    long p5Start = System.currentTimeMillis();
                    processAndUploadSitemapGz(s3AsyncClient, tracker, metrics);
                    metrics.phase5Ms = System.currentTimeMillis() - p5Start;

                    // --- PHASE 6: Invalidate CDN Cache ---
                    long p6Start = System.currentTimeMillis();
                    invalidateCloudFrontCache();
                    metrics.phase6Ms = System.currentTimeMillis() - p6Start;
                } catch (Exception e) {
                    if (e.toString().contains("SdkClientException") || (e.getCause() != null && e.getCause().toString().contains("SdkClientException"))) {
                        log.info("ℹ️ Local build: AWS credentials not found. Skipping S3 upload phases.");
                    } else {
                        throw e;
                    }
                } finally {
                    tracker.save();
                }
            } else {
                tracker.save();
            }

            // --- PHASE 7: Notify IndexNow API ---
            log.info("\n[PHASE 7] Submitting updated card URLs to IndexNow API...");
            long p7Start = System.currentTimeMillis();
            try {
                CompletableFuture<Void> indexNowFuture = IndexNowService.flushQueueAsync();
                if (indexNowFuture != null) {
                    indexNowFuture.join();
                }
            } catch (Exception e) {
                log.error("⚠️ IndexNow submission error: {}", e.getMessage());
            } finally {
                IndexNowService.shutdown();
                metrics.phase7Ms = System.currentTimeMillis() - p7Start;
            }

            long totalDuration = System.currentTimeMillis() - pipelineStart;
            printDeploymentReport(totalDuration, metrics, hasAwsCredentials);

        } catch (Exception e) {
            log.error("\n❌ PIPELINE FAILED: {}", e.getMessage());
        }
    }

    private static void processAndUploadWebFiles(S3AsyncClient s3Client, FileTracker tracker, DeploymentMetrics metrics) throws Exception {
        Path outputDir = Paths.get(OUTPUT_DIR);
        AtomicInteger uploadCount = metrics.webFilesUploaded;
        AtomicInteger skipCount = metrics.webFilesSkipped;
        final int BROTLI_FAST_QUALITY = 9;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try (Stream<Path> paths = Files.walk(outputDir)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                    if (fileName.equalsIgnoreCase(".ds_store") || (fileName.startsWith("sitemap") && fileName.endsWith(".xml")) || fileName.endsWith(".xml.gz") || fileName.endsWith(".properties")) {
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
                                    byte[] rawHtml = HTMLMinifier.minifyHTMLToBytes(file.toFile());
                                    metrics.rawWebBytes.addAndGet(rawHtml.length);
                                    byte[] brData = BrotliCompressor.compressBytes(rawHtml, BROTLI_FAST_QUALITY);
                                    metrics.compressedWebBytes.addAndGet(brData.length);
                                    uploadBytes(s3Client, s3Key, brData, "text/html; charset=utf-8", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                case "css" -> {
                                    byte[] rawCss = CSSMinifier.minifyCSSToBytes(file.toFile());
                                    metrics.rawWebBytes.addAndGet(rawCss.length);
                                    byte[] brData = BrotliCompressor.compressBytes(rawCss, BROTLI_FAST_QUALITY);
                                    metrics.compressedWebBytes.addAndGet(brData.length);
                                    uploadBytes(s3Client, s3Key, brData, "text/css; charset=utf-8", "br", CACHE_LONG, uploadCount, tracker, file, currentHash);
                                }
                                case "js" -> {
                                    String cacheControl = fileName.equalsIgnoreCase("serviceworker.js") ? CACHE_SHORT : CACHE_LONG;
                                    byte[] rawJs = Files.readAllBytes(file);
                                    metrics.rawWebBytes.addAndGet(rawJs.length);
                                    byte[] brData = BrotliCompressor.compressBytes(rawJs, BROTLI_FAST_QUALITY);
                                    metrics.compressedWebBytes.addAndGet(brData.length);
                                    uploadBytes(s3Client, s3Key, brData, "application/javascript; charset=utf-8", "br", cacheControl, uploadCount, tracker, file, currentHash);
                                }
                                case "json" -> {
                                    byte[] rawJson = Files.readAllBytes(file);
                                    metrics.rawWebBytes.addAndGet(rawJson.length);
                                    byte[] brData = BrotliCompressor.compressBytes(rawJson, BROTLI_FAST_QUALITY);
                                    metrics.compressedWebBytes.addAndGet(brData.length);
                                    uploadBytes(s3Client, s3Key, brData, "application/json; charset=utf-8", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                case "xml", "xsl" -> {
                                    byte[] rawXml = Files.readAllBytes(file);
                                    metrics.rawWebBytes.addAndGet(rawXml.length);
                                    byte[] brData = BrotliCompressor.compressBytes(rawXml, BROTLI_FAST_QUALITY);
                                    metrics.compressedWebBytes.addAndGet(brData.length);
                                    uploadBytes(s3Client, s3Key, brData, "application/xml; charset=utf-8", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                                }
                                case "ico" -> {
                                    metrics.rawWebBytes.addAndGet(Files.size(file));
                                    metrics.compressedWebBytes.addAndGet(Files.size(file));
                                    uploadRawFile(s3Client, file, s3Key, "image/x-icon", CACHE_LONG, uploadCount, tracker, currentHash);
                                }
                                case "txt" -> {
                                    metrics.rawWebBytes.addAndGet(Files.size(file));
                                    metrics.compressedWebBytes.addAndGet(Files.size(file));
                                    uploadRawFile(s3Client, file, s3Key, "text/plain; charset=utf-8", CACHE_SHORT, uploadCount, tracker, currentHash);
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
        if (tracker != null) {
            tracker.save();
        }
    }

    private static void processAndUploadImages(S3AsyncClient s3Client, FileTracker tracker, DeploymentMetrics metrics) throws Exception {
        Path imagesDir = Paths.get(IMAGES_DIR);
        Path outputDir = Paths.get(OUTPUT_DIR);

        if (!Files.exists(imagesDir)) {
            log.info("-> Images directory not found, skipping phase.");
            return;
        }

        AtomicInteger uploadCount = metrics.imagesUploaded;
        AtomicInteger skipCount = metrics.imagesSkipped;

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
                                metrics.imageBytes.addAndGet(Files.size(file));
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
        if (tracker != null) {
            tracker.save();
        }
    }

    private static void cleanOrphanedS3Files(S3AsyncClient s3Client, DeploymentMetrics metrics) {
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
                metrics.orphansSwept.set(objectsToDelete.size());

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

    private static void processAndUploadSitemapGz(S3AsyncClient s3Client, FileTracker tracker, DeploymentMetrics metrics) throws Exception {
        Path outputDir = Paths.get(OUTPUT_DIR);
        List<File> sitemapFiles = new ArrayList<>();

        if (Files.exists(outputDir)) {
            try (Stream<Path> paths = Files.walk(outputDir)) {
                paths.filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .filter(f -> f.getName().startsWith("sitemap") && f.getName().endsWith(".xml"))
                        .forEach(sitemapFiles::add);
            }
        }

        if (sitemapFiles.isEmpty()) {
            log.error("-> WARNING: No sitemap XML files found. Skipping Sitemap upload.");
            return;
        }

        AtomicInteger count = metrics.sitemapsUploaded;

        for (File sitemapFile : sitemapFiles) {
            String s3Key = sitemapFile.getName();
            String currentHash = tracker.getHash(sitemapFile.toPath());

            uploadRawFile(s3Client, sitemapFile.toPath(), s3Key, "application/xml; charset=utf-8", CACHE_SHORT, count, tracker, currentHash);
            log.info("-> Uploaded sitemap {} to S3", s3Key);
        }
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
        if (tracker != null && localFile != null) {
            tracker.updateHash(localFile, preCalculatedHash);
        }

        if (s3Client == null) return;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentEncoding(contentEncoding)
                .contentLanguage("en-US")
                .cacheControl(cacheControl)
                .metadata(SECURITY_METADATA)
                .build();

        s3Client.putObject(request, AsyncRequestBody.fromBytes(data)).join();
        counter.incrementAndGet();
    }

    private static void uploadRawFile(S3AsyncClient s3Client, Path localFile, String s3Key, String contentType, String cacheControl, AtomicInteger counter, FileTracker tracker, String preCalculatedHash) {
        if (tracker != null && localFile != null) {
            tracker.updateHash(localFile, preCalculatedHash);
        }

        if (s3Client == null) return;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentLanguage("en-US")
                .cacheControl(cacheControl)
                .metadata(SECURITY_METADATA)
                .storageClass(StorageClass.INTELLIGENT_TIERING)
                .build();

        s3Client.putObject(request, AsyncRequestBody.fromFile(localFile)).join();
        counter.incrementAndGet();
    }

    public static void printDeploymentReport(long totalMs, DeploymentMetrics metrics, boolean isProd) {
        long rawWeb = metrics.rawWebBytes.get();
        long compWeb = metrics.compressedWebBytes.get();
        long imgBytes = metrics.imageBytes.get();
        long totalRaw = rawWeb + imgBytes;
        long totalTransferred = compWeb + imgBytes;
        long bytesSaved = Math.max(0, rawWeb - compWeb);
        double savingPct = rawWeb > 0 ? ((double) bytesSaved / rawWeb) * 100.0 : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================================================================================\n");
        sb.append("📊 ").append(isProd ? "PRODUCTION DEPLOYMENT & METRICS REPORT" : "LOCAL BUILD & METRICS REPORT").append("\n");
        sb.append("================================================================================\n");
        sb.append("🎴 Cards & Content Assets:\n");
        sb.append(String.format("   • Total Cards Processed:      %,d%n", metrics.totalCards.get()));
        sb.append(String.format("   • Generation & Conversion:    %,d ms%n", metrics.phase1_2Ms));
        sb.append("\n");
        if (isProd) {
            sb.append("📦 AWS S3 Cloud Sync & Data Transfer:\n");
            sb.append(String.format("   • Web Assets (HTML/CSS/JS):   %,d uploaded (%,d skipped - cached)%n", metrics.webFilesUploaded.get(), metrics.webFilesSkipped.get()));
            sb.append(String.format("   • Images (AVIF / Scans):      %,d uploaded (%,d skipped - cached)%n", metrics.imagesUploaded.get(), metrics.imagesSkipped.get()));
            sb.append(String.format("   • Sitemaps & Feeds:           %,d uploaded%n", metrics.sitemapsUploaded.get()));
            sb.append(String.format("   • Ghost Files Swept:          %,d removed%n", metrics.orphansSwept.get()));
            sb.append(String.format("   • Raw Web File Volume:        %,.1f KB%n", rawWeb / 1024.0));
            sb.append(String.format("   • Transferred Web Volume:     %,.1f KB%n", compWeb / 1024.0));
            sb.append(String.format("   • Brotli Bandwidth Saved:     %,.1f KB (%.1f%% reduction)%n", bytesSaved / 1024.0, savingPct));
            sb.append(String.format("   • Total S3 Volume Transferred: %,.1f KB%n", totalTransferred / 1024.0));
            sb.append("\n");
            sb.append("⏱️ Phase Execution Breakdown:\n");
            sb.append(String.format("   • [Phase 1 & 2] Local Build:  %,d ms%n", metrics.phase1_2Ms));
            sb.append(String.format("   • [Phase 3] Web Assets Sync:  %,d ms%n", metrics.phase3Ms));
            sb.append(String.format("   • [Phase 4] Images Sync:      %,d ms%n", metrics.phase4Ms));
            sb.append(String.format("   • [Phase 4.5] S3 Ghost Sweep: %,d ms%n", metrics.phase45Ms));
            sb.append(String.format("   • [Phase 5] Sitemap GZ Sync:  %,d ms%n", metrics.phase5Ms));
            sb.append(String.format("   • [Phase 6] CloudFront Edge:  %,d ms%n", metrics.phase6Ms));
            sb.append(String.format("   • [Phase 7] IndexNow Ping:    %,d ms%n", metrics.phase7Ms));
            sb.append("   -----------------------------------------------------------------------------\n");
        }
        sb.append(String.format("   • TOTAL EXECUTION TIME:       %,d ms%n", totalMs));
        sb.append("================================================================================\n");
        log.info(sb.toString());
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

    /**
     * Builds all local HTML files, sitemaps, and images.
     * Shared identically between SiteBuilderPipeline (Production) and LocalDevPipeline (Local Preview).
     */
    @SuppressWarnings("unchecked")
    public static List<CardPageGenerator.CardData> buildLocalArtifacts(TimestampTracker timeTracker, FileTracker tracker) {
        // Ensure stable CSS version for hash stability if content didn't change
        String cssHash = tracker.getHash(Paths.get("src/main/resources/css/main.css"));
        if (cssHash != null && cssHash.length() >= 8) {
            SharedTemplates.setBuildId(cssHash.substring(0, 8));
        } else {
            SharedTemplates.setBuildId("stable");
        }

        // Prefetch latest Firestore ratings into cache before generation starts
        FirestoreRatingInjector.prefetchRatings();

        // --- PARALLEL PHASES: HTML Generation & Image WebP Conversion ---
        log.info("\n[PHASE 1 & 2] Launching HTML Generation and Image WebP Conversion in parallel...");

        final List<CardPageGenerator.CardData>[] generatedCards = (List<CardPageGenerator.CardData>[]) new List<?>[1];
        try (ExecutorService phaseExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<Void> htmlTask = CompletableFuture.runAsync(() -> {
                log.info("  -> [PHASE 1] Generating HTML files and Sitemap...");
                FileGenerator.setTimestampTracker(timeTracker);
                CardPageGenerator.setTimestampTracker(timeTracker);
                SitemapGenerator.setTimestampTracker(timeTracker);

                FileGenerator.copyResources();
                IndexNowService.ensureValidationFile();
                FileGenerator.buildCollectionOverview();
                FileGenerator.buildOtherCollections();
                FileGenerator.buildStaticPages();
                generatedCards[0] = CardPageGenerator.run();

                SitemapGenerator.generate(generatedCards[0]); // Sitemap & robots.txt now ready
                timeTracker.save();
            }, phaseExecutor);

            CompletableFuture<Void> imageTask = CompletableFuture.runAsync(() -> {
                log.info("  -> [PHASE 2] Converting images to AVIF ...");
                ImageConverter.main(new String[0]);
            }, phaseExecutor);

            // Wait for both tasks to complete concurrently
            CompletableFuture.allOf(htmlTask, imageTask).join();
        }
        return generatedCards[0];
    }

}