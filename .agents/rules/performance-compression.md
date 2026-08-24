# Performance, Asset Compression & Core Web Vitals Guidelines

## 1. Core Web Vitals Targets
- **Largest Contentful Paint (LCP):** Target $< 1.2\text{s}$.
  - Hero card scans must declare `fetchpriority="high"` and be preloaded in `<head>`.
  - Below-the-fold images must use `loading="lazy"`.
- **Cumulative Layout Shift (CLS):** Invariant $\text{CLS} = 0$.
  - Explicit `width` and `height` attributes must be specified on every `<img>`, `<picture>`, and video tag.
- **Interaction to Next Paint (INP):** Target $0\text{ms}$.
  - Zero heavy JavaScript frameworks. Core HTML is statically rendered.

## 2. Companion File Synchronicity (`.gz` & `.br`)
- Every generated `.html` and `.css` file in `output/` must synchronously generate matching pre-compressed companions:
  - `.html.gz` (GZIP via `GZIPCompressor.java`)
  - `.html.br` (Brotli level 11 via `BrotliCompressor.java`)
  - `.css.gz` & `.css.br`
- Always verify companion generation during local pipeline runs (`mvn exec:java@local`).

## 3. Pure AVIF Image Pipeline
- **AVIF Standard:** All scans are generated in pure AVIF (`image/avif`) across 4 standard responsive widths: `200w`, `400w`, `600w`, `900w`.
- **Zero Legacy Formats:** No legacy WebP or JPEG fallback files are emitted to save bandwidth and storage overhead.
- **Orphan Sweeping:** Run `ImageConverter.scanAndSweepOrphans()` to maintain clean asset directories.

## 4. Incremental Build Caching
- Respect `output/generation-timestamps.properties` managed by `TimestampTracker.java`.
- Do not trigger full companion regenerations or image re-encodings when source data is unchanged.
