# Token Economics & Execution Efficiency Rule

## 1. Context Boundaries & Token Hygiene
- **Massive Data Files Invariant:** Never read massive data or cache files in full into context (enforced deterministically by `.agents/hooks.json`):
  - `content/json/cards.json` (~770 KB / ~200k tokens)
  - `content/json/market-data-cache.json` (~197 KB)
  - `MissingImages.txt` (~174 KB)
  - Generated files under `output/*.html` or sitemaps
- **Targeted Inspection Protocol:**
  - Use `grep` via `run_command` with specific query terms to locate keys or entries.
  - Use `read_file` strictly with bounded `StartLine` and `EndLine` slices ($\le 100$ lines at a time).
  - Inspect Java records (`CardData`, `CardJson`, `MarketSale`) rather than raw JSON payloads whenever possible.
- **Surgical Diffing:** Always use `edit_file` with minimal necessary target lines. Never rewrite entire large Java classes or configuration files unmodified.

## 2. Dynamic Model Tier Recommendation Protocol
To maintain peak token efficiency and execution accuracy, agents evaluate incoming tasks and propose the appropriate model tier in implementation plans or initial scoping responses:

| Model Tier | Capability Profile | Typical Workflows in `card-collectionJava` |
| :--- | :--- | :--- |
| **Tier 1: Fast / Medium**<br>*(Latest Flash / Medium in IDE)* | High throughput, low latency, optimal token economy. | • Freemarker template adjustments (`.ftlh`, CSS)<br>• Single-class JUnit 5 unit tests<br>• Market data enrichers execution (`@enrich-market-data`, `@enrich-tcdb`)<br>• Routine bug fixes, dependency bumps, and agent rules |
| **Tier 2: Deep Reasoning / Pro**<br>*(Latest Pro / Thinking in IDE)* | Multi-step reasoning, architectural synthesis, subtle edge-case detection. | • Multi-system architecture refactoring<br>• Concurrency & Virtual Thread synchronization<br>• Valuation algorithms (IQR outlier rejection, trimmed medians, comp pricing)<br>• Complex AWS S3/CloudFront sync, Firestore streaming, or IndexNow batching |

*Implementation Plan Standard:* Every `implementation_plan.md` must include a `## 🎯 Recommended Execution Model` section declaring the recommended tier and rationale.

## 3. Working Tree Hygiene & Prefix-Matchable Commands
- **Preserve User Data Work:** Never discard or overwrite uncommitted changes in `content/json/cards.json` or `MissingImages.txt`.
- **Never Run `git add .` or `git add -A`:** Strictly stage only files directly modified for the task (enforced by `.agents/hooks.json`).
- **Auto-Approvable Command Shapes (`run_command`):**
  - Avoid command substitutions (`$(...)` or backticks) and shell variable expansions (`$VAR`) in `run_command` calls so user permission grants remain prefix-matchable.
  - Split mixed sandbox/unsandboxed command chains (`&&`, `;`) so only the command requiring network or `.git` lock access runs with `BypassSandbox: true`.

## 4. Dual-Loop Execution & Subagent Offloading
- **Fast Inner Loop (Active Coding):**
  1. Incremental Compilation: `mvn test-compile`
  2. Targeted Test: `mvn test -Dtest=TargetClassTest`
  3. Style Check: `mvn spotless:check`
- **Comprehensive Outer Gate (Pre-PR):**
  1. `mvn spotless:apply`
  2. `mvn clean test`
  3. `mvn exec:java@local`
- **Subagent Delegation (`invoke_subagent`):** Offload heavy full regression builds (`mvn clean test`), full site generation (`mvn exec:java@local`), and bulk scrapers to isolated subagents returning a concise **Executive Diff Memo** (pass/fail summary, modified files, specific failure stacktraces) rather than raw multi-thousand-line logs.
