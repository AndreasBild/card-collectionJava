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
 * Senior Java Developer Implementation for Firestore Rating Injection.
 * This tool injects live AggregateRating data into static HTML pages.
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

    /**
     * Initializes the Firebase Admin SDK using a service account JSON string
     * provided via environment variable or a local JSON file.
     */
    private static void initFirebase() throws IOException {
        String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        String serviceAccountPath = "firebase/maulmann-3f90d-firebase-adminsdk-fbsvc-78c9f10838";

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();

        if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
            log.info("Initializing Firebase using JSON from environment variable.");
            optionsBuilder.setCredentials(GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))));
        } else {
            File serviceAccountFile = new File(serviceAccountPath);
            if (serviceAccountFile.exists()) {
                log.info("Initializing Firebase using service account file: {}", serviceAccountPath);
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream(serviceAccountFile)));
            } else {
                log.error("Firebase credentials missing. Neither FIREBASE_SERVICE_ACCOUNT_JSON env var nor file at {} exists.", serviceAccountPath);
                throw new IllegalStateException("Firebase credentials not found.");
            }
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(optionsBuilder.build());
            log.info("Firebase Admin SDK initialized successfully.");
        }
    }

    /**
     * Fetches all documents from the specified Firestore collection in a single synchronous request.
     *
     * @return A map where the key is the Document ID and the value is the document data.
     */
    private static Map<String, Map<String, Object>> fetchFirestoreData() throws ExecutionException, InterruptedException {
        log.info("Fetching data from Firestore collection: {}", COLLECTION_NAME);
        Firestore db = FirestoreClient.getFirestore();

        // Single synchronous request to minimize Firestore reads
        QuerySnapshot querySnapshot = db.collection(COLLECTION_NAME).get().get();

        Map<String, Map<String, Object>> dataMap = new HashMap<>();
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            dataMap.put(document.getId(), document.getData());
        }
        log.info("Successfully cached {} Firestore documents in memory.", dataMap.size());
        return dataMap;
    }

    /**
     * Iterates over all HTML files in the build target directory and injects JSON-LD ratings.
     */
    private static void processHtmlFiles(Map<String, Map<String, Object>> firestoreData) throws IOException {
        Path targetPath = Paths.get(TARGET_DIR);
        if (!Files.exists(targetPath)) {
            log.warn("Target directory {} not found. Aborting file processing.", TARGET_DIR);
            return;
        }

        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        try (Stream<Path> paths = Files.walk(targetPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))
                    .forEach(path -> {
                        if (injectRating(path, firestoreData)) {
                            count.incrementAndGet();
                        }
                    });
        }
        log.info("Completed: Injected ratings into {} files.", count.get());
    }

    /**
     * Parses a single HTML file, extracts the card ID and name, and injects the AggregateRating JSON-LD if data exists.
     * @return true if rating was injected, false otherwise.
     */
    private static boolean injectRating(Path path, Map<String, Map<String, Object>> firestoreData) {
        try {
            File file = path.toFile();
            Document doc = Jsoup.parse(file, "UTF-8");

            // Extract card ID from data-card-id attribute (e.g. on vote-container)
            Element cardElement = doc.select("[data-card-id]").first();
            if (cardElement == null) {
                return false; // Not a card detail page or missing ID
            }

            String cardId = cardElement.attr("data-card-id");
            Map<String, Object> data = firestoreData.get(cardId);

            if (data != null) {
                long ratingCount = parseLong(data.get("ratingCount"));
                double ratingSum = parseDouble(data.get("ratingSum"));

                if (ratingCount > 0) {
                    double averageRating = ratingSum / ratingCount;

                    // Extract product name from H1 or fallback to document title
                    String productName = doc.title();
                    Element h1Element = doc.selectFirst("h1");
                    if (h1Element != null && !h1Element.text().isBlank()) {
                        productName = h1Element.text();
                    }

                    // Pass the extracted name to the JSON-LD generator
                    String jsonLdSnippet = generateProductJsonLd(productName, averageRating, ratingCount);

                    // Create and inject the script tag into the <head>
                    Element scriptTag = new Element("script");
                    scriptTag.attr("type", "application/ld+json");
                    scriptTag.text(jsonLdSnippet);
                    doc.head().appendChild(scriptTag);

                    // Overwrite the original file with modified DOM
                    Files.writeString(path, doc.outerHtml(), StandardCharsets.UTF_8);
                    log.info("Injected ratings into: {}", path.getFileName());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to process HTML file: {}", path, e);
            // System continues with next file
        }
        return false;
    }

    /**
     * Generates a valid JSON-LD Product snippet containing AggregateRating and the mandatory name field.
     */
    private static String generateProductJsonLd(String productName, double averageRating, long reviewCount) {
        // Escape quotes to ensure valid JSON syntax
        String safeName = productName != null ? productName.replace("\"", "\\\"") : "Unknown Product";

        return String.format(Locale.US,
                "{\n" +
                        "  \"@context\": \"https://schema.org\",\n" +
                        "  \"@type\": \"Product\",\n" +
                        "  \"name\": \"%s\",\n" +
                        "  \"aggregateRating\": {\n" +
                        "    \"@type\": \"AggregateRating\",\n" +
                        "    \"ratingValue\": %.1f,\n" +
                        "    \"reviewCount\": %d\n" +
                        "  }\n" +
                        "}", safeName, averageRating, reviewCount);
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