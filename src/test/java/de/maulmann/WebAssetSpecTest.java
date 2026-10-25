package de.maulmann;

import static org.junit.jupiter.api.Assertions.*;

import de.maulmann.SiteBuilderPipeline.AssetTransform;
import de.maulmann.SiteBuilderPipeline.DeploymentMetrics;
import de.maulmann.SiteBuilderPipeline.WebAssetSpec;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** Pins the table-driven web asset upload rules to the behaviour of the former per-extension switch. */
@DisplayName("SiteBuilderPipeline web asset upload rules")
class WebAssetSpecTest {

    private static final String LONG = "public, max-age=31536000, immutable";
    private static final String SHORT = "max-age=0, must-revalidate";

    @TempDir
    Path tempDir;

    private static void assertSpec(String fileName, String type, String cache, AssetTransform transform, boolean brotli) {
        WebAssetSpec spec = SiteBuilderPipeline.resolveWebAsset(fileName);
        assertNotNull(spec, fileName);
        assertEquals(type, spec.contentType(), fileName);
        assertEquals(cache, spec.cacheControl(), fileName);
        assertEquals(transform, spec.transform(), fileName);
        assertEquals(brotli, spec.brotli(), fileName);
    }

    @Test
    void resolvesEveryUploadedExtensionLikeTheOriginalSwitch() {
        assertSpec("index.html", "text/html; charset=utf-8", SHORT, AssetTransform.MINIFY_HTML, true);
        assertSpec("main.css", "text/css; charset=utf-8", LONG, AssetTransform.MINIFY_CSS, true);
        assertSpec("collector-features.js", "application/javascript; charset=utf-8", LONG, AssetTransform.NONE, true);
        assertSpec("manifest.json", "application/json; charset=utf-8", SHORT, AssetTransform.NONE, true);
        assertSpec("rss.xml", "application/xml; charset=utf-8", SHORT, AssetTransform.NONE, true);
        assertSpec("style.xsl", "application/xml; charset=utf-8", SHORT, AssetTransform.NONE, true);
        assertSpec("favicon.ico", "image/x-icon", LONG, AssetTransform.NONE, false);
        assertSpec("robots.txt", "text/plain; charset=utf-8", SHORT, AssetTransform.NONE, false);
    }

    @Test
    void serviceWorkerIsNeverCachedLong() {
        assertSpec("serviceworker.js", "application/javascript; charset=utf-8", SHORT, AssetTransform.NONE, true);
    }

    @Test
    void unknownOrExtensionlessFilesAreSkipped() {
        assertNull(SiteBuilderPipeline.resolveWebAsset("photo.png"));
        assertNull(SiteBuilderPipeline.resolveWebAsset("LICENSE"));
        assertNull(SiteBuilderPipeline.resolveWebAsset("archive.zip"));
    }

    @Test
    void sdkClientExceptionDetectionMatchesDirectAndWrappedCauses() {
        SdkClientException sdk = SdkClientException.create("no credentials");
        assertTrue(SiteBuilderPipeline.isSdkClientException(sdk));
        assertTrue(SiteBuilderPipeline.isSdkClientException(new RuntimeException("wrapper", sdk)));
        assertFalse(SiteBuilderPipeline.isSdkClientException(new RuntimeException("boom")));
        assertFalse(SiteBuilderPipeline.isSdkClientException(new IllegalStateException("x", new RuntimeException("y"))));
    }

    private static S3AsyncClient mockClient(AtomicReference<PutObjectRequest> captured) {
        return (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[] {S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("putObject".equals(method.getName())) {
                        captured.set((PutObjectRequest) args[0]);
                        return CompletableFuture.completedFuture(PutObjectResponse.builder().build());
                    }
                    return null;
                });
    }

    @Test
    void brotliAssetRecordsRawAndCompressedBytesAndUploadsWithBrEncoding() throws Exception {
        Path css = tempDir.resolve("main.css");
        Files.writeString(css, "body { color : red ; }\n".repeat(50), StandardCharsets.UTF_8);

        AtomicReference<PutObjectRequest> captured = new AtomicReference<>();
        DeploymentMetrics metrics = new DeploymentMetrics();
        FileTracker tracker = new FileTracker(tempDir.resolve("hashes.properties").toString());

        SiteBuilderPipeline.uploadWebAsset(mockClient(captured), css, "main.css",
                SiteBuilderPipeline.resolveWebAsset("main.css"), metrics, tracker, tracker.getHash(css));

        assertEquals(1, metrics.webFilesUploaded.get());
        assertTrue(metrics.rawWebBytes.get() > 0);
        assertTrue(metrics.compressedWebBytes.get() > 0);
        assertTrue(metrics.compressedWebBytes.get() < metrics.rawWebBytes.get(), "Brotli must shrink repetitive CSS");
        assertEquals("br", captured.get().contentEncoding());
        assertEquals("text/css; charset=utf-8", captured.get().contentType());
        assertEquals(LONG, captured.get().cacheControl());
        assertNotNull(tracker.getStoredHash(css));
    }

    @Test
    void rawAssetCountsSameSizeForRawAndCompressedAndHasNoContentEncoding() throws Exception {
        Path txt = tempDir.resolve("robots.txt");
        Files.writeString(txt, "User-agent: *\n", StandardCharsets.UTF_8);

        AtomicReference<PutObjectRequest> captured = new AtomicReference<>();
        DeploymentMetrics metrics = new DeploymentMetrics();
        FileTracker tracker = new FileTracker(tempDir.resolve("hashes2.properties").toString());

        SiteBuilderPipeline.uploadWebAsset(mockClient(captured), txt, "robots.txt",
                SiteBuilderPipeline.resolveWebAsset("robots.txt"), metrics, tracker, tracker.getHash(txt));

        long size = Files.size(txt);
        assertEquals(size, metrics.rawWebBytes.get());
        assertEquals(size, metrics.compressedWebBytes.get());
        assertEquals(1, metrics.webFilesUploaded.get());
        assertNull(captured.get().contentEncoding());
        assertEquals(SHORT, captured.get().cacheControl());
    }
}
