package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Fast Local Development & Preview Pipeline.
 * Builds all HTML, CSS, JS, images, sitemaps, and metadata locally in output/
 * while completely bypassing AWS S3 uploads, compression passes, CloudFront
 * invalidations, and live IndexNow API calls.
 * Usage:
 *   mvn exec:java@local
 *   mvn exec:java@local -Dexec.args="--serve" (starts local web server on <a href="http://localhost:8080">...</a>)
 */
public class LocalDevPipeline {

    private static final Logger log = LoggerFactory.getLogger(LocalDevPipeline.class);
    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        log.info("==================================================");
        log.info("🛠️ STARTING LOCAL DEV PIPELINE (PREVIEW MODE)");
        log.info("==================================================");
        log.info("ℹ️ AWS S3 upload, compression & CloudFront invalidation skipped.");

        FileTracker tracker = new FileTracker(OUTPUT_DIR + "/sync-hashes.properties");
        TimestampTracker timeTracker = new TimestampTracker(OUTPUT_DIR + "/generation-timestamps.properties");

        SiteBuilderPipeline.DeploymentMetrics metrics = new SiteBuilderPipeline.DeploymentMetrics();
        long p1_2Start = System.currentTimeMillis();
        List<CardPageGenerator.CardData> cards = SiteBuilderPipeline.buildLocalArtifacts(timeTracker, tracker);
        metrics.phase1_2Ms = System.currentTimeMillis() - p1_2Start;
        if (cards != null) {
            metrics.totalCards.set(cards.size());
        }

        long duration = System.currentTimeMillis() - startTime;
        SiteBuilderPipeline.printDeploymentReport(duration, metrics, false);

        log.info("📂 Preview pages locally:");
        log.info("   -> Home:            file://{}/index.html", new File(OUTPUT_DIR).getAbsolutePath());
        log.info("   -> Rainbows:        file://{}/rainbows.html", new File(OUTPUT_DIR).getAbsolutePath());
        log.info("   -> Juwan Howard:    file://{}/Juwan-Howard-Collection.html", new File(OUTPUT_DIR).getAbsolutePath());

        boolean serve = false;
        int port = 8081;
        for (String arg : args) {
            if ("--serve".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg) || "serve".equalsIgnoreCase(arg)) {
                serve = true;
            } else if (arg.startsWith("--port=")) {
                try { port = Integer.parseInt(arg.substring(7)); } catch (Exception ignored) {}
            }
        }

        if (serve) {
            startLocalServer(port);
        }
    }

    private static void startLocalServer(int port) {
        try {
            Path outputDir = Paths.get(OUTPUT_DIR).toAbsolutePath();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                try {
                    String rawPath = exchange.getRequestURI().getPath();
                    if (rawPath.equals("/") || rawPath.isEmpty()) {
                        rawPath = "/index.html";
                    }
                    // Prevent path traversal
                    Path resolved = outputDir.resolve(rawPath.startsWith("/") ? rawPath.substring(1) : rawPath).normalize();
                    if (!resolved.startsWith(outputDir) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
                        String notFound = "404 Not Found";
                        byte[] notFoundBytes = notFound.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(404, notFoundBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(notFoundBytes);
                        }
                        return;
                    }

                    long fileSize = Files.size(resolved);
                    String contentType = getMimeType(resolved.getFileName().toString());
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

                    boolean isHead = exchange.getRequestMethod().equalsIgnoreCase("HEAD");
                    if (isHead) {
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                        return;
                    }

                    exchange.sendResponseHeaders(200, fileSize);
                    try (InputStream is = Files.newInputStream(resolved);
                         OutputStream os = exchange.getResponseBody()) {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        os.flush();
                    }
                } catch (Exception e) {
                    try { exchange.close(); } catch (Exception ignored) {}
                }
            });
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            log.info("🌐 Local preview server running at: http://localhost:{}/", port);
            log.info("   (Press Ctrl+C to stop the server)");
        } catch (Exception e) {
            log.error("Failed to start local dev server: {}", e.getMessage());
        }
    }

    private static String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".avif")) return "image/avif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
