package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class S3UploadFiles {
    private static final String pathSource = "../card-CollectionJava/output";
    // Specify the bucket name
    private static final String bucketName = "maulmann.de";

    public static void main(String[] args) {
        // Define the AWS region where your bucket is located
        Region region = Region.EU_CENTRAL_1;

        // Create S3 client using credentials from the specified profile (JavaSDKUser)
        S3Client s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create("JavaSDKUser"))
                .build();

        File directory = new File(pathSource);

        for (File file : Objects.<File[]>requireNonNull(directory.listFiles())) {
            if (file.isFile()) {
                try {
                    if (file.getName().endsWith(".html")) {
                        uploadFile(s3Client,file, "text/html");

                    } else if (file.getName().endsWith(".css")) {
                        uploadFile(s3Client,file, "text/css");

                    } else {
                        System.err.println("Failed to decide on filetype: " + file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private static void uploadFile(S3Client s3Client, File file, String fileType) {
        try {
            // Custom metadata using a Map
            final Map<String, String> customMetadata = new HashMap<>();
            customMetadata.put("Content-Type", fileType);
            customMetadata.put("Content-Encoding", "gzip");
            customMetadata.put("Content-Language", "en-US");

            // Create a PutObjectRequest with the bucket name, key (file name), file, and metadata
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3UploadFiles.bucketName)
                    .key(file.getName())  // You can modify this to include a folder structure in the bucket
                    .contentEncoding(customMetadata.get("Content-Encoding"))
                    .contentType(customMetadata.get("Content-Type"))
                    .contentLanguage(customMetadata.get("Content-Language"))
                    .build();

            // Upload the file to S3 with metadata
            PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));

            System.out.println("Successfully uploaded: " + file.getName() + " with ETag: " + response.eTag());

        } catch (S3Exception e) {
            e.printStackTrace();
        }
    }
}
