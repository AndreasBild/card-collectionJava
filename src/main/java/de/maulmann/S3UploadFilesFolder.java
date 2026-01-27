package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class S3UploadFilesFolder {

    // --- CONFIGURATION ---
    private static final String PATH_SOURCE = "output/cards/";
    private static final String BUCKET_NAME = "maulmann.de";

    // Set this to your desired subfolder (e.g. "v2" or "assets/2023").
    // Leave it empty ("") to upload to the bucket root.
    private static final String DESTINATION_FOLDER = "cards";

    private static final String PROFILE_NAME = "JavaSDKUser";
    private static final Region REGION = Region.EU_CENTRAL_1;

    // Define which files are expected to be GZIPPED
    private static final Set<String> GZIP_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".html", ".css", ".js", ".json", ".xml", ".svg", ".txt"
    ));

    public static void main(String[] args) {
        S3Client s3Client = S3Client.builder()
                .region(REGION)
                .credentialsProvider(ProfileCredentialsProvider.create(PROFILE_NAME))
                .build();

        File rootDirectory = new File(PATH_SOURCE);

        if (!rootDirectory.exists()) {
            System.err.println("Source directory does not exist: " + rootDirectory.getAbsolutePath());
            return;
        }

        System.out.println("Starting recursive upload to bucket: " + BUCKET_NAME +
                (DESTINATION_FOLDER.isEmpty() ? "" : "/" + DESTINATION_FOLDER));

        uploadDirectory(s3Client, rootDirectory, rootDirectory);
        System.out.println("Upload complete.");
    }

    private static void uploadDirectory(S3Client s3Client, File currentDir, File rootDirectory) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                uploadDirectory(s3Client, file, rootDirectory);
            } else {
                uploadFile(s3Client, file, rootDirectory);
            }
        }
    }

    private static void uploadFile(S3Client s3Client, File file, File rootDirectory) {
        try {
            // 1. Calculate the relative path (e.g. css/style.css)
            String relativePath = calculateRelativePath(file, rootDirectory);

            // 2. Prepend the destination folder if one is set
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

            s3Client.putObject(requestBuilder.build(), RequestBody.fromFile(file));

            String status = isGzip ? "[GZIP]" : "[RAW] ";
            System.out.println("Uploaded " + status + ": " + s3Key);

        } catch (S3Exception e) {
            System.err.println("S3 Error uploading " + file.getName() + ": " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the path relative to the source root (e.g. css/style.css)
     */
    private static String calculateRelativePath(File file, File rootDirectory) {
        String absoluteFilePath = file.getAbsolutePath();
        String absoluteRootPath = rootDirectory.getAbsolutePath();

        if (absoluteFilePath.startsWith(absoluteRootPath)) {
            String relativePath = absoluteFilePath.substring(absoluteRootPath.length());

            // Remove leading slash if present
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            // Normalize slashes to forward slash for S3
            return relativePath.replace("\\", "/");
        }
        return file.getName();
    }

    /**
     * Helper to remove trailing slashes from the config folder name
     */
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