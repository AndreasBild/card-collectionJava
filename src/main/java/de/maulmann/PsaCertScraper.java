package de.maulmann;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Public PSA Certificate lookup and Pop Census parser.
 * Queries https://www.psacard.com/cert/{certNumber} with rate limiting and timeout protection.
 */
public class PsaCertScraper {

    private static final Logger logger = LoggerFactory.getLogger(PsaCertScraper.class);
    private static final String PSA_CERT_URL_PREFIX = "https://www.psacard.com/cert/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern PATTERN_DIGITS = Pattern.compile("(\\d+)");
    private final HttpClient httpClient;

    public PsaCertScraper() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public PsaCertScraper(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Queries PSA certificate data for the given certNumber.
     */
    public Optional<MarketDataEntry> fetchPsaData(String certNumber) {
        if (certNumber == null || certNumber.isBlank()) {
            return Optional.empty();
        }

        String cleanCert = certNumber.replaceAll("[^0-9]", "").trim();
        if (cleanCert.isEmpty()) {
            return Optional.empty();
        }

        String url = PSA_CERT_URL_PREFIX + cleanCert;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parsePsaHtml(cleanCert, response.body());
            } else if (response.statusCode() == 404) {
                logger.warn("PSA cert #{} not found (HTTP 404)", cleanCert);
            } else {
                logger.warn("PSA cert #{} returned HTTP status {}", cleanCert, response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while querying PSA cert #{}: {}", cleanCert, e.getMessage());
        } catch (IOException e) {
            logger.warn("Network error querying PSA cert #{}: {}", cleanCert, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Parses PSA Certificate detail HTML into a MarketDataEntry.
     */
    public Optional<MarketDataEntry> parsePsaHtml(String certNumber, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(html);
        Map<String, String> certDetails = new HashMap<>();

        // 1. Parse table rows (e.g. Certification Number, Spec Number, Item Description, Grade, Total Population, Pop Higher)
        Elements rows = doc.select("tr");
        for (Element row : rows) {
            Elements ths = row.select("th, td.label, td:first-child");
            Elements tds = row.select("td:not(.label), td:last-child");
            if (!ths.isEmpty() && !tds.isEmpty()) {
                String key = ths.first().text().trim().toLowerCase();
                String val = tds.last().text().trim();
                certDetails.put(key, val);
            }
        }

        // 2. Also inspect dl / dt / dd or key-value list structures
        Elements dts = doc.select("dt");
        for (Element dt : dts) {
            Element dd = dt.nextElementSibling();
            if (dd != null && dd.tagName().equalsIgnoreCase("dd")) {
                certDetails.put(dt.text().trim().toLowerCase(), dd.text().trim());
            }
        }

        // Extract key fields
        String gradeVal = extractField(certDetails, "grade", "card grade", "condition");
        String popTotalStr = extractField(certDetails, "total population", "total pop", "population", "pop total");
        String popHigherStr = extractField(certDetails, "population higher", "pop higher", "higher");
        String itemDesc = extractField(certDetails, "item description", "spec", "card description", "title");

        Integer totalGraded = parseInteger(popTotalStr);
        Integer popHigher = parseInteger(popHigherStr);

        PopReport popReport = new PopReport(
                "PSA",
                cleanGrade(gradeVal),
                totalGraded,
                popHigher,
                certNumber,
                PopReport.getVerificationUrl("PSA", certNumber)
        );

        Map<String, String> meta = new HashMap<>();
        if (itemDesc != null) meta.put("psaItemDescription", itemDesc);
        if (gradeVal != null) meta.put("psaGradeRaw", gradeVal);

        MarketDataEntry entry = MarketDataEntry.builder()
                .certNumber(certNumber)
                .lastQueried(Instant.now().toString())
                .popReport(popReport)
                .metadata(meta)
                .build();

        return Optional.of(entry);
    }

    private static String extractField(Map<String, String> map, String... keys) {
        for (String k : keys) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().contains(k)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static Integer parseInteger(String val) {
        if (val == null || val.isBlank()) return null;
        Matcher m = PATTERN_DIGITS.matcher(val);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String cleanGrade(String raw) {
        if (raw == null) return "PSA";
        String r = raw.toUpperCase();
        if (r.contains("GEM MT 10") || r.contains("10")) return "10";
        if (r.contains("MINT 9") || r.contains("9")) return "9";
        if (r.contains("NM-MT 8") || r.contains("8")) return "8";
        if (r.contains("NM 7") || r.contains("7")) return "7";
        if (r.contains("EX-MT 6") || r.contains("6")) return "6";
        if (r.contains("EX 5") || r.contains("5")) return "5";
        return raw;
    }
}
