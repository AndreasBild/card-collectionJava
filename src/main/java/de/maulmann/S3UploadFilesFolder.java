package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class S3UploadFilesFolder {

    // --- CONFIGURATION ---
    private static final String PATH_SOURCE = "output/cards/";
    private static final String BUCKET_NAME = "maulmann.de";

    private static final String DESTINATION_FOLDER = "cards";
    private static final String PROFILE_NAME = "JavaSDKUser";
    private static final Region REGION = Region.EU_CENTRAL_1;

    private static final Set<String> GZIP_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".html", ".css", ".js", ".json", ".xml", ".svg", ".txt"
    ));

    // Thread-safe tracking
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // 1. Build the Asynchronous Client
        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(REGION)
                .credentialsProvider(ProfileCredentialsProvider.create(PROFILE_NAME))
                .build();

        File rootDirectory = new File(PATH_SOURCE);

        if (!rootDirectory.exists()) {
            System.err.println("Source directory does not exist: " + rootDirectory.getAbsolutePath());
            return;
        }

        System.out.println("Starting ultra-fast async upload to bucket: " + BUCKET_NAME +
                (DESTINATION_FOLDER.isEmpty() ? "" : "/" + DESTINATION_FOLDER));

        try {
            uploadDirectoryAsync(s3AsyncClient, rootDirectory);

            long endTime = System.currentTimeMillis();
            System.out.println("\n--- Upload Summary ---");
            System.out.println("Successfully uploaded: " + successCount.get() + " files");
            System.out.println("Failed to upload:      " + failureCount.get() + " files");
            System.out.println("Total execution time:  " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            System.err.println("Critical error during upload routing: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always close the async client to shut down the Netty event loops
            s3AsyncClient.close();
        }
    }

    private static void uploadDirectoryAsync(S3AsyncClient s3AsyncClient, File rootDirectory) throws IOException {
        // We will store all the "promises" of future uploads here
        List<CompletableFuture<PutObjectResponse>> futures = new ArrayList<>();

        Files.walkFileTree(rootDirectory.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Kick off the upload and keep track of the CompletableFuture
                CompletableFuture<PutObjectResponse> future = uploadFileAsync(s3AsyncClient, file.toFile(), rootDirectory);
                futures.add(future);
                return FileVisitResult.CONTINUE;
            }
        });

        // 2. Wait for all asynchronous network calls to finish before moving on
        System.out.println("All uploads dispatched to Netty. Waiting for AWS acknowledgments...");
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private static CompletableFuture<PutObjectResponse> uploadFileAsync(S3AsyncClient s3AsyncClient, File file, File rootDirectory) {
        String relativePath = calculateRelativePath(file, rootDirectory);
        String s3Key = DESTINATION_FOLDER.isEmpty()
                ? relativePath
                : cleanPath(DESTINATION_FOLDER) + "/" + relativePath;

        String contentType = determineContentType(file);
        boolean isGzip = shouldBeGzipped(file);

        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType(contentType)
                .contentLanguage("en-US");

        if (isGzip) {
            requestBuilder.contentEncoding("gzip");
        }

        // 3. Fire the non-blocking request using AsyncRequestBody
        CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(
                requestBuilder.build(),
                AsyncRequestBody.fromFile(file)
        );

        // 4. Attach a callback to handle the success or failure whenever it finishes
        return future.whenComplete((response, exception) -> {
            if (exception != null) {
                failureCount.incrementAndGet();
                System.err.println("Failed to upload " + file.getName() + ": " + exception.getMessage());
            } else {
                successCount.incrementAndGet();
                String status = isGzip ? "[GZIP]" : "[RAW] ";
                System.out.println("Uploaded " + status + ": " + s3Key);
            }
        });
    }

    private static String calculateRelativePath(File file, File rootDirectory) {
        String absoluteFilePath = file.getAbsolutePath();
        String absoluteRootPath = rootDirectory.getAbsolutePath();

        if (absoluteFilePath.startsWith(absoluteRootPath)) {
            String relativePath = absoluteFilePath.substring(absoluteRootPath.length());
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            return relativePath.replace("\\", "/");
        }
        return file.getName();
    }

    private static String cleanPath(String path) {
        if (path.endsWith("/") || path.endsWith("\\")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean shouldBeGzipped(File file) {
        String name = file.getName().toLowerCase();
        for (String ext : GZIP_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private static String determineContentType(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".xml")) return "application/xml";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".pdf")) return "application/pdf";

        return "application/octet-stream";
    }
}