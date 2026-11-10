# Performance, Compression, AVIF & Schema.org Invariants

## 1. Core Web Vitals & Freemarker Standards (`src/main/resources/templates/`)
- **Null-Safe Templates:** Always use Freemarker null-safe operators (`${card.title!''}`, `<#if card.parallel??>`) to prevent `TemplateModelException`.
- **Largest Contentful Paint ($\text{LCP} < 1.2\text{s}$):** Hero card scans must declare `fetchpriority="high"` and be preloaded in `<head>`. Below-the-fold images must use `loading="lazy"`.
- **Cumulative Layout Shift ($\text{CLS} = 0$):** Specify explicit `width="600" height="840"` (or exact aspect dimensions) on every `<img>`, `<picture>`, and video tag.
- **Interaction to Next Paint ($\text{INP} = 0\text{ms}$):** Zero external JS frameworks; use lightweight Vanilla/Micro-JS only.

## 2. Companion File Synchronicity (`.gz` & `.br`)
- Every generated `.html` and `.css` file in `output/` must synchronously generate matching pre-compressed companions via [`GZIPCompressor.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/GZIPCompressor.java) (`.gz`) and [`BrotliCompressor.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/BrotliCompressor.java) (`.br` level 11).
- Respect `output/generation-timestamps.properties` via [`TimestampTracker.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/TimestampTracker.java) so unmodified sources are not re-compressed unnecessarily.

## 3. Pure AVIF Image Pipeline
- **Pure AVIF Standard:** All card scans and responsive renditions use `image/avif` across `200w`, `400w`, `600w`, and `900w` with `srcset` and `sizes`. Emit no legacy WebP or JPEG fallback files.
- **Orphan Sweeping & Fallbacks:** Maintain asset cleanliness via `ImageConverter.scanAndSweepOrphans()` and resolve paths via `CardUtils.getCardImagePath(...)`.

## 4. Schema.org JSON-LD & Discovery Manifests
- Every card page must include compliant `<script type="application/ld+json">` (`@context: "https://schema.org"`, `@type: "ItemPage"` / `"IndividualProduct"`, `name`, `description`, `image`) generated via [`CardSchemaGenerator.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/CardSchemaGenerator.java), plus canonical `<link rel="canonical">` and OpenGraph tags.
- Keep [`llms.txt`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/llms.txt), `llms-full.txt`, and XML sitemaps ([`SitemapGenerator.java`](file:///Users/andreasbild/IdeaProjects/card-collectionJava/src/main/java/de/maulmann/SitemapGenerator.java)) synchronized.
