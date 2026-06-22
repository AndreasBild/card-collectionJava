package de.maulmann;

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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
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
            String cssHash = tracker.getHash(Paths.get("src/main/resources/css/main.css"));
            if (cssHash != null && cssHash.length() >= 8) {
                SharedTemplates.setBuildId(cssHash.substring(0, 8));
            } else {
                SharedTemplates.setBuildId("stable");
            }

            // --- PHASE 1: Generate Site HTML & Sitemap ---
            System.out.println("\n[PHASE 1] Generating HTML files and Sitemap...");
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
            System.out.println("\n[PHASE 1.5] Injecting Firestore ratings...");
            String firebaseCreds = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            File firebaseFile = new File("firebase/maulmann-3f90d-firebase-adminsdk-fbsvc-78c9f10838");

            if ((firebaseCreds == null || firebaseCreds.isEmpty()) && !firebaseFile.exists()) {
                System.err.println("⚠️ WARNING: Firebase credentials (env or file) are missing! Ratings will NOT be injected.");
            } else {
                FirestoreRatingInjector.main(new String[0]);
            }

            // --- PHASE 2: Convert Images to WebP ---
            System.out.println("\n[PHASE 2] Converting images to WebP...");
            ImageConverter.main(new String[0]);

            // --- PHASE 3: Compress & Upload HTML/CSS/JS/XML ---
            System.out.println("\n[PHASE 3] Minifying, Compressing, and Uploading Web Files...");
            processAndUploadWebFiles(s3AsyncClient, tracker);

            // --- PHASE 4: Upload Images (No Compression) ---
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
        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        try (var scope = new StructuredTaskScope<Void>()) {
            try (Stream<Path> paths = Files.walk(outputDir)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    String s3Key = outputDir.relativize(file).toString().replace("\\", "/");

                    if (fileName.equals("sitemap.xml") || fileName.equals("sitemap.xml.gz") || fileName.equals("sync-hashes.properties")) {
                        return;
                    }

                    String currentHash = tracker.getHash(file);
                    String storedHash = tracker.getStoredHash(file);
                    if (currentHash != null && currentHash.equals(storedHash)) {
                        skipCount.incrementAndGet();
                        return;
                    }

                    scope.fork(() -> {
                        try {
                            if (fileName.endsWith(".html")) {
                                byte[] brData = BrotliCompressor.compressBytes(HTMLMinifier.minifyHTMLToBytes(file.toFile()), BrotliCompressor.BEST_QUALITY);
                                uploadBytes(s3Client, s3Key, brData, "text/html", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                            } else if (fileName.endsWith(".css")) {
                                byte[] brData = BrotliCompressor.compressBytes(CSSMinifier.minifyCSSToBytes(file.toFile()), BrotliCompressor.BEST_QUALITY);
                                uploadBytes(s3Client, s3Key, brData, "text/css", "br", CACHE_LONG, uploadCount, tracker, file, currentHash);
                            } else if (fileName.endsWith(".js")) {
                                byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BrotliCompressor.BEST_QUALITY);
                                uploadBytes(s3Client, s3Key, brData, "application/javascript", "br", CACHE_LONG, uploadCount, tracker, file, currentHash);
                            } else if (fileName.endsWith(".json")) {
                                byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BrotliCompressor.BEST_QUALITY);
                                uploadBytes(s3Client, s3Key, brData, "application/json", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                            } else if (fileName.endsWith(".xml")) {
                                byte[] gzippedData = GZIPCompressor.compressBytes(Files.readAllBytes(file), 9);
                                uploadBytes(s3Client, s3Key, gzippedData, "application/xml", "gzip", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                            } else if (fileName.endsWith(".ico")) {
                                uploadRawFile(s3Client, file, s3Key, "image/x-icon", CACHE_LONG, uploadCount, tracker, currentHash);
                            } else if (fileName.startsWith("robots") || fileName.endsWith(".txt")) {
                                byte[] brData = BrotliCompressor.compressBytes(Files.readAllBytes(file), BrotliCompressor.BEST_QUALITY);
                                uploadBytes(s3Client, s3Key, brData, "text/plain", "br", CACHE_SHORT, uploadCount, tracker, file, currentHash);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to process " + fileName + ": " + e.getMessage());
                        }
                        return null;
                    });
                });
            }
            scope.join();
        }

        System.out.println("-> Uploaded " + uploadCount.get() + " web files. (Skipped " + skipCount.get() + " unmodified files).");
    }

    private static void processAndUploadImages(S3AsyncClient s3Client, FileTracker tracker) throws Exception {
        Path imagesDir = Paths.get(IMAGES_DIR);
        Path outputDir = Paths.get(OUTPUT_DIR);

        if (!Files.exists(imagesDir)) {
            System.out.println("-> Images directory not found, skipping phase.");
            return;
        }

        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        try (var scope = new StructuredTaskScope<Void>()) {
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
                        scope.fork(() -> {
                            try {
                                uploadRawFile(s3Client, file, s3Key, contentType, CACHE_LONG, uploadCount, tracker, currentHash);
                            } catch (Exception e) {
                                System.err.println("Failed to upload image " + fileName + ": " + e.getMessage());
                            }
                            return null;
                        });
                    }
                });
            }
            scope.join();
        }

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
                .build();

        s3Client.putObject(request, AsyncRequestBody.fromFile(localFile)).join();
        counter.incrementAndGet();
        if (tracker != null && localFile != null) {
            tracker.updateHash(localFile, preCalculatedHash);
        }
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