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

    private static final String BASE_URL = CardUtils.BASE_URL;
    private static final TriviaManager TRIVIA_MANAGER = TriviaManager.getInstance();
    private static final java.util.Properties RATING_CACHE = new java.util.Properties();
    private static boolean ratingCacheLoaded = false;

    public static synchronized void loadRatingCache() {
        java.io.File cacheFile = new java.io.File("output/rating-cache.properties");
        if (cacheFile.exists()) {
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(cacheFile.toPath())) {
                RATING_CACHE.clear();
                RATING_CACHE.load(in);
                ratingCacheLoaded = true;
            } catch (Exception _) {
                // Ignore
            }
        }
    }

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
            sb.append(createFaqItem(faq.question(), faq.answer()));
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
        String collectionName = switch (overviewPage != null ? overviewPage : "") {
            case "Flawless.html" -> "Flawless";
            case "Baseball.html" -> "Baseball";
            case "Panini.html" -> "Panini";
            case "Wantlist.html" -> "Wantlist";
            default -> "Collection";
        };

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
        sb.append("      \"primaryImageOfPage\": {\n");
        sb.append("        \"@type\": \"ImageObject\",\n");
        sb.append("        \"@id\": \"").append(cardUrl).append("#primaryimage\",\n");
        sb.append("        \"url\": \"").append(frontImgUrl).append("\",\n");
        sb.append("        \"contentUrl\": \"").append(frontImgUrl).append("\",\n");
        sb.append("        \"width\": 1200,\n");
        sb.append("        \"height\": 1680,\n");
        sb.append("        \"caption\": \"").append(escapeJson(h1Title)).append("\"\n");
        sb.append("      },\n");
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
            String cleanPrimary = CardPageGenerator.cleanPlayerName(playerPrimary);
            sb.append(",\n        \"sameAs\": \"https://en.wikipedia.org/wiki/").append(escapeJson(cleanPrimary.replace(" ", "_"))).append("\"\n");
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

        // 5. Product Schema (Active JSON-LD)
        if (!ratingCacheLoaded) {
            loadRatingCache();
        }

        String cachedRating = c.stableId != null ? RATING_CACHE.getProperty(c.stableId) : null;
        if (cachedRating == null && c.filename != null) {
            cachedRating = RATING_CACHE.getProperty(c.filename);
        }

        long ratingCount = 0;
        double ratingSum = 0.0;
        if (cachedRating != null) {
            String[] parts = cachedRating.split(":", 2);
            if (parts.length == 2) {
                try {
                    ratingCount = Long.parseLong(parts[0]);
                    ratingSum = Double.parseDouble(parts[1]);
                } catch (Exception _) {}
            }
        }

        boolean hasCachedRating = ratingCount > 0 && ratingSum >= 0;

        sb.append("<script type=\"application/ld+json\" id=\"product-schema-template\">\n");

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
            sb.append("  \"material\": \"Premium Hobby Parallel\"");
        } else {
            sb.append(",\n  \"category\": \"Sports Trading Cards\"");
        }

        if (hasCachedRating) {
            double rawAverage = ratingSum / ratingCount;
            double averageRating = Math.clamp(rawAverage, 1.0, 5.0);
            sb.append(String.format(java.util.Locale.US,
                    ",\n  \"aggregateRating\": {\n" +
                    "    \"@type\": \"AggregateRating\",\n" +
                    "    \"ratingValue\": %.1f,\n" +
                    "    \"reviewCount\": %d\n" +
                    "  }\n", averageRating, ratingCount));
        } else {
            sb.append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    public static String generateRainbowJsonLd(List<Map<String, Object>> rainbowSets) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"application/ld+json\">\n");
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@graph\": [\n");

        // 1. CollectionPage Node
        sb.append("    {\n");
        sb.append("      \"@type\": \"CollectionPage\",\n");
        sb.append("      \"@id\": \"").append(BASE_URL).append("/rainbows.html#webpage\",\n");
        sb.append("      \"url\": \"").append(BASE_URL).append("/rainbows.html\",\n");
        sb.append("      \"name\": \"Parallel Rainbow Tracker & Set Checklists\",\n");
        sb.append("      \"description\": \"Tracking the hunt for complete parallel rainbow sets, PMGs, Refractors, and rare 90s basketball card variants in the Juwan Howard private vault.\",\n");
        sb.append("      \"inLanguage\": \"en\",\n");
        sb.append("      \"isPartOf\": { \"@type\": \"WebSite\", \"name\": \"Maulmann Private Vault\", \"url\": \"").append(BASE_URL).append("\" },\n");
        sb.append("      \"publisher\": { \"@type\": \"Organization\", \"name\": \"Maulmann Private Vault\", \"url\": \"").append(BASE_URL).append("\" },\n");
        sb.append("      \"about\": { \"@type\": \"Person\", \"name\": \"Juwan Howard\", \"sameAs\": \"https://en.wikipedia.org/wiki/Juwan_Howard\" },\n");
        sb.append("      \"breadcrumb\": { \"@id\": \"").append(BASE_URL).append("/rainbows.html#breadcrumb\" },\n");
        sb.append("      \"mainEntity\": { \"@id\": \"").append(BASE_URL).append("/rainbows.html#mainlist\" }\n");
        sb.append("    },\n");

        // 2. BreadcrumbList Node
        List<Map<String, String>> bcItems = List.of(
                Map.of("name", "Home", "link", BASE_URL + "/index.html"),
                Map.of("name", "Rainbow Tracker", "link", BASE_URL + "/rainbows.html")
        );
        sb.append(SharedTemplates.getBreadcrumbJsonLd(bcItems, BASE_URL + "/rainbows.html#breadcrumb")).append(",\n");

        // 3. Main ItemList Node
        sb.append("    {\n");
        sb.append("      \"@type\": \"ItemList\",\n");
        sb.append("      \"@id\": \"").append(BASE_URL).append("/rainbows.html#mainlist\",\n");
        sb.append("      \"name\": \"Juwan Howard Single-Card Parallel Rainbow Checklists\",\n");
        sb.append("      \"numberOfItems\": ").append(rainbowSets.size()).append(",\n");
        sb.append("      \"itemListElement\": [\n");

        for (int i = 0; i < rainbowSets.size(); i++) {
            Map<String, Object> set = rainbowSets.get(i);
            String name = (String) set.get("name");
            String season = (String) set.get("season");
            String company = (String) set.get("company");
            String brand = (String) set.get("brand");
            String theme = (String) set.get("theme");
            String number = (String) set.get("number");
            int acquiredCount = (int) set.get("acquiredCount");
            int totalCount = (int) set.get("totalCount");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cards = (List<Map<String, Object>>) set.get("cards");

            sb.append("        {\n");
            sb.append("          \"@type\": \"ListItem\",\n");
            sb.append("          \"position\": ").append(i + 1).append(",\n");
            sb.append("          \"item\": {\n");
            sb.append("            \"@type\": \"ItemList\",\n");
            sb.append("            \"name\": \"").append(escapeJson(name)).append("\",\n");
            sb.append("            \"description\": \"Season: ").append(escapeJson(season))
                    .append(" | Company: ").append(escapeJson(company))
                    .append(" | Brand: ").append(escapeJson(brand))
                    .append(" | Set: ").append(escapeJson(theme))
                    .append(" | Card #: ").append(escapeJson(number))
                    .append(" | Progress: ").append(acquiredCount).append("/").append(totalCount).append(" acquired\",\n");
            sb.append("            \"numberOfItems\": ").append(cards.size()).append(",\n");
            sb.append("            \"itemListElement\": [\n");

            for (int j = 0; j < cards.size(); j++) {
                Map<String, Object> card = cards.get(j);
                String variant = (String) card.get("variant");
                String serial = (String) card.get("serial");
                boolean acquired = Boolean.TRUE.equals(card.get("acquired"));
                String url = card.containsKey("url") ? BASE_URL + "/" + card.get("url") : BASE_URL + "/Wantlist.html";
                String imgPath = card.containsKey("imgPath") ? BASE_URL + "/" + card.get("imgPath") : BASE_URL + "/images/logo.png";
                String cardTitle = card.containsKey("title") ? (String) card.get("title") : "Juwan Howard " + season + " " + brand + " " + variant + " #" + number;

                sb.append("              {\n");
                sb.append("                \"@type\": \"ListItem\",\n");
                sb.append("                \"position\": ").append(j + 1).append(",\n");
                sb.append("                \"item\": {\n");
                sb.append("                  \"@type\": [\"VisualArtwork\", \"Product\"],\n");
                sb.append("                  \"name\": \"").append(escapeJson(cardTitle)).append("\",\n");
                sb.append("                  \"description\": \"").append(escapeJson(cardTitle)).append(" (Serial/Print Run: ").append(escapeJson(serial)).append(")\",\n");
                sb.append("                  \"artMedium\": \"Sports Trading Card\",\n");
                sb.append("                  \"artworkSurface\": \"").append(escapeJson(variant)).append("\",\n");
                sb.append("                  \"category\": \"Sports Trading Cards\",\n");
                sb.append("                  \"brand\": { \"@type\": \"Brand\", \"name\": \"").append(escapeJson(brand)).append("\" },\n");
                sb.append("                  \"manufacturer\": { \"@type\": \"Organization\", \"name\": \"").append(escapeJson(company)).append("\" },\n");
                sb.append("                  \"url\": \"").append(escapeJson(url)).append("\",\n");
                sb.append("                  \"image\": \"").append(escapeJson(imgPath)).append("\",\n");
                sb.append("                  \"offers\": {\n");
                sb.append("                    \"@type\": \"Offer\",\n");
                sb.append("                    \"price\": \"0.00\",\n");
                sb.append("                    \"priceCurrency\": \"USD\",\n");
                sb.append("                    \"availability\": \"https://schema.org/").append(acquired ? "InStock" : "OutOfStock").append("\",\n");
                sb.append("                    \"description\": \"").append(acquired ? "Acquired parallel variant serially numbered " + escapeJson(serial) : "Unacquired target variant (" + escapeJson(serial) + ")").append("\"\n");
                sb.append("                  }\n");
                sb.append("                }\n");
                sb.append("              }").append(j < cards.size() - 1 ? "," : "").append("\n");
            }

            sb.append("            ]\n");
            sb.append("          }\n");
            sb.append("        }").append(i < rainbowSets.size() - 1 ? "," : "").append("\n");
        }

        sb.append("      ]\n");
        sb.append("    },\n");

        // 4. FAQPage Node
        sb.append("    {\n");
        sb.append("      \"@type\": \"FAQPage\",\n");
        sb.append("      \"@id\": \"").append(BASE_URL).append("/rainbows.html#faq\",\n");
        sb.append("      \"name\": \"Parallel Rainbow Tracker FAQs\",\n");
        sb.append("      \"mainEntity\": [\n");
        sb.append("        {\n");
        sb.append("          \"@type\": \"Question\",\n");
        sb.append("          \"name\": \"What is a sports card Parallel Rainbow?\",\n");
        sb.append("          \"acceptedAnswer\": {\n");
        sb.append("            \"@type\": \"Answer\",\n");
        sb.append("            \"text\": \"A Rainbow in sports card collecting represents acquiring every single parallel color, serial-numbered variant, and 1/1 Masterpiece produced for a specific card set during a release season.\"\n");
        sb.append("          }\n");
        sb.append("        },\n");
        sb.append("        {\n");
        sb.append("          \"@type\": \"Question\",\n");
        sb.append("          \"name\": \"How is parallel rainbow completion progress calculated?\",\n");
        sb.append("          \"acceptedAnswer\": {\n");
        sb.append("            \"@type\": \"Answer\",\n");
        sb.append("            \"text\": \"Completion progress is calculated by dividing the count of acquired parallel cards by the total known manufactured parallel variants in that specific card set.\"\n");
        sb.append("          }\n");
        sb.append("        },\n");
        sb.append("        {\n");
        sb.append("          \"@type\": \"Question\",\n");
        sb.append("          \"name\": \"What happens if a parallel card variant is missing from the rainbow?\",\n");
        sb.append("          \"acceptedAnswer\": {\n");
        sb.append("            \"@type\": \"Answer\",\n");
        sb.append("            \"text\": \"Unacquired parallel variants are marked as SEEKING and actively tracked on our Juwan Howard Wantlist for future acquisition.\"\n");
        sb.append("          }\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
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
        return CardUtils.isValidForSchema(str);
    }

    private static String formatMulti(String val) {
        return CardUtils.formatMulti(val);
    }

    private static String escapeJson(String text) {
        return CardUtils.escapeJson(text);
    }

    private static String escapeHtml(String text) {
        return CardUtils.escapeHtml(text);
    }
}
