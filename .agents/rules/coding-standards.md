# Modern Java 26 Coding Standards & Quality Guidelines

## 1. JVM & Language Invariants
- **Java 26 Preview Features:** Strictly maintain Java 26 preview features. Never downgrade `<maven.compiler.source>`, `<maven.compiler.target>`, or `<maven.compiler.release>` in `pom.xml`.
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
