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

public class S3UploadFiles {
    private static final String pathOutput = "../card-CollectionJava/output/";
    private static final String generatedFileLocation = pathOutput + "index.html";
    private static final File file = new File(generatedFileLocation);
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


        // List objects in the specified bucket
        uploadFile(s3Client);

        // Close the S3 client
        s3Client.close();
    }


    private static void uploadFile(S3Client s3Client) {
        try {
            // Custom metadata using a Map
            final Map<String, String> customMetadata = new HashMap<>();
            customMetadata.put("Content-Type", "text/html");  // setting content-type
            customMetadata.put("Content-Encoding", "gzip");  // setting content-encoding
            customMetadata.put("Content-Language", "en-US");  // setting content-language


            // Create a PutObjectRequest with the bucket name, key (file name), file, and metadata
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3UploadFiles.bucketName)
                    .key(S3UploadFiles.file.getName())  // You can modify this to include a folder structure in the bucket
                    .contentEncoding(customMetadata.get("Content-Encoding"))
                    .contentType(customMetadata.get("Content-Type"))
                    .contentLanguage(customMetadata.get("Content-Language"))
                    .build();

            // Upload the file to S3 with metadata
            PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromFile(S3UploadFiles.file));

            System.out.println("Successfully uploaded: " + S3UploadFiles.file.getName() + " with ETag: " + response.eTag());

        } catch (S3Exception e) {
            e.printStackTrace();
        }
    }
}
