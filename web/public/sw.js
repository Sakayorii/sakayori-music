/* =============================================================================
   SakayoriMusic — Web · Service Worker
   ─────────────────────────────────────────────────────────────────────────────
   Strategy:
     • PRECACHE the app shell (HTML/CSS/JS/locales index) on install so the
       SPA boots even with the network unplugged.
     • For navigation requests (HTML), serve the cached index as a fallback
       when the network is unreachable — keeps the SPA usable offline.
     • For static assets (same origin, GET), use stale-while-revalidate so
       repeat visits are instant and updates land on the next refresh.
     • For locale JSONs, cache-first (they hardly ever change between
       deploys, so we want offline-first behaviour).
     • For /api/stream/, /api/video/ — pass-through (NEVER cache big media,
       the in-app Offline module handles persistent track downloads).
     • For other /api/ JSON — network-first, fall back to cache.
   ========================================================================== */

const VERSION = "v3";
const SHELL_CACHE = `sm-shell-${VERSION}`;
const RUNTIME_CACHE = `sm-runtime-${VERSION}`;
const LOCALE_CACHE = `sm-locales-${VERSION}`;

const SHELL_URLS = [
    "/",
    "/index.html",
    "/styles.css",
    "/app.js",
    "/locales/_index.json",
    "/locales/en.json",
];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(SHELL_CACHE).then((cache) =>
            // `addAll` is atomic — if any request fails the install fails, so
            // we use individual `add()` calls and swallow misses (e.g. missing
            // locale on a fresh deploy).
            Promise.all(
                SHELL_URLS.map((u) => cache.add(u).catch((err) => {
                    console.warn(`[sw] precache miss for ${u}: ${err.message}`);
                }))
            )
        ).then(() => self.skipWaiting())
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(
                keys
                    .filter((k) => !k.endsWith(VERSION))
                    .map((k) => caches.delete(k))
            )
        ).then(() => self.clients.claim())
    );
});

// Detect the request type so we can pick a strategy.
function classify(url) {
    if (url.origin !== self.location.origin) return "cross-origin";
    if (url.pathname.startsWith("/api/stream/")) return "media";
    if (url.pathname.startsWith("/api/video/")) return "media";
    if (url.pathname.startsWith("/api/prefetch")) return "passthrough";
    if (url.pathname.startsWith("/api/cookies/")) return "passthrough";
    if (url.pathname.startsWith("/api/")) return "api-json";
    if (url.pathname.startsWith("/locales/")) return "locale";
    if (url.pathname.endsWith(".html") ||
        url.pathname === "/") return "navigation";
    if (url.pathname.match(/\.(css|js|png|jpg|jpeg|svg|ico|webp|woff2?|ttf)$/i))
        return "static";
    return "default";
}

self.addEventListener("fetch", (event) => {
    const req = event.request;
    if (req.method !== "GET") return;

    const url = new URL(req.url);
    const kind = classify(url);

    // Big media or "fire and forget" endpoints: always go to network so the
    // browser's HTTP Range support works correctly and we don't blow up the
    // Cache Storage quota with megabytes of audio.
    if (kind === "media" || kind === "passthrough" || kind === "cross-origin") return;

    if (kind === "navigation") {
        // Network-first for HTML so deploys land immediately. Fall back to
        // cached /index.html (SPA shell) on offline / 5xx.
        event.respondWith(
            fetch(req)
                .then((res) => {
                    // Mirror successful navigations into the shell cache for next time.
                    const copy = res.clone();
                    caches.open(SHELL_CACHE).then((c) => c.put("/index.html", copy));
                    return res;
                })
                .catch(() =>
                    caches.match("/index.html", { ignoreSearch: true })
                        .then((hit) => hit || new Response(
                            "<h1>Offline</h1><p>SakayoriMusic Web is offline and the app shell isn't cached yet.</p>",
                            { headers: { "Content-Type": "text/html; charset=utf-8" } }
                        ))
                )
        );
        return;
    }

    if (kind === "locale") {
        // Cache-first; let it live forever in LOCALE_CACHE until the SW version
        // changes. The bootstrap runs every server boot anyway.
        event.respondWith(
            caches.open(LOCALE_CACHE).then(async (cache) => {
                const hit = await cache.match(req);
                if (hit) return hit;
                try {
                    const res = await fetch(req);
                    if (res.ok) cache.put(req, res.clone());
                    return res;
                } catch {
                    // Last-resort: try any version of the shell cache.
                    return caches.match(req) || new Response("{}", {
                        headers: { "Content-Type": "application/json" },
                    });
                }
            })
        );
        return;
    }

    if (kind === "api-json") {
        // Network-first: fresh data when online, cached when not.
        event.respondWith(
            fetch(req)
                .then((res) => {
                    if (res.ok && res.status === 200) {
                        const copy = res.clone();
                        caches.open(RUNTIME_CACHE).then((c) => c.put(req, copy));
                    }
                    return res;
                })
                .catch(() =>
                    caches.match(req).then((hit) => hit || new Response(
                        JSON.stringify({ error: "offline", offline: true }),
                        { status: 503, headers: { "Content-Type": "application/json" } }
                    ))
                )
        );
        return;
    }

    // Default for static assets — stale-while-revalidate.
    event.respondWith(
        caches.open(RUNTIME_CACHE).then(async (cache) => {
            const hit = await cache.match(req);
            const fetchPromise = fetch(req)
                .then((res) => {
                    if (res.ok) cache.put(req, res.clone());
                    return res;
                })
                .catch(() => hit);
            return hit || fetchPromise;
        })
    );
});

// Allow the page to ask the SW to clear caches (used by Settings → "Clear
// cache" action so users can recover from a bad cached deploy).
self.addEventListener("message", (event) => {
    if (event.data?.type === "PURGE_CACHES") {
        event.waitUntil(
            caches.keys().then((keys) =>
                Promise.all(keys.map((k) => caches.delete(k)))
            ).then(() => {
                event.ports?.[0]?.postMessage?.({ ok: true });
            })
        );
    }
});
