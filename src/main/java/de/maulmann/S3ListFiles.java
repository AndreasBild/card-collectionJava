package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class S3ListFiles {

    private static final String BUCKET_NAME = "maulmann.de";
    private static final Region REGION = Region.EU_CENTRAL_1;
    private static final String PROFILE_NAME = "JavaSDKUser";

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        System.out.println("Starting high-speed bucket scan for: " + BUCKET_NAME);

        // Thread-safe counters for our summary
        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);

        // 1. Use the Async Client with try-with-resources for automatic cleanup
        try (S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(ProfileCredentialsProvider.create(PROFILE_NAME))
                .build()) {

            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME)
                    .build();

            // 2. Use the Reactive Paginator instead of a manual do-while loop
            ListObjectsV2Publisher publisher = s3AsyncClient.listObjectsV2Paginator(request);

            // 3. Subscribe to the stream of pages as AWS sends them
            CompletableFuture<Void> future = publisher.subscribe(response -> {
                response.contents().forEach(object -> {
                    System.out.println(" - " + object.key() + " (size: " + object.size() + " bytes)");
                    totalFiles.incrementAndGet();
                    totalBytes.addAndGet(object.size());
                });
            });

            // Wait for the entire bucket to finish streaming
            future.join();

            long endTime = System.currentTimeMillis();

            // Format bytes to Megabytes for easier reading
            double totalMb = totalBytes.get() / (1024.0 * 1024.0);

            System.out.println("\n--- Bucket Summary ---");
            System.out.println("Total Files:      " + totalFiles.get());
            System.out.printf("Total Size:       %.2f MB\n", totalMb);
            System.out.println("Execution time:   " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            System.err.println("Error scanning bucket: " + e.getMessage());
            e.printStackTrace();
        }
    }
}