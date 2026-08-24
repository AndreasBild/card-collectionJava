package de.maulmann;

import java.util.List;
import java.util.Locale;

/**
 * Zero-JS, accessible SVG Sparkline & Price Trend Chart Generator.
 * Pre-computes responsive vector graphs at build-time for instantaneous rendering.
 */
public class SvgSparklineGenerator {

    private static final int DEFAULT_WIDTH = 280;
    private static final int DEFAULT_HEIGHT = 64;
    private static final int PADDING_X = 14;
    private static final int PADDING_Y = 12;

    /**
     * Generates a fully contained, responsive SVG price trend chart.
     */
    public static String generateSparkline(List<PricePoint> points, String chartId) {
        if (points == null || points.isEmpty()) {
            return "";
        }

        int width = DEFAULT_WIDTH;
        int height = DEFAULT_HEIGHT;
        String safeId = chartId != null ? chartId.replaceAll("[^a-zA-Z0-9_-]", "") : "sparkline";

        double minPrice = points.getFirst().price();
        double maxPrice = points.getFirst().price();
        for (PricePoint p : points) {
            if (p.price() < minPrice) minPrice = p.price();
            if (p.price() > maxPrice) maxPrice = p.price();
        }

        double priceRange = maxPrice - minPrice;
        if (priceRange <= 0.001) {
            priceRange = 1.0; // Avoid division by zero for flat trends
        }

        int usableWidth = width - (2 * PADDING_X);
        int usableHeight = height - (2 * PADDING_Y);

        StringBuilder pointsStr = new StringBuilder();
        StringBuilder areaPath = new StringBuilder();
        StringBuilder circles = new StringBuilder();

        PricePoint first = points.getFirst();
        PricePoint last = points.getLast();
        boolean isPositiveTrend = last.price() >= first.price();
        String strokeColor = isPositiveTrend ? "#10b981" : "#ef4444";
        String gradientStart = isPositiveTrend ? "rgba(16, 185, 129, 0.35)" : "rgba(239, 68, 68, 0.35)";
        String gradientEnd = isPositiveTrend ? "rgba(16, 185, 129, 0.0)" : "rgba(239, 68, 68, 0.0)";

        int n = points.size();
        double firstX = PADDING_X;
        double lastX = PADDING_X;

        for (int i = 0; i < n; i++) {
            PricePoint p = points.get(i);
            double x = (n == 1) ? (width / 2.0) : PADDING_X + ((double) i / (n - 1)) * usableWidth;
            double normY = (p.price() - minPrice) / priceRange;
            double y = (height - PADDING_Y) - (normY * usableHeight);

            if (i == 0) firstX = x;
            if (i == n - 1) lastX = x;

            if (i > 0) pointsStr.append(" ");
            pointsStr.append(String.format(Locale.US, "%.1f,%.1f", x, y));

            String formattedPrice = String.format(Locale.US, "$%.2f", p.price());
            String tooltip = p.date() + " | " + formattedPrice + " (" + (p.grade() != null ? p.grade() : "Raw") + ") - " + p.source();

            boolean isLast = (i == n - 1);
            int radius = isLast ? 4 : 2;
            String circleFill = isLast ? strokeColor : "#ffffff";
            String circleStroke = isLast ? "#ffffff" : strokeColor;

            circles.append(String.format(Locale.US,
                    "  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.5\">" +
                            "<title>%s</title></circle>\n",
                    x, y, radius, circleFill, circleStroke, escapeXml(tooltip)));
        }

        // Build closed area path for fill gradient
        areaPath.append(String.format(Locale.US, "M %.1f %.1f L %s L %.1f %.1f Z",
                firstX, (double) height,
                pointsStr,
                lastX, (double) height));

        String ariaLabel = String.format(Locale.US,
                "Price trend chart from $%.2f to $%.2f across %d sales data points.",
                first.price(), last.price(), n);

        return "<svg class=\"price-sparkline-svg\" viewBox=\"0 0 " + width + " " + height + "\" " +
                "width=\"" + width + "\" height=\"" + height + "\" " +
                "role=\"img\" aria-label=\"" + escapeXml(ariaLabel) + "\" preserveAspectRatio=\"none\">\n" +
                "  <defs>\n" +
                "    <linearGradient id=\"grad-" + safeId + "\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\">\n" +
                "      <stop offset=\"0%\" stop-color=\"" + gradientStart + "\" />\n" +
                "      <stop offset=\"100%\" stop-color=\"" + gradientEnd + "\" />\n" +
                "    </linearGradient>\n" +
                "  </defs>\n" +
                "  <path d=\"" + areaPath + "\" fill=\"url(#grad-" + safeId + ")\" />\n" +
                "  <polyline fill=\"none\" stroke=\"" + strokeColor + "\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" points=\"" + pointsStr + "\" />\n" +
                circles +
                "</svg>";
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
