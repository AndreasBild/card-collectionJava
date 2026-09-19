package de.maulmann;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Scraper and enricher for MrTorte Beckett Book Value (BV) data.
 * Fetches Juwan Howard collection checklists from mrtorte.de with polite HTTP requests
 * and persists enriched valuations to content/json/beckett-values.json.
 */
public class MrtorteBeckettEnricher {

    private static final Logger logger = LoggerFactory.getLogger(MrtorteBeckettEnricher.class);

    private static final String HAVELIST_URL = "https://mrtorte.de/index.php?id=7&menue=2&per_page=644&page_number=1&suche=";
    private static final String COMMONS_URL = "https://mrtorte.de/index.php?id=145&menue=16&per_page=756&page_number=1&suche=";

    private static final Pattern PATTERN_SEASON = Pattern.compile("^(\\d{4}(?:-\\d{2})?)");
    private static final Pattern PATTERN_CARD_NUM = Pattern.compile("#([A-Za-z0-9\\-/]+)");

    private final HttpClient httpClient;

    public MrtorteBeckettEnricher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public record ScrapedCard(
            String cardName,
            Double beckettValue,
            String serialNumber,
            String printRun,
            String team,
            String category
    ) {}

    public List<ScrapedCard> scrapeAll() throws IOException, InterruptedException {
        List<ScrapedCard> all = new ArrayList<>();

        logger.info("Fetching Havelist (Hits/Numbered) from {}", HAVELIST_URL);
        String htmlHits = fetch(HAVELIST_URL);
        List<ScrapedCard> hits = parseHits(htmlHits);
        logger.info("Parsed {} cards from Havelist", hits.size());
        all.addAll(hits);

        // Polite delay
        Thread.sleep(1000);

        logger.info("Fetching Commons & Inserts from {}", COMMONS_URL);
        String htmlCommons = fetch(COMMONS_URL);
        List<ScrapedCard> commons = parseCommons(htmlCommons);
        logger.info("Parsed {} cards from Commons", commons.size());
        all.addAll(commons);

        logger.info("Total cards scraped from MrTorte: {}", all.size());
        return all;
    }

    public String fetch(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP error " + resp.statusCode() + " fetching " + url);
        }
        return resp.body();
    }

    public List<ScrapedCard> parseHits(String html) {
        List<ScrapedCard> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table.table");
        if (table == null) return list;

        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() >= 5) {
                String cardName = cells.get(0).text().trim();
                String serialNumber = cells.get(1).text().trim();
                String printRun = cells.get(2).text().trim();
                String team = cells.get(3).text().trim();
                String rawBv = cells.get(4).text().trim();

                Double bv = parseBv(rawBv);
                String cleanSerial = "one".equalsIgnoreCase(serialNumber) ? null : serialNumber;
                String cleanPrintRun = "nn".equalsIgnoreCase(printRun) ? null : printRun;

                list.add(new ScrapedCard(cardName, bv, cleanSerial, cleanPrintRun, team.isBlank() ? null : team, "Numbered/Hits"));
            }
        }
        return list;
    }

    public List<ScrapedCard> parseCommons(String html) {
        List<ScrapedCard> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table.table");
        if (table == null) return list;

        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() >= 3) {
                String cardName = cells.get(0).text().trim();
                String rawBv = cells.get(1).text().trim();
                String type = cells.get(2).text().trim();

                Double bv = parseBv(rawBv);
                list.add(new ScrapedCard(cardName, bv, null, null, null, "Commons/Inserts (" + type + ")"));
            }
        }
        return list;
    }

    public static Double parseBv(String rawBv) {
        if (rawBv == null || rawBv.isBlank() || "n/a".equalsIgnoreCase(rawBv) || "kA".equalsIgnoreCase(rawBv) || "nn".equalsIgnoreCase(rawBv)) {
            return null;
        }
        String clean = rawBv.replace("$", "").replace(".", "").replace(",", ".").trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int enrichCache(List<ScrapedCard> scrapedCards, List<CardData> localCards, BeckettValueCache cache) {
        String nowIso = Instant.now().toString();
        int matched = 0;

        for (CardData lc : localCards) {
            Optional<ScrapedCard> match = findBestMatch(lc, scrapedCards);
            if (match.isPresent()) {
                ScrapedCard sc = match.get();
                BeckettValueEntry entry = new BeckettValueEntry(
                        sc.cardName(),
                        sc.beckettValue(),
                        sc.serialNumber(),
                        sc.printRun(),
                        sc.team(),
                        sc.category(),
                        nowIso
                );
                cache.put(lc.id, entry);
                matched++;
            }
        }
        return matched;
    }

    public Optional<ScrapedCard> findBestMatch(CardData lc, List<ScrapedCard> candidates) {
        String lSeason = clean(lc.get("Season"));
        String lBrand = clean(lc.get("Brand"));
        String lNum = cleanNum(lc.get("Number"));
        String lVariant = clean(lc.get("Variant"));
        String lTheme = clean(lc.get("Theme"));
        String lPrintRun = clean(lc.get("Print Run"));
        boolean isAuto = "Yes".equalsIgnoreCase(lc.get("Autograph"));
        boolean isPatch = "Yes".equalsIgnoreCase(lc.get("Memorabilia"));

        ScrapedCard bestCandidate = null;
        int bestScore = -1;

        for (ScrapedCard sc : candidates) {
            String sName = sc.cardName().toLowerCase(Locale.ROOT);
            int score = 0;

            // Season match
            Matcher sm = PATTERN_SEASON.matcher(sc.cardName());
            String sSeason = sm.find() ? sm.group(1).toLowerCase(Locale.ROOT) : "";
            if (!lSeason.isEmpty() && !sSeason.isEmpty()) {
                if (lSeason.equals(sSeason)) {
                    score += 30;
                } else if (lSeason.length() >= 4 && sSeason.length() >= 4 && lSeason.substring(0, 4).equals(sSeason.substring(0, 4))) {
                    score += 15;
                } else {
                    continue; // Skip different seasons
                }
            }

            // Card number match
            Matcher nm = PATTERN_CARD_NUM.matcher(sc.cardName());
            String sNum = nm.find() ? nm.group(1).toLowerCase(Locale.ROOT) : "";
            if (!lNum.isEmpty() && !sNum.isEmpty()) {
                if (lNum.equals(sNum)) {
                    score += 35;
                } else if (lNum.contains(sNum) || sNum.contains(lNum)) {
                    score += 15;
                } else {
                    score -= 30;
                }
            } else if (lNum.isEmpty() && sNum.isEmpty()) {
                score += 10;
            }

            // Hits
            if (isAuto) {
                if (sName.contains("auto") || sName.contains("sign")) score += 20;
                else score -= 15;
            } else {
                if (sName.contains("auto") || sName.contains("sign")) score -= 15;
            }

            if (isPatch) {
                if (sName.contains("jersey") || sName.contains("patch") || sName.contains("game used") || sName.contains("materials") || sName.contains("gu")) score += 20;
                else score -= 15;
            }

            // Brand
            if (!lBrand.isEmpty()) {
                for (String word : lBrand.split("\\s+")) {
                    if (word.length() > 2 && sName.contains(word)) score += 10;
                }
            }

            // Variant
            if (!lVariant.isEmpty() && !"base".equals(lVariant)) {
                for (String word : lVariant.split("\\s+")) {
                    if (word.length() > 2 && sName.contains(word)) score += 15;
                }
            } else if ("base".equals(lVariant)) {
                if (sName.contains("refractor") || sName.contains("gold") || sName.contains("silver") || sName.contains("platinum") || sName.contains("credentials")) {
                    score -= 15;
                }
            }

            if (!lTheme.isEmpty()) {
                for (String word : lTheme.split("\\s+")) {
                    if (word.length() > 2 && sName.contains(word)) score += 10;
                }
            }

            // Print run
            if (!lPrintRun.isEmpty() && sc.printRun() != null && lPrintRun.equals(sc.printRun())) {
                score += 25;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = sc;
            }
        }

        if (bestCandidate != null && bestScore >= 45) {
            return Optional.of(bestCandidate);
        }
        return Optional.empty();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanNum(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replace("#", "");
    }

    public static void main(String[] args) {
        logger.info("=== Starting MrTorte Beckett Value Enrichment ===");
        try {
            MrtorteBeckettEnricher enricher = new MrtorteBeckettEnricher();
            List<ScrapedCard> scraped = enricher.scrapeAll();

            Path cardsPath = Paths.get("content/json/cards.json");
            List<CardData> localCards = CardDataLoader.loadCards(cardsPath);
            logger.info("Loaded {} local cards from {}", localCards.size(), cardsPath);

            BeckettValueCache cache = BeckettValueCache.loadDefault();
            int matched = enricher.enrichCache(scraped, localCards, cache);
            cache.saveDefault();

            logger.info("Successfully matched {} / {} cards with Beckett Values", matched, localCards.size());
            logger.info("Total entries in content/json/beckett-values.json: {}", cache.size());
        } catch (Exception e) {
            logger.error("Failed to enrich Beckett values: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
