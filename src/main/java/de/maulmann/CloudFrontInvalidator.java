package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

public class CloudFrontInvalidator {

    private static final Logger logger = LoggerFactory.getLogger(CloudFrontInvalidator.class);

    private String getDistributionId() throws IOException {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.error("Sorry, unable to find config.properties");
                throw new IOException("config.properties not found in classpath");
            }
            prop.load(input);
            return prop.getProperty("cloudfront.distribution.id");
        }
    }

    public void invalidate() {
        try {
            String distributionId = getDistributionId();
            if (distributionId == null || distributionId.isEmpty()) {
                logger.error("CloudFront Distribution ID is not set in config.properties");
                return;
            }
            logger.info("Starting CloudFront invalidation for distribution ID: {}", distributionId);

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
                        .distributionId(distributionId)
                        .invalidationBatch(invalidationBatch)
                        .build();

                cloudFrontClient.createInvalidation(invalidationRequest);

                logger.info("Successfully created CloudFront invalidation request.");

            }
        } catch (Exception e) {
            logger.error("Error during CloudFront invalidation", e);
        }
    }
}