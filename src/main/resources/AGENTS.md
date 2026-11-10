# Frontend, Freemarker & PWA Invariants (`src/main/resources/AGENTS.md`)

## 1. Freemarker & Semantic HTML (`templates/`)
- **Null-Safe Expressions:** Always guard optional attributes with Freemarker null-safe defaults (`${card.title!''}`, `<#if card.parallel??>`) to prevent `TemplateModelException`.
- **Zero / Micro-JS Architecture:** Never introduce external JS frameworks. Client-side enhancements must remain lightweight Vanilla JS preserving $\text{INP} = 0\text{ms}$.
- **Semantic Structure:** Use `<main>`, `<article>`, `<header>`, `<figure>`, `<figcaption>`, and `<footer>`, with canonical `<link rel="canonical">` and OpenGraph metadata.

## 2. Pure AVIF Image Engine & Core Web Vitals
- **Pure AVIF Only:** Render card scans exclusively as `image/avif` across responsive widths (`200w`, `400w`, `600w`, `900w`) with `srcset` and `sizes`. Emit no legacy WebP/JPEG fallbacks.
- **Zero Layout Shift ($\text{CLS} = 0$):** Specify explicit `width="600" height="840"` (or exact aspect dimensions) on every `<img>` and `<picture>` element.
- **LCP Prioritization ($\text{LCP} < 1.2\text{s}$):** Hero card scans must use `fetchpriority="high"` and `<head>` preload; below-the-fold images must use `loading="lazy"`.

## 3. Security Headers & PWA Offline Cache (`templates/head.html`, `pwa/serviceWorker.js`)
- **CSP & Policies:** Maintain strict CSP (`default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`), `<meta name="referrer" content="strict-origin-when-cross-origin">`, and `camera=(), microphone=(), geolocation=()` permissions policy in `head.html`.
- **Service Worker Cache:** Keep `MAX_IMAGE_CACHE_ENTRIES >= 250` in `pwa/serviceWorker.js`, precache `/index.html`, `/binder.html`, and `/rainbows.html`, and preserve navigation preload on activation.
