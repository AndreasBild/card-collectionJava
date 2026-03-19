package de.maulmann;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

public class ImageHashChecker {

    private final File hashStoreFile;
    private final Properties hashes;

    public ImageHashChecker(String hashStoreFilePath) {
        this.hashStoreFile = new File(hashStoreFilePath);
        this.hashes = new Properties();
        loadHashes();
    }

    // Lädt die bisherigen Hashes aus der Datei
    private void loadHashes() {
        if (hashStoreFile.exists()) {
            try (InputStream in = new FileInputStream(hashStoreFile)) {
                hashes.load(in);
            } catch (IOException e) {
                System.err.println("Konnte Hash-Datei nicht laden: " + e.getMessage());
            }
        }
    }

    // Speichert die aktualisierten Hashes in der Datei
    public void saveHashes() {
        try (OutputStream out = new FileOutputStream(hashStoreFile)) {
            hashes.store(out, "Automatisierter Image Build Hash Store");
        } catch (IOException e) {
            System.err.println("Konnte Hash-Datei nicht speichern: " + e.getMessage());
        }
    }

    // Prüft, ob ein Bild neu verarbeitet werden muss
    public boolean needsProcessing(File sourceFile, File targetFile) {
        if (!targetFile.exists()) {
            return true; // Zieldatei fehlt, muss generiert werden
        }

        try {
            String currentHash = calculateMD5(sourceFile);
            String storedHash = hashes.getProperty(sourceFile.getAbsolutePath());

            // Wenn sich der Hash geändert hat oder nicht existiert, muss es verarbeitet werden
            if (currentHash != null && !currentHash.equals(storedHash)) {
                return true;
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println("Fehler beim Hashing von " + sourceFile.getName() + ": " + e.getMessage());
            return true; // Im Zweifel lieber neu generieren
        }

        return false; // Alles aktuell!
    }

    // Aktualisiert den Hash nach erfolgreicher Verarbeitung
    public void markAsProcessed(File sourceFile) {
        try {
            String hash = calculateMD5(sourceFile);
            hashes.setProperty(sourceFile.getAbsolutePath(), hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println("Konnte Hash nach Verarbeitung nicht aktualisieren: " + e.getMessage());
        }
    }

    // Berechnet den eigentlichen MD5 Hash der Datei
    private String calculateMD5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file.toPath())) {
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