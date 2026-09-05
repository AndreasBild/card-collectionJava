# Token Economics & Execution Efficiency Rule

## 1. Context Boundaries & Token Hygiene
- **Massive Data Files Invariant:** Never read massive data or cache files in full into context. Specifically:
  - `content/json/cards.json` (~770 KB / ~200k tokens)
  - `content/json/market-data-cache.json` (~197 KB)
  - Generated files under `output/*.html` or sitemaps
- **Targeted Inspection Protocol:**
  - Use `grep_search` with specific query terms to locate keys or entries.
  - Use `view_file` strictly with bounded `StartLine` and `EndLine` slices (maximum 50–100 lines at a time).
  - Inspect Java records (`CardData`, `CardJson`, `MarketSale`) rather than raw JSON payloads whenever possible.
- **Surgical Diffing:** Always use targeted diff tools (`replace_file_content` or `multi_replace_file_content`) with minimal necessary context lines. Never rewrite entire large Java classes or configuration files unmodified.

## 2. Dynamic Model Tier Recommendation Protocol
To maintain peak token efficiency and execution accuracy, agents dynamically evaluate incoming tasks and propose the appropriate model tier in implementation plans or initial scoping responses:

| Model Tier | Capability Profile | Typical Workflows in `card-collectionJava` |
| :--- | :--- | :--- |
| **Tier 1: Fast / Medium**<br>*(Latest Flash / Medium in IDE)* | High throughput, sub-second latency, optimal token economy. | • Freemarker template adjustments (`.ftlh`, CSS)<br>• Single-class JUnit 5 unit tests<br>• Market data enrichers execution (`@enrich-market-data`, `@enrich-tcdb`)<br>• Routine bug fixes, dependency bumps, and agent rules |
| **Tier 2: Deep Reasoning / Pro**<br>*(Latest Pro / Thinking in IDE)* | Multi-step reasoning, architectural synthesis, subtle edge-case detection. | • Multi-system architecture refactoring<br>• Concurrency & Virtual Thread synchronization<br>• Valuation algorithms (IQR outlier rejection, trimmed medians, comp pricing)<br>• Complex AWS S3/CloudFront sync, Firestore streaming, or IndexNow batching |

*Implementation Plan Standard:* Every `implementation_plan.md` must include a `## 🎯 Recommended Execution Model` section declaring the recommended tier and rationale.

## 3. Working Tree Hygiene & Unrelated Changes
- **Preserve User Data Work:** The workspace owner frequently runs independent data scrapers or collection audits that modify `content/json/cards.json` or `MissingImages.txt`.
- **Never Run `git add .` Blindly:** Strictly stage only files directly modified for the agent's specific task.
- Never discard or overwrite existing uncommitted changes in `content/json/cards.json` or `MissingImages.txt` unless the task explicitly targets dataset reconciliation.

## 4. Frequent Execution Shortcuts
Always prefer targeted Maven execution profiles over full rebuilds:
- **Local Dev Pipeline & Preview:** `mvn exec:java@local` (or `mvn exec:java@local -Dexec.args="--serve"`)
- **Point130 & Market Comps Enricher:** `mvn exec:java@enrich-market-data`
- **SportsCardsPro Tier Enricher:** `mvn exec:java@enrich-sportscardspro`
- **TCDB Checklist Enricher:** `mvn exec:java@enrich-tcdb`
- **Fast Syntax & Type Check:** `mvn test-compile`
- **Single Test Class:** `mvn test -Dtest=TargetClassTest`

## 5. Dual-Loop Execution Strategy
Separate iteration into two distinct loops:

### 5.1 Fast Inner Loop (Active Development & TDD)
Do NOT run the entire test suite or local site generator on intermediate code changes.
1. Incremental Compilation: `mvn test-compile`
2. Targeted Test: `mvn test -Dtest=TargetClassTest`
3. Style Check: `mvn spotless:check`

### 5.2 Comprehensive Outer Gate (Pre-Commit / Pre-PR)
Execute only after the inner loop passes and task logic is finalized:
1. `mvn clean test` (runs full JUnit 5 suite).
2. `mvn exec:java@local` (validates pipeline, Freemarker templates, and `.gz`/`.br` companions).
3. `mvn spotless:apply` (applies Google Java Style automatically).

## 6. Command Output & Log Truncation
- Avoid commands that generate unbounded terminal output into conversation context.
- Scope and filter Maven commands (`-Dtest=...` or `-q`).
- Do not poll running tasks or background timers in tight loops.

## 7. Cache & Timestamp Preservation
- Respect `output/generation-timestamps.properties` and `TimestampTracker`.
- Never delete the timestamp cache unless explicitly debugging incremental rebuild mechanics.

## 8. High-Signal Communication
- Eliminate conversational pleasantries, repetitive apologies, and generic introductions.
- Always provide concise, actionable markdown with direct clickable `file://` links.
