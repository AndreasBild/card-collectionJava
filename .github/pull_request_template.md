## 📝 Summary of Changes
<!-- Brief summary of what was generated or changed in this feature branch -->
- 

## 🤖 Jules Agent Checklist & Instructions
<!-- Jules: Asynchronously inspect this PR and perform the following tasks before merge approval -->
- [ ] **Analyze Changes:** Inspect newly added or modified methods and classes.
- [ ] **Generate Unit Tests:** Add comprehensive JUnit 5 tests under `src/test/java/de/maulmann/` covering normal cases and edge cases.
- [ ] **Verify Invariants:**
  - [ ] Pre-compression synchronicity (`.gz` / `.br` files generated for static changes).
  - [ ] Schema.org JSON-LD structured data compliance.
  - [ ] Cache integrity (`TimestampTracker` / `generation-timestamps.properties`).
- [ ] **CI Health:** Ensure GitHub Actions (`CI Build & Test Verification`) passes.

## 🔍 Local Verification Done (Antigravity & IntelliJ)
- [x] Compiled with Java 26 preview features.
- [x] Verified in IntelliJ IDEA (Syntax, static analysis, DB inspections).
- [x] Tested locally via `LocalDevPipeline` / `SiteBuilderPipeline`.
