package de.maulmann;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for rendering SEO-optimized titles, headings, meta descriptions,
 * AI snapshot summaries, external links, and related card recommendations.
 */
public class CardMetadataRenderer {

    private static final String RELATIVE_IMAGES_PATH = "../../images";

    public static String getGradingString(CardData c) {
        String gradingCo = c.get("Grading Co.");
        String grade = c.get("Grade");
        if (CardData.isValid(gradingCo) && CardData.isValid(grade)) {
            return gradingCo + "-" + grade;
        } else if (CardData.isValid(gradingCo)) {
            return gradingCo;
        } else if (CardData.isValid(grade)) {
            return grade;
        }
        return "";
    }

    public static String generateBrowserTitle(CardData c, String overviewPage) {
        String player = getPrimaryPlayer(c);
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String brand = c.get("Brand");
        String season = c.get("Season");
        String variant = c.get("Variant");
        String gradingStr = getGradingString(c);

        StringBuilder sb = new StringBuilder();
        sb.append(player).append(" ").append(season).append(" ").append(brand);
        if (CardData.isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number);
        if (!gradingStr.isEmpty()) {
            sb.append(" ").append(gradingStr);
        }
        sb.append(" | ").append(player).append(" Private Collection");
        return sb.toString();
    }

    public static String generateH1(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String gradingStr = getGradingString(c);

        StringBuilder sb = new StringBuilder();
        sb.append(player).append(" | ").append(season).append(" ").append(brand);
        if (CardData.isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" ").append(theme);
        }
        if (CardData.isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number);
        if (!gradingStr.isEmpty()) {
            sb.append(" ").append(gradingStr);
        }
        return sb.toString();
    }

    public static String generateH1Html(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.has("Number") ? " #" + c.get("Number") : "";
        String gradingStr = getGradingString(c);

        StringBuilder sb = new StringBuilder();
        sb.append("<span class=\"player-name\">").append(player).append("</span><br>");
        sb.append("<span class=\"sub-title\">").append(season).append(" ").append(brand);
        if (CardData.isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" ").append(theme);
        }
        if (CardData.isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append(" ").append(variant);
        }
        sb.append(number).append("</span>");
        if (!gradingStr.isEmpty()) {
            sb.append("<br><span class=\"sub-title grading-subtitle\">").append(gradingStr).append("</span>");
        }
        return sb.toString();
    }

    public static String generateMetaDescription(CardData c) {
        String player = getPrimaryPlayer(c);
        String season = c.get("Season");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");

        StringBuilder sb = new StringBuilder();
        sb.append("View details for the ").append(season).append(" ").append(brand).append(" ").append(player);

        if (c.has("Number")) {
            sb.append(" card #").append(c.get("Number"));
        }
        sb.append(" from our ").append(player).append(" Private Collection. ");

        if (CardData.isValid(variant) && !variant.equalsIgnoreCase("Base")) {
            sb.append("Rare ").append(variant).append(" variant. ");
        } else if (CardData.isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append("Features ").append(theme).append(" design. ");
        }

        String serial = c.get("Serial/Print Run");
        if (CardData.isValid(serial)) {
            sb.append("Numbered ").append(serial).append(". ");
        }

        sb.append("A must-see for any ").append(player).append(" Super Collector. High-res scans and hobby history.");

        String result = sb.toString();
        if (result.length() > 160) {
            result = result.substring(0, 157) + "...";
        }
        return result;
    }

    public static String generateAltText(CardData c, String view) {
        String base = c.get("Season") + " " + c.get("Brand") + " " + formatMulti(c.get("Player"));
        if (view.equals("front")) {
            return "Front scan of " + base + " - " + c.get("Variant") + " edition (" + formatMulti(c.get("Team")) + ") - " + getPrimaryPlayer(c) + " Collector Private Collection";
        } else {
            return "Back scan of " + base + " showing stats for " + formatMulti(c.get("Team")) + " - " + getPrimaryPlayer(c) + " Collector Private Collection";
        }
    }

    public static String generateAiSnapshotText(CardData c) {
        String player = formatMulti(c.get("Player"));
        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");
        String variant = c.get("Variant");
        String number = c.get("Number");
        String team = formatMulti(c.get("Team"));
        String serial = c.get("Serial");
        String printRun = c.get("Print Run");

        StringBuilder sb = new StringBuilder();
        sb.append("This ").append(season).append(" ").append(company).append(" ").append(brand);

        if (CardData.isValid(theme) && !theme.equalsIgnoreCase(brand)) {
            sb.append(" (").append(theme).append(")");
        }

        sb.append(" card features ").append(player).append(" during his tenure with the ").append(team).append(".");

        if (CardData.isValid(number) && !number.equals("-")) {
            sb.append(" Card number #").append(number).append(".");
        }

        boolean hasVariant = CardData.isValid(variant) && !variant.equalsIgnoreCase("Base");
        if (hasVariant) {
            sb.append(" This is the coveted ").append(variant).append(" parallel variation");
        }

        if (CardData.isValid(printRun)) {
            if ("1".equals(printRun)) {
                if (hasVariant) {
                    sb.append(" with a printrun of 1. Masterpiece.");
                } else {
                    sb.append(" Masterpiece with a printrun of 1.");
                }
            } else {
                if (hasVariant) {
                    sb.append(" with a printrun of ").append(CardData.isValid(serial) ? serial + "/" + printRun : printRun).append(".");
                } else {
                    sb.append(" This card has a printrun of ").append(CardData.isValid(serial) ? serial + "/" + printRun : printRun).append(".");
                }
            }
        } else if (hasVariant) {
            sb.append(".");
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(" Includes official certified autograph.");
        }

        if (c.has("Memorabilia") && c.get("Memorabilia").equalsIgnoreCase("Yes")) {
            sb.append(" Contains authentic game-used memorabilia patch.");
        }

        return sb.toString();
    }

    public static List<Map<String, String>> findRelatedCards(CardData target, CardIndex index, int limit, Set<String> rareCardIds) {
        if (target == null || index == null || limit <= 0) return Collections.emptyList();

        List<CardData> candidates = index.getCandidatesForRelated(target);
        Map<CardData, Integer> scored = new HashMap<>();

        for (CardData c : candidates) {
            if (c.stableId != null && c.stableId.equals(target.stableId)) continue;

            int score = 0;
            if (c.get("Season").equals(target.get("Season"))) score += 10;

            String cBrand = c.get("Brand");
            String tBrand = target.get("Brand");
            if (cBrand.equalsIgnoreCase(tBrand)) score += 8;
            else if (cBrand.contains(tBrand) || tBrand.contains(cBrand)) score += 5;

            if (c.get("Company").equals(target.get("Company"))) score += 3;

            String cPlayer = c.get("Player");
            String tPlayer = target.get("Player");
            if (cPlayer.equals(tPlayer)) score += 15;

            boolean targetIsRare = rareCardIds != null && rareCardIds.contains(target.stableId);
            boolean cIsRare = rareCardIds != null && rareCardIds.contains(c.stableId);
            if (targetIsRare && cIsRare) score += 7;

            scored.put(c, score);
        }

        List<CardData> top = scored.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue());
                    if (cmp != 0) return cmp;
                    if (e1.getKey().stableId != null && e2.getKey().stableId != null) {
                        return e1.getKey().stableId.compareTo(e2.getKey().stableId);
                    }
                    return e1.getKey().filename.compareTo(e2.getKey().filename);
                })
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Map<String, String>> result = new ArrayList<>();
        for (CardData c : top) {
            Map<String, String> item = new HashMap<>();
            String relUrl = "../" + c.seasonFolder + "/" + c.filename;
            item.put("title", generateH1(c));
            item.put("url", relUrl);
            item.put("link", relUrl);

            String rawImageBase = c.filenameBase.substring(0, c.filenameBase.lastIndexOf("-"));
            String imageBaseName = CardPageGenerator.resolveDiskImageBase(c.seasonFolder, rawImageBase, c);
            String imgBase = RELATIVE_IMAGES_PATH + "/" + c.seasonFolder + "/" + imageBaseName + "-front";
            String thumbAvif = imgBase + "-200w.avif";
            String thumbFallback = imgBase + ".avif";

            item.put("imgBase", imgBase);
            item.put("thumbWebp", thumbAvif);
            item.put("thumbAvif", thumbAvif);
            item.put("thumb", thumbAvif);
            item.put("thumbFallback", thumbFallback);
            item.put("alt", generateAltText(c, "front"));
            item.put("variant", c.has("Variant") ? c.get("Variant") : "Base");
            item.put("season", c.get("Season"));
            item.put("brand", c.get("Brand"));
            item.put("meta", c.get("Season") + " " + c.get("Brand") + (c.has("Variant") ? " - " + c.get("Variant") : ""));

            result.add(item);
        }
        return result;
    }

    public static boolean isRareParallel(CardData c) {
        String variant = c.get("Variant").toLowerCase();
        String theme = c.get("Theme").toLowerCase();
        return variant.contains("refractor") || variant.contains("pmg") || variant.contains("ruby") ||
                variant.contains("autograph") || variant.contains("patch") || c.has("Serial") ||
                theme.contains("flawless") || theme.contains("exquisite");
    }

    public static List<Map<String, String>> generateExternalLinks(CardData c) {
        List<Map<String, String>> links = new ArrayList<>();

        String primaryPlayer = getPrimaryPlayer(c);
        String cleanPlayer = CardData.cleanPlayerName(primaryPlayer);
        String season = c.get("Season");
        String brand = c.get("Brand");
        String variant = c.get("Variant");
        String number = c.get("Number");

        String ebayQuery = cleanPlayer + " " + season + " " + brand + " " + (CardData.isValid(variant) && !variant.equals("Base") ? variant : "") + " " + (CardData.isValid(number) ? "#" + number : "");
        String ebayUrl = "https://www.ebay.com/sch/i.html?_nkw=" + ebayQuery.trim().replace(" ", "+");
        links.add(Map.of("name", "Similar cards on eBay", "url", ebayUrl, "icon", "ebay"));

        String bkpQuery = cleanPlayer + " " + season + " " + brand;
        String bkpUrl = "https://www.beckett.com/search?q=" + bkpQuery.trim().replace(" ", "+");
        links.add(Map.of("name", "Beckett Checklist", "url", bkpUrl, "icon", "beckett"));

        if (cleanPlayer.equalsIgnoreCase("Juwan Howard")) {
            links.add(Map.of("name", "Juwan Howard Career Stats", "url", "https://www.basketball-reference.com/players/h/howarju01.html", "icon", "bref"));
        } else {
            String wikiUrl = "https://en.wikipedia.org/wiki/" + cleanPlayer.replace(" ", "_");
            links.add(Map.of("name", " Wikipedia Profile", "url", wikiUrl, "icon", "wiki"));
        }

        return links;
    }

    public static String getSeasonHighlights(CardData c, String overviewPage, TriviaManager triviaManager) {
        if (triviaManager != null) {
            String triviaText = triviaManager.getTrivia("playerHighlights", c.attributes);
            if (triviaText != null && !triviaText.trim().isEmpty()) {
                return triviaText;
            }
        }

        String p = getPrimaryPlayerName(c.get("Player"));
        if ("Baseball.html".equals(overviewPage) || isBaseballPlayer(p)) {
            return p + " is an iconic Major League Baseball player featured in this premium sports memorabilia release.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(p);
        String team = c.get("Team");
        if (team != null && !team.isBlank()) {
            sb.append(" starred for the ").append(team);
        } else {
            sb.append(" is highlighted");
        }
        String season = c.get("Season");
        if (season != null && !season.isBlank()) {
            sb.append(" during the ").append(season).append(" campaign");
        }
        sb.append(", capturing his court presence and veteran leadership.");
        if (c.isRookie()) {
            sb.append(" This issue represents a key rookie-year appearance from his introductory season in professional basketball.");
        } else if (c.isAutograph()) {
            sb.append(" This piece is further distinguished by an official manufacturer-certified autograph.");
        } else if (c.isPatch()) {
            sb.append(" The card embeds authentic game-worn memorabilia celebrating his professional tenure.");
        }
        return sb.toString();
    }

    public static String getEraContext(CardData c, String overviewPage, TriviaManager triviaManager) {
        String p = getPrimaryPlayerName(c.get("Player"));
        if ("Baseball.html".equals(overviewPage) || isBaseballPlayer(p)) {
            return "MLB Baseball Autograph & Relic Era: Premium certified signatures and authentic game-used memorabilia preserved for baseball collectors.";
        }
        if ("Flawless.html".equals(overviewPage) || "Panini.html".equals(overviewPage)) {
            return "Ultra-High-End Premium Era: Featuring low-numbered parallel cards, certified signatures, and game-worn patch swatches of basketball icons.";
        }

        if (triviaManager != null) {
            String triviaText = triviaManager.getTrivia("eraContext", c.attributes);
            if (triviaText != null && !triviaText.trim().isEmpty()) {
                return triviaText;
            }
        }

        String brand = c.get("Brand");
        String company = c.get("Company");
        String season = c.get("Season");
        StringBuilder sb = new StringBuilder();
        if (season != null && !season.isBlank()) {
            sb.append(season).append(" Hobby Era: ");
        } else {
            sb.append("Contemporary Basketball Hobby Era: ");
        }
        if (brand != null && !brand.isBlank()) {
            sb.append("The ").append(brand).append(" release");
            if (company != null && !company.isBlank() && !brand.toLowerCase().contains(company.toLowerCase())) {
                sb.append(" by ").append(company);
            }
            sb.append(" exemplifies ");
        } else {
            sb.append("This era represents ");
        }
        if (c.has("Print Run") || c.has("Serial")) {
            sb.append("the advent of limited serial-numbered chase cards and premium finishing techniques that shaped modern sports card collecting.");
        } else if (c.isRefractorOrFoil()) {
            sb.append("the widespread adoption of premium refractive foil and chromium card chemistry that redefined 90s aesthetic standards.");
        } else {
            sb.append("groundbreaking photography and innovative insert architectures that defined the golden age of basketball card collecting.");
        }
        return sb.toString();
    }

    public static String getPrimaryPlayer(CardData c) {
        String raw = c.get("Player");
        if (raw == null || raw.trim().isEmpty()) return "Juwan Howard";
        return raw.contains(",") ? raw.split(",")[0].trim() : raw.trim();
    }

    public static String getPrimaryPlayerName(String player) {
        if (player == null) return "";
        return player.contains(",") ? player.split(",")[0].trim() : player.trim();
    }

    public static boolean isBaseballPlayer(String p) {
        if (p == null) return false;
        String l = p.toLowerCase();
        return l.contains("gagne") || l.contains("bunning") || l.contains("ozzie") ||
                l.contains("carlton") || l.contains("will clark") || l.contains("griffey");
    }

    public static String formatMulti(String s) {
        if (s == null) return "";
        return s.replace(",", " / ");
    }
}
