package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Isolated service for generating JSON-LD Schema.org metadata and FAQ sections for card detail pages.
 */
public class CardSchemaGenerator {

    private static final String BASE_URL = "https://www.maulmann.de";
    private static final TriviaManager TRIVIA_MANAGER = new TriviaManager();

    public static String generateFaqHtml(CardPageGenerator.CardData c) {
        StringBuilder sb = new StringBuilder();

        String player = c.get("Player");
        if (player != null && player.contains(",")) player = player.split(",")[0].trim();

        String season = c.get("Season");
        String company = c.get("Company");
        String brand = c.get("Brand");
        String theme = c.get("Theme");

        String fullSet = (isValid(theme) ? theme + " " : "") + (isValid(c.get("Variant")) ? c.get("Variant") : "Base");
        String uniqueQuestion = "Why is this " + season + " " + brand + " " + fullSet + " card significant?";
        StringBuilder uniqueAns = new StringBuilder();

        if (isHolyGrail(c)) {
            uniqueAns.append("This card is considered a 'Holy Grail' insert in 90s basketball card collecting. Produced by ").append(company)
                    .append(" during the ").append(season).append(" season, its legendarily low print run and iconic design make it a focal point of our collection.");
        } else if (c.has("Variant") && c.get("Variant").toLowerCase().contains("refractor")) {
            uniqueAns.append("Utilizing ").append(company).append("'s iconic Chromium technology, the light-diffracting coating gives this card a stunning rainbow sheen under direct light.");
        } else if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            uniqueAns.append("Featuring a certified signature guaranteed by ").append(company).append(", it bridges authentic player memorabilia with high-end card design.");
        } else {
            uniqueAns.append("Produced by ").append(company).append(", it stands out as an authentic piece of ").append(season).append(" basketball hobby history in this private collection.");
        }

        sb.append(createFaqItem(uniqueQuestion, uniqueAns.toString()));

        if (isHolyGrail(c)) {
            sb.append(createFaqItem("Is this a 'Holy Grail' card?", "Yes, this card belongs to one of the most prestigious parallel series in the hobby. These are extremely rare and heavily targeted by high-end collectors."));
        }

        String combined = c.get("Serial/Print Run");
        if (isValid(combined)) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + combined + ", making it a strictly limited edition collectible."));
        } else if (c.has("Serial")) {
            sb.append(createFaqItem("How rare is this specific card?", "This card is serially numbered " + c.get("Serial") + " out of a total print run of " + c.get("Print Run") + "."));
        }

        if (c.has("Rookie")) {
            String rookieAns = c.get("Rookie").equalsIgnoreCase("Yes") ?
                    "Yes, this is an official Rookie Card (RC) from " + player + "'s debut season, holding premium value for collectors." :
                    "No, this card was released during the " + season + " season, which was not " + player + "'s debut season.";
            sb.append(createFaqItem("Is this a " + player + " Rookie Card?", rookieAns));
        }

        if (c.has("Autograph") && c.get("Autograph").equalsIgnoreCase("Yes")) {
            sb.append(createFaqItem("Is the autograph authentic?", "Yes, this card features a manufacturer-certified autograph guaranteed by " + company + "."));
        }

        if (c.has("Grade")) {
            sb.append(createFaqItem("Is this card professionally graded?", "Yes, this card has been graded by " + c.get("Grading Co.") + " and received a condition score of " + c.get("Grade") + "."));
        }

        List<TriviaManager.FaqItem> rookieFaqs = TRIVIA_MANAGER.getFaqs("rookieFaq", c.attributes);
        for (TriviaManager.FaqItem faq : rookieFaqs) {
            sb.append(createFaqItem(faq.question, faq.answer));
        }

        return sb.toString();
    }

    private static String createFaqItem(String question, String answer) {
        return "<details class=\"faq-details\"><summary class=\"faq-summary\">" + escapeHtml(question) + "</summary><p class=\"faq-answer\">" + escapeHtml(answer) + "</p></details>";
    }

    public static String generateJsonLd(CardPageGenerator.CardData c, String desc, String h1Title, String overviewPage, String imageBaseName, String faqHtml) {
        String frontImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-front.avif";
        String backImgUrl = BASE_URL + "/images/" + c.seasonFolder + "/" + imageBaseName + "-back.avif";
        String cardUrl = BASE_URL + "/cards/" + c.seasonFolder + "/" + c.filename;

        // Determine matching collection name for breadcrumb
        String collectionName = "Collection";
        if ("Flawless.html".equals(overviewPage)) {
            collectionName = "Flawless";
        } else if ("Baseball.html".equals(overviewPage)) {
            collectionName = "Baseball";
        } else if ("Panini.html".equals(overviewPage)) {
            collectionName = "Panini";
        } else if ("Wantlist.html".equals(overviewPage)) {
            collectionName = "Wantlist";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("<script type=\"application/ld+json\">\n");
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@graph\": [\n");

        // 1. BreadcrumbList
        List<Map<String, String>> bcItems = new ArrayList<>();
        bcItems.add(Map.of("name", "Home", "link", BASE_URL + "/index.html"));
        bcItems.add(Map.of("name", collectionName, "link", BASE_URL + "/" + overviewPage));
        bcItems.add(Map.of("name", c.get("Season"), "link", BASE_URL + "/" + overviewPage + "#" + c.seasonFolder.toLowerCase()));
        bcItems.add(Map.of("name", h1Title, "link", cardUrl));
        sb.append(SharedTemplates.getBreadcrumbJsonLd(bcItems, cardUrl + "#breadcrumb")).append(",\n");

        // 2. ItemPage Node
        sb.append("    {\n");
        sb.append("      \"@type\": \"ItemPage\",\n");
        sb.append("      \"@id\": \"").append(cardUrl).append("#webpage\",\n");
        sb.append("      \"url\": \"").append(cardUrl).append("\",\n");
        sb.append("      \"name\": \"").append(escapeJson(h1Title)).append("\",\n");
        sb.append("      \"description\": \"").append(escapeJson(desc)).append("\",\n");
        sb.append("      \"primaryImageOfPage\": { \"@type\": \"ImageObject\", \"url\": \"").append(frontImgUrl).append("\" },\n");
        sb.append("      \"breadcrumb\": { \"@id\": \"").append(cardUrl).append("#breadcrumb\" }\n");
        sb.append("    },\n");

        // 3. VisualArtwork
        String playerPrimary = c.get("Player");
        if (playerPrimary != null && playerPrimary.contains(",")) playerPrimary = playerPrimary.split(",")[0].trim();
        String companyName = c.get("Company");

        sb.append("    {\n");
        sb.append("      \"@type\": \"VisualArtwork\",\n");
        sb.append("      \"@id\": \"").append(cardUrl).append("#artwork\",\n");
        sb.append("      \"mainEntityOfPage\": \"").append(cardUrl).append("\",\n");
        sb.append("      \"name\": \"").append(escapeJson(h1Title)).append("\",\n");
        sb.append("      \"image\": [ \"").append(frontImgUrl).append("\", \"").append(backImgUrl).append("\" ],\n");
        sb.append("      \"description\": \"").append(escapeJson(desc)).append("\",\n");
        if (isValid(companyName)) {
            sb.append("      \"creator\": { \"@type\": \"Organization\", \"name\": \"").append(escapeJson(companyName)).append("\" },\n");
        }

        String year = extractYear(c.get("Season"));
        if (year != null) {
            sb.append("      \"dateCreated\": \"").append(year).append("\",\n");
        }

        String editionInfo = c.get("Serial/Print Run");
        if (!isValid(editionInfo)) {
            if (c.has("Serial") && c.has("Print Run")) {
                editionInfo = c.get("Serial") + " / " + c.get("Print Run");
            } else if (c.has("Print Run")) {
                editionInfo = "Print Run: " + c.get("Print Run");
            } else if (c.has("Serial")) {
                editionInfo = "Serial #" + c.get("Serial");
            }
        }
        if (isValid(editionInfo)) {
            sb.append("      \"artEdition\": \"").append(escapeJson(editionInfo)).append("\",\n");
        }

        sb.append("      \"about\": {\n");
        sb.append("        \"@type\": \"Person\",\n");
        sb.append("        \"name\": \"").append(escapeJson(formatMulti(c.get("Player")))).append("\"");
        if (isValid(playerPrimary)) {
            sb.append(",\n        \"sameAs\": \"https://en.wikipedia.org/wiki/").append(escapeJson(playerPrimary.replace(" ", "_"))).append("\"\n");
        } else {
            sb.append("\n");
        }
        sb.append("      },\n");
        sb.append("      \"artMedium\": \"Trading Card\",\n");
        sb.append("      \"artform\": \"Sports Memorabilia\"\n");
        sb.append("    }");

        // 4. FAQPage (if present)
        if (faqHtml != null && !faqHtml.isEmpty()) {
            sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"@type\": \"FAQPage\",\n");
            sb.append("      \"name\": \"Frequently Asked Questions\",\n");
            sb.append("      \"mainEntity\": [\n");

            Document doc = Jsoup.parseBodyFragment(faqHtml);
            Elements details = doc.select("details");
            for (int i = 0; i < details.size(); i++) {
                Element detail = details.get(i);
                String q = detail.select("summary").text();
                String a = detail.select("p").text();
                sb.append("        {\n");
                sb.append("          \"@type\": \"Question\",\n");
                sb.append("          \"name\": \"").append(escapeJson(q)).append("\",\n");
                sb.append("          \"acceptedAnswer\": {\n");
                sb.append("            \"@type\": \"Answer\",\n");
                sb.append("            \"text\": \"").append(escapeJson(a)).append("\"\n");
                sb.append("          }\n");
                sb.append("        }");
                if (i < details.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("      ]\n");
            sb.append("    }\n");
        } else {
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("</script>\n");

        // 5. Product Schema Template (for rating injection)
        sb.append("<script type=\"application/json\" id=\"product-schema-template\">\n");
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@type\": \"Product\",\n");
        sb.append("  \"@id\": \"").append(cardUrl).append("#product\",\n");
        sb.append("  \"mainEntityOfPage\": \"").append(cardUrl).append("\",\n");
        sb.append("  \"name\": \"").append(escapeJson(h1Title)).append("\",\n");
        sb.append("  \"image\": [ \"").append(frontImgUrl).append("\", \"").append(backImgUrl).append("\" ],\n");
        sb.append("  \"description\": \"").append(escapeJson(desc)).append("\",\n");
        sb.append("  \"sku\": \"").append(c.stableId).append("\"");

        if (isValid(c.get("Number"))) {
            sb.append(",\n  \"mpn\": \"").append(escapeJson(c.get("Number"))).append("\"");
        }
        if (isValid(c.get("Brand"))) {
            sb.append(",\n  \"brand\": { \"@type\": \"Brand\", \"name\": \"").append(escapeJson(c.get("Brand"))).append("\" }");
        }
        if (isValid(c.get("Company"))) {
            sb.append(",\n  \"manufacturer\": { \"@type\": \"Organization\", \"name\": \"").append(escapeJson(c.get("Company"))).append("\" }");
        }

        if (c.has("Grade") || c.has("Grading Co.")) {
            sb.append(",\n  \"itemCondition\": \"https://schema.org/UsedCondition\"");
        }

        String color = detectColor(c.get("Variant"));
        if (color != null) {
            sb.append(",\n  \"color\": \"").append(color).append("\"");
        }

        if (isHolyGrail(c)) {
            sb.append(",\n  \"category\": \"Sports Trading Cards\",\n");
            sb.append("  \"material\": \"Premium Hobby Parallel\"\n");
        } else {
            sb.append(",\n  \"category\": \"Sports Trading Cards\"\n");
        }
        sb.append("}\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    private static String extractYear(String season) {
        if (season == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(season);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String detectColor(String variant) {
        if (!isValid(variant)) return null;
        String lower = variant.toLowerCase();
        if (lower.contains("ruby")) return "Ruby";
        if (lower.contains("gold")) return "Gold";
        if (lower.contains("emerald")) return "Emerald";
        if (lower.contains("red")) return "Red";
        if (lower.contains("blue")) return "Blue";
        if (lower.contains("green")) return "Green";
        if (lower.contains("purple")) return "Purple";
        if (lower.contains("orange")) return "Orange";
        if (lower.contains("black")) return "Black";
        if (lower.contains("silver")) return "Silver";
        if (lower.contains("bronze")) return "Bronze";
        if (lower.contains("platinum")) return "Platinum";
        if (lower.contains("pink")) return "Pink";
        if (lower.contains("yellow")) return "Yellow";
        if (lower.contains("teal")) return "Teal";
        return null;
    }

    private static boolean isHolyGrail(CardPageGenerator.CardData c) {
        String theme = c.get("Theme").toLowerCase();
        String variant = c.get("Variant").toLowerCase();
        return theme.contains("precious metal gems") || theme.contains("ruby") || theme.contains("flawless") ||
                variant.contains("precious metal gems") || variant.contains("pmg") || variant.contains("ruby") ||
                variant.contains("masterpiece") || variant.contains("1/1") || variant.contains("flawless");
    }

    private static boolean isValid(String str) {
        return str != null && !str.trim().isEmpty() && !str.equalsIgnoreCase("null") && !str.equals("-");
    }

    private static String formatMulti(String val) {
        if (val == null) return "";
        return val.replaceAll("\\s*,\\s*", " / ");
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        String unescaped = text.replace("&quot;", "\"")
                               .replace("&amp;", "&")
                               .replace("&#39;", "'")
                               .replace("&lt;", "<")
                               .replace("&gt;", ">");
        return unescaped.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
