package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SvgSparklineGenerator Tests")
class SvgSparklineGeneratorTest {

    @Test
    @DisplayName("Should return empty string when price points list is null or empty")
    void testEmptyOrNullPoints() {
        assertEquals("", SvgSparklineGenerator.generateSparkline(null, "test"));
        assertEquals("", SvgSparklineGenerator.generateSparkline(List.of(), "test"));
    }

    @Test
    @DisplayName("Should render valid SVG with green stroke for positive price trajectory")
    void testPositiveTrajectory() {
        List<PricePoint> points = List.of(
                new PricePoint("2023-01", 50.0, "eBay Sold", "PSA 10"),
                new PricePoint("2024-01", 75.0, "Goldin", "PSA 10"),
                new PricePoint("2025-01", 120.0, "eBay Sold", "PSA 10")
        );

        String svg = SvgSparklineGenerator.generateSparkline(points, "card-123");

        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"), "Must start with <svg tag");
        assertTrue(svg.endsWith("</svg>"), "Must end with </svg> tag");
        assertTrue(svg.contains("#10b981"), "Positive trend must use green stroke #10b981");
        assertTrue(svg.contains("<polyline"), "Must include polyline path for line chart");
        assertTrue(svg.contains("<circle"), "Must render point circles with tooltips");
        assertTrue(svg.contains("eBay Sold"), "Tooltip must contain comp source");
        assertTrue(svg.contains("$120.00"), "Tooltip must contain formatted price");
        assertTrue(svg.contains("role=\"img\""), "Must include accessible role attribute");
    }

    @Test
    @DisplayName("Should render valid SVG with red stroke for negative price trajectory")
    void testNegativeTrajectory() {
        List<PricePoint> points = List.of(
                new PricePoint("2023-01", 100.0, "eBay Sold", "Raw"),
                new PricePoint("2025-01", 60.0, "eBay Sold", "Raw")
        );

        String svg = SvgSparklineGenerator.generateSparkline(points, "card-neg");

        assertNotNull(svg);
        assertTrue(svg.contains("#ef4444"), "Negative trend must use red stroke #ef4444");
    }

    @Test
    @DisplayName("Should handle single data point safely without division by zero")
    void testSinglePoint() {
        List<PricePoint> points = List.of(
                new PricePoint("2025-01", 150.0, "PWCC", "PSA 9")
        );

        String svg = SvgSparklineGenerator.generateSparkline(points, "single");

        assertNotNull(svg);
        assertFalse(svg.isEmpty());
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("$150.00"));
    }
}
