package de.maulmann;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Validates and activates dormant Product schemas if rating data exists.
 * Deletes invalid schema templates to maintain GSC compliance.
 */
public class FirestoreRatingInjector {

    private static final Logger log = LoggerFactory.getLogger(FirestoreRatingInjector.class);
    private static final String COLLECTION_NAME = "Trading_cards";
    private static final String TARGET_DIR = "output/cards";

    public static void main(String[] args) {
        log.info("Starting Firestore rating injection process...");
        try {
            initFirebase();
            Map<String, Map<String, Object>> firestoreData = fetchFirestoreData();
            processHtmlFiles(firestoreData);
            log.info("Firestore rating injection completed successfully.");
        } catch (Exception e) {
            log.error("Fatal error during Firestore rating injection", e);
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
                throw new IllegalStateException("Firebase credentials not found.");
            }
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(optionsBuilder.build());
        }
    }

    private static Map<String, Map<String, Object>> fetchFirestoreData() throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME).get().get();

        Map<String, Map<String, Object>> dataMap = new HashMap<>();
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            dataMap.put(document.getId(), document.getData());
        }
        return dataMap;
    }

    private static void processHtmlFiles(Map<String, Map<String, Object>> firestoreData) throws IOException {
        Path targetPath = Paths.get(TARGET_DIR);
        if (!Files.exists(targetPath)) return;

        try (Stream<Path> paths = Files.walk(targetPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))
                    .forEach(path -> injectRating(path, firestoreData));
        }
    }

    private static boolean injectRating(Path path, Map<String, Map<String, Object>> firestoreData) {
        try {
            File file = path.toFile();
            Document doc = Jsoup.parse(file, "UTF-8");

            Element templateScript = doc.selectFirst("script#product-schema-template");
            if (templateScript == null) {
                return false;
            }

            Element cardElement = doc.select("[data-card-id]").first();
            String cardId = cardElement != null ? cardElement.attr("data-card-id") : null;
            Map<String, Object> data = cardId != null ? firestoreData.get(cardId) : null;

            boolean isDomModified = false;

            if (data != null) {
                long ratingCount = parseLong(data.get("ratingCount"));
                double ratingSum = parseDouble(data.get("ratingSum"));

                if (ratingCount > 0) {
                    double averageRating = ratingSum / ratingCount;
                    String jsonContent = templateScript.html();
                    int lastBraceIndex = jsonContent.lastIndexOf("}");

                    if (lastBraceIndex != -1) {
                        String ratingInjection = String.format(Locale.US,
                                ",\n  \"aggregateRating\": {\n" +
                                        "    \"@type\": \"AggregateRating\",\n" +
                                        "    \"ratingValue\": %.1f,\n" +
                                        "    \"reviewCount\": %d\n" +
                                        "  }\n", averageRating, ratingCount);

                        jsonContent = jsonContent.substring(0, lastBraceIndex) + ratingInjection + "}";

                        // Activate script for crawlers
                        templateScript.text(jsonContent);
                        templateScript.attr("type", "application/ld+json");
                        templateScript.removeAttr("id");
                        isDomModified = true;
                        log.info("Injected ratings into: {}", path.getFileName());

                    }
                }
            }

            // Clean up DOM: Remove the template if no rating was found
            if (!isDomModified) {
                templateScript.remove();
                isDomModified = true;
            }

            if (isDomModified) {
                Files.writeString(path, doc.outerHtml(), StandardCharsets.UTF_8);
                return true;
            }

        } catch (Exception e) {
            log.error("Failed to process HTML file: {}", path, e);
        }
        return false;
    }

    private static long parseLong(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        return 0;
    }

    private static double parseDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return 0.0;
    }
}