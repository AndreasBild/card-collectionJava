# PWA Offline & Security Header Invariants

## Security & Caching Guidelines
1. **Content Security Policy (CSP):**
   - Maintained in `src/main/resources/templates/head.html`.
   - Restrict scripts, frames, and objects (`object-src 'none'`).
   - Allow required connections for Google Analytics, Firebase Firestore, and IndexNow without introducing third-party script vulnerabilities.

2. **PWA Service Worker & Offline Cache:**
   - Maintain `MAX_IMAGE_CACHE_ENTRIES` (minimum 250) in `src/main/resources/pwa/serviceWorker.js` for offline high-res AVIF scan browsing.
   - Precache critical interactive pages: `/index.html`, `/binder.html`, `/rainbows.html`.
   - Enable navigation preload during Service Worker activation.

3. **Referrer & Permissions Policies:**
   - Always include `<meta name="referrer" content="strict-origin-when-cross-origin">`.
   - Disable unused browser sensors and APIs (`camera=(), microphone=(), geolocation=()`).
