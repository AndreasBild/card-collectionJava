package de.maulmann;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * Pins the java.time based date formatting in SitemapGenerator to the legacy SimpleDateFormat output,
 * so sitemap {@code <lastmod>} and RSS {@code <pubDate>} values stay byte-identical.
 */
class SitemapGeneratorDateFormatTest {

    private static SimpleDateFormat legacy(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    @Test
    void isoDateMatchesLegacyFormatForInstants() {
        long[] millis = {0L, 1_000_000_000_000L, 1_759_000_000_123L, 1_798_761_599_999L};
        for (long m : millis) {
            assertEquals(
                    legacy("yyyy-MM-dd").format(new Date(m)),
                    SitemapGenerator.ISO_DATE.format(Instant.ofEpochMilli(m)));
        }
    }

    @Test
    void isoDateToRfc822MatchesLegacyParseAndFormat() throws ParseException {
        String[] days = {"1994-11-05", "2000-02-29", "2024-12-31", "2026-08-02", "2026-10-25"};
        for (String day : days) {
            String expected = legacy("EEE, dd MMM yyyy HH:mm:ss Z").format(legacy("yyyy-MM-dd").parse(day));
            assertEquals(expected, SitemapGenerator.isoDateToRfc822(day));
        }
    }

    @Test
    void trackerTimestampToRfc822MatchesLegacyConversion() {
        String stable = "02.08.2026 14:05:09";
        LocalDateTime ldt = LocalDateTime.parse(stable, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        String expected = legacy("EEE, dd MMM yyyy HH:mm:ss Z")
                .format(Date.from(ldt.atZone(java.time.ZoneId.of("UTC")).toInstant()));
        assertEquals(expected, SitemapGenerator.RFC_822.format(ldt.toInstant(ZoneOffset.UTC)));
        assertEquals("Sun, 02 Aug 2026 14:05:09 +0000", expected);
    }
}
