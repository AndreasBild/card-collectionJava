package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;

import java.util.UUID;

public class CloudFrontInvalidator {

    private static final Logger logger = LoggerFactory.getLogger(CloudFrontInvalidator.class);
    private static final String DISTRIBUTION_ID = "E2R4RQKEX6C6Y6";

    public void invalidate() {
        logger.info("Starting CloudFront invalidation for distribution ID: {}", DISTRIBUTION_ID);

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL)
                .build()) {

            Paths invalidationPaths = Paths.builder()
                    .items("/*")
                    .quantity(1)
                    .build();

            InvalidationBatch invalidationBatch = InvalidationBatch.builder()
                    .paths(invalidationPaths)
                    .callerReference(UUID.randomUUID().toString())
                    .build();

            CreateInvalidationRequest invalidationRequest = CreateInvalidationRequest.builder()
                    .distributionId(DISTRIBUTION_ID)
                    .invalidationBatch(invalidationBatch)
                    .build();

            cloudFrontClient.createInvalidation(invalidationRequest);

            logger.info("Successfully created CloudFront invalidation request.");

        } catch (Exception e) {
            logger.error("Error during CloudFront invalidation", e);
        }
    }
}