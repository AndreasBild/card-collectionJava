package de.maulmann;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3ListFiles {

    public static void main(String[] args) {
        // Define the AWS region where your bucket is located
        Region region = Region.EU_CENTRAL_1;

        // Create S3 client using credentials from the specified profile (JavaSDKUser)
        S3Client s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create("JavaSDKUser"))
                .build();

        // Specify the bucket name
        String bucketName = "maulmann.de";

        // List objects in the specified bucket
        listBucketObjects(s3Client, bucketName);

        // Close the S3 client
        s3Client.close();
    }

    private static void listBucketObjects(S3Client s3Client, String bucketName) {
        try {
            // Create a request to list objects in the bucket
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            // Get the list of objects
            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(request);

                // Print the objects found in the bucket
                for (S3Object object : response.contents()) {
                    System.out.println(" - " + object.key() + " (size: " + object.size() + ")");
                }

                // If there are more objects to list, update the request with the continuation token
                request = request.toBuilder()
                        .continuationToken(response.nextContinuationToken())
                        .build();
            } while (response.isTruncated());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
