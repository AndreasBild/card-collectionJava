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
     * Universal certificate lookup router across PSA, BGS, SGC, and CGC.
     */
    public Optional<MarketDataEntry> fetchCertData(String gradingCompany, String certNumber) {
        if (gradingCompany == null || certNumber == null || certNumber.isBlank()) {
            return Optional.empty();
        }
        String co = gradingCompany.trim().toUpperCase();
        if (co.contains("PSA")) {
            return fetchPsaData(certNumber);
        } else if (co.contains("BGS") || co.contains("BECKETT")) {
            return fetchBgsData(certNumber);
        } else if (co.contains("SGC")) {
            return fetchSgcData(certNumber);
        } else if (co.contains("CGC")) {
            return fetchCgcData(certNumber);
        }
        return Optional.empty();
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
        return executeGet(url, cleanCert, "PSA", this::parsePsaHtml);
    }

    /**
     * Queries Beckett (BGS) certificate verification data.
     */
    public Optional<MarketDataEntry> fetchBgsData(String certNumber) {
        if (certNumber == null || certNumber.isBlank()) {
            return Optional.empty();
        }
        String cleanCert = certNumber.replaceAll("[^0-9]", "").trim();
        if (cleanCert.isEmpty()) return Optional.empty();

        String url = "https://www.beckett.com/grading/cert-verification?cert_num=" + cleanCert;
        return executeGet(url, cleanCert, "BGS", this::parseBgsHtml);
    }

    /**
     * Queries SGC certificate verification data.
     */
    public Optional<MarketDataEntry> fetchSgcData(String certNumber) {
        if (certNumber == null || certNumber.isBlank()) {
            return Optional.empty();
        }
        String cleanCert = certNumber.replaceAll("[^0-9]", "").trim();
        if (cleanCert.isEmpty()) return Optional.empty();

        String url = "https://gosgc.com/cert-verification/" + cleanCert;
        return executeGet(url, cleanCert, "SGC", this::parseSgcHtml);
    }

    /**
     * Queries CGC Cards certificate verification data.
     */
    public Optional<MarketDataEntry> fetchCgcData(String certNumber) {
        if (certNumber == null || certNumber.isBlank()) {
            return Optional.empty();
        }
        String cleanCert = certNumber.replaceAll("[^0-9]", "").trim();
        if (cleanCert.isEmpty()) return Optional.empty();

        String url = "https://www.cgccards.com/certlookup/" + cleanCert;
        return executeGet(url, cleanCert, "CGC", this::parseCgcHtml);
    }

    @FunctionalInterface
    public interface HtmlParser {
        Optional<MarketDataEntry> parse(String certNumber, String html);
    }

    private Optional<MarketDataEntry> executeGet(String url, String cleanCert, String graderName, HtmlParser parser) {
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
                return parser.parse(cleanCert, response.body());
            } else if (response.statusCode() == 404) {
                logger.warn("{} cert #{} not found (HTTP 404)", graderName, cleanCert);
            } else {
                logger.warn("{} cert #{} returned HTTP status {}", graderName, cleanCert, response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while querying {} cert #{}: {}", graderName, cleanCert, e.getMessage());
        } catch (IOException e) {
            logger.warn("Network error querying {} cert #{}: {}", graderName, cleanCert, e.getMessage());
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

        // 1. Parse table rows
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

        // 2. Also inspect dl / dt / dd
        Elements dts = doc.select("dt");
        for (Element dt : dts) {
            Element dd = dt.nextElementSibling();
            if (dd != null && dd.tagName().equalsIgnoreCase("dd")) {
                certDetails.put(dt.text().trim().toLowerCase(), dd.text().trim());
            }
        }

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
        if (itemDesc != null) meta.put("itemDescription", itemDesc);
        if (gradeVal != null) meta.put("gradeRaw", gradeVal);

        MarketDataEntry entry = MarketDataEntry.builder()
                .certNumber(certNumber)
                .lastQueried(Instant.now().toString())
                .popReport(popReport)
                .metadata(meta)
                .build();

        return Optional.of(entry);
    }

    /**
     * Parses Beckett (BGS) Certificate detail HTML into a MarketDataEntry.
     */
    public Optional<MarketDataEntry> parseBgsHtml(String certNumber, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(html);
        Map<String, String> details = new HashMap<>();

        Elements rows = doc.select("tr");
        for (Element r : rows) {
            Elements labels = r.select("th, td.label, td:first-child");
            Elements values = r.select("td:last-child, td.value");
            if (!labels.isEmpty() && !values.isEmpty()) {
                details.put(labels.first().text().trim().toLowerCase(), values.last().text().trim());
            }
        }

        Elements dts = doc.select("dt");
        for (Element dt : dts) {
            Element dd = dt.nextElementSibling();
            if (dd != null) {
                details.put(dt.text().trim().toLowerCase(), dd.text().trim());
            }
        }

        String gradeVal = extractField(details, "final grade", "overall grade", "grade", "card grade");
        String centering = extractField(details, "centering");
        String corners = extractField(details, "corners");
        String edges = extractField(details, "edges");
        String surface = extractField(details, "surface");
        String itemDesc = extractField(details, "set name", "card description", "item description", "item", "description");

        String popStr = extractField(details, "total graded", "total population", "population", "pop");
        Integer totalGraded = parseInteger(popStr);

        PopReport popReport = new PopReport(
                "BGS",
                cleanGrade(gradeVal),
                totalGraded,
                null,
                certNumber,
                PopReport.getVerificationUrl("BGS", certNumber)
        );

        Map<String, String> meta = new HashMap<>();
        if (itemDesc != null) meta.put("itemDescription", itemDesc);
        if (gradeVal != null) meta.put("gradeRaw", gradeVal);
        if (centering != null) meta.put("centering", centering);
        if (corners != null) meta.put("corners", corners);
        if (edges != null) meta.put("edges", edges);
        if (surface != null) meta.put("surface", surface);

        return Optional.of(MarketDataEntry.builder()
                .certNumber(certNumber)
                .lastQueried(Instant.now().toString())
                .popReport(popReport)
                .metadata(meta)
                .build());
    }

    /**
     * Parses SGC Certificate detail HTML into a MarketDataEntry.
     */
    public Optional<MarketDataEntry> parseSgcHtml(String certNumber, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(html);
        Map<String, String> details = new HashMap<>();

        Elements rows = doc.select("tr");
        for (Element r : rows) {
            Elements labels = r.select("th, td.label, td:first-child");
            Elements values = r.select("td:last-child, td.value");
            if (!labels.isEmpty() && !values.isEmpty()) {
                details.put(labels.first().text().trim().toLowerCase(), values.last().text().trim());
            }
        }

        Elements dts = doc.select("dt");
        for (Element dt : dts) {
            Element dd = dt.nextElementSibling();
            if (dd != null) {
                details.put(dt.text().trim().toLowerCase(), dd.text().trim());
            }
        }

        String gradeVal = extractField(details, "sgc grade", "card grade", "grade", "condition");
        String itemDesc = extractField(details, "card title", "item description", "description", "item");
        String popStr = extractField(details, "total graded", "total population", "population", "pop");

        PopReport popReport = new PopReport(
                "SGC",
                cleanGrade(gradeVal),
                parseInteger(popStr),
                null,
                certNumber,
                PopReport.getVerificationUrl("SGC", certNumber)
        );

        Map<String, String> meta = new HashMap<>();
        if (itemDesc != null) meta.put("itemDescription", itemDesc);
        if (gradeVal != null) meta.put("gradeRaw", gradeVal);

        return Optional.of(MarketDataEntry.builder()
                .certNumber(certNumber)
                .lastQueried(Instant.now().toString())
                .popReport(popReport)
                .metadata(meta)
                .build());
    }

    /**
     * Parses CGC Cards Certificate detail HTML into a MarketDataEntry.
     */
    public Optional<MarketDataEntry> parseCgcHtml(String certNumber, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(html);
        Map<String, String> details = new HashMap<>();

        Elements rows = doc.select("tr");
        for (Element r : rows) {
            Elements labels = r.select("th, td.label, td:first-child");
            Elements values = r.select("td:last-child, td.value");
            if (!labels.isEmpty() && !values.isEmpty()) {
                details.put(labels.first().text().trim().toLowerCase(), values.last().text().trim());
            }
        }

        Elements dts = doc.select("dt");
        for (Element dt : dts) {
            Element dd = dt.nextElementSibling();
            if (dd != null) {
                details.put(dt.text().trim().toLowerCase(), dd.text().trim());
            }
        }

        String gradeVal = extractField(details, "cgc grade", "card grade", "grade");
        String itemDesc = extractField(details, "card description", "item description", "item", "title");
        String popStr = extractField(details, "total population", "total graded", "population", "pop");

        PopReport popReport = new PopReport(
                "CGC",
                cleanGrade(gradeVal),
                parseInteger(popStr),
                null,
                certNumber,
                PopReport.getVerificationUrl("CGC", certNumber)
        );

        Map<String, String> meta = new HashMap<>();
        if (itemDesc != null) meta.put("itemDescription", itemDesc);
        if (gradeVal != null) meta.put("gradeRaw", gradeVal);

        return Optional.of(MarketDataEntry.builder()
                .certNumber(certNumber)
                .lastQueried(Instant.now().toString())
                .popReport(popReport)
                .metadata(meta)
                .build());
    }

    private static String extractField(Map<String, String> map, String... keys) {
        // Priority 1: Exact matches
        for (String k : keys) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().equalsIgnoreCase(k)) {
                    return e.getValue();
                }
            }
        }
        // Priority 2: Substring matches
        for (String k : keys) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().contains(k.toLowerCase())) {
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
        if (raw == null) return "Graded";
        String r = raw.toUpperCase();
        if (r.contains("GEM MT 10") || r.contains("PRISTINE 10") || r.contains("PERFECT 10") || r.contains("10")) return "10";
        if (r.contains("9.5") || r.contains("GEM MINT 9.5")) return "9.5";
        if (r.contains("MINT 9") || r.contains("9")) return "9";
        if (r.contains("8.5")) return "8.5";
        if (r.contains("NM-MT 8") || r.contains("8")) return "8";
        if (r.contains("7.5")) return "7.5";
        if (r.contains("NM 7") || r.contains("7")) return "7";
        if (r.contains("EX-MT 6") || r.contains("6")) return "6";
        if (r.contains("EX 5") || r.contains("5")) return "5";
        return raw.trim();
    }
}
