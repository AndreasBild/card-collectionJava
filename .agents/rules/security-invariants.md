# Security Invariants & PWA Offline Integrity

## 1. Secret Protection Invariant
- **Zero Secrets in Git:** NEVER commit AWS IAM credentials, Firebase Service Account JSON files (`src/main/resources/config/firebase_config.json`), IndexNow API keys, or `.env` files into source control.
- **Environment Driven:** Source pipeline secrets strictly from environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `FIREBASE_CONFIG_JSON`). Ensure local credential files use mode `600`.

## 2. Content Security Policy (CSP) & HTTP Headers (`src/main/resources/templates/head.html`)
- **CSP Invariants:** Maintain `default-src 'self'`, `object-src 'none'`, and `frame-ancestors 'none'`, restricting scripts and connections to required origins (Google Analytics, Firebase Firestore, IndexNow).
- **Referrer & Permissions Policies:** Always include `<meta name="referrer" content="strict-origin-when-cross-origin">` and disable unused sensors (`camera=(), microphone=(), geolocation=()`).

## 3. PWA Service Worker & Offline Cache (`src/main/resources/pwa/serviceWorker.js`)
- Maintain LRU cache for offline high-res AVIF browsing (`MAX_IMAGE_CACHE_ENTRIES >= 250`).
- Precache critical interactive pages (`/index.html`, `/binder.html`, `/rainbows.html`) and enable navigation preload during Service Worker activation.
