# Modern Java 27 Coding Standards & Quality Guidelines

## 1. JVM & Language Invariants
- **Java 27 Preview Features:** Strictly maintain Java 27 preview features. Never downgrade `<maven.compiler.source>`, `<maven.compiler.target>`, or `<maven.compiler.release>` in `pom.xml`.
- **Compiler Flags:** Always ensure surefire and compiler plugins include `--enable-preview` and `-Xlint:all`.

## 2. Modern Concurrency & Architecture
- **Virtual Threads:** Use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent file I/O, parallel image conversions, and remote API calls. Avoid heavy platform thread pools for blocking operations.
- **Immutable Data Records:** Model data transfer objects, intermediate metadata, and card indexes as Java `record` classes (e.g. `CardRecord`, `ImageMeta`).
- **Pattern Matching:** Use pattern matching for `switch` expressions and `instanceof` checks to write expressive, branch-exhaustive code.
- **Sealed Interfaces / Classes:** Use sealed type hierarchies when modeling closed domain states (e.g., pipeline phases, image format types).

## 3. Memory & Stream Efficiency (Hot Paths)
- **Zero Allocations:** Avoid unnecessary object allocations in tight batch loops (e.g. rendering 10,000+ cards or scanning directories).
- **Stream Optimization:** Prefer primitive streams (`IntStream`, `LongStream`) and array-backed lookups over boxed collections in critical paths.

## 4. Code Formatting & Spotless
- **Google Java Style:** Enforced by `spotless-maven-plugin`.
- **Pre-Commit Check:** Run `mvn spotless:check`.
- **Automatic Formatting:** Run `mvn spotless:apply` to fix formatting discrepancies automatically.
- **Javadoc:** Provide concise Javadoc explaining *why* non-obvious architecture choices or performance optimizations were made.

## 5. Senior Fullstack & Ecosystem Standards
- **Spring Boot & MySQL (Upstream & Services):** Maintain idiomatic Spring Boot conventions, robust JPA/JDBC interactions, parameterized queries, and clean relational schemas when working with upstream data pipelines or backend services.
- **CSS & UI Excellence:** Author semantic, modern CSS (container queries, CSS variables, subgrid, flexbox/grid) honoring the zero-CLS design system and responsive viewport budgets.
- **Modern JavaScript:** Employ lightweight Vanilla/Micro-JS for client-side enhancement without bloating CWV or violating the Zero-JS/Micro-JS delivery model.
