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

## 2. Dual-Loop Execution Strategy
To balance fast turnarounds with rigorous quality gates, agents must separate iteration into two distinct loops:

### 2.1 Fast Inner Loop (Active Development & TDD)
Do NOT run the entire test suite or local site generator on intermediate code changes.
1. **Incremental Compilation Check:**
   ```bash
   mvn test-compile
   ```
2. **Targeted Test Execution:** Run only the specific test covering the code under modification:
   ```bash
   mvn test -Dtest=TargetClassTest
   ```
3. **Format Check:**
   ```bash
   mvn spotless:check
   ```

### 2.2 Comprehensive Outer Gate (Pre-Commit / Pre-PR)
Execute the complete quality gate only after the inner loop passes and the feature/fix logic is finalized:
1. `mvn clean test` (runs full JUnit 5 suite).
2. `mvn exec:java@local` (validates pipeline, templates, companion `.gz`/`.br` compression, and timestamp cache).
3. `mvn spotless:apply` (ensures exact Google Java Style compliance).

## 3. Command Output & Log Truncation
- Avoid commands that generate unbounded terminal output into the conversation transcript.
- When inspecting build or test outputs, filter or scope using `-Dtest=...` or `-q` (quiet mode where appropriate).
- Do not poll running tasks or timers in tight loops.

## 4. Cache & Timestamp Preservation
- Respect `output/generation-timestamps.properties` and `TimestampTracker`.
- Never delete the timestamp cache unless explicitly debugging incremental rebuild mechanics. Full site regeneration triggers costly AVIF image encodings and massive disk I/O.

## 5. High-Signal Communication
- Eliminate conversational pleasantries, repetitive apologies, and generic introductions.
- Always provide concise, actionable markdown with direct clickable `file://` links to modified lines and symbols.
