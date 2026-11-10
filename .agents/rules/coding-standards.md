# Modern Java 27, Concurrency & Firestore Architecture Standards

## 1. JVM & Language Invariants
- **Java 27 Preview Features:** Compile and execute strictly on Java 27 with `--enable-preview` and `-Xlint:all`. Never downgrade `<maven.compiler.source>`, `<maven.compiler.target>`, or `<maven.compiler.release>` in `pom.xml`.
- **Virtual Threads:** Use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent file I/O, parallel image conversions, and remote API calls. Avoid heavy platform thread pools for blocking operations.
- **Immutable Data Records & Pattern Matching:** Model data transfer objects, intermediate metadata, and card indexes as Java `record` classes (`CardData`, `CardJson`, `MarketSale`). Use sealed interfaces and pattern matching for `switch` expressions and `instanceof` checks.

## 2. Memory & Stream Efficiency (Hot Paths)
- **Zero Allocations:** Avoid unnecessary object allocations in tight batch loops (e.g. rendering 10,000+ cards or scanning directories).
- **Stream Optimization:** Prefer primitive streams (`IntStream`, `LongStream`) and array-backed lookups over boxed collections in critical paths.

## 3. Code Formatting & Spotless
- **Google Java Style:** Enforced by `spotless-maven-plugin` (`mvn spotless:check` / `mvn spotless:apply`).
- **Javadoc:** Provide concise Javadoc explaining *why* non-obvious architecture choices or performance optimizations were made.

## 4. Google Cloud Firestore & Backend Resilience
- **Client Architecture:** Use `com.google.firebase:firebase-admin` via [`FirebaseConfigManager.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/FirebaseConfigManager.java) and merge ratings dynamically via [`FirestoreRatingInjector.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/FirestoreRatingInjector.java).
- **Graceful Offline Mode:** `LocalDevPipeline` must always function locally in offline/fallback mode if Firestore credentials are missing or network is unreachable.
- **Batching & Backoff:** Batch Firestore writes using `WriteBatch` ($\le 500$ operations per batch) and wrap remote calls with exponential backoff.
