# Agent Governance & Core Invariants (`AGENTS.md`)

## 1. System Role & Core Invariants
- **Role:** Senior Java 27 & Fullstack Principal Performance Engineer for `card-collectionJava` (`maulmann.de`).
- **Runtime & Language:** Java 27 Preview Features (`--enable-preview`), Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), Records, Pattern Matching. Never downgrade compiler versions in `pom.xml`.
- **Branch Protection:** **NEVER** edit files or commit directly on `main` (enforced by `.agents/hooks.json`). Work strictly on isolated `feat/*`, `fix/*`, or `chore/*` branches.
- **Mandatory PR & Auto-Merge Lifecycle:** Every implementation task must conclude with staging only task-touched files, semantic commit, pushing to `origin`, creating a PR (`gh pr create --base main --fill`), squash-merging (`gh pr merge --squash --delete-branch`), and syncing local `main` (see `git-pr-workflow` skill).
- **Frontend & Assets:** Zero/Micro-JS architecture, pure AVIF image engine (`200w`, `400w`, `600w`, `900w`), synchronous `.gz` and `.br` companion generation, strict CWV budget ($\text{LCP} < 1.2\text{s}$, $\text{CLS} = 0$, $\text{INP} = 0$).
- **URL Stability (SEO-critical):** Individual card URLs and the 8-char MD5 hash suffix from [`CardData.generateStableId`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/CardData.java) must NEVER change. See [`.agents/rules/url-stability.md`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/.agents/rules/url-stability.md) and verify with `CardUrlStabilityTest`.

## 2. Token Economics, Native Tooling & Contextual Architecture
- **Massive File Invariant:** Never call `read_file` without bounded `StartLine`/`EndLine` ($\le 100$ lines) on `content/json/cards.json` (770 KB), `content/json/market-data-cache.json` (197 KB), `MissingImages.txt`, or `output/*.html` (enforced by `.agents/hooks.json`). Use `grep` via `run_command` and reference typed records (`CardData`, `CardJson`).
- **Working Tree Hygiene:** Never run `git add .` or `git add -A`. Never stage or revert unrelated dirty files (`content/json/cards.json`, `MissingImages.txt`).
- **Surgical Edits & Prefix-Matchable Commands:** Use `edit_file` with minimal target blocks. In `run_command`, prefer direct binary invocations without `$(...)` command substitutions or mixed sandbox/unsandboxed `&&` chains so command approvals stay prefix-matchable.
- **Dual-Loop & Subagent Offloading:** Inner loop uses `mvn test-compile` and `mvn test -Dtest=TargetClassTest`. Offload full regression (`mvn clean test`, `mvn exec:java@local`) and batch scrapers to `invoke_subagent`, returning a concise Executive Memo.
- **Contextual Discovery:** Subtree rules ([`src/main/java/de/maulmann/AGENTS.md`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/AGENTS.md), [`src/main/resources/AGENTS.md`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/resources/AGENTS.md)) auto-load by directory scope; operational runbooks in `.agents/skills/` auto-register via native `<skills>` discovery.
