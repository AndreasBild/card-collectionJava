package de.maulmann;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for notifying search engines via the IndexNow API protocol.
 * Batches created or updated card URLs to prevent network stream exhaustion and HTTP 429 rate limiting.
 */
public class IndexNowService {

    private static final Logger log = LoggerFactory.getLogger(IndexNowService.class);
    private static final List<String> INDEXNOW_ENDPOINTS = List.of(
            "https://api.indexnow.org/indexnow",
            "https://yandex.com/indexnow",
            "https://search.seznam.cz/indexnow"
    );

    /**
     * Sends the IndexNow JSON payload with retry handling for HTTP 429 and fallback to participating IndexNow endpoints.
     */
    private static void sendPayloadWithRetry(List<String> urlList) {
        String key = getKey();
        String host = getHost();

        if ("true".equalsIgnoreCase(System.getProperty("INDEXNOW_TEST_MODE")) || host.contains("example.com") || host.contains("localhost")) {
            log.info("ℹ️ Test mode active (host={}): Skipping live IndexNow HTTP request for {} URL(s).", host, urlList.size());
            return;
        }

        String keyLocation = "https://" + host + "/" + key + ".txt";

        Map<String, Object> payload = new HashMap<>();
        payload.put("host", host);
        payload.put("key", key);
        payload.put("keyLocation", keyLocation);
        payload.put("urlList", urlList);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize IndexNow JSON payload", e);
            return;
        }

        for (String endpoint : INDEXNOW_ENDPOINTS) {
            int attempt = 0;
            long backoffMs = 2000;

            while (attempt <= MAX_RETRIES_ON_429) {
                attempt++;
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                            .timeout(Duration.ofSeconds(20))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();

                    if (status == 200 || status == 202) {
                        log.info("✅ IndexNow notification successful via {} for {} URL(s). Response Code: {}", endpoint, urlList.size(), status);
                        return;
                    } else if (status == 429) {
                        log.warn("⚠️ IndexNow API ({}) responded with HTTP 429. Attempt {}/{} - Retrying in {} ms...",
                                endpoint, attempt, MAX_RETRIES_ON_429 + 1, backoffMs);
                        if (attempt <= MAX_RETRIES_ON_429) {
                            Thread.sleep(backoffMs);
                            backoffMs *= 2;
                            continue;
                        }
                    } else if (status == 403) {
                        log.warn("ℹ️ IndexNow endpoint {} returned HTTP 403. Trying fallback participating endpoint...", endpoint);
                        break;
                    } else {
                        log.warn("⚠️ IndexNow endpoint {} responded with HTTP {}: {}", endpoint, status, response.body());
                        break;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("IndexNow API request interrupted", ie);
                    return;
                } catch (Exception e) {
                    log.error("Failed to send IndexNow notification via {} for {} URL(s) (Attempt {}/{})", endpoint, urlList.size(), attempt, MAX_RETRIES_ON_429 + 1, e);
                    if (attempt <= MAX_RETRIES_ON_429) {
                        try {
                            Thread.sleep(backoffMs);
                            backoffMs *= 2;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }
    }
    private static final String DEFAULT_HOST = "www.maulmann.de";
    private static final String DEFAULT_KEY = "527d7f6c267a449b8c4812117f05b108";
    private static final int MAX_URLS_PER_REQUEST = 10000;
    private static final int MAX_RETRIES_ON_429 = 3;

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Thread-safe batch queue for collecting card URLs during bulk page generation
    private static final Set<String> QUEUED_URLS = ConcurrentHashMap.newKeySet();

    /**
     * Retrieves the configured IndexNow API Key.
     */
    public static String getKey() {
        String sysProp = System.getProperty("INDEXNOW_API_KEY");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String envKey = System.getenv("INDEXNOW_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return DEFAULT_KEY;
    }

    /**
     * Gets the configured host for IndexNow submission.
     */
    public static String getHost() {
        String envHost = System.getenv("INDEXNOW_HOST");
        return (envHost != null && !envHost.isBlank()) ? envHost.trim() : DEFAULT_HOST;
    }

    /**
     * Ensures the IndexNow key validation file exists in the specified output directory.
     */
    public static Path ensureValidationFile(Path outputDir) {
        String key = getKey();
        Path validationFilePath = outputDir.resolve(key + ".txt");
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            Files.writeString(validationFilePath, key, StandardCharsets.UTF_8);
            log.info("IndexNow key validation file verified at: {}", validationFilePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write IndexNow key validation file to {}", validationFilePath, e);
        }
        return validationFilePath;
    }

    /**
     * Default helper to ensure key validation file in standard output directory.
     */
    public static Path ensureValidationFile() {
        return ensureValidationFile(Paths.get("output"));
    }

    /**
     * Queues a card URL for batch submission.
     * Prevents issuing thousands of individual concurrent HTTP calls.
     *
     * @param url The card URL to queue
     */
    public static void queueUrl(String url) {
        if (url != null && !url.isBlank()) {
            QUEUED_URLS.add(url.trim());
        }
    }

    /**
     * Queues multiple card URLs for batch submission.
     *
     * @param urls Collection of card URLs to queue
     */
    public static void queueUrls(Collection<String> urls) {
        if (urls != null) {
            urls.stream()
                    .filter(u -> u != null && !u.isBlank())
                    .forEach(u -> QUEUED_URLS.add(u.trim()));
        }
    }

    /**
     * Flushes all queued URLs and submits them asynchronously in batch requests.
     *
     * @return CompletableFuture representing completion of the queued submission batch
     */
    public static CompletableFuture<Void> flushQueueAsync() {
        if (QUEUED_URLS.isEmpty()) {
            log.info("ℹ️ IndexNow: No updated card URLs queued for submission.");
            return CompletableFuture.completedFuture(null);
        }

        // Atomic drain: move all URLs out of the set in one pass to prevent TOCTOU race
        List<String> urlsToSubmit = new ArrayList<>();
        QUEUED_URLS.removeIf(url -> { urlsToSubmit.add(url); return true; });
        log.info("Flushing {} queued card URL(s) to IndexNow API...", urlsToSubmit.size());
        return submitUrlsAsync(urlsToSubmit);
    }

    /**
     * Submits a single card URL to IndexNow asynchronously in the background.
     */
    public static CompletableFuture<Void> submitUrlAsync(String url) {
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return submitUrlsAsync(List.of(url));
    }

    /**
     * Submits a list of card URLs to IndexNow asynchronously in the background.
     * Large lists are automatically chunked into batches of 10,000 URLs per request.
     */
    public static CompletableFuture<Void> submitUrlsAsync(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<String> validUrls = urls.stream()
                .filter(u -> u != null && !u.isBlank())
                .distinct()
                .toList();

        if (validUrls.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < validUrls.size(); i += MAX_URLS_PER_REQUEST) {
                List<String> batch = validUrls.subList(i, Math.min(i + MAX_URLS_PER_REQUEST, validUrls.size()));
                sendPayloadWithRetry(batch);
            }
        }, executor);
    }

    /**
     * Cleanly shuts down the static ExecutorService and HttpClient.
     * Should be called at the end of the pipeline to ensure a clean JVM exit.
     */
    public static void shutdown() {
        try {
            executor.close();
        } catch (Exception e) {
            log.warn("Error closing IndexNow executor: {}", e.getMessage());
        }
        try {
            httpClient.close();
        } catch (Exception e) {
            log.warn("Error closing IndexNow HttpClient: {}", e.getMessage());
        }
    }
}
