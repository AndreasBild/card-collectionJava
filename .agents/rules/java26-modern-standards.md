# Modern Java 26 Standards & Code Quality

## Language & JVM Invariants
1. **Java 26 Preview Features:**
   - Compile and execute strictly on Java 26 with `--enable-preview`.
   - Never downgrade `<maven.compiler.source>`, `<maven.compiler.target>`, or `<maven.compiler.release>` in `pom.xml`.

2. **Modern Concurrency & Records:**
   - Use Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) for I/O and batch-processing tasks.
   - Model immutable data transfer entities as Java `record`s (e.g. `CardRecord`, `ImageMeta`).
   - Prefer Pattern Matching for `switch` and `instanceof`.

3. **Spotless Code Formatting:**
   - Adhere to Google Java Style via Spotless.
   - Run `mvn spotless:check` to verify and `mvn spotless:apply` to automatically format before committing.

4. **Zero-Allocation Stream Efficiency:**
   - Avoid creating temporary garbage objects in tight loops (e.g., card rendering or image verification loops).
   - Prefer primitive streams or specialized collections when processing large card datasets.
