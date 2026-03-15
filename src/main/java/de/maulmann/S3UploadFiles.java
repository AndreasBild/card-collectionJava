package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class S3UploadFiles {

    private static final String PATH_SOURCE = "output";
    private static final String BUCKET_NAME = "maulmann.de";
    private static final Region REGION = Region.EU_CENTRAL_1;
    private static final String PROFILE_NAME = "JavaSDKUser";

    // Thread-safe tracking
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        Path directory = Paths.get(PATH_SOURCE);

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            System.err.println("Source directory does not exist or is not a folder: " + directory.toAbsolutePath());
            return;
        }

        System.out.println("Starting high-speed async root upload to: " + BUCKET_NAME);

        // 1. Build the Asynchronous Client (Non-blocking Netty)
        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(ProfileCredentialsProvider.create(PROFILE_NAME))
                .build()) {

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 2. Stream through the directory safely
            try (Stream<Path> paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    String contentType = null;

                    if (fileName.endsWith(".html")) {
                        contentType = "text/html";
                    } else if (fileName.endsWith(".css")) {
                        contentType = "text/css";
                    } else {
                        System.err.println("Skipping unsupported filetype: " + file.getFileName());
                    }

                    if (contentType != null) {
                        // Kick off the async upload
                        futures.add(uploadFileAsync(s3AsyncClient, file, contentType));
                    }
                });
            }

            // 3. Wait for all AWS acknowledgments
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long endTime = System.currentTimeMillis();
            System.out.println("\n--- Upload Summary ---");
            System.out.println("Successfully uploaded: " + successCount.get() + " files");
            System.out.println("Failed to upload:      " + failureCount.get() + " files");
            System.out.println("Total execution time:  " + (endTime - startTime) + " ms");

            // Optional: Trigger CloudFront Invalidation here once all uploads are finished
            // CloudFrontInvalidator invalidator = new CloudFrontInvalidator();
            // invalidator.invalidate();

        } catch (Exception e) {
            System.err.println("Critical error during upload routing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static CompletableFuture<Void> uploadFileAsync(S3AsyncClient s3AsyncClient, Path file, String contentType) {
        String fileName = file.getFileName().toString();

        // Build request directly without the intermediate HashMap
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(fileName)
                .contentType(contentType)
                .contentEncoding("gzip")
                .contentLanguage("en-US")
                .build();

        // Fire the non-blocking request
        CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(
                putObjectRequest,
                AsyncRequestBody.fromFile(file)
        );

        // Attach callback for logging and counting
        return future.thenAccept(response -> {
            successCount.incrementAndGet();
            System.out.println("Uploaded: " + fileName + " (ETag: " + response.eTag() + ")");
        }).exceptionally(ex -> {
            failureCount.incrementAndGet();
            System.err.println("Failed to upload " + fileName + ": " + ex.getMessage());
            return null;
        });
    }
}