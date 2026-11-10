# Backend & Pipeline Invariants (`src/main/java/de/maulmann/AGENTS.md`)

## 1. Java 27 & Concurrency Standards
- **Preview & Linting:** Compile and test strictly with Java 27 `--enable-preview` and `-Xlint:all`.
- **Virtual Threads:** Use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent file I/O, AVIF encoding, compression, and remote API calls.
- **Data Modeling & Hot Paths:** Use immutable `record` types (`CardData`, `CardJson`, `MarketSale`), sealed interfaces, and pattern matching (`switch` / `instanceof`). Avoid temporary object allocations inside 10,000+ card rendering loops; prefer primitive streams (`IntStream`, `LongStream`).
- **Formatting:** Enforce Google Java Style via `mvn spotless:check` / `mvn spotless:apply`.

## 2. URL Stability & Domain Integrity
- **Immutable Card URLs:** Never alter the output of [`CardData.generateStableId`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/CardData.java) (first 8 hex chars of MD5 over `relevantKeys` in exact order and `key:value|` format). Never add new fields to `relevantKeys`. Verify any generator or `CardData` change against `CardUrlStabilityTest`.
- **Schema.org & Sitemaps:** Every card page requires compliant JSON-LD via [`CardSchemaGenerator.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/CardSchemaGenerator.java) and sitemap indexing via [`SitemapGenerator.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/SitemapGenerator.java).

## 3. Companion Compression, Caching & Firestore
- **Synchronous Companions:** Every generated `.html` and `.css` file in `output/` must emit matching `.gz` ([`GZIPCompressor.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/GZIPCompressor.java)) and `.br` ([`BrotliCompressor.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/BrotliCompressor.java)) companions while respecting incremental timestamps in [`TimestampTracker.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/TimestampTracker.java).
- **Firestore Resilience:** Batch writes via `WriteBatch` ($\le 500$ ops/batch). [`LocalDevPipeline.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/LocalDevPipeline.java) and [`FirestoreRatingInjector.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/FirestoreRatingInjector.java) must degrade gracefully in offline/missing-credential environments.
