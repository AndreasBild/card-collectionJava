# 🏀 maulmann.de – Card Collection Engine (`card-collectionJava`)

[![Java 26](https://img.shields.io/badge/Java-26%20Preview-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven Build & CI](https://img.shields.io/badge/Build-Maven%203.9+-C71A36?logo=apachemaven&logoColor=white)](pom.xml)
[![Zero-JS Performance](https://img.shields.io/badge/Performance-Core%20Web%20Vitals%20100-brightgreen)](https://www.maulmann.de)
[![LLMO & SEO](https://img.shields.io/badge/SEO-Schema.org%20JSON--LD-blue)](src/main/java/de/maulmann/CardSchemaGenerator.java)

> **High-Performance Static Site Generator (SSG) & Publishing Pipeline** for [maulmann.de](https://www.maulmann.de) – the ultimate Juwan Howard Super Collector Archive and Showcase.

---

## 🚀 Key Features & Highlights

- **⚡ Zero / Micro-JS Architecture:** Blazing-fast static delivery with zero layout shifts ($\text{CLS} = 0$, $\text{LCP} < 1.2\text{s}$).
- **🤖 LLM & Search Engine Optimization (LLMO/SEO):** Comprehensive **Schema.org JSON-LD** structured data for every card entity via `CardSchemaGenerator`.
- **🗜️ Pre-Compressed Asset Pipeline:** Automated in-place minification and synchronous companion compression (**GZIP** `.gz` and **Brotli** `.br`).
- **🔄 Incremental & Cache-Aware Builds:** Deterministic timestamp caching (`generation-timestamps.properties`) preventing redundant rendering.
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
mvn clean compile exec:java -Dexec.mainClass="de.maulmann.LocalDevPipeline"
```

### 2. Full Production Pipeline Build
Executes full site generation, image optimization, minification, compression, Firestore sync, sitemaps, and deployment triggers:
```bash
mvn clean compile exec:java -Dexec.mainClass="de.maulmann.SiteBuilderPipeline"
```

### 3. Run JUnit 5 Test Suite
```bash
mvn test
```

---

## 🏗️ Project Architecture Map

```text
content/json/                  # Source datasets (cards.json, baseball.json, etc.)
src/main/
├── java/de/maulmann/
│   ├── SiteBuilderPipeline.java   # Production orchestrator
│   ├── LocalDevPipeline.java      # Fast local dev build
│   ├── CardDataLoader.java        # Card dataset parser
│   ├── CardPageGenerator.java     # Core Freemarker rendering engine
│   ├── CardSchemaGenerator.java   # Schema.org JSON-LD structured data
│   ├── ImageConverter.java        # WebP image processing
│   ├── GZIPCompressor.java        # GZIP companion generator (.gz)
│   ├── BrotliCompressor.java      # Brotli companion generator (.br)
│   └── FirestoreRatingInjector.java # Firebase rating integration
└── resources/
    └── templates/                 # Freemarker UI templates (.ftlh)
output/                            # Generated static site distribution
```

---

## 📄 License & Ownership
Private collection archive and publishing pipeline © Andreas Bild ([maulmann.de](https://www.maulmann.de)).
