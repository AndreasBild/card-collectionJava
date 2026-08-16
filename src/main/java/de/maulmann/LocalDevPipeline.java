package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
            HttpServer server = SimpleFileServer.createFileServer(
                    new InetSocketAddress(port),
                    outputDir,
                    SimpleFileServer.OutputLevel.INFO
            );
            server.start();
            log.info("🌐 Local preview server running at: http://localhost:{}/", port);
            log.info("   (Press Ctrl+C to stop the server)");
        } catch (Exception e) {
            log.error("Failed to start local dev server: {}", e.getMessage());
        }
    }
}
