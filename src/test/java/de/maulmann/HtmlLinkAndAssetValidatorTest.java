package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Automated HTML Link and Asset Validator Tests")
class HtmlLinkAndAssetValidatorTest {

    private static final Path OUTPUT_DIR = Paths.get("output");

    @BeforeAll
    static void ensureOutputGenerated() {
        if (!Files.exists(OUTPUT_DIR.resolve("index.html")) || !Files.exists(OUTPUT_DIR.resolve("Juwan-Howard-Collection.html"))) {
            FileGenerator.generate();
        }
    }

    @Test
    @DisplayName("Verify that all internal links on core pages resolve to existing files")
    void testCorePagesInternalLinks() throws Exception {
        List<String> corePages = List.of(
                "index.html",
                "Juwan-Howard-Collection.html",
                "rainbows.html",
                "binder.html",
                "Flawless.html",
                "Panini.html",
                "Baseball.html",
                "Wantlist.html"
        );

        List<String> brokenLinks = new ArrayList<>();

        for (String pageName : corePages) {
            Path pagePath = OUTPUT_DIR.resolve(pageName);
            if (!Files.exists(pagePath)) continue;

            Document doc = Jsoup.parse(Files.readString(pagePath));
            Elements links = doc.select("a[href]");

            for (Element link : links) {
                String href = link.attr("href").trim();

                // Skip external URLs, anchor hashes, mailto, javascript
                if (href.startsWith("http://") || href.startsWith("https://") ||
                        href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("javascript:")) {
                    continue;
                }

                // Strip query parameters and anchors
                String cleanHref = href;
                int queryIdx = cleanHref.indexOf('?');
                if (queryIdx != -1) cleanHref = cleanHref.substring(0, queryIdx);
                int hashIdx = cleanHref.indexOf('#');
                if (hashIdx != -1) cleanHref = cleanHref.substring(0, hashIdx);

                if (cleanHref.isEmpty()) continue;

                Path targetFile = OUTPUT_DIR.resolve(cleanHref).normalize();
                if (!Files.exists(targetFile)) {
                    brokenLinks.add("On " + pageName + ": broken link -> " + href + " (resolved to " + targetFile + ")");
                }
            }
        }

        assertTrue(brokenLinks.isEmpty(), "Found broken links in core pages:\n" + String.join("\n", brokenLinks));
    }

    @Test
    @DisplayName("Verify that all CSS stylesheets and favicon assets referenced in HTML files exist")
    void testStylesheetAndFaviconAssets() throws Exception {
        Path indexHtml = OUTPUT_DIR.resolve("index.html");
        if (!Files.exists(indexHtml)) return;

        Document doc = Jsoup.parse(Files.readString(indexHtml));
        Elements cssLinks = doc.select("link[rel=stylesheet]");

        for (Element css : cssLinks) {
            String href = css.attr("href");
            String cleanHref = href.split("\\?")[0];
            Path cssPath = OUTPUT_DIR.resolve(cleanHref.startsWith("/") ? cleanHref.substring(1) : cleanHref).normalize();
            assertTrue(Files.exists(cssPath), "Referenced stylesheet must exist: " + href);
        }
    }
}
