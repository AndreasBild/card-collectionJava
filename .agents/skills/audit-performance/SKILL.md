---
name: audit-performance
description: Audit Core Web Vitals, HTML/CSS minification payloads, Brotli/Gzip compression ratios, and asset budgets for maulmann.de.
---

# Performance & Compression Audit Skill

Use this skill to inspect and optimize Core Web Vitals (LCP < 1.2s, CLS = 0, INP = 0) and asset delivery sizes for `card-collectionJava`.

## Verification & Audit Steps

### 1. Execute Local Build
```bash
mvn exec:java@local
```

### 2. Verify Pre-Compression Files in `output/`
Ensure every generated HTML and CSS file has valid `.gz` and `.br` companions:
```bash
# Check presence of Brotli and GZIP companions
ls -lh output/*.html output/*.br output/*.gz
```

### 3. Start Local Preview Server
```bash
mvn exec:java@local -Dexec.args="--serve"
```
Open `http://localhost:8080` in Chrome DevTools to audit:
- Network tab: Ensure Brotli/AVIF payload sizes match targets.
- Performance tab: Confirm Largest Contentful Paint (LCP) is under 1.2s.
- Layout Shift: Confirm Cumulative Layout Shift (CLS) = 0.
