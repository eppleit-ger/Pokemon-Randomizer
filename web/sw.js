// Offline support: network first (so updates arrive immediately), cache as fallback.
const CACHE = 'randomizer-__BUILD__';
const SHELL = [
  './', 'index.html', 'style.css', 'app.js', 'games.js', 'worker.js', 'engine/randomizer.js',
  'manifest.webmanifest', 'icons/icon-192.png', 'icons/apple-touch-icon.png',
  'presets/gold.rnqs', 'presets/emerald.rnqs', 'presets/platin.rnqs', 'presets/black.rnqs', 'presets/black2.rnqs',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  // Always revalidate with the server; GitHub Pages otherwise lets browsers
  // reuse files for 10 minutes, which hides freshly deployed fixes.
  const fresh = req.mode === 'navigate'
    ? fetch(req.url, { cache: 'no-cache', credentials: 'same-origin' })
    : fetch(req, { cache: 'no-cache' });
  event.respondWith(
    fresh
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req, { ignoreSearch: true })),
  );
});
