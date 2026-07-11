package de.maulmann;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Seeder script to initialize every card in the collection with a 5-star rating and 1 like.
 */
public class FirestoreRatingSeeder {

    private static final Logger log = LoggerFactory.getLogger(FirestoreRatingSeeder.class);
    private static final String COLLECTION_NAME = "Trading_cards";
    private static final String CONTENT_DIR = "content";

    public static void main(String[] args) {
        log.info("Starting Firestore rating seeding process...");
        try {
            initFirebase();
            processCollections();
            log.info("Firestore rating seeding completed successfully.");
        } catch (Exception e) {
            log.error("Fatal error during Firestore rating seeding", e);
            System.exit(1);
        }
    }

    private static void initFirebase() throws IOException {
        String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        String serviceAccountPath = "firebase/maulmann-3f90d-firebase-adminsdk-fbsvc-78c9f10838";

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();

        if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
            optionsBuilder.setCredentials(GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))));
        } else {
            File serviceAccountFile = new File(serviceAccountPath);
            if (serviceAccountFile.exists()) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream(serviceAccountFile)));
            } else {
                throw new IllegalStateException("Firebase credentials not found. Set FIREBASE_SERVICE_ACCOUNT_JSON or ensure the service account file exists at " + serviceAccountPath);
            }
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(optionsBuilder.build());
        }
    }

    private static void processCollections() throws IOException {
        Firestore db = FirestoreClient.getFirestore();
        final WriteBatch[] batch = {db.batch()};
        int[] counter = {0};
        int[] totalProcessed = {0};

        // Main collection files
        File contentDir = new File(CONTENT_DIR);
        File[] mainFiles = contentDir.listFiles((dir, name) -> name.endsWith(".html"));
        if (mainFiles != null) {
            for (File file : mainFiles) {
                processFile(file.toPath(), db, batch, counter, totalProcessed);
            }
        }

        // Other collections
        File otherDir = new File(CONTENT_DIR, "other");
        if (otherDir.exists()) {
            File[] otherFiles = otherDir.listFiles((dir, name) -> name.endsWith(".html")
                    && !name.equalsIgnoreCase("imprint.html")
                    && !name.equalsIgnoreCase("privacy.html"));
            if (otherFiles != null) {
                for (File file : otherFiles) {
                    processFile(file.toPath(), db, batch, counter, totalProcessed);
                }
            }
        }

        if (counter[0] > 0) {
            log.info("Committing final batch of {} updates...", counter[0]);
            batch[0].commit();
        }
        log.info("Total unique cards seeded: {}", totalProcessed[0]);
    }

    private static void processFile(Path path, Firestore db, WriteBatch[] batch, int[] counter, int[] totalProcessed) throws IOException {
        File file = path.toFile();
        Document doc = Jsoup.parse(file, "UTF-8");
        Elements tables = doc.select("table");

        for (Element table : tables) {
            Elements rows = table.select("tr");
            if (rows.isEmpty()) continue;

            String[] headers = null;
            int headerRowIndex = -1;

            for (int i = 0; i < rows.size(); i++) {
                Elements cells = rows.get(i).children();
                if (cells.isEmpty()) continue;
                headers = new String[cells.size()];
                for (int j = 0; j < cells.size(); j++) {
                    headers[j] = cells.get(j).text().trim();
                }
                // Look for the "Player" header to identify the card table
                boolean hasPlayer = false;
                for (String h : headers) {
                    if ("Player".equalsIgnoreCase(h)) {
                        hasPlayer = true;
                        break;
                    }
                }
                if (hasPlayer) {
                    headerRowIndex = i;
                    break;
                }
            }

            if (headerRowIndex == -1) continue;

            for (int i = headerRowIndex + 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cols = row.children();
                if (cols.isEmpty()) continue;

                Map<String, String> dataMap = new HashMap<>();
                for (int j = 0; j < cols.size() && j < headers.length; j++) {
                    dataMap.put(headers[j], cols.get(j).text().trim());
                }

                String stableId = CardPageGenerator.generateStableId(dataMap);
                seedCard(stableId, db, batch, counter, totalProcessed);
            }
        }
    }

    private static void seedCard(String cardId, Firestore db, WriteBatch[] batch, int[] counter, int[] totalProcessed) {
        Map<String, Object> update = new HashMap<>();
        update.put("likes", 1);
        update.put("dislikes", 0);
        update.put("ratingCount", 1);
        update.put("ratingSum", 5.0);

        batch[0].set(db.collection(COLLECTION_NAME).document(cardId), update);
        counter[0]++;
        totalProcessed[0]++;

        if (counter[0] >= 500) {
            log.info("Committing batch of 500 updates...");
            batch[0].commit();
            batch[0] = db.batch();
            counter[0] = 0;
        }
    }
}
