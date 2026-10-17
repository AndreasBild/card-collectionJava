package de.maulmann;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SEO guard: individual card URLs (including the hash suffix from {@link CardData#generateStableId})
 * must never change. See {@code .agents/rules/url-stability.md}.
 *
 * <p>The golden file lists every card page path ({@code cards/<season>/<name>-<hash>.html}) that has been
 * published. Sitemap {@code <loc>} entries are derived from these paths, so they are covered as well.
 * Adding cards is fine (the golden file is append-only in practice); removing or altering an existing
 * entry fails this test.
 *
 * <p>To intentionally refresh the golden file after ADDING cards, run:
 * {@code mvn test -Dtest=CardUrlStabilityTest -Dgolden.update=true} and review the git diff: it must contain
 * additions only. Any removed or changed line is a URL change and needs explicit approval plus a 301 redirect.
 */
@DisplayName("Card URL stability (SEO)")
class CardUrlStabilityTest {

    private static final String DATA_DIR = System.getProperty("card.data.dir", "content/json");
    private static final List<String> BUCKETS =
            List.of("cards.json", "baseball.json", "flawless.json", "wantlist.json", "panini.json");
    private static final Path GOLDEN = Path.of("src/test/resources/golden/card-urls.txt");

    /** Same construction as {@code CardPageGenerator.run}: {@code new CardData(json, null).fullRelativePath}. */
    private static SortedSet<String> currentUrls() {
        SortedSet<String> urls = new TreeSet<>();
        for (String bucket : BUCKETS) {
            Path file = Path.of(DATA_DIR, bucket);
            if (!Files.exists(file)) {
                continue;
            }
            for (CardJson card : CardDataLoader.loadCardsFromJson(file.toString())) {
                urls.add(new CardData(card, null).fullRelativePath);
            }
        }
        return urls;
    }

    @Test
    @DisplayName("Every previously published card URL is still generated unchanged")
    void existingCardUrlsNeverChange() throws IOException {
        SortedSet<String> current = currentUrls();
        assertFalse(current.isEmpty(), "No cards found in " + DATA_DIR + " - the guard would pass vacuously");

        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.write(GOLDEN, current, StandardCharsets.UTF_8);
            return;
        }

        Set<String> golden = Files.readAllLines(GOLDEN, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
        assertFalse(golden.isEmpty(), "Golden file is empty: " + GOLDEN);

        SortedSet<String> missing = new TreeSet<>(golden);
        missing.removeAll(current);

        assertTrue(missing.isEmpty(),
                missing.size() + " published card URL(s) are no longer generated (changed or removed). "
                        + "Card URLs must never change (SEO). First 20: "
                        + missing.stream().limit(20).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("generateStableId is pinned (hash inputs must never change)")
    void stableIdIsPinned() {
        assertEquals("349449fd", CardData.generateStableId(Map.of(
                "Player", "Juwan Howard", "Team", "Washington Bullets", "Season", "1994-95",
                "Company", "Upper Deck", "Brand", "Collectors Choice", "Theme", "Base Set",
                "Variant", "Base", "Number", "278")));
        assertEquals("150c387a", CardData.generateStableId(Map.of(
                "Player", "Juwan Howard, Chris Webber", "Team", "Washington Bullets, Washington Bullets",
                "Season", "1996-97", "Company", "Fleer", "Brand", "Ultra", "Variant", "Gold Medallion",
                "Number", "G12", "Serial", "#123", "Print Run", "500")));
        assertEquals("737a8d5c", CardData.generateStableId(Map.of(
                "Player", "Juwan Howard", "Season", "1995-96", "Company", "Topps", "Brand", "Finest",
                "Number", "830", "Grading Co.", "PSA", "Grade", "10")));
    }
}
