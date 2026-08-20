# Freemarker & Frontend Standards

## Template Conventions (`src/main/resources/templates/`)
1. **Safe Variable Handling:**
   - Always use Freemarker null-safe operators (e.g. `${card.title!''}` or `<#if card.attributes??>`) to prevent `TemplateModelException` during rendering.

2. **Zero / Micro-JS Architecture:**
   - Do not inject external JavaScript frameworks (React, Vue, etc.) or heavy runtime libraries.
   - Any client-side interaction must use lightweight Vanilla JS (e.g. micro ServiceWorkers, 3D card tilt CSS/JS).

3. **Core Web Vitals Guarantees:**
   - Always specify explicit `width` and `height` attributes on `<img>` and `<picture>` tags to prevent layout shifts (CLS = 0).
   - Use `loading="lazy"` for all below-the-fold images and `fetchpriority="high"` for hero/LCP images.

4. **Semantic HTML5 Structure:**
   - Structure card layouts with `<main>`, `<article>`, `<header>`, `<figure>`, `<figcaption>`, and `<footer>`.
