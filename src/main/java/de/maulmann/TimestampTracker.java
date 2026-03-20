package de.maulmann;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages stable timestamps for generated HTML files.
 * If the content (excluding the timestamp) hasn't changed, the old timestamp is returned.
 */
public class TimestampTracker {
    private final File storeFile;
    private final Properties storedData = new Properties();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // Key: File path, Value: {hash}:{timestamp}
    private final ConcurrentHashMap<String, String> currentSessionData = new ConcurrentHashMap<>();

    public TimestampTracker(String filePath) {
        this.storeFile = new File(filePath);
        if (storeFile.exists()) {
            try (InputStream in = Files.newInputStream(storeFile.toPath())) {
                storedData.load(in);
            } catch (Exception e) {
                System.err.println("Could not load timestamp hash file: " + e.getMessage());
            }
        }
    }

    /**
     * Returns a stable timestamp for the given content.
     * @param identifier A unique identifier for the file (e.g., its relative path).
     * @param content The generated HTML content (should contain a placeholder for the timestamp).
     * @return A stable timestamp string.
     */
    public String getStableTimestamp(String identifier, String content) {
        String contentToHash = content.replace("[[STABLE_TIME]]", "")
                .replaceAll("main\\.css\\?v=\\d+", "main.css?v=STABLE");
        String currentHash = calculateHash(contentToHash);
        String entry = (String) storedData.get(identifier);

        if (entry != null) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2 && parts[0].equals(currentHash)) {
                // Content is the same, reuse the old timestamp
                String stableTime = parts[1];
                currentSessionData.put(identifier, currentHash + ":" + stableTime);
                return stableTime;
            }
        }

        // Content changed or new file, generate new timestamp
        String newTime = LocalDateTime.now().format(formatter);
        currentSessionData.put(identifier, currentHash + ":" + newTime);
        return newTime;
    }

    public void save() {
        try {
            File parent = storeFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            // Merge current session data into stored data
            currentSessionData.forEach((k, v) -> storedData.setProperty(k, v));

            try (OutputStream out = Files.newOutputStream(storeFile.toPath())) {
                storedData.store(out, "Automated Generation Timestamp Cache");
            }
        } catch (Exception e) {
            System.err.println("Could not save timestamp hash file: " + e.getMessage());
        }
    }

    private String calculateHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
