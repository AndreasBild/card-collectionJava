# Agent Governance & Core Invariants (`AGENTS.md`)

## 1. System Role & Core Invariants
- **Role:** Principal Systems & Performance Engineer operating locally for `card-collectionJava` (`maulmann.de`).
- **Runtime & Language:** Java 26 Preview Features (`--enable-preview`), Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), Records, Pattern Matching. Never downgrade compiler versions in `pom.xml`.
- **Branch Protection:** Antigravity must **NEVER** edit files or commit directly on `main`. Work strictly on isolated feature/fix/chore branches.
- **Frontend & Assets:** Zero/Micro-JS architecture, pure AVIF image engine (`200w`, `400w`, `600w`, `900w`), synchronous `.gz` and `.br` companion generation, strict CWV budget ($\text{LCP} < 1.2\text{s}$, $\text{CLS} = 0$, $\text{INP} = 0$).

## 2. Token Economics & Dataset Protection
- **Massive File Invariant:** Never load large raw datasets (`content/json/cards.json` [770 KB], `market-data-cache.json` [197 KB], or generated HTML files in `output/`) into context in full. Use `grep_search` or bounded line slices (≤ 100 lines). Reference typed Java records (`CardData`, `CardJson`).
- **Working Tree Hygiene:** Never run `git add .` or stage unrelated modified data files (`MissingImages.txt`, `cards.json`). Only stage files directly touched by the specific task.
- **Surgical Diff Edits:** Use narrow replacement blocks (`replace_file_content`). Never rewrite entire large classes unmodified.
- **Dual-Loop Execution:** Inner loop uses `mvn test-compile` and targeted `mvn test -Dtest=TargetClassTest`. Full regression (`mvn test`, `mvn exec:java@local`) is reserved for pre-PR quality gates.
- **Dynamic Model Tier:** Recommend **Tier 1 (Fast / Medium)** for templates, styling, single tests, and scrapers; **Tier 2 (Deep Reasoning / Pro)** for concurrency, complex valuation algorithms (IQR trimming), and cloud sync pipelines.

## 3. Progressive Skill Router (On-Demand Execution)
Operational runbooks consume ~30 tokens of metadata until triggered. Use dedicated project skills in `.agents/skills/`:

| Workflow | Skill Name | Description |
| :--- | :--- | :--- |
| **Test Suite** | `test-suite` | Run JUnit 5 unit, snapshot, and integration test suite with Java 26 preview features. |
| **Static Analysis** | `static-analysis` | Run Spotless Google Java Style checks and Maven compiler static analysis. |
| **Site Build** | `build-pipeline` | Execute local dev (`LocalDevPipeline`) or full production pipeline and verify companion outputs. |
| **Schema & SEO** | `verify-schema` | Validate Schema.org JSON-LD structured data and semantic HTML metadata. |
| **Performance Audit** | `audit-performance` | Audit Core Web Vitals, HTML/CSS minification payloads, and Brotli/Gzip ratios. |
| **Snapshot Tests** | `validate-snapshots` | Run HTML golden master snapshot tests and dead-link asset validators. |
| **Market Data** | `enrich-market-data` | Fetch sales comps (Point130), TCDB checklists, and apply IQR outlier filtering. |
| **Git & PR** | `git-pr-workflow` | Execute 6-stage lifecycle, stage semantic commits, and automate `gh pr create` handoff to Jules. |
| **Context Audit** | `optimize-context` | Audit token budgets, detect rule bloat, and adapt to new model optimizations. |
