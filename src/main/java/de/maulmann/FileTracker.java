package de.maulmann;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Properties;

/**
 * Unified utility for tracking file changes using MD5 hashes stored in a properties file.
 */
public class FileTracker {
    private final File storeFile;
    private final Properties hashes = new Properties();

    public FileTracker(String filePath) {
        this.storeFile = new File(filePath);
        if (storeFile.exists()) {
            try (InputStream in = Files.newInputStream(storeFile.toPath())) {
                hashes.load(in);
            } catch (Exception e) {
                System.err.println("Could not load hash file: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if the MD5 hash of the file has changed compared to the stored value.
     */
    public boolean hasChanged(Path file) {
        try {
            String currentHash = calculateMD5(file);
            String storedHash = hashes.getProperty(file.toString());
            return storedHash == null || !currentHash.equals(storedHash);
        } catch (Exception e) {
            return true; // Assume changed on error
        }
    }

    /**
     * Updates the hash entry for a file using a pre-calculated hash.
     */
    public void updateHash(Path file, String preCalculatedHash) {
        if (preCalculatedHash != null) {
            hashes.setProperty(file.toString(), preCalculatedHash);
        }
    }

    /**
     * Updates the hash entry for a file in memory by recalculating it.
     */
    public void updateHash(Path file) {
        try {
            String hash = calculateMD5(file);
            updateHash(file, hash);
        } catch (Exception ignored) {
        }
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
            File parent = storeFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (OutputStream out = Files.newOutputStream(storeFile.toPath())) {
                hashes.store(out, "Automated File Build Hash Cache");
            }
        } catch (Exception e) {
            System.err.println("Could not save hash file: " + e.getMessage());
        }
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
