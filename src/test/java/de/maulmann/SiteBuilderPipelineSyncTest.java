package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SiteBuilderPipeline Sync & Deployment Tests")
class SiteBuilderPipelineSyncTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("resolveAwsCredentialsProvider should return composite chain including Default and Profile providers")
    void testResolveAwsCredentialsProviderChain() {
        AwsCredentialsProvider provider = SiteBuilderPipeline.resolveAwsCredentialsProvider();
        assertNotNull(provider);
        assertInstanceOf(AwsCredentialsProviderChain.class, provider);
    }

    @Test
    @DisplayName("fetchExistingS3Keys should return empty set when client is null")
    void testFetchExistingS3KeysNullClient() {
        Set<String> keys = SiteBuilderPipeline.fetchExistingS3Keys(null, "images/");
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    @DisplayName("fetchExistingS3Keys should paginate and aggregate keys from S3")
    void testFetchExistingS3KeysWithPagination() {
        AtomicInteger pageCallCount = new AtomicInteger(0);

        S3AsyncClient mockClient = (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("listObjectsV2".equals(method.getName()) && args.length == 1) {
                        int page = pageCallCount.incrementAndGet();
                        if (page == 1) {
                            ListObjectsV2Response res = ListObjectsV2Response.builder()
                                    .contents(
                                            S3Object.builder().key("images/1996-97/card1.avif").build(),
                                            S3Object.builder().key("images/1996-97/card2.avif").build()
                                    )
                                    .nextContinuationToken("token-page-2")
                                    .isTruncated(true)
                                    .build();
                            return CompletableFuture.completedFuture(res);
                        } else {
                            ListObjectsV2Response res = ListObjectsV2Response.builder()
                                    .contents(
                                            S3Object.builder().key("images/2003-04/card3.avif").build()
                                    )
                                    .nextContinuationToken(null)
                                    .isTruncated(false)
                                    .build();
                            return CompletableFuture.completedFuture(res);
                        }
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException("Method " + method.getName() + " not mocked");
                }
        );

        Set<String> keys = SiteBuilderPipeline.fetchExistingS3Keys(mockClient, "images/");
        assertEquals(3, keys.size());
        assertTrue(keys.contains("images/1996-97/card1.avif"));
        assertTrue(keys.contains("images/1996-97/card2.avif"));
        assertTrue(keys.contains("images/2003-04/card3.avif"));
        assertEquals(2, pageCallCount.get());
    }

    @Test
    @DisplayName("uploadBytes on success should update FileTracker hash and increment counter")
    void testUploadBytesSuccessUpdatesTracker() throws Exception {
        Path trackerFile = tempDir.resolve("sync-hashes.properties");
        FileTracker tracker = new FileTracker(trackerFile.toString());
        Path dummyFile = tempDir.resolve("index.html");
        Files.writeString(dummyFile, "<html><body>Hello</body></html>");

        String computedHash = tracker.getHash(dummyFile);
        assertNull(tracker.getStoredHash(dummyFile));

        AtomicInteger counter = new AtomicInteger(0);

        S3AsyncClient mockClient = (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("putObject".equals(method.getName())) {
                        return CompletableFuture.completedFuture(PutObjectResponse.builder().build());
                    }
                    return null;
                }
        );

        SiteBuilderPipeline.uploadBytes(
                mockClient,
                "index.html",
                "<html><body>Hello</body></html>".getBytes(StandardCharsets.UTF_8),
                "text/html; charset=utf-8",
                "br",
                "max-age=0, must-revalidate",
                counter,
                tracker,
                dummyFile,
                computedHash
        );

        assertEquals(1, counter.get());
        assertEquals(computedHash, tracker.getStoredHash(dummyFile));
    }

    @Test
    @DisplayName("uploadBytes on failure should throw exception and NOT update FileTracker hash or increment counter")
    void testUploadBytesFailureDoesNotUpdateTracker() throws Exception {
        Path trackerFile = tempDir.resolve("sync-hashes.properties");
        FileTracker tracker = new FileTracker(trackerFile.toString());
        Path dummyFile = tempDir.resolve("page.html");
        Files.writeString(dummyFile, "<html>Fail</html>");

        String computedHash = tracker.getHash(dummyFile);
        AtomicInteger counter = new AtomicInteger(0);

        S3AsyncClient mockClient = (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("putObject".equals(method.getName())) {
                        CompletableFuture<PutObjectResponse> future = new CompletableFuture<>();
                        future.completeExceptionally(S3Exception.builder().message("Network connection timeout").build());
                        return future;
                    }
                    return null;
                }
        );

        assertThrows(Exception.class, () -> SiteBuilderPipeline.uploadBytes(
                mockClient,
                "page.html",
                "<html>Fail</html>".getBytes(StandardCharsets.UTF_8),
                "text/html; charset=utf-8",
                "br",
                "max-age=0, must-revalidate",
                counter,
                tracker,
                dummyFile,
                computedHash
        ));

        assertEquals(0, counter.get());
        assertNull(tracker.getStoredHash(dummyFile), "Tracker must NOT update hash if upload fails");
    }

    @Test
    @DisplayName("uploadRawFile on success should update FileTracker hash and increment counter")
    void testUploadRawFileSuccessUpdatesTracker() throws Exception {
        Path trackerFile = tempDir.resolve("sync-hashes.properties");
        FileTracker tracker = new FileTracker(trackerFile.toString());
        Path dummyImage = tempDir.resolve("card.avif");
        Files.write(dummyImage, new byte[]{0x00, 0x01, 0x02});

        String computedHash = tracker.getHash(dummyImage);
        assertNull(tracker.getStoredHash(dummyImage));

        AtomicInteger counter = new AtomicInteger(0);

        S3AsyncClient mockClient = (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("putObject".equals(method.getName())) {
                        return CompletableFuture.completedFuture(PutObjectResponse.builder().build());
                    }
                    return null;
                }
        );

        SiteBuilderPipeline.uploadRawFile(
                mockClient,
                dummyImage,
                "images/card.avif",
                "image/avif",
                "public, max-age=31536000, immutable",
                counter,
                tracker,
                computedHash
        );

        assertEquals(1, counter.get());
        assertEquals(computedHash, tracker.getStoredHash(dummyImage));
    }

    @Test
    @DisplayName("uploadRawFile on failure should throw and NOT update FileTracker hash or increment counter")
    void testUploadRawFileFailureDoesNotUpdateTracker() throws Exception {
        Path trackerFile = tempDir.resolve("sync-hashes.properties");
        FileTracker tracker = new FileTracker(trackerFile.toString());
        Path dummyImage = tempDir.resolve("failed-card.avif");
        Files.write(dummyImage, new byte[]{0x0A, 0x0B, 0x0C});

        String computedHash = tracker.getHash(dummyImage);
        AtomicInteger counter = new AtomicInteger(0);

        S3AsyncClient mockClient = (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("putObject".equals(method.getName())) {
                        CompletableFuture<PutObjectResponse> future = new CompletableFuture<>();
                        future.completeExceptionally(S3Exception.builder().message("Simulated socket drop").build());
                        return future;
                    }
                    return null;
                }
        );

        assertThrows(Exception.class, () -> SiteBuilderPipeline.uploadRawFile(
                mockClient,
                dummyImage,
                "images/failed-card.avif",
                "image/avif",
                "public, max-age=31536000, immutable",
                counter,
                tracker,
                computedHash
        ));

        assertEquals(0, counter.get());
        assertNull(tracker.getStoredHash(dummyImage), "Tracker must NOT update hash if upload fails");
    }

    @Test
    @DisplayName("uploadRawFile and uploadBytes with null S3 client should safely return without updating tracker")
    void testUploadWithNullClientDoesNotUpdateTracker() throws Exception {
        Path trackerFile = tempDir.resolve("sync-hashes.properties");
        FileTracker tracker = new FileTracker(trackerFile.toString());
        Path dummyImage = tempDir.resolve("offline-card.avif");
        Files.write(dummyImage, new byte[]{0x01, 0x02});

        String computedHash = tracker.getHash(dummyImage);
        AtomicInteger counter = new AtomicInteger(0);

        SiteBuilderPipeline.uploadRawFile(
                null,
                dummyImage,
                "images/offline-card.avif",
                "image/avif",
                "public, max-age=31536000, immutable",
                counter,
                tracker,
                computedHash
        );

        assertEquals(0, counter.get());
        assertNull(tracker.getStoredHash(dummyImage));

        SiteBuilderPipeline.uploadBytes(
                null,
                "offline.html",
                "test".getBytes(StandardCharsets.UTF_8),
                "text/html; charset=utf-8",
                "br",
                "max-age=0",
                counter,
                tracker,
                dummyImage,
                computedHash
        );

        assertEquals(0, counter.get());
        assertNull(tracker.getStoredHash(dummyImage));
    }
}
