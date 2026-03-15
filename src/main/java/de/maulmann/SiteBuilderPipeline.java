package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
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
    private static final String BUCKET_NAME = "maulmann.de";
    private static final Region REGION = Region.EU_CENTRAL_1;
    private static final String PROFILE_NAME = "JavaSDKUser";

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        System.out.println("==================================================");
        System.out.println("🚀 STARTING MASTER BUILD PIPELINE");
        System.out.println("==================================================");

        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(ProfileCredentialsProvider.create(PROFILE_NAME))
                .build()) {

            // --- PHASE 1: Generate Site ---
            System.out.println("\n[PHASE 1] Generating HTML files...");
            FileGenerator.main(new String[0]); // Triggers your main HTML/Card generation

            // --- PHASE 2: Compress & Upload HTML/CSS ---
            System.out.println("\n[PHASE 2] Minifying, Compressing, and Uploading HTML/CSS...");
            processAndUploadWebFiles(s3AsyncClient);

            // --- PHASE 3: Generate, Compress & Upload Sitemap ---
            System.out.println("\n[PHASE 3] Processing Sitemap...");
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

                try {
                    if (fileName.endsWith(".html")) {
                        // 1. Minify & Compress In-Memory
                        byte[] gzippedData = GZIPCompressor.compressBytes(HTMLMinifier.minifyHTMLToBytes(file.toFile()), 9);
                        // 2. Upload directly from RAM
                        uploadFutures.add(uploadBytesAsync(s3Client, file, gzippedData, "text/html", uploadCount));
                    }
                    else if (fileName.endsWith(".css")) {
                        // 1. Minify & Compress In-Memory
                        byte[] gzippedData = GZIPCompressor.compressBytes(CSSMinifier.minifyCSSToBytes(file.toFile()), 9);
                        // 2. Upload directly from RAM
                        uploadFutures.add(uploadBytesAsync(s3Client, file, gzippedData, "text/css", uploadCount));
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process " + fileName + ": " + e.getMessage());
                }
            });
        }

        // Wait for all Netty uploads to finish
        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        System.out.println("-> Successfully processed and uploaded " + uploadCount.get() + " HTML/CSS files.");
    }

    private static void processAndUploadSitemap(S3AsyncClient s3Client) throws Exception {
        // 1. Generate standard sitemap.xml
        SitemapGenerator.generate();

        File sitemapFile = new File(OUTPUT_DIR + "/sitemap.xml");
        File sitemapGzFile = new File(OUTPUT_DIR + "/sitemap.xml.gz");

        if (!sitemapFile.exists()) {
            throw new Exception("Sitemap was not generated properly.");
        }

        // 2. Compress to sitemap.xml.gz
        GZIPCompressor.compressFile(sitemapFile, sitemapGzFile, 9);
        System.out.println("-> Compressed sitemap to sitemap.xml.gz");

        // 3. Upload to S3
        String s3Key = "sitemap.xml.gz";
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType("application/xml") // Tells browser it's an XML file under the hood
                .contentEncoding("gzip")        // Tells browser to decompress it automatically
                .build();

        CompletableFuture<PutObjectResponse> future = s3Client.putObject(
                request,
                AsyncRequestBody.fromFile(sitemapGzFile)
        );

        future.join(); // Wait for this specific upload to finish
        System.out.println("-> Successfully uploaded sitemap.xml.gz to S3");
    }

    private static CompletableFuture<Void> uploadBytesAsync(S3AsyncClient s3Client, Path localFile, byte[] data, String contentType, AtomicInteger counter) {
        // Calculate the relative path for S3 (e.g., cards/1997-98/Juwan.html)
        Path basePath = Paths.get(OUTPUT_DIR);
        String s3Key = basePath.relativize(localFile).toString().replace("\\", "/");

        // Set the critical metadata
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentEncoding("gzip") // CRITICAL: Tells the browser the file is compressed
                .contentLanguage("en-US")
                .build();

        // Upload the byte array directly from RAM
        return s3Client.putObject(request, AsyncRequestBody.fromBytes(data))
                .thenAccept(response -> counter.incrementAndGet())
                .exceptionally(ex -> {
                    System.err.println("Failed to upload " + s3Key + ": " + ex.getMessage());
                    return null;
                });
    }
}