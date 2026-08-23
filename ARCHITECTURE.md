# Architecture & Agent Workflow Guide (`card-collectionJava`)

## 1. System Overview & Core Objectives
`card-collectionJava` is an ultra-high-performance static site generation (SSG) pipeline and publishing engine for **maulmann.de** (the Juwan Howard Super Collector archive & showcase).

### Core Architectural Principles:
- **Zero-JS / Micro-JS Delivery:** Maximum performance, near-perfect Core Web Vitals (LCP, INP, CLS).
- **LLM & Search Engine Optimization (LLMO/SEO):** Schema.org JSON-LD structured entity data injected on every card page.
- **Pre-computed Asset Pipeline:** In-place HTML/CSS minification, Next-Gen AVIF conversion, and companion pre-compression (`.gz`, `.br`).
- **Incremental & Cache-Aware Builds:** Deterministic file modification tracking to eliminate redundant generation cycles.
- **Automated Cloud Sync & Discovery:** Seamless integration with AWS S3/CloudFront, Google Firebase Firestore, and IndexNow.
- **Upstream Data Ingestion:** Card datasets are maintained in the upstream `cardCollection` project (MySQL / Spring Boot) and exported directly into `content/json/cards.json` before triggering pipeline builds.

---

## 2. Dual-Agent Infrastructure & Development Lifecycle

The development infrastructure combines **Local Generation (Antigravity)**, **IDE Verification & Commit (IntelliJ IDEA)**, and **Cloud CI & Test Generation (Jules on GitHub)**.

```mermaid
flowchart TD
    subgraph Step1 ["1. Initialization (IntelliJ)"]
        Main[Branch: main] -->|Create Feature Branch| FB[Branch: feature/xyz]
    end

    subgraph Step2 ["2. Generation (Antigravity)"]
        FB -->|Run Prompts / Tasks| AG[Antigravity Agent (Terminal / IDE)]
        AG -->|Generate / Refactor Code| Files[Local File Changes]
    end

    subgraph Step3_4 ["3 & 4. Compile, Analyze & Refine (IntelliJ)"]
        Files --> Sync[IntelliJ File Sync]
        Sync --> Compile[Syntax & Type Check / Compile]
        Sync --> DBCheck[Verify MySQL / Data Logic via IntelliJ DB Tools]
        Sync --> StaticAnalysis[Static Code Analysis & Lints]
    end

    subgraph Step5 ["5. Commit & Push (IntelliJ)"]
        Compile & DBCheck & StaticAnalysis --> Commit[IntelliJ Commit & Push to Origin]
    end

    subgraph Step6 ["6. PR & Jules Integration (GitHub)"]
        Commit --> PR[Create Pull Request on GitHub]
        PR --> Jules[Jules Agent (Async on GitHub)]
        Jules -->|Generate Missing Unit Tests| Tests[Unit & Integration Tests]
        Jules -->|Run Build & Verify CI| CI[GitHub Actions Build Checks]
        CI -->|Pass & Review| Merge[Merge into main]
    end
```

---

## 3. Strict Workflow Governance Rules

To prevent faulty generated code from reaching production, **Antigravity, Jules, and all developers must strictly obey these workflow rules**:

### 3.1 Feature-Branching & Main Protection
- **No Direct Changes on Main:** Antigravity and Jules **must never** execute modifications directly on the `main` branch. All work is isolated in dedicated feature branches (`feature/<topic>` or `fix/<topic>`).
- **Branch Isolation:** Each discrete task must have its own branch created via IntelliJ Git integration before code generation begins.

### 3.2 Six-Stage Workflow Cycle
1. **Initialisierung (Initialization):** Create a dedicated feature branch via IntelliJ Git integration.
2. **Generierung (Generation):** Execute Antigravity commands/prompts in the terminal or workspace to generate or refactor code.
3. **Kompilierung & Typenprüfung (Compile & Type Check):** Switch to IntelliJ IDEA. The IDE synchronizes file changes and is used for syntax verification, Java 26 preview type checking, and static code inspection.
4. **Refining & Verification:** Perform manual adjustments, fix compiler warnings/errors, and use IntelliJ Database Tools to verify MySQL/Firestore schemas and queries.
5. **Commit & Push:** After local verification passes, stage and commit the changes via IntelliJ IDEA in the feature branch and push to GitHub.
6. **PR & Jules-Integration:** Open a Pull Request on GitHub. The **Jules** agent asynchronously:
   - Analyzes newly introduced classes/methods.
   - Generates missing unit and integration tests (`src/test/java`).
   - Runs full build checks and test suites before PR review and merge.

---

## 4. High-Level Technical Pipeline Flow

```mermaid
flowchart TD
    subgraph Inputs ["1. Raw Inputs & Assets"]
        HTML[Raw Content / HTML <br/><code>content/</code>]
        IMG[Original Images <br/><code>images/</code>]
        FTL[Freemarker Templates <br/><code>src/main/resources/templates/</code>]
        FS[(Firebase Firestore <br/>Card Ratings)]
    end

    subgraph CoreEngine ["2. Processing Engine (Java 26)"]
        Loader[CardDataLoader & HtmlToJsonConverter]
        Tracker[TimestampTracker & FileTracker]
        Gen[CardPageGenerator & SharedTemplates]
        Schema[CardSchemaGenerator (JSON-LD)]
        ImgConv[ImageConverter (AVIF/Responsive)]
        Rating[FirestoreRatingInjector]
    end

    subgraph Optimization ["3. Post-Processing & Compression"]
        Min[HTMLMinifier & CSSMinifier]
        CompGZ[GZIPCompressor]
        CompBR[BrotliCompressor]
        Sitemap[SitemapGenerator]
    end

    subgraph Outputs ["4. Output & Deployment"]
        Dist[Static Site Output <br/><code>output/</code>]
        AWS[AWS S3 + CloudFront CDN]
        INow[IndexNow API Submissions]
    end

    HTML --> Loader
    IMG --> ImgConv
    Loader --> Gen
    FTL --> Gen
    FS <--> Rating
    Rating --> Gen
    Gen --> Schema
    Schema --> Min
    ImgConv --> Dist
    Min --> CompGZ & CompBR
    CompGZ & CompBR --> Dist
    Gen --> Sitemap --> Dist
    Tracker -.-> Loader
    Dist --> AWS
    Sitemap --> INow
```

---

## 5. Directory Layout & Module Responsibilities

```text
.
├── .agents/
│   ├── rules/                    # Antigravity micro-rules (compression, freemarker)
│   └── skills/                   # Antigravity skills (build-pipeline, verify-schema)
├── .editorconfig                 # Standardized formatting for IDE & Agents
├── .github/
│   ├── workflows/ci.yml          # Automated CI test verification
│   └── pull_request_template.md  # Jules review checklist
├── .jules/
│   └── setup.sh                  # Environment initialization script for Jules Cloud Agent
├── AGENTS.md                     # Core developer persona & coding standards
├── ARCHITECTURE.md               # System topology, workflow rules, and invariants
├── README.md                     # Project overview & badges
├── llms.txt                      # AI crawler manifest & site summary
├── output/                       # Generated site artifacts (.html, .br, .gz, .webp, sitemaps)
│   └── generation-timestamps.properties # Build-cache tracking file
├── content/                      # Raw source HTML and card data collections
│   └── json/                     # JSON datasets (cards.json exported from cardCollection)
├── images/                       # Source image assets
├── src/
│   ├── main/
│   │   ├── java/de/maulmann/
│   │   │   ├── SiteBuilderPipeline.java      # Main production pipeline orchestrator
│   │   │   ├── LocalDevPipeline.java         # Fast local build pipeline (skips external APIs)
│   │   │   ├── CardDataLoader.java           # Card dataset loader & parser
│   │   │   ├── HtmlToJsonConverter.java      # Content HTML to structured CardJson parser
│   │   │   ├── CardPageGenerator.java        # Core Freemarker page generation logic
│   │   │   ├── CardSchemaGenerator.java      # Schema.org JSON-LD generator for card entities
│   │   │   ├── FirestoreRatingInjector.java  # Injects real-time community ratings into static pages
│   │   │   ├── FirestoreRatingSeeder.java    # Seeds initial rating data to Firebase
│   │   │   ├── ImageConverter.java           # Automated image conversion (WebP, resizing)
│   │   │   ├── HTMLMinifier.java             # In-house whitespace/comment stripping
│   │   │   ├── CSSMinifier.java              # YUI-based CSS compression
│   │   │   ├── GZIPCompressor.java           # Pre-generates .gz companions
│   │   │   ├── BrotliCompressor.java         # Pre-generates .br companions via brotli4j
│   │   │   ├── SitemapGenerator.java         # XML/HTML sitemap builder
│   │   │   ├── IndexNowService.java          # Submits updated URLs to search engines via IndexNow
│   │   │   ├── TimestampTracker.java         # State persistence for incremental generation
│   │   │   └── FileTracker.java              # File hash and modification check utilities
│   │   └── resources/
│   │       └── templates/                    # Freemarker (.ftlh) UI templates
│   └── test/
│       └── java/de/maulmann/                 # Jules test generation target (JUnit 5)
```

---

## 6. Execution Commands & Agent Verification

### 6.1 Local Development Build
Runs an incremental build without invoking external APIs (skips AWS, Firebase remote seeding, IndexNow):
```bash
mvn clean compile exec:java -Dexec.mainClass="de.maulmann.LocalDevPipeline"
```

### 6.2 Full Production Pipeline Build
Executes the full pipeline (image conversion, minification, compression, Firestore sync, sitemaps, deployment triggers):
```bash
mvn clean compile exec:java -Dexec.mainClass="de.maulmann.SiteBuilderPipeline"
```

### 6.3 Test Suite Execution (Local & CI)
```bash
mvn test
```

---

## 7. Critical Invariants for Antigravity & Jules

When proposing changes, creating PRs, or refactoring code:

1. **Companion Pre-Compression Sync:**
   - Any modification to static HTML/CSS output must ensure companion files (`.html.gz`, `.html.br`, `.css.gz`, `.css.br`) are updated synchronously via [GZIPCompressor](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/GZIPCompressor.java) and [BrotliCompressor](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/BrotliCompressor.java).
2. **Incremental Cache Integrity:**
   - Never arbitrarily wipe `output/generation-timestamps.properties`. Respect [TimestampTracker](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/TimestampTracker.java) logic to prevent unnecessary full regenerations.
3. **Structured Data Completeness (LLMO):**
   - Every generated card page must maintain a valid, complete JSON-LD Schema.org block generated by [CardSchemaGenerator](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/CardSchemaGenerator.java).
4. **Zero Heavy Client-Side Frameworks:**
   - Keep client-side footprint zero-JS or minimal vanilla JS. Do not introduce heavy frontend frameworks into the static templates.
5. **Secrets & External Credentials:**
   - AWS credentials and Firebase Service Account keys must never be committed to git. Use environment variables and [FirebaseConfigManager](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/FirebaseConfigManager.java).
