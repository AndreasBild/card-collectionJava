const APP_PREFIX = 'maulmann_cards_';
const VERSION = 'v1.1.1';
const CACHE_NAME = APP_PREFIX + VERSION;

// The files to make available for offline use.
const URLS = [
    './',
    './index.html',
    './Juwan-Howard-Collection.html',
    './Baseball.html',
    './Flawless.html',
    './Panini.html',
    './Wantlist.html',
    './manifest.json',
    './css/main.css',
    './favicon/android-chrome-192x192.png',
    './favicon/android-chrome-512x512.png',
    './favicon/favicon-32x32.png',
    './favicon/favicon-16x16.png',
    './favicon/favicon.ico'
];

// Respond with cached resources
self.addEventListener('fetch', function (e) {
    e.respondWith(
        caches.match(e.request, { ignoreSearch: true }).then(function (request) {
            if (request) {
                return request;
            } else {
                return fetch(e.request);
            }
        })
    );
});

// Cache resources during installation
self.addEventListener('install', function (e) {
    e.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) {
            console.log('Installing cache : ' + CACHE_NAME);
            return cache.addAll(URLS);
        })
    );
});

// Delete outdated caches
self.addEventListener('activate', function (e) {
    e.waitUntil(
        caches.keys().then(function (keyList) {
            return Promise.all(keyList.map(function (key) {
                if (key !== CACHE_NAME && key.startsWith(APP_PREFIX)) {
                    console.log('Deleting old cache : ' + key);
                    return caches.delete(key);
                }
            }));
        })
    );
});

// Handle push notifications
self.addEventListener('push', function (event) {
    const data = event.data ? event.data.json() : {};
    const title = data.title || 'Maulmann Cards Update';
    const options = {
        body: data.body || 'New cards have been added to the collection!',
        icon: '/favicon/android-chrome-192x192.png',
        badge: '/favicon/favicon-32x32.png',
        data: {
            url: data.url || '/'
        }
    };

    event.waitUntil(
        self.registration.showNotification(title, options)
    );
});

// Handle notification click
self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    const urlToOpen = new URL(event.notification.data.url, self.location.origin).href;

    event.waitUntil(
        clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        }).then((windowClients) => {
            let matchingClient = null;

            for (let i = 0; i < windowClients.length; i++) {
                const windowClient = windowClients[i];
                if (windowClient.url === urlToOpen) {
                    matchingClient = windowClient;
                    break;
                }
            }

            if (matchingClient) {
                return matchingClient.focus();
            } else {
                return clients.openWindow(urlToOpen);
            }
        })
    );
});

// Message listener for skipWaiting
self.addEventListener('message', (event) => {
    if (event.data === 'skipWaiting') {
        self.skipWaiting();
    }
});