package de.maulmann;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified utility for tracking file changes using MD5 hashes stored in a properties file.
 */
public class FileTracker {
    private static final Logger log = LoggerFactory.getLogger(FileTracker.class);
    private static final String META_SUFFIX = "@meta";
    private final File storeFile;
    private final Properties hashes = new Properties();

    public FileTracker(String filePath) {
        this.storeFile = new File(filePath);
        if (storeFile.exists()) {
            try (InputStream in = Files.newInputStream(storeFile.toPath())) {
                hashes.load(in);
            } catch (IOException e) {
                log.warn("Could not load hash file: {}", e.getMessage());
            }
        }
    }

    /**
     * Normalizes path separators to standard forward slashes for cross-platform compatibility.
     */
    public static String normalizePath(Path file) {
        return file.toString().replace('\\', '/');
    }

    /**
     * Checks if the file changed compared to the stored state.
     * Fast path: if size and last-modified time match the values recorded together with the stored hash,
     * the file is treated as unchanged without re-reading it. Otherwise the MD5 hash is authoritative.
     */
    public boolean hasChanged(Path file) {
        try {
            if (!Files.exists(file)) return true;
            String key = normalizePath(file);
            String storedHash = hashes.getProperty(key);
            if (storedHash == null) return true;

            String meta = metadataOf(file);
            if (meta.equals(hashes.getProperty(key + META_SUFFIX))) {
                return false;
            }

            String currentHash = calculateMD5(file);
            if (currentHash == null) return true;
            boolean changed = !currentHash.equals(storedHash);
            if (!changed) {
                // Content identical (e.g. only mtime touched): remember metadata so the next run can skip hashing.
                hashes.setProperty(key + META_SUFFIX, meta);
            }
            return changed;
        } catch (Exception e) {
            return true; // Assume changed on error
        }
    }

    /**
     * Updates the hash entry for a file using a pre-calculated hash.
     * Metadata is cleared because it cannot be verified to belong to that hash; the next check re-hashes once.
     */
    public void updateHash(Path file, String preCalculatedHash) {
        if (preCalculatedHash != null) {
            String key = normalizePath(file);
            hashes.setProperty(key, preCalculatedHash);
            hashes.remove(key + META_SUFFIX);
        }
    }

    /**
     * Updates the hash entry for a file in memory by recalculating it.
     */
    public void updateHash(Path file) {
        try {
            // Read metadata BEFORE hashing: if the file changes meanwhile, the stored meta is stale
            // and the next check falls back to a full hash instead of missing the change.
            String meta = metadataOf(file);
            String hash = calculateMD5(file);
            if (hash != null) {
                String key = normalizePath(file);
                hashes.setProperty(key, hash);
                hashes.setProperty(key + META_SUFFIX, meta);
            }
        } catch (Exception e) {
            log.debug("Could not update hash for {}: {}", file, e.getMessage());
        }
    }

    /**
     * Returns the previously stored hash for a file.
     */
    public String getStoredHash(Path file) {
        return hashes.getProperty(normalizePath(file));
    }

    /**
     * Calculates the MD5 hash and returns it, or null if the file doesn't exist.
     */
    public String getHash(Path file) {
        try {
            return calculateMD5(file);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Saves all hashes from memory to the properties file.
     */
    public void save() {
        try {
            Path parent = storeFile.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Cleanup: Remove entries for files that no longer exist
            hashes.entrySet().removeIf(entry -> {
                String filePath = (String) entry.getKey();
                if (filePath.endsWith(META_SUFFIX)) {
                    filePath = filePath.substring(0, filePath.length() - META_SUFFIX.length());
                }
                return !Files.exists(Paths.get(filePath));
            });

            // Atomic write: a crash mid-save must not leave a truncated hash file behind.
            Path target = storeFile.toPath();
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                hashes.store(out, "Automated File Build Hash Cache");
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Could not save hash file: {}", e.getMessage());
        }
    }

    private static String metadataOf(Path file) throws IOException {
        return Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
    }

    private String calculateMD5(Path file) throws Exception {
        if (!Files.exists(file)) return null;
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
