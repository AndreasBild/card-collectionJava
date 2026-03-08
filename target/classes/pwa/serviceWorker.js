const GHPATH = '.';
// Change to a different app prefix name
const APP_PREFIX = 'my_awesome_';
const VERSION = 'version_01';

// The files to make available for offline use. make sure to add
// others to this list
const URLS = [
    `${GHPATH}/`, // Represents the root path, often equivalent to index.html
    `${GHPATH}/index.html`,
    `${GHPATH}/manifest.json`,
    `${GHPATH}/serviceWorker.js`,
    `${GHPATH}/css/main.css`,
    // Key favicons (using paths updated in manifest.json)
    `${GHPATH}/favicon/android-chrome-192x192.png`,
    `${GHPATH}/favicon/android-chrome-512x512.png`,
    `${GHPATH}/favicon/apple-touch-icon.png`,
    `${GHPATH}/favicon/favicon-32x32.png`,
    `${GHPATH}/favicon/favicon-16x16.png`,
    `${GHPATH}/favicon.ico`
];

const CACHE_NAME = APP_PREFIX + VERSION
self.addEventListener('fetch', function (e) {
    console.log('Fetch request : ' + e.request.url);
    e.respondWith(
        caches.match(e.request).then(function (request) {
            if (request) {
                console.log('Responding with cache : ' + e.request.url);
                return request
            } else {
                console.log('File is not cached, fetching : ' + e.request.url);
                return fetch(e.request)
            }
        })
    )
})

self.addEventListener('install', function (e) {
    e.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) {
            console.log('Installing cache : ' + CACHE_NAME);
            return cache.addAll(URLS)
        })
    )
})

self.addEventListener('activate', function (e) {
    e.waitUntil(
        caches.keys().then(function (keyList) {
            var cacheWhitelist = keyList.filter(function (key) {
                return key.indexOf(APP_PREFIX)
            })
            cacheWhitelist.push(CACHE_NAME);
            return Promise.all(keyList.map(function (key, i) {
                if (cacheWhitelist.indexOf(key) === -1) {
                    console.log('Deleting cache : ' + keyList[i]);
                    return caches.delete(keyList[i])
                }
            }))
        })
    )
})