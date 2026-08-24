## 📝 Summary of Changes
<!-- Brief summary of what was generated or changed in this branch -->
- 

## 🏗️ 6-Stage Quality Gate Checklist

### Stage 1: Analysis & Discovery
- [x] Reviewed domain models (`CardData`, `CardJson`, `cards.json`) and architecture invariants.
- [x] Checked for regression impact on static site pipelines and sitemaps.

### Stage 2: Architecture & Design
- [x] Preserved Core Web Vitals budgets (LCP < 1.2s, CLS = 0, INP = 0).
- [x] Ensured Freemarker template null safety and semantic HTML5 output.

### Stage 3: Branch Isolation
- [x] Created dedicated branch (`feature/*`, `fix/*`, or `chore/*`) isolated from `main`.

### Stage 4: Implementation (TDD & Java 26)
- [x] Implemented with modern Java 26 constructs (Records, Virtual Threads, Pattern Matching).
- [x] Zero stubbing / zero TODO placeholders in production paths.

### Stage 5: Local Quality Gate Verification
- [x] `mvn spotless:check` passed cleanly (Google Java Style).
- [x] `mvn clean test` passed (all JUnit 5 tests green).
- [x] `mvn exec:java@local` executed without errors.
- [x] Synchronous companion files (`.gz` and `.br`) generated for all static output.
- [x] Schema.org JSON-LD and snapshot tests verified.

### Stage 6: Automated PR & Jules CI Review
- [ ] Jules automated review triggered on GitHub.
- [ ] CI build & test workflow green.

---

## 🤖 Jules Agent Checklist & Instructions
<!-- Jules: Asynchronously inspect this PR and perform the following tasks before merge approval -->
- [ ] **Analyze Changes:** Inspect newly added or modified methods and classes.
- [ ] **Generate Unit Tests:** Add comprehensive JUnit 5 tests under `src/test/java/de/maulmann/` covering normal cases and edge cases.
- [ ] **Verify Invariants:**
  - [ ] Pre-compression synchronicity (`.gz` / `.br` files generated for static changes).
  - [ ] Schema.org JSON-LD structured data compliance.
  - [ ] Cache integrity (`TimestampTracker` / `generation-timestamps.properties`).
- [ ] **CI Health:** Ensure GitHub Actions (`CI Build & Test Verification`) passes.
