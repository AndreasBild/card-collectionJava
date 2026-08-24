# Database & Firestore Query Performance Guidelines

## 1. Google Cloud Firestore Integration Standards
- **Client Architecture:** Use `com.google.firebase:firebase-admin` via [`FirebaseConfigManager.java`](file:///src/main/java/de/maulmann/FirebaseConfigManager.java).
- **Graceful Offline Mode:** The pipeline must always function locally in offline / fallback mode if Firestore credentials are missing or network is unreachable. Never crash `LocalDevPipeline` due to missing Firebase secrets.

## 2. Query Performance & Batching Rules
- **Batch Operations:** All Firestore write operations (ratings, card metadata, view counts) must be batched using `WriteBatch`. Respect the Firestore maximum limit of **500 operations per batch**.
- **Composite Indexing:** When querying cards across multiple filters (e.g. `year`, `brand`, `parallel`, `rating`), ensure indexes are configured in Firebase console or indexed queries.
- **Connection Lifecycle & Virtual Threads:** Utilize virtual threads for asynchronous Firestore I/O without blocking worker threads.
- **Rate Limiting & Exponential Backoff:** Wrap external Firestore calls with retry loops and exponential backoff to handle rate limits and transient network glitches.

## 3. Data Consistency & Rating Injection
- **Rating Injection:** Card ratings and community metrics are merged dynamically during static generation via [`FirestoreRatingInjector.java`](file:///src/main/java/de/maulmann/FirestoreRatingInjector.java).
- **Schema Sanity Check:** Ensure default values exist when remote documents are uninitialized or missing.
