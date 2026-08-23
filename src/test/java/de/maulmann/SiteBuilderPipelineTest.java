package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SiteBuilderPipeline Tests")
class SiteBuilderPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DeploymentMetrics should initialize with zero and allow concurrent atomic accumulation")
    void testDeploymentMetricsAccumulation() {
        SiteBuilderPipeline.DeploymentMetrics metrics = new SiteBuilderPipeline.DeploymentMetrics();

        assertEquals(0, metrics.totalCards.get());
        assertEquals(0, metrics.webFilesUploaded.get());
        assertEquals(0, metrics.rawWebBytes.get());

        metrics.totalCards.addAndGet(1442);
        metrics.webFilesUploaded.addAndGet(50);
        metrics.rawWebBytes.addAndGet(1024 * 1024);
        metrics.compressedWebBytes.addAndGet(256 * 1024);
        metrics.imagesUploaded.addAndGet(100);
        metrics.sitemapsUploaded.addAndGet(35);
        metrics.orphansSwept.addAndGet(3);

        assertEquals(1442, metrics.totalCards.get());
        assertEquals(50, metrics.webFilesUploaded.get());
        assertEquals(1024 * 1024, metrics.rawWebBytes.get());
        assertEquals(256 * 1024, metrics.compressedWebBytes.get());
        assertEquals(100, metrics.imagesUploaded.get());
        assertEquals(35, metrics.sitemapsUploaded.get());
        assertEquals(3, metrics.orphansSwept.get());
    }

    @Test
    @DisplayName("printDeploymentReport should execute without exceptions for both local and prod modes")
    void testPrintDeploymentReport() {
        SiteBuilderPipeline.DeploymentMetrics metrics = new SiteBuilderPipeline.DeploymentMetrics();
        metrics.totalCards.set(100);
        metrics.webFilesUploaded.set(10);
        metrics.webFilesSkipped.set(5);
        metrics.rawWebBytes.set(50000);
        metrics.compressedWebBytes.set(15000);

        assertDoesNotThrow(() -> SiteBuilderPipeline.printDeploymentReport(1234, metrics, false));
        assertDoesNotThrow(() -> SiteBuilderPipeline.printDeploymentReport(5678, metrics, true));
    }
}
