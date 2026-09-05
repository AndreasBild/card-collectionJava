# Jules Operational Guidelines & Repository Rules

## 1. Primary Directives & Invariants
- **Target Java Version:** This project strictly uses **Java 26**.
- **DO NOT MODIFY JAVA VERSION IN POM.XML:** Never modify, downgrade, or revert `<maven.compiler.source>`, `<maven.compiler.target>`, or `<maven.compiler.release>` in `pom.xml` to 21, 17, or any earlier version.
- **Do not downgrade dependencies:** Do not change dependencies or build properties to accommodate older JDKs. The authoritative CI pipeline runs on JDK 26 (`.github/workflows/ci.yml`).
- **Main Branch Protection:** Never push commits directly to `main`. Operate strictly within the assigned Pull Request branch.

## 2. Token & Context Efficiency
- **Scoped Diff Inspection:** Do not scan or read the entire repository. Inspect only changed files and methods in the Pull Request diff (`gh pr diff` or PR file list).
- **Context Boundary Discipline:** Avoid loading large data files (e.g. `content/json/cards.json` [~770 KB] or `market-data-cache.json` [~197 KB]) into your prompt context. Use targeted line slices or existing test fixture records (`CardData`, `CardJson`).
- **Lean Review Output:** Keep pull request reviews and generated comments high-signal and concise. Do not dump entire files or verbose logs into PR comments.

## 3. Test Generation & Quality Standards
- **Framework:** JUnit Jupiter (JUnit 5.x) & modern assertions (`org.junit.jupiter.api.Assertions.*`).
- **Modern Java 26 Features:** Leverage Java 26 language features (records, pattern matching, switch expressions, virtual threads).
- **Scope:** Focus strictly on generating unit and integration test coverage (`src/test/java`) for newly added or altered logic, edge cases, and null safety.
- **Test Isolation & Mocks:** Tests must be completely offline and deterministic. Never invoke real external APIs (AWS S3, Google Firestore, Point130, IndexNow). Use mock fixtures, temp directories (`@TempDir`), or mock HTTP responses.

## 4. Execution Efficiency & Spotless Formatting
- **Targeted Test Execution (Inner Loop):** During test generation and debugging, run only the specific test class being authored:
  ```bash
  mvn test -Dtest=YourNewTest
  ```
- **Spotless Formatting Requirement:** Before finalizing any commit, automatically apply Google Java Style formatting:
  ```bash
  mvn spotless:apply
  ```
  *Note:* The CI pipeline enforces `mvn spotless:check`. Unformatted code will break CI.
- **Final Verification (Outer Gate):** Run the complete test suite only once after formatting and single-test validation succeed:
  ```bash
  mvn test -B
  ```
