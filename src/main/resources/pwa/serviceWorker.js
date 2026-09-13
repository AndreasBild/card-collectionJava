const APP_PREFIX = 'maulmann_cards_';
const VERSION = '[[BUILD_ID]]';
const CACHE_NAME = APP_PREFIX + VERSION;
const IMAGE_CACHE = APP_PREFIX + 'images_v1';
const OFFLINE_URL = './offline.html';

// The core app shell to pre-cache during installation. Heavy collection pages are cached on-demand.
const URLS = [
    './',
    './index.html',
    OFFLINE_URL,
    './manifest.json',
    './collector-features.js',
    './css/main.css',
    './favicon/android-chrome-192x192.png',
    './favicon/android-chrome-512x512.png',
    './favicon/favicon-32x32.png',
    './favicon/favicon-16x16.png',
    './favicon/favicon.ico'
];

// Cache resources during installation
self.addEventListener('install', function (e) {
    e.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) {
            console.log('Installing cache : ' + CACHE_NAME);
            return cache.addAll(URLS);
        })
    );
    self.skipWaiting();
});

// Delete outdated caches
self.addEventListener('activate', function (e) {
    e.waitUntil(
        Promise.all([
            caches.keys().then(function (keyList) {
                return Promise.all(keyList.map(function (key) {
                    if (key.startsWith(APP_PREFIX) && key !== CACHE_NAME && key !== IMAGE_CACHE) {
                        console.log('Deleting old cache : ' + key);
                        return caches.delete(key);
                    }
                }));
            }),
            self.registration.navigationPreload ? self.registration.navigationPreload.enable().catch(() => {}) : Promise.resolve()
        ])
    );
    // Claim clients immediately
    self.clients.claim();

    // Check for updates immediately on activation
    checkForUpdates();
});

// Maximum number of card scans to store offline in LRU image cache
const MAX_IMAGE_CACHE_ENTRIES = 250;
let trimScheduled = false;

function scheduleTrimImageCache() {
    if (trimScheduled) return;
    trimScheduled = true;
    setTimeout(async () => {
        trimScheduled = false;
        try {
            const cache = await caches.open(IMAGE_CACHE);
            const keys = await cache.keys();
            if (keys.length > MAX_IMAGE_CACHE_ENTRIES) {
                const toDelete = keys.slice(0, keys.length - MAX_IMAGE_CACHE_ENTRIES);
                await Promise.all(toDelete.map(k => cache.delete(k)));
            }
        } catch (_) {}
    }, 3000);
}

// Respond with cached resources
self.addEventListener('fetch', function (event) {
    const request = event.request;

    // Skip non-GET requests
    if (request.method !== 'GET') return;

    // Strategy for Images: Cache First, then Network with debounced LRU pruning
    if (request.destination === 'image') {
        event.respondWith(
            caches.open(IMAGE_CACHE).then(async (cache) => {
                const cached = await cache.match(request);
                if (cached) return cached;
                try {
                    const networkResponse = await fetch(request);
                    if (networkResponse && networkResponse.ok) {
                        cache.put(request, networkResponse.clone());
                        scheduleTrimImageCache();
                    }
                    return networkResponse;
                } catch (_) {
                    return cached || Response.error();
                }
            })
        );
        return;
    }

    // Strategy for Navigation (HTML documents): Fast Cache with Navigation Preload & SWR background update
    if (request.mode === 'navigate') {
        event.respondWith((async () => {
            const cache = await caches.open(CACHE_NAME);
            const cachedResponse = await cache.match(request, { ignoreSearch: true });

            const networkFetch = (async () => {
                try {
                    const preloadResponse = await event.preloadResponse;
                    const networkResponse = preloadResponse || await fetch(request);
                    if (networkResponse && networkResponse.ok && request.url.startsWith('http')) {
                        await cache.put(request, networkResponse.clone());
                    }
                    return networkResponse;
                } catch (_) {
                    return null;
                }
            })();

            if (cachedResponse) {
                event.waitUntil(networkFetch);
                return cachedResponse;
            }

            const response = await networkFetch;
            if (response) return response;

            const offlineResponse = await cache.match(OFFLINE_URL);
            return offlineResponse || Response.error();
        })());
        return;
    }

    // Strategy for Static Assets (CSS, JS, manifest, fonts): Stale-While-Revalidate
    event.respondWith(
        caches.open(CACHE_NAME).then(cache => {
            return cache.match(request, { ignoreSearch: true }).then(cachedResponse => {
                const fetchPromise = fetch(request).then(networkResponse => {
                    if (networkResponse && networkResponse.ok && request.url.startsWith('http')) {
                        const responseClone = networkResponse.clone();
                        event.waitUntil(cache.put(request, responseClone));
                    }
                    return networkResponse;
                }).catch(() => null);
                return cachedResponse || fetchPromise;
            });
        })
    );
});

// Function to check for updates (Latest Card Count)
async function checkForUpdates() {
    try {
        const response = await fetch('./latest.json?t=' + Date.now());
        if (!response.ok) return;

        const data = await response.json();
        const lastKnownCount = await getStoredCardCount();

        if (lastKnownCount && data.cardCount > lastKnownCount) {
            showNotification(data.cardCount - lastKnownCount);
            updateBadge(data.cardCount - lastKnownCount);
        }

        // Store the new count
        await setStoredCardCount(data.cardCount);
    } catch (error) {
        console.error('Failed to check for updates:', error);
    }
}

async function getStoredCardCount() {
    return new Promise((resolve) => {
        const request = indexedDB.open('maulmann_pwa_db', 1);
        request.onupgradeneeded = (e) => {
            e.target.result.createObjectStore('settings');
        };
        request.onsuccess = (e) => {
            const db = e.target.result;
            const transaction = db.transaction('settings', 'readonly');
            const store = transaction.objectStore('settings');
            const getRequest = store.get('cardCount');
            getRequest.onsuccess = () => resolve(getRequest.result);
            getRequest.onerror = () => resolve(null);
        };
        request.onerror = () => resolve(null);
    });
}

async function setStoredCardCount(count) {
    return new Promise((resolve) => {
        const request = indexedDB.open('maulmann_pwa_db', 1);
        request.onupgradeneeded = (e) => {
            e.target.result.createObjectStore('settings');
        };
        request.onsuccess = (e) => {
            const db = e.target.result;
            const transaction = db.transaction('settings', 'readwrite');
            const store = transaction.objectStore('settings');
            store.put(count, 'cardCount');
            transaction.oncomplete = () => resolve();
        };
        request.onerror = () => resolve();
    });
}

function showNotification(newCards) {
    const title = 'Maulmann Cards Update';
    const options = {
        body: `${newCards} new cards have been added to the collection!`,
        icon: './favicon/android-chrome-192x192.png',
        badge: './favicon/favicon-32x32.png',
        data: {
            url: './Juwan-Howard-Collection.html'
        }
    };
    self.registration.showNotification(title, options);
}

function updateBadge(count) {
    if ('setAppBadge' in navigator) {
        navigator.setAppBadge(count).catch(error => {
            console.error('Error setting badge:', error);
        });
    }
}

// Handle notification click
self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    const urlToOpen = new URL(event.notification.data.url, self.location.origin).href;

    event.waitUntil(
        clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        }).then((windowClients) => {
            for (let i = 0; i < windowClients.length; i++) {
                const windowClient = windowClients[i];
                if (windowClient.url === urlToOpen) {
                    return windowClient.focus();
                }
            }
            return clients.openWindow(urlToOpen);
        })
    );
});

// Message listener
self.addEventListener('message', (event) => {
    if (event.data === 'skipWaiting') {
        self.skipWaiting();
    }
    if (event.data === 'clearBadge') {
        if ('clearAppBadge' in navigator) {
            navigator.clearAppBadge();
        }
    }
});
