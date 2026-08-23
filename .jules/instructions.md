# Jules Operational Guidelines & Repository Rules

## 1. Primary Directives & Invariants
- **Target Java Version:** This project strictly uses **Java 26**.
- **DO NOT MODIFY JAVA VERSION IN POM.XML:** Never modify, downgrade, or revert `<maven.compiler.source>`, `<maven.compiler.target>`, or `<maven.compiler.release>` in `pom.xml` to 21, 17, or any earlier version.
- **Do not downgrade dependencies:** Do not change dependencies or build properties to accommodate older JDKs. The authoritative CI pipeline runs on JDK 26 (`.github/workflows/ci.yml`).

## 2. Test Generation & Quality Standards
- **Framework:** JUnit Jupiter (JUnit 5.x) & Assertions.
- **Modern Java 26 Features:** You are encouraged to leverage Java 26 language features (records, pattern matching, virtual threads, switch expressions).
- **Scope:** Focus strictly on generating unit and integration test coverage (`src/test/java`), verifying edge cases, and testing public APIs.
