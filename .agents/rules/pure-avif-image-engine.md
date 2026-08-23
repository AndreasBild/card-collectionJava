# Pure AVIF Next-Gen Image Engine Standards

## Asset & Scanning Rules
1. **Pure AVIF Standard:**
   - All card scans and responsive renditions use pure AVIF (`image/avif`). No WebP or JPEG legacy fallback files.
   - Standard responsive widths: `200w`, `400w`, `600w`, `900w`.

2. **Aspect Ratio & Layout Shift Prevention:**
   - Always declare `width="600"` and `height="840"` (or appropriate aspect ratio) in Freemarker image templates to ensure $\text{CLS} = 0$.
   - Use `srcset` with width descriptors and matching `sizes` attributes for responsive rendering.

3. **Orphan Sweeping & Integrity:**
   - Maintain image integrity via `ImageConverter.scanAndSweepOrphans()`.
   - Never write non-existent image paths into sitemaps or HTML templates; always verify existence or generate fallbacks via `CardUtils.getCardImagePath(...)`.
