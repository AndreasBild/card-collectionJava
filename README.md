# 🏀 maulmann.de – Card Collection Engine (`card-collectionJava`)

[![Java 26](https://img.shields.io/badge/Java-26%20Preview-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven Build & CI](https://img.shields.io/badge/Build-Maven%203.9+-C71A36?logo=apachemaven&logoColor=white)](pom.xml)
[![Zero-JS Performance](https://img.shields.io/badge/Performance-Core%20Web%20Vitals%20100-brightgreen)](https://www.maulmann.de)
[![LLMO & SEO](https://img.shields.io/badge/SEO-Schema.org%20JSON--LD-blue)](src/main/java/de/maulmann/CardSchemaGenerator.java)
[![Code Style: Spotless](https://img.shields.io/badge/Code%20Style-Spotless%20Google%20Java-blue)](pom.xml)

> **High-Performance Static Site Generator (SSG) & Publishing Pipeline** for [maulmann.de](https://www.maulmann.de) – the ultimate Juwan Howard Super Collector Archive and Showcase.

---

## 🚀 Key Features & Highlights

- **⚡ Zero / Micro-JS Architecture:** Blazing-fast static delivery with zero layout shifts ($\text{CLS} = 0$, $\text{LCP} < 1.2\text{s}$).
- **🖼️ Pure AVIF Next-Gen Image Engine:** Multi-threaded responsive AVIF generation (`.avif`, `200w`, `400w`, `600w`, `900w`) via Virtual Threads and `avifenc`, with automated orphan image sweeping and fallback generation.
- **🤖 LLM & Search Engine Optimization (LLMO/SEO):** Comprehensive **Schema.org JSON-LD** structured data for every card entity via [`CardSchemaGenerator`](file:///src/main/java/de/maulmann/CardSchemaGenerator.java), canonical URLs, Open Graph, Twitter Cards, `llms.txt`, and XML image sitemaps.
- **🗜️ Pre-Compressed Asset Pipeline:** Automated in-place minification and synchronous companion compression (**GZIP** `.gz` and **Brotli** `.br`).
- **🔄 Incremental & Cache-Aware Builds:** Deterministic timestamp caching (`generation-timestamps.properties`) preventing redundant rendering.
- **📖 3D 9-Pocket Collector's Binder:** Realistic 3D flip-page binder view ([`binder.html`](file:///src/main/java/de/maulmann/BinderPageGenerator.java)) with keyboard navigation and page jump shortcuts.
- **🌈 Parallel Rainbow Tracker:** Strict single-card parallel completion matrix ([`rainbows.html`](file:///src/main/java/de/maulmann/RainbowPageGenerator.java)) with visual progress indicators and rare 1/1 badges.
- **🔍 Side-by-Side Spec Comparison Matrix:** Compare multiple cards with front/back scan synchronization, difference-highlighting, and direct URL sharing (`#compare=...`).
- **📱 PWA Offline & Security Hardened:** Offline AVIF image caching (LRU 250), Service Worker navigation preload, app shortcuts, strict Content Security Policy (CSP), and permissions policies.
- **☁️ Cloud & Discovery Integration:**
  - **AWS S3 & CloudFront:** Static asset hosting and automatic CDN cache invalidation.
  - **Google Firebase Firestore:** Real-time community rating injection and seeding.
  - **IndexNow API:** Instant indexing submissions to search engines on publish.

---

## 📁 Repository Quick Links & Documentation

- 📘 [**ARCHITECTURE.md**](ARCHITECTURE.md) – Detailed system architecture, data DAG, and the 6-stage development lifecycle.
- 🤖 [**AGENTS.md**](AGENTS.md) – Operational guidelines, Java 26 standards, and persona boundaries (Antigravity & Jules).
- 📜 [**llms.txt**](llms.txt) – AI crawler manifest and semantic structure map.

---

## 🛠️ Quickstart & Execution Commands

### Prerequisites
- **JDK 26** (with preview features enabled)
- **Maven 3.9+**

### 1. Local Development Build (Fast, Offline)
Runs incremental site generation without external network calls (skips AWS, Firebase remote seeding, IndexNow):
```bash
mvn exec:java@local
```
To build and start the embedded local preview web server:
```bash
mvn exec:java@local -Dexec.args="--serve"
```

### 2. Full Production Pipeline Build
Executes full site generation, image optimization, minification, compression, Firestore sync, sitemaps, and deployment triggers:
```bash
mvn exec:java@prod
# or: mvn clean compile exec:java -Dexec.mainClass="de.maulmann.SiteBuilderPipeline"
```

### 3. Code Quality & Test Suite
```bash
# Run all JUnit 5 unit & integration tests
mvn test

# Verify and apply Spotless code formatting
mvn spotless:check
mvn spotless:apply
```

---

## 🏗️ Project Architecture Map

```text
content/json/                  # Source datasets (cards.json, baseball.json, etc.)
src/main/
├── java/de/maulmann/
│   ├── SiteBuilderPipeline.java   # Production orchestrator & deployment
│   ├── LocalDevPipeline.java      # Fast local dev build & preview web server
│   ├── CardDataLoader.java        # Card dataset parser & loader
│   ├── CardPageGenerator.java     # Core Freemarker rendering engine
│   ├── BinderPageGenerator.java   # 3D 9-pocket collector's binder generator
│   ├── RainbowPageGenerator.java  # Strict parallel rainbow tracker generator
│   ├── StaticPageGenerator.java   # Hub, error, wantlist & team page generator
│   ├── CardSchemaGenerator.java   # Schema.org JSON-LD structured data
│   ├── CardMetadataRenderer.java  # Freemarker helper & metadata formatter
│   ├── CardStatsService.java      # Collection analytics & statistics service
│   ├── ImageConverter.java        # Pure AVIF conversion & responsive srcset generator
│   ├── HTMLMinifier.java          # Whitespace & comment stripping
│   ├── CSSMinifier.java           # CSS minification
│   ├── GZIPCompressor.java        # GZIP companion generator (.gz)
│   ├── BrotliCompressor.java      # Brotli companion generator (.br)
│   ├── SitemapGenerator.java      # XML/HTML sitemaps, RSS feed & llms.txt
│   ├── IndexNowService.java       # Instant search engine indexing
│   ├── FirestoreRatingInjector.java # Firebase rating integration
│   └── TimestampTracker.java      # Incremental build-cache manager
└── resources/
    ├── css/                       # Vanilla CSS styles & theme tokens
    ├── pwa/                       # Service Worker, manifest & collector JS
    └── templates/                 # Freemarker UI templates (.ftlh)
output/                            # Generated static site distribution
```

---

## 📄 License & Ownership
Private collection archive and publishing pipeline © Andreas Bild ([maulmann.de](https://www.maulmann.de)).
