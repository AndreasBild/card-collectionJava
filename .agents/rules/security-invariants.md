# Security Invariants & PWA Integrity Guidelines

## 1. Secret Protection Invariant
- **Zero Secrets in Git:** NEVER commit AWS IAM credentials, Firebase Service Account JSON files, IndexNow API keys, or `.env` files into source control.
- **Environment Driven:** Pipeline secrets must be sourced strictly from environment variables (e.g. `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `FIREBASE_CONFIG_JSON`).

## 2. Content Security Policy (CSP) & HTTP Headers
- Configured in [`src/main/resources/templates/head.html`](file:///src/main/resources/templates/head.html).
- **Invariants:**
  - `default-src 'self'`
  - `object-src 'none'`
  - `frame-ancestors 'none'`
  - Restrict script execution to trusted domains (Google Analytics / Cloudflare if active).
- **Referrer Policy:** `<meta name="referrer" content="strict-origin-when-cross-origin">`.
- **Permissions Policy:** Block sensitive device sensors (`camera=(), microphone=(), geolocation=()`).

## 3. PWA & Service Worker Offline Caching
- Service Worker configuration in `src/main/resources/pwa/serviceWorker.js`.
- Maintain LRU cache for offline AVIF scans (`MAX_IMAGE_CACHE_ENTRIES` >= 250).
- Ensure navigation preload is handled gracefully during Service Worker activation.
