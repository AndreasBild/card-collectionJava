package de.maulmann;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private static final String CACHE_FILE = "output/rating-cache.properties";

    private static final java.util.Properties ratingCache = new java.util.Properties();

    public static void main(String[] args) {
        log.info("Starting Firestore rating injection process...");
        try {
            loadCache();
            FirebaseConfigManager.initFirebase();
            Map<String, Map<String, Object>> firestoreData = fetchFirestoreData();
            processHtmlFiles(firestoreData);
            saveCache();
            log.info("Firestore rating injection completed successfully.");
        } catch (Exception e) {
            log.error("Fatal error during Firestore rating injection", e);
            System.exit(1);
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

        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
             Stream<Path> paths = Files.walk(targetPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))
                    .forEach(path -> executor.submit(() -> injectRating(path, firestoreData)));
        }
    }

    private static void loadCache() {
        File file = new File(CACHE_FILE);
        if (file.exists()) {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                ratingCache.load(in);
            } catch (IOException e) {
                log.warn("Could not load rating cache: {}", e.getMessage());
            }
        }
    }

    private static void saveCache() {
        File file = new File(CACHE_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            ratingCache.store(out, "Firestore Rating Injection Cache");
        } catch (IOException e) {
            log.warn("Could not save rating cache: {}", e.getMessage());
        }
    }

    private static boolean injectRating(Path path, Map<String, Map<String, Object>> firestoreData) {
        try {
            File file = path.toFile();
            Document doc = Jsoup.parse(file, "UTF-8");
            boolean modified = processDocument(doc, path.getFileName().toString(), firestoreData);
            if (modified) {
                log.info("MODIFIED DISK FILE IN FIRESTORE INJECTOR: {}", path);
                Files.writeString(path, doc.outerHtml(), StandardCharsets.UTF_8);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to process HTML file: {}", path, e);
        }
        return false;
    }

    static boolean processDocument(Document doc, String fileName, Map<String, Map<String, Object>> firestoreData) {
        Element cardElement = doc.select("[data-card-id]").first();
        String cardId = cardElement != null ? cardElement.attr("data-card-id") : null;
        Map<String, Object> data = cardId != null ? firestoreData.get(cardId) : null;

        Element templateScript = doc.selectFirst("script#product-schema-template");
        boolean isJsonTemplate = templateScript != null && "application/json".equalsIgnoreCase(templateScript.attr("type"));

        boolean modified = false;

        if (data != null) {
            long ratingCount = parseLong(data.get("ratingCount"));
            double ratingSum = parseDouble(data.get("ratingSum"));

            if (ratingCount > 0 && ratingSum >= 0) {
                String cacheKey = cardId != null ? cardId : fileName;
                String cacheValue = ratingCount + ":" + ratingSum;
                String storedValue = ratingCache.getProperty(cacheKey);

                if (!cacheValue.equals(storedValue) || isJsonTemplate) {
                    double rawAverage = ratingSum / ratingCount;
                    double averageRating = Math.clamp(rawAverage, 1.0, 5.0);

                    Element productScript = templateScript;
                    if (productScript == null) {
                        for (Element script : doc.select("script[type=application/ld+json]")) {
                            if (script.html().contains("\"@type\": \"Product\"")) {
                                productScript = script;
                                break;
                            }
                        }
                    }

                    if (productScript != null) {
                        String jsonContent = productScript.html();
                        int lastBraceIndex = jsonContent.lastIndexOf("}");
                        if (lastBraceIndex != -1 && !jsonContent.contains("\"aggregateRating\"")) {
                            String ratingInjection = String.format(Locale.US,
                                    ",\n  \"aggregateRating\": {\n" +
                                            "    \"@type\": \"AggregateRating\",\n" +
                                            "    \"ratingValue\": %.1f,\n" +
                                            "    \"reviewCount\": %d\n" +
                                            "  }\n", averageRating, ratingCount);

                            jsonContent = jsonContent.substring(0, lastBraceIndex) + ratingInjection + "}";
                            productScript.text(jsonContent);
                            productScript.attr("type", "application/ld+json");
                            productScript.removeAttr("id");
                            modified = true;
                        } else if (isJsonTemplate) {
                            productScript.attr("type", "application/ld+json");
                            productScript.removeAttr("id");
                            modified = true;
                        }
                    }

                    if (!cacheValue.equals(storedValue)) {
                        log.info("Injected NEW ratings into: {} (Count: {}, Sum: {})", fileName, ratingCount, ratingSum);
                        ratingCache.setProperty(cacheKey, cacheValue);
                    }
                }
            }
        }

        if (isJsonTemplate && (data == null || parseLong(data.get("ratingCount")) <= 0)) {
            templateScript.remove();
            modified = true;
        }

        return modified;
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