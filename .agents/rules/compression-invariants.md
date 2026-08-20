# Compression & Companion File Invariants

## Invariant Rules for HTML/CSS Modifications
Whenever you generate, modify, or refactor static HTML pages or CSS stylesheets in this project:

1. **Synchronous Companion Updates:**
   - Every `.html` file generated in `output/` must have matching `.html.gz` (GZIP) and `.html.br` (Brotli) companion files.
   - Every `.css` file must have matching `.css.gz` and `.css.br` files.
   - Use `de.maulmann.GZIPCompressor` and `de.maulmann.BrotliCompressor` to generate these companions.

2. **Incremental Cache Respect:**
   - Always check `output/generation-timestamps.properties` via `TimestampTracker` before re-compressing unmodified files.
   - Do not trigger full companion regenerations if the underlying source has not changed.

3. **No Uncompressed Output in Production:**
   - Never output plain HTML/CSS to the production distribution without pre-compressed companions.
