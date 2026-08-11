package de.maulmann;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class IndexNowServiceTest {

    @BeforeAll
    static void setUp() {
        System.setProperty("INDEXNOW_TEST_MODE", "true");
    }

    @Test
    void testGetKeyAndHostDefaults() {
        assertEquals("527d7f6c267a449b8c4812117f05b108", IndexNowService.getKey());
        assertEquals("www.maulmann.de", IndexNowService.getHost());
    }

    @Test
    void testEnsureValidationFileCreatesFileWithKey(@TempDir Path tempDir) throws IOException {
        Path validationFile = IndexNowService.ensureValidationFile(tempDir);
        assertTrue(Files.exists(validationFile), "Validation file should exist in target directory");
        assertEquals("527d7f6c267a449b8c4812117f05b108.txt", validationFile.getFileName().toString());
        
        String content = Files.readString(validationFile);
        assertEquals("527d7f6c267a449b8c4812117f05b108", content.trim());
    }

    @Test
    void testSubmitUrlAsyncNullOrBlankDoesNotFail() {
        CompletableFuture<Void> nullFuture = IndexNowService.submitUrlAsync(null);
        assertNotNull(nullFuture);
        assertTrue(nullFuture.isDone());

        CompletableFuture<Void> emptyFuture = IndexNowService.submitUrlAsync("  ");
        assertNotNull(emptyFuture);
        assertTrue(emptyFuture.isDone());
    }

    @Test
    void testSubmitUrlsAsyncEmptyList() {
        CompletableFuture<Void> future = IndexNowService.submitUrlsAsync(List.of());
        assertNotNull(future);
        assertTrue(future.isDone());
    }

    @Test
    void testSubmitUrlAsyncCompletesWithoutException() {
        String testCardUrl = "https://test.example.com/cards/2021-22/test-card-1.html";
        CompletableFuture<Void> future = IndexNowService.submitUrlAsync(testCardUrl);
        assertNotNull(future);
        assertDoesNotThrow(() -> future.join());
    }

    @Test
    void testSubmitUrlsAsyncBatching() {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            urls.add("https://test.example.com/cards/2021-22/card-" + i + ".html");
        }
        CompletableFuture<Void> future = IndexNowService.submitUrlsAsync(urls);
        assertNotNull(future);
        assertDoesNotThrow(() -> future.join());
    }

    @Test
    void testQueueAndFlushUrls() {
        IndexNowService.queueUrl("https://test.example.com/cards/2021-22/card-q1.html");
        IndexNowService.queueUrl("https://test.example.com/cards/2021-22/card-q2.html");
        IndexNowService.queueUrls(List.of(
                "https://test.example.com/cards/2021-22/card-q3.html",
                "https://test.example.com/cards/2021-22/card-q4.html"
        ));

        CompletableFuture<Void> future = IndexNowService.flushQueueAsync();
        assertNotNull(future);
        assertDoesNotThrow(() -> future.join());
    }
}
