package de.maulmann;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SiteBuilderPipeline {

    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) {
        long pipelineStart = System.currentTimeMillis();
        System.out.println("==================================================");
        System.out.println("🚀 STARTING MASTER BUILD PIPELINE (LOCAL ONLY)");
        System.out.println("==================================================");

        try {
            // Initialisiere den Hash-Cache
            FileTracker tracker = new FileTracker(OUTPUT_DIR + "/sync-hashes.properties");
            TimestampTracker timeTracker = new TimestampTracker(OUTPUT_DIR + "/generation-timestamps.properties");

            // Ensure stable CSS version for hash stability if content didn't change
            String cssHash = tracker.getHash(Paths.get("src/main/resources/css/main.css"));
            if (cssHash != null && cssHash.length() >= 8) {
                SharedTemplates.setBuildId(cssHash.substring(0, 8));
            } else {
                SharedTemplates.setBuildId("stable");
            }

            // --- PHASE 1: Generate Site HTML & Sitemap ---
            System.out.println("\n[PHASE 1] Generating HTML files and Sitemap...");
            FileGenerator.setTimestampTracker(timeTracker);
            CardPageGenerator.setTimestampTracker(timeTracker);
            SitemapGenerator.setTimestampTracker(timeTracker);

            FileGenerator.copyResources();
            FileGenerator.buildCollectionOverview();
            FileGenerator.buildOtherCollections();
            FileGenerator.buildStaticPages();
            CardPageGenerator.run();

            timeTracker.save();
            SitemapGenerator.generate();

            // --- PHASE 1.5: Inject Firestore Ratings ---
            System.out.println("\n[PHASE 1.5] Injecting Firestore ratings...");
            String firebaseCreds = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            File firebaseFile = new File("firebase/maulmann-3f90d-firebase-adminsdk-fbsvc-78c9f10838");

            if ((firebaseCreds == null || firebaseCreds.isEmpty()) && !firebaseFile.exists()) {
                System.err.println("⚠️ WARNING: Firebase credentials (env or file) are missing! Ratings will NOT be injected.");
            } else {
                FirestoreRatingInjector.main(new String[0]);
            }

            // --- PHASE 2: Convert Images to WebP ---
            System.out.println("\n[PHASE 2] Converting images to WebP...");
            ImageConverter.main(new String[0]);

            // Speichere die neuen Hashes, damit sie beim nächsten Build bekannt sind
            tracker.save();

            long pipelineEnd = System.currentTimeMillis();
            System.out.println("\n==================================================");
            System.out.println("✅ BUILD COMPLETE IN " + (pipelineEnd - pipelineStart) + " ms");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("\n❌ BUILD FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
