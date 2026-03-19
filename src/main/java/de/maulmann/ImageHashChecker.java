package de.maulmann;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Properties;

public class ImageHashChecker {
    private final File storeFile;
    private final Properties hashes = new Properties();

    public ImageHashChecker(String filePath) {
        this.storeFile = new File(filePath);
        if (storeFile.exists()) {
            try (InputStream in = Files.newInputStream(storeFile.toPath())) {
                hashes.load(in);
            } catch (Exception e) {
                System.err.println("Konnte Image-Hash-Datei nicht laden: " + e.getMessage());
            }
        }
    }

    /**
     * Prüft, ob sich der MD5-Hash der Quelldatei gegenüber dem gespeicherten Wert geändert hat.
     */
    public boolean isUnchanged(Path file) {
        try {
            String currentHash = calculateMD5(file);
            String storedHash = hashes.getProperty(file.toString());
            return currentHash != null && currentHash.equals(storedHash);
        } catch (Exception e) {
            return false; // Im Zweifel als "geändert" markieren
        }
    }

    /**
     * Aktualisiert den Hash-Eintrag für eine Datei im Speicher.
     */
    public void updateHash(Path file) {
        try {
            hashes.setProperty(file.toString(), calculateMD5(file));
        } catch (Exception ignored) {
        }
    }

    /**
     * Schreibt alle Hashes aus dem Speicher in die .properties Datei.
     */
    public void save() {
        try {
            File parent = storeFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (OutputStream out = Files.newOutputStream(storeFile.toPath())) {
                hashes.store(out, "Automatisierter Image Build Hash Cache");
            }
        } catch (Exception e) {
            System.err.println("Konnte Image-Hash-Datei nicht speichern: " + e.getMessage());
        }
    }

    private String calculateMD5(Path file) throws Exception {
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