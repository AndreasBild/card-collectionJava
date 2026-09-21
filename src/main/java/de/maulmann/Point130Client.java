package de.maulmann;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance client and HTML parser for 130point.com sales comp search.
 * Extracts confirmed completed transactions (auctions, fixed-price, and accepted Best Offers)
 * across eBay, Goldin, and major sports card marketplaces.
 */
public class Point130Client {

    private static final Logger logger = LoggerFactory.getLogger(Point130Client.class);
    private static final String SEARCH_ENDPOINT = "https://back.130point.com/sales/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern GRADE_PATTERN = Pattern.compile("\\b(PSA|BGS|SGC|CGC)\\s*(\\d+(?:\\.\\d+)?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_CARD_NUM_PATTERN = Pattern.compile("(?:#|no\\.?\\s+|card\\s*#?\\s*)([A-Za-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter DATE_PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy")
            .toFormatter(Locale.ENGLISH);

    private final HttpClient httpClient;

    public record CardCompResult(
            List<PricePoint> comps,
            Double estimatedValue,
            Double lastSoldPrice,
            String lastSoldDate
    ) {}

    public Point130Client() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public Point130Client(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Builds an optimized search query for a card entity.
     */
    public static String buildSearchQuery(CardData card) {
        if (card == null) return "";
        StringBuilder sb = new StringBuilder();

        String season = card.get("Season");
        if (season != null && !season.isBlank()) {
            // Use 4-digit start year if season is e.g. "1994-95" -> "1994"
            String startYear = season.contains("-") ? season.split("-")[0].trim() : season.trim();
            sb.append(startYear).append(" ");
        }

        String player = card.get("Player");
        if (player != null && !player.isBlank()) {
            sb.append(player.trim()).append(" ");
        }

        String brand = card.get("Brand");
        if (brand != null && !brand.isBlank()) {
            sb.append(brand.trim()).append(" ");
        }

        String theme = card.get("Theme");
        String variant = card.get("Variant");
        boolean isLegacy = variant != null && variant.toLowerCase(Locale.ROOT).contains("legacy");

        if (theme != null && !theme.isBlank() && !"Base Set".equalsIgnoreCase(theme.trim()) && !isLegacy) {
            String themeTrimmed = theme.trim();
            if (brand == null || !brand.toLowerCase(Locale.ROOT).contains(themeTrimmed.toLowerCase(Locale.ROOT))) {
                sb.append(themeTrimmed).append(" ");
            }
        }

        String cardNumber = card.get("Number");
        if (cardNumber != null && !cardNumber.isBlank()) {
            String num = cardNumber.trim();
            if (num.toUpperCase(Locale.ROOT).startsWith("ROW")) {
                Matcher rowMatcher = Pattern.compile("(?i)(row\\s*\\d+)").matcher(num);
                if (rowMatcher.find()) {
                    sb.append(rowMatcher.group(1)).append(" ");
                } else {
                    sb.append(num).append(" ");
                }
            } else if (num.startsWith("#")) {
                sb.append(num).append(" ");
            } else {
                sb.append("#").append(num).append(" ");
            }
        }

        if (variant != null && !variant.isBlank() && !"Base".equalsIgnoreCase(variant.trim())) {
            String varTrimmed = variant.trim();
            if (theme == null || !theme.equalsIgnoreCase(varTrimmed)) {
                sb.append(varTrimmed).append(" ");
            }
        }

        String printRun = card.get("Print Run");
        if (printRun != null && !printRun.isBlank() && !"null".equalsIgnoreCase(printRun.trim()) && !"-".equals(printRun.trim())) {
            String cleanPrintRun = printRun.trim().replaceAll("[^0-9]", "");
            if (!cleanPrintRun.isBlank()) {
                sb.append("/").append(cleanPrintRun).append(" ");
            }
        }

        String grader = card.get("Grading Co.");
        String grade = card.get("Grade");
        if (grader != null && !grader.isBlank() && grade != null && !grade.isBlank()) {
            sb.append(grader.trim()).append(" ").append(grade.trim()).append(" ");
        }

        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Queries 130point for market sales matching the card and calculates pricing analytics.
     */
    public Optional<CardCompResult> fetchComps(CardData card) {
        String query = buildSearchQuery(card);
        if (query.isBlank()) {
            return Optional.empty();
        }
        return fetchCompsForQuery(query, card);
    }

    /**
     * Executes HTTP POST query to 130point and parses returned transaction table.
     * Uses curl ProcessBuilder as primary transport to bypass Cloudflare Error 1015 TLS fingerprint blocks,
     * with transparent fallback to standard Java HttpClient.
     */
    public Optional<CardCompResult> fetchCompsForQuery(String query, CardData referenceCard) {
        String html = fetchRawHtml(query);
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parseSalesHtml(html, referenceCard));
    }

    private String fetchRawHtml(String query) {
        // 1. Try curl transport first (bypasses Cloudflare 1015 TLS handshake bot challenge)
        String curlResult = fetchViaCurl(query);
        if (curlResult != null && !curlResult.isBlank() && !curlResult.contains("error code: 1015")) {
            return curlResult;
        }

        // 2. Transparent fallback to Java HttpClient
        return fetchViaHttpClient(query);
    }

    private String fetchViaCurl(String query) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-m", "60", "-X", "POST", SEARCH_ENDPOINT,
                    "-H", "User-Agent: " + USER_AGENT,
                    "-H", "Origin: https://130point.com",
                    "-H", "Referer: https://130point.com/sales/",
                    "-H", "X-Requested-With: XMLHttpRequest",
                    "--data-urlencode", "query=" + query
            );
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && sb.length() > 0) {
                return sb.toString();
            }
        } catch (Exception e) {
            logger.debug("curl transport unavailable, falling back to HttpClient: {}", e.getMessage());
        }
        return null;
    }

    private String fetchViaHttpClient(String query) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String formBody = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SEARCH_ENDPOINT))
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("Accept", "text/html, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://130point.com")
                        .header("Referer", "https://130point.com/sales/")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-site")
                        .timeout(Duration.ofSeconds(45))
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429 || response.statusCode() == 403 || response.statusCode() == 503) {
                    logger.warn("130point HTTP {} for [{}]. Backing off with jitter (attempt {}/{})...", response.statusCode(), query, attempt, maxAttempts);
                    if (attempt < maxAttempts) {
                        try {
                            long jitter = (long) (Math.random() * 1000);
                            TimeUnit.MILLISECONDS.sleep(4500L * attempt + jitter);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        continue;
                    }
                    return null;
                }

                if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                    logger.warn("130point query [{}] failed with HTTP status {}", query, response.statusCode());
                    return null;
                }

                return response.body();
            } catch (IOException e) {
                if (e instanceof java.net.http.HttpTimeoutException || (e.getMessage() != null && e.getMessage().contains("timed out"))) {
                    logger.warn("130point query timed out for [{}] (attempt {}/{}). Retrying with backoff...", query, attempt, maxAttempts);
                } else {
                    logger.warn("I/O error querying 130point for [{}]: {}", query, e.getMessage());
                }
                if (attempt < maxAttempts) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("130point request interrupted for [{}]", query);
                return null;
            }
        }
        return null;
    }

    /**
     * Parses the raw HTML snippet returned by 130point into structured PricePoint records.
     */
    public CardCompResult parseSalesHtml(String html, CardData referenceCard) {
        if (html == null || html.isBlank()) {
            return new CardCompResult(List.of(), null, null, null);
        }

        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("tr[id=dRow], tr[data-price]");

        List<PricePoint> comps = new ArrayList<>();

        for (Element row : rows) {
            String priceStr = row.attr("data-price");
            if (priceStr.isBlank()) {
                // Fallback to currencyData span
                Element priceEl = row.selectFirst("span#0-1-price, span.currencyData");
                if (priceEl != null) priceStr = priceEl.text().trim();
            }

            double price;
            try {
                // Robustly clean currency symbols, commas, and trailing noise
                String sanitized = priceStr.replaceAll("[^0-9.]", "").trim();
                if (sanitized.isEmpty()) continue;
                price = Double.parseDouble(sanitized);
            } catch (Exception e) {
                continue;
            }

            if (price <= 0.0) continue;

            Element titleEl = row.selectFirst("#titleText, td#dCol span#titleText a");
            String title = (titleEl != null) ? titleEl.text().trim() : "";

            // Check relevance against card details
            if (!isRelevantMatch(title, referenceCard)) {
                continue;
            }

            Element dateEl = row.selectFirst("#dateText, span#dateText");
            String dateText = (dateEl != null) ? dateEl.text() : "";
            String isoDate = parseDateToIso(dateText);

            Element auctionLabel = row.selectFirst("#auctionLabel, span#auctionLabel");
            String saleType = (auctionLabel != null) ? auctionLabel.text().trim() : "eBay Comp";

            String detectedGrade = extractGrade(title);
            if (detectedGrade == null && referenceCard != null) {
                detectedGrade = referenceCard.get("Grade");
            }

            comps.add(new PricePoint(isoDate, price, "eBay (" + saleType + ")", detectedGrade));
        }

        // Sort chronologically ascending (oldest to newest) null-safe
        comps.sort(Comparator.comparing(PricePoint::date, Comparator.nullsFirst(Comparator.naturalOrder())));

        if (comps.isEmpty()) {
            return new CardCompResult(List.of(), null, null, null);
        }

        // Calculate analytics
        PricePoint latest = comps.getLast();
        Double lastSold = latest.price();
        String lastDate = latest.date();

        // Estimated Value (FMV) = Trimmed median with IQR outlier rejection
        Double estimatedValue = calculateTrimmedFmv(comps);

        return new CardCompResult(Collections.unmodifiableList(comps), estimatedValue, lastSold, lastDate);
    }

    /**
     * Validates that a listing title genuinely corresponds to the target card entity.
     */
    public boolean isRelevantMatch(String title, CardData referenceCard) {
        if (title == null || title.isBlank()) return true;
        if (referenceCard == null) return true;

        String lowerTitle = title.toLowerCase(Locale.ROOT);

        // Check player name if present
        String player = referenceCard.get("Player");
        if (player != null && !player.isBlank()) {
            String[] parts = player.toLowerCase(Locale.ROOT).split("\\s+");
            // Check last name at least
            String lastName = parts[parts.length - 1];
            if (!lowerTitle.contains(lastName)) {
                return false;
            }
        }

        // Check card number if present and non-empty
        String number = referenceCard.get("Number");
        if (number != null && !number.isBlank() && !"-".equals(number.trim()) && !"null".equalsIgnoreCase(number.trim())) {
            String cleanNum = number.replace("#", "").trim();
            if (!cleanNum.isEmpty()) {
                // 1. If title explicitly declares card numbers (e.g. "#45" or "card 45"), check them
                Matcher explicitMatcher = EXPLICIT_CARD_NUM_PATTERN.matcher(title);
                boolean foundAnyExplicit = false;
                boolean matchedExplicit = false;
                while (explicitMatcher.find()) {
                    foundAnyExplicit = true;
                    String candidateNum = explicitMatcher.group(1).trim();
                    if (isCardNumberMatch(cleanNum, candidateNum)) {
                        matchedExplicit = true;
                        break;
                    }
                }

                if (foundAnyExplicit) {
                    if (!matchedExplicit) {
                        // Title explicitly has a different card number, reject
                        return false;
                    }
                } else {
                    // 2. No explicit "#" found. Strip grades (PSA 10), print runs (/25), years (1994), and lot sizes
                    String strippedTitle = GRADE_PATTERN.matcher(title).replaceAll(" ");
                    strippedTitle = Pattern.compile("/\\d+").matcher(strippedTitle).replaceAll(" ");
                    strippedTitle = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b").matcher(strippedTitle).replaceAll(" ");
                    strippedTitle = Pattern.compile("(?i)\\blot\\s+of\\s+\\d+\\b").matcher(strippedTitle).replaceAll(" ");

                    String targetDigits = cleanNum.replaceAll("[^0-9]", "");
                    Matcher seatMatcher = Pattern.compile("(?i)\\bseat\\s*(\\d+)\\b").matcher(cleanNum);
                    if (seatMatcher.find()) {
                        targetDigits = seatMatcher.group(1);
                    }
                    String patternNum = Pattern.quote(cleanNum);
                    if (!targetDigits.isEmpty()) {
                        patternNum = "(?:" + Pattern.quote(cleanNum) + "|\\b" + Pattern.quote(targetDigits) + ")";
                    }
                    Pattern numPattern = Pattern.compile("\\b" + patternNum + "\\b", Pattern.CASE_INSENSITIVE);
                    if (!numPattern.matcher(strippedTitle).find()) {
                        return false;
                    }
                }
            }
        }

        // Row alignment check for Flair Showcase
        Matcher cardRowMatcher = Pattern.compile("(?i)\\brow\\s*(\\d+)\\b").matcher(number != null ? number : "");
        if (cardRowMatcher.find()) {
            String cardRow = cardRowMatcher.group(1);
            Matcher titleRowMatcher = Pattern.compile("(?i)\\brow\\s*(\\d+)\\b").matcher(title);
            if (titleRowMatcher.find()) {
                String titleRow = titleRowMatcher.group(1);
                if (!cardRow.equals(titleRow)) {
                    return false;
                }
            }
        }

        // Parallel & Variant Alignment
        String variant = referenceCard.get("Variant");
        String theme = referenceCard.get("Theme");
        String printRun = referenceCard.get("Print Run");

        String varLower = variant != null ? variant.toLowerCase(Locale.ROOT) : "";
        String themeLower = theme != null ? theme.toLowerCase(Locale.ROOT) : "";

        boolean isPmg = varLower.contains("pmg") || varLower.contains("precious metal") || themeLower.contains("precious metal");
        boolean isLegacy = varLower.contains("legacy") || themeLower.contains("legacy");
        boolean isRuby = varLower.contains("ruby") || varLower.contains("rubies");
        boolean isEmerald = varLower.contains("emerald");
        boolean isRefractor = varLower.contains("refractor") || themeLower.contains("refractor");
        boolean isSuperfractor = varLower.contains("superfractor");
        boolean is1of1 = varLower.contains("1/1") || varLower.contains("masterpiece") || "1".equals(printRun);
        boolean isCredentials = varLower.contains("credentials");
        boolean isPlatinum = varLower.contains("platinum");
        boolean isGold = varLower.contains("gold") && !varLower.contains("marigold");
        boolean isSilver = varLower.contains("silver");

        boolean isHighEndParallel = isPmg || isLegacy || isRuby || isEmerald || isSuperfractor || is1of1 || isCredentials;

        if (isHighEndParallel) {
            if (isPmg && !lowerTitle.contains("pmg") && !lowerTitle.contains("precious metal")) {
                return false;
            }
            if (isLegacy && !lowerTitle.contains("legacy")) {
                return false;
            }
            if (isRuby && !lowerTitle.contains("ruby") && !lowerTitle.contains("rubies")) {
                return false;
            }
            if (isEmerald && !lowerTitle.contains("emerald")) {
                return false;
            }
            if (isSuperfractor && !lowerTitle.contains("superfractor")) {
                return false;
            }
            if (is1of1 && !lowerTitle.contains("1/1") && !lowerTitle.contains("1 of 1") && !lowerTitle.contains("masterpiece")) {
                return false;
            }
            if (isCredentials && !lowerTitle.contains("credentials")) {
                return false;
            }

            // Differentiate PMG Red vs PMG Green
            if (isPmg) {
                if (varLower.contains("green") && lowerTitle.contains("red") && !lowerTitle.contains("green")) {
                    return false;
                }
                if (varLower.contains("red") && lowerTitle.contains("green") && !lowerTitle.contains("red")) {
                    return false;
                }
            }
        } else {
            // General refractor / metallic parallel checks
            if (isRefractor && !lowerTitle.contains("refractor")) {
                return false;
            }
            if (isPlatinum && !lowerTitle.contains("platinum")) {
                return false;
            }
            if (isGold && !lowerTitle.contains("gold")) {
                return false;
            }
            if (isSilver && !lowerTitle.contains("silver")) {
                return false;
            }
        }

        // Conversely, if reference card is a BASE card, reject listings that are for rare parallels
        boolean isBase = (variant == null || variant.isBlank() || "Base".equalsIgnoreCase(variant))
                && (theme == null || theme.isBlank() || "Base Set".equalsIgnoreCase(theme) || "Base".equalsIgnoreCase(theme))
                && (printRun == null || printRun.isBlank() || "-".equals(printRun) || "null".equalsIgnoreCase(printRun));

        if (isBase) {
            if (lowerTitle.contains("pmg") || lowerTitle.contains("precious metal")
                    || lowerTitle.contains("legacy collection") || lowerTitle.contains("legacy")
                    || lowerTitle.contains("refractor") || lowerTitle.contains("ruby")
                    || lowerTitle.contains("emerald") || lowerTitle.contains("credentials")
                    || lowerTitle.contains("superfractor") || lowerTitle.contains("masterpiece")
                    || lowerTitle.contains("1/1") || lowerTitle.contains("1 of 1")) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCardNumberMatch(String target, String candidate) {
        if (target == null || candidate == null) return false;
        if (target.equalsIgnoreCase(candidate)) return true;

        String targetClean = target;
        Matcher seatMatcher = Pattern.compile("(?i)\\bseat\\s*(\\d+)\\b").matcher(target);
        if (seatMatcher.find()) {
            targetClean = seatMatcher.group(1);
        }

        String targetDigits = targetClean.replaceAll("[^0-9]", "");
        String candDigits = candidate.replaceAll("[^0-9]", "");
        if (!targetDigits.isEmpty() && !candDigits.isEmpty()) {
            if (targetDigits.equals(candDigits)) return true;
            try {
                return Long.parseLong(targetDigits) == Long.parseLong(candDigits);
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    /**
     * Parses dates like "Sun 14 Jun 2026 17:05:33 GMT" or "2026-06-14" into ISO format.
     */
    public static String parseDateToIso(String rawDateText) {
        if (rawDateText == null || rawDateText.isBlank()) {
            return null;
        }

        Matcher m = DATE_PATTERN.matcher(rawDateText);
        if (m.find()) {
            String day = m.group(1);
            String month = m.group(2);
            String year = m.group(3);
            String dateStr = day + " " + month + " " + year;
            try {
                LocalDate parsed = LocalDate.parse(dateStr, DATE_PARSER);
                return parsed.toString();
            } catch (Exception ignored) {
            }
        }

        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(rawDateText);
        if (isoMatcher.find()) {
            return isoMatcher.group(1);
        }

        return null;
    }

    /**
     * Detects grading company and grade in the listing title (e.g. "PSA 10", "BGS 9.5").
     */
    private static String extractGrade(String title) {
        if (title == null) return null;
        Matcher m = GRADE_PATTERN.matcher(title);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT) + " " + m.group(2);
        }
        return null;
    }

    /**
     * Calculates Fair Market Value as the IQR-filtered trimmed median of recent comps.
     * Prevents single extreme anomalies (counterfeits, mislabeled reprints) from distorting valuations.
     */
    public static Double calculateTrimmedFmv(List<PricePoint> comps) {
        if (comps == null || comps.isEmpty()) return null;

        int count = Math.min(8, comps.size());
        List<Double> recentPrices = new ArrayList<>();
        for (int i = comps.size() - count; i < comps.size(); i++) {
            recentPrices.add(comps.get(i).price());
        }
        recentPrices.sort(Double::compareTo);

        // For 4 or more sales points, apply Interquartile Range (IQR) filtering
        List<Double> filteredPrices;
        if (recentPrices.size() >= 4) {
            double q1 = getPercentile(recentPrices, 25.0);
            double q3 = getPercentile(recentPrices, 75.0);
            double iqr = q3 - q1;
            double lowerBound = Math.max(0.0, q1 - (1.5 * iqr));
            double upperBound = q3 + (1.5 * iqr);

            filteredPrices = recentPrices.stream()
                    .filter(p -> p >= lowerBound && p <= upperBound)
                    .toList();

            if (filteredPrices.isEmpty()) {
                filteredPrices = recentPrices;
            }
        } else {
            filteredPrices = recentPrices;
        }

        if (filteredPrices.size() % 2 == 1) {
            return filteredPrices.get(filteredPrices.size() / 2);
        } else {
            int mid = filteredPrices.size() / 2;
            return (filteredPrices.get(mid - 1) + filteredPrices.get(mid)) / 2.0;
        }
    }

    private static double getPercentile(List<Double> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0.0;
        if (sortedList.size() == 1) return sortedList.getFirst();
        double rank = (percentile / 100.0) * (sortedList.size() - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        if (lowerIndex == upperIndex) {
            return sortedList.get(lowerIndex);
        }
        double weight = rank - lowerIndex;
        return sortedList.get(lowerIndex) * (1.0 - weight) + sortedList.get(upperIndex) * weight;
    }
}
