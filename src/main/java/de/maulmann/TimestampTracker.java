package de.maulmann;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages stable timestamps for generated HTML files.
 * If the content (excluding the timestamp) hasn't changed, the old timestamp is returned.
 */
public class TimestampTracker {
    private static final Logger log = LoggerFactory.getLogger(TimestampTracker.class);
    private final File storeFile;
    private final Properties storedData = new Properties();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // Key: File path, Value: {hash}:{timestamp}
    private final ConcurrentHashMap<String, String> currentSessionData = new ConcurrentHashMap<>();

    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern ISO_DATE_PREFIX = Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*");
    private static final Pattern MAIN_CSS_PATTERN = Pattern.compile("main\\.css\\?v=[a-fA-F0-9]+");

    public TimestampTracker(String filePath) {
        this.storeFile = new File(filePath);
        if (storeFile.exists()) {
            try (InputStream in = Files.newInputStream(storeFile.toPath())) {
                storedData.load(in);
            } catch (IOException e) {
                log.warn("Could not load timestamp hash file: {}", e.getMessage());
            }
        }
    }

    /**
     * Returns a stable timestamp for the given content.
     * @param hash A unique identifier for the file (e.g., its relative path).
     * @param timestamp The generated HTML content (should contain a placeholder for the timestamp).
     */
    public record TimestampEntry(String hash, String timestamp) {
        public static TimestampEntry parse(String raw) {
            if (raw == null) return null;
            String[] parts = raw.split(":", 2);
            return parts.length == 2 ? new TimestampEntry(parts[0], parts[1]) : null;
        }

        public String toRaw() {
            return hash + ":" + timestamp;
        }
    }

    public boolean isModified(String identifier, String content) {
        String currentHash = contentHash(content);
        TimestampEntry entry = TimestampEntry.parse((String) storedData.get(identifier));
        return entry == null || !entry.hash().equals(currentHash);
    }

    public String getStableTimestamp(String identifier, String content) {
        String currentHash = contentHash(content);
        TimestampEntry entry = TimestampEntry.parse((String) storedData.get(identifier));

        if (entry != null && entry.hash().equals(currentHash)) {
            // Content is the same, reuse the old timestamp
            String stableTime = entry.timestamp();
            currentSessionData.put(identifier, new TimestampEntry(currentHash, stableTime).toRaw());
            return stableTime;
        }

        // Content changed or new file, generate new timestamp
        String newTime = LocalDateTime.now().format(formatter);
        currentSessionData.put(identifier, new TimestampEntry(currentHash, newTime).toRaw());
        return newTime;
    }

    /**
     * Returns an ISO 8601 date string (yyyy-MM-dd) for the given file identifier based on tracked timestamps.
     * @param identifier Relative file path identifier.
     * @return ISO formatted date string (e.g. 2026-08-02).
     */
    public String getIsoDate(String identifier) {
        return getIsoDateOrDefault(identifier, ISO_DATE_FORMATTER.format(LocalDateTime.now()));
    }

    /**
     * Returns an ISO 8601 date string (yyyy-MM-dd) for the given file identifier, falling back to default if unavailable.
     * @param identifier Relative file path identifier.
     * @param defaultIsoDate Fallback ISO date string.
     * @return ISO formatted date string.
     */
    public String getIsoDateOrDefault(String identifier, String defaultIsoDate) {
        String raw = currentSessionData.get(identifier);
        if (raw == null) {
            raw = storedData.getProperty(identifier);
        }
        TimestampEntry entry = TimestampEntry.parse(raw);
        if (entry != null && entry.timestamp() != null) {
            String ts = entry.timestamp();
            String[] spaceParts = ts.split(" ");
            if (spaceParts.length >= 1 && spaceParts[0].contains(".")) {
                String[] dotParts = spaceParts[0].split("\\.");
                if (dotParts.length == 3) {
                    return String.format("%s-%s-%s", dotParts[2], dotParts[1], dotParts[0]);
                }
            } else if (ISO_DATE_PREFIX.matcher(ts).matches()) {
                return ts.substring(0, 10);
            }
        }
        return defaultIsoDate;
    }

    public void save() {
        try {
            Path parent = storeFile.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Merge current session data into stored data
            currentSessionData.forEach((k, v) -> storedData.setProperty(k, v));

            // Cleanup: Remove entries for files that no longer exist
            storedData.entrySet().removeIf(entry -> {
                String identifier = (String) entry.getKey();
                Path filePath = parent != null ? parent.resolve(identifier) : Paths.get(identifier);
                return !Files.exists(filePath);
            });

            try (OutputStream out = Files.newOutputStream(storeFile.toPath())) {
                storedData.store(out, "Automated Generation Timestamp Cache");
            }
        } catch (IOException e) {
            log.warn("Could not save timestamp hash file: {}", e.getMessage());
        }
    }

    /** Hash of the page content with the volatile timestamp placeholder and CSS cache-buster neutralised. */
    private static String contentHash(String content) {
        return HashUtils.md5Hex(MAIN_CSS_PATTERN.matcher(content.replace("[[STABLE_TIME]]", ""))
                .replaceAll("main.css?v=STABLE"));
    }
}
