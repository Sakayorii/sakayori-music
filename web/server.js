/**
 * SakayoriMusic — Web (Node.js port)  ·  Optimized
 *
 * What this server does:
 *   • Search YouTube Music (songs / videos / albums / playlists / artists)
 *   • Resolve song details + suggested "watch next" radio queue
 *   • Resolve artist / album / playlist details
 *   • Stream audio / video of a YouTube video (via yt-dlp)
 *   • Lyrics from LRCLIB with YouTube auto-captions fallback
 *   • Home / explore feeds
 *
 * Optimizations layered on top:
 *   1. **In-flight request coalescing** — concurrent calls for the same
 *      videoId share a single yt-dlp process.
 *   2. **LRU caches** for resolved URLs, search results, home shelves,
 *      lyrics, song metadata, artist/album/playlist payloads.
 *   3. **Keep-alive undici Agent** for upstream googlevideo fetches —
 *      saves the TCP+TLS handshake on every Range request.
 *   4. **Prefetch hint** endpoint that warms the next track's URL while
 *      the current one is still playing.
 *   5. **Cookie-pool rotation** with auto-sanitization (drop cookies into
 *      `web/cookies/` — bad ones are skipped at load time, dead ones at
 *      runtime).
 *   6. **Static-asset cache headers** (immutable + ETag) and gzip+brotli.
 *   7. **Graceful upstream cleanup** — when the browser aborts a Range
 *      request mid-flight, we destroy the upstream body to free sockets.
 */

const path = require("path");
const fs = require("fs");
const os = require("os");
const { spawn } = require("child_process");
const { Readable } = require("stream");

const express = require("express");
const cors = require("cors");
const compression = require("compression");
const { LRUCache } = require("lru-cache");
const { Agent, fetch: undiciFetch } = require("undici");

// ===========================================================================
// 0. Constants & shared infra
// ===========================================================================
const PORT = process.env.PORT || 3000;
const YT_DLP = process.env.YT_DLP || "yt-dlp";

// Keep-alive HTTP agent so upstream googlevideo connections are reused.
// HTTP Range requests are extremely chatty (many short reads), so reusing
// sockets is a huge win.
const upstreamAgent = new Agent({
    keepAliveTimeout: 60_000,
    keepAliveMaxTimeout: 600_000,
    connections: 64,
    pipelining: 1,
});

// Browser-y UA for googlevideo
const UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

// ===========================================================================
// 1. Cookie pool (multi-account) with auto-rotation + sanitization
// ===========================================================================
const YT_COOKIES = process.env.YT_COOKIES || "";
const YT_COOKIES_BROWSER = process.env.YT_COOKIES_BROWSER || "";
const COOKIES_DIR = path.join(__dirname, "cookies");
const COOKIES_TMP = path.join(os.tmpdir(), "smweb-cookies");

let cookiePool = [];
let deadCookies = new Set();

function sanitizeCookieText(text) {
    const lines = String(text || "").split(/\r?\n/);
    const out = [];
    for (const line of lines) {
        const t = line.trim();
        if (!t || t.startsWith("#")) continue;
        const parts = line.split("\t");
        if (parts.length < 7) continue;
        if (!/^\.?[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(parts[0])) continue;
        if (!/^(TRUE|FALSE)$/i.test(parts[1])) continue;
        out.push(line);
    }
    if (!out.length) return null;
    return (
        "# Netscape HTTP Cookie File\n" +
        "# Auto-normalized by SakayoriMusic Web\n\n" +
        out.join("\n") +
        "\n"
    );
}

function loadCookiePool() {
    cookiePool = [];
    deadCookies.clear();
    if (!fs.existsSync(COOKIES_DIR)) return;
    try { fs.mkdirSync(COOKIES_TMP, { recursive: true }); } catch { /* noop */ }

    let files = [];
    try { files = fs.readdirSync(COOKIES_DIR); } catch { return; }

    for (const f of files) {
        const src = path.join(COOKIES_DIR, f);
        try {
            const stat = fs.statSync(src);
            if (!stat.isFile()) continue;
            const text = fs.readFileSync(src, "utf8");
            const norm = sanitizeCookieText(text);
            if (!norm) {
                console.warn(`[cookies] skipped ${f}: no valid cookie lines`);
                continue;
            }
            const dst = path.join(
                COOKIES_TMP,
                f.replace(/[^a-z0-9._-]/gi, "_") + ".cookies.txt"
            );
            fs.writeFileSync(dst, norm);
            cookiePool.push({ id: f, normalizedPath: dst });
        } catch (e) {
            console.warn(`[cookies] skipped ${f}: ${e.message}`);
        }
    }
    if (cookiePool.length) {
        console.log(
            `[cookies] loaded ${cookiePool.length} cookie file(s) from cookies/ ` +
            `(rotation enabled)`
        );
    }
}
loadCookiePool();

(function autoDetectSingleCookie() {
    if (cookiePool.length || YT_COOKIES || YT_COOKIES_BROWSER) return;
    const candidates = [
        path.join(__dirname, "cookies.txt"),
        path.join(__dirname, "youtube-cookies.txt"),
        path.join(__dirname, "yt-cookies.txt"),
    ];
    for (const c of candidates) {
        try {
            if (fs.existsSync(c)) {
                process.env.YT_COOKIES = c;
                console.log(`[cookies] using single file: ${c}`);
                return;
            }
        } catch { /* noop */ }
    }
})();

function pickCookie() {
    for (const c of cookiePool) if (!deadCookies.has(c.id)) return c;
    return null;
}
function markCookieDead(id) {
    deadCookies.add(id);
    const remain = cookiePool.length - deadCookies.size;
    console.warn(`[cookies] '${id}' looks dead — ${remain} cookie(s) remaining`);
    if (remain === 0 && cookiePool.length) {
        console.warn(`[cookies] all cookies failed; resetting pool to retry from top`);
        deadCookies.clear();
    }
}
function isAuthError(msg) {
    return /Sign in to confirm|cookies|HTTP Error 403|consent|requires.*authentication|Forbidden/i
        .test(String(msg || ""));
}
function buildAuthArgs(cookieEntry) {
    if (cookieEntry) return ["--cookies", cookieEntry.normalizedPath];
    if (process.env.YT_COOKIES) return ["--cookies", process.env.YT_COOKIES];
    if (YT_COOKIES_BROWSER) return ["--cookies-from-browser", YT_COOKIES_BROWSER];
    return [];
}

// ===========================================================================
// 2. yt-dlp wrapper with cookie rotation
// ===========================================================================
function runYtDlp(baseArgs, cookieEntry) {
    return new Promise((resolve, reject) => {
        const args = [...baseArgs, ...buildAuthArgs(cookieEntry)];
        const p = spawn(YT_DLP, args, { windowsHide: true });
        let out = "";
        let err = "";
        p.stdout.on("data", (b) => (out += b.toString("utf8")));
        p.stderr.on("data", (b) => (err += b.toString("utf8")));
        p.on("error", reject);
        p.on("close", (code) => {
            if (code === 0 && out.trim()) resolve(out);
            else reject(new Error(err || `yt-dlp exited ${code}`));
        });
    });
}

async function runYtDlpWithRotation(baseArgs) {
    const tried = new Set();
    let lastErr = null;
    while (true) {
        const cookie = pickCookie();
        if (cookie && tried.has(cookie.id)) break;
        try {
            return await runYtDlp(baseArgs, cookie);
        } catch (e) {
            lastErr = e;
            if (!cookie) break;
            tried.add(cookie.id);
            if (isAuthError(e.message)) {
                markCookieDead(cookie.id);
                continue;
            }
            break;
        }
    }
    if (lastErr && (process.env.YT_COOKIES || YT_COOKIES_BROWSER) && !cookiePool.length) {
        try { return await runYtDlp(baseArgs, null); } catch (e) { lastErr = e; }
    }
    throw lastErr || new Error("yt-dlp failed");
}

function refreshCookies() {
    loadCookiePool();
    return cookiePool.map((c) => c.id);
}

// ===========================================================================
// 3. URL resolvers (audio + video) with caching + in-flight coalescing
// ===========================================================================
//
// Coalescing means that if two HTTP requests arrive for /api/stream/X at the
// same time and the URL isn't cached yet, we only spawn ONE yt-dlp process
// and both requests await the same promise.  This prevents a thundering
// herd when the browser does multiple Range requests in parallel.
//
const URL_CACHE = new LRUCache({
    max: 200,
    ttl: 5 * 60 * 1000, // 5 min — googlevideo URLs expire after ~6h but we
    // refresh aggressively so 403s become rare.
});
const VIDEO_URL_CACHE = new LRUCache({ max: 200, ttl: 5 * 60 * 1000 });

const inflightAudio = new Map();
const inflightVideo = new Map();

function ytDlpResolveAudioUrl(videoId) {
    return runYtDlpWithRotation([
        "-q",
        "--no-warnings",
        "-f", "bestaudio[ext=m4a]/bestaudio/best",
        "-g",
        "--no-playlist",
        `https://www.youtube.com/watch?v=${videoId}`,
    ]).then((out) => out.split(/\r?\n/).filter(Boolean)[0]);
}

function ytDlpResolveVideoUrl(videoId) {
    return runYtDlpWithRotation([
        "-q",
        "--no-warnings",
        "-f", "best[ext=mp4][acodec!=none][vcodec!=none]/best[acodec!=none][vcodec!=none]/best",
        "-g",
        "--no-playlist",
        `https://www.youtube.com/watch?v=${videoId}`,
    ]).then((out) => out.split(/\r?\n/).filter(Boolean)[0]);
}

async function resolveAudio(videoId) {
    const cached = URL_CACHE.get(videoId);
    if (cached) return cached;
    if (inflightAudio.has(videoId)) return inflightAudio.get(videoId);
    const p = (async () => {
        try {
            const url = await ytDlpResolveAudioUrl(videoId);
            const mime = /mime=audio%2Fwebm/i.test(url) ? "audio/webm" : "audio/mp4";
            const entry = { url, mime };
            URL_CACHE.set(videoId, entry);
            return entry;
        } finally {
            inflightAudio.delete(videoId);
        }
    })();
    inflightAudio.set(videoId, p);
    return p;
}

async function resolveVideo(videoId) {
    const cached = VIDEO_URL_CACHE.get(videoId);
    if (cached) return cached;
    if (inflightVideo.has(videoId)) return inflightVideo.get(videoId);
    const p = (async () => {
        try {
            const url = await ytDlpResolveVideoUrl(videoId);
            const mime = /mime=video%2Fwebm/i.test(url) ? "video/webm" : "video/mp4";
            const entry = { url, mime };
            VIDEO_URL_CACHE.set(videoId, entry);
            return entry;
        } finally {
            inflightVideo.delete(videoId);
        }
    })();
    inflightVideo.set(videoId, p);
    return p;
}

// ===========================================================================
// 4. YouTube auto-captions  →  LRC
// ===========================================================================
async function ytDlpFetchSubs(videoId, lang = "en") {
    const tmp = path.join(os.tmpdir(), `smweb-subs-${videoId}-${Date.now()}`);
    const outTmpl = `${tmp}.%(ext)s`;
    const baseArgs = [
        "--skip-download",
        "--write-auto-subs",
        "--write-subs",
        "--sub-format", "vtt",
        "--sub-langs", `${lang},${lang}.*`,
        "-o", outTmpl,
        "--no-warnings",
        "--no-playlist",
        "-q",
        `https://www.youtube.com/watch?v=${videoId}`,
    ];
    try {
        await runYtDlpWithRotation(baseArgs).catch(() => null);
        const dir = path.dirname(tmp);
        const base = path.basename(tmp);
        const file = fs.readdirSync(dir).find(
            (f) => f.startsWith(base) && f.endsWith(".vtt")
        );
        if (!file) return null;
        const full = path.join(dir, file);
        const text = fs.readFileSync(full, "utf8");
        fs.unlink(full, () => { });
        return text;
    } catch {
        return null;
    }
}

function vttToLrc(vtt) {
    const out = [];
    let curStart = null;
    const seen = new Set();
    for (const raw of vtt.split(/\r?\n/)) {
        const line = raw.trim();
        if (!line || /^WEBVTT/i.test(line) || /^NOTE/i.test(line)) continue;
        const m = /^(\d{2}):(\d{2}):(\d{2})[.,](\d{3})\s*-->/.exec(line);
        if (m) {
            const total =
                parseInt(m[1], 10) * 3600 +
                parseInt(m[2], 10) * 60 +
                parseInt(m[3], 10) +
                parseInt(m[4], 10) / 1000;
            const lm = Math.floor(total / 60);
            const ls = (total - lm * 60).toFixed(2).padStart(5, "0");
            curStart = `[${String(lm).padStart(2, "0")}:${ls}]`;
            continue;
        }
        if (curStart && !/^\d/.test(line)) {
            const clean = line.replace(/<[^>]+>/g, "").trim();
            if (!clean) continue;
            const lrc = `${curStart}${clean}`;
            if (seen.has(lrc)) continue;
            seen.add(lrc);
            out.push(lrc);
        }
    }
    return out.join("\n");
}

// ===========================================================================
// 5. ytmusic-api (lazy single instance) + helpers
// ===========================================================================
let YTMusicCtor = null;
async function loadYTMusic() {
    if (YTMusicCtor) return YTMusicCtor;
    const mod = await import("ytmusic-api");
    YTMusicCtor = mod.default || mod.YTMusic || mod;
    return YTMusicCtor;
}

let ytmusicPromise = null;
function getYTMusic() {
    if (!ytmusicPromise) {
        ytmusicPromise = (async () => {
            const Ctor = await loadYTMusic();
            const ytm = new Ctor();
            await ytm.initialize();
            return ytm;
        })().catch((err) => {
            ytmusicPromise = null;
            throw err;
        });
    }
    return ytmusicPromise;
}

function pickThumb(thumbnails) {
    if (!Array.isArray(thumbnails) || thumbnails.length === 0) return null;
    return thumbnails.reduce((best, t) =>
        !best ? t : (t.width || 0) > (best.width || 0) ? t : best, null
    );
}

function normalizeArtist(a) {
    if (!a) return null;
    if (typeof a === "string") return { name: a, artistId: null };
    return {
        name: typeof a.name === "string" ? a.name : a.name?.name || "",
        artistId: a.artistId ?? a.name?.artistId ?? null,
    };
}

function normalizeSong(s) {
    if (!s) return null;
    let artists = [];
    if (Array.isArray(s.artists) && s.artists.length) {
        artists = s.artists.map(normalizeArtist).filter(Boolean);
    } else if (s.artist) {
        const a = normalizeArtist(s.artist);
        if (a) artists = [a];
    }
    return {
        type: s.type || "SONG",
        videoId: s.videoId || s.id || null,
        name: s.name || s.title || "",
        artists,
        album: s.album ? { name: s.album.name, albumId: s.album.albumId } : null,
        duration: s.duration ?? null,
        thumbnail: pickThumb(s.thumbnails) || s.thumbnail || null,
    };
}

// ===========================================================================
// 6. Memoization caches for the YT Music endpoints
// ===========================================================================
const SEARCH_CACHE = new LRUCache({ max: 500, ttl: 5 * 60 * 1000 });
const SUGGEST_CACHE = new LRUCache({ max: 1000, ttl: 30 * 60 * 1000 });
const SONG_CACHE = new LRUCache({ max: 500, ttl: 30 * 60 * 1000 });
const UPNEXT_CACHE = new LRUCache({ max: 200, ttl: 30 * 60 * 1000 });
const ARTIST_CACHE = new LRUCache({ max: 200, ttl: 60 * 60 * 1000 });
const ALBUM_CACHE = new LRUCache({ max: 500, ttl: 60 * 60 * 1000 });
const PLAYLIST_CACHE = new LRUCache({ max: 200, ttl: 30 * 60 * 1000 });
const HOME_CACHE = new LRUCache({ max: 1, ttl: 10 * 60 * 1000 });
const LYRICS_CACHE = new LRUCache({ max: 500, ttl: 6 * 60 * 60 * 1000 });

async function memoize(cache, key, fn) {
    const hit = cache.get(key);
    if (hit) return hit;
    const v = await fn();
    cache.set(key, v);
    return v;
}

// ===========================================================================
// 7. Express app
// ===========================================================================
const app = express();
app.disable("x-powered-by");
app.use(cors());
app.use(compression());
app.use(express.json());

// Static — long cache + ETag.  index.html stays no-cache so the SPA shell
// always reflects the latest deploy.
app.use(express.static(path.join(__dirname, "public"), {
    etag: true,
    lastModified: true,
    maxAge: "1d",
    setHeaders(res, filePath) {
        if (filePath.endsWith(".html")) {
            res.setHeader("Cache-Control", "no-cache");
        }
    },
}));

// ---------------------------------------------------------------------------
// Health
// ---------------------------------------------------------------------------
app.get("/api/health", (_req, res) => {
    res.json({
        ok: true,
        name: "SakayoriMusic Web",
        version: "2.1.0-optimized",
        cookies: {
            pool: cookiePool.map((c) => c.id),
            dead: [...deadCookies],
            envFile: process.env.YT_COOKIES || null,
            envBrowser: YT_COOKIES_BROWSER || null,
        },
        cache: {
            urls: URL_CACHE.size,
            videoUrls: VIDEO_URL_CACHE.size,
            search: SEARCH_CACHE.size,
            songs: SONG_CACHE.size,
            artists: ARTIST_CACHE.size,
            albums: ALBUM_CACHE.size,
            playlists: PLAYLIST_CACHE.size,
            lyrics: LYRICS_CACHE.size,
            home: HOME_CACHE.size,
        },
    });
});

app.post("/api/cookies/refresh", (_req, res) => {
    res.json({ ok: true, loaded: refreshCookies() });
});

// ---------------------------------------------------------------------------
// Search + suggest (cached)
// ---------------------------------------------------------------------------
app.get("/api/search", async (req, res) => {
    const q = (req.query.q || "").toString().trim();
    const type = (req.query.type || "songs").toString();
    if (!q) return res.status(400).json({ error: "Missing q" });

    try {
        const data = await memoize(SEARCH_CACHE, `${type}:${q.toLowerCase()}`, async () => {
            const ytm = await getYTMusic();
            let results;
            switch (type) {
                case "videos": results = await ytm.searchVideos(q); break;
                case "albums": results = await ytm.searchAlbums(q); break;
                case "artists": results = await ytm.searchArtists(q); break;
                case "playlists": results = await ytm.searchPlaylists(q); break;
                case "all": results = await ytm.search(q); break;
                case "songs":
                default: results = await ytm.searchSongs(q); break;
            }
            const normalized = (results || []).map((r) => {
                if (r.type === "SONG" || r.type === "VIDEO" || r.videoId) return normalizeSong(r);
                return {
                    type: r.type,
                    id: r.albumId || r.playlistId || r.artistId || r.id,
                    name: r.name || r.title,
                    artist: r.artist?.name || r.artists?.[0]?.name || null,
                    thumbnail: pickThumb(r.thumbnails),
                };
            });
            return { query: q, type, results: normalized };
        });
        res.setHeader("Cache-Control", "public, max-age=60");
        res.json(data);
    } catch (err) {
        console.error("[/api/search]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/suggest", async (req, res) => {
    const q = (req.query.q || "").toString().trim();
    if (!q) return res.json({ suggestions: [] });
    try {
        const data = await memoize(SUGGEST_CACHE, q.toLowerCase(), async () => {
            const ytm = await getYTMusic();
            const list = await ytm.getSearchSuggestions(q).catch(() => []);
            return { suggestions: list || [] };
        });
        res.setHeader("Cache-Control", "public, max-age=300");
        res.json(data);
    } catch {
        res.json({ suggestions: [] });
    }
});

// ---------------------------------------------------------------------------
// Song / radio / artist / album / playlist (cached)
// ---------------------------------------------------------------------------
app.get("/api/song/:videoId", async (req, res) => {
    try {
        const data = await memoize(SONG_CACHE, req.params.videoId, async () => {
            const ytm = await getYTMusic();
            const song = await ytm.getSong(req.params.videoId);
            return normalizeSong(song);
        });
        res.json(data);
    } catch (err) {
        console.error("[/api/song]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/up-next/:videoId", async (req, res) => {
    try {
        const data = await memoize(UPNEXT_CACHE, req.params.videoId, async () => {
            const ytm = await getYTMusic();
            const upNext = await ytm.getUpNexts(req.params.videoId);
            return {
                videoId: req.params.videoId,
                songs: (upNext || []).map(normalizeSong).filter(Boolean),
            };
        });
        res.json(data);
    } catch (err) {
        console.error("[/api/up-next]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/artist/:id", async (req, res) => {
    try {
        const data = await memoize(ARTIST_CACHE, req.params.id, async () => {
            const ytm = await getYTMusic();
            const a = await ytm.getArtist(req.params.id);
            return {
                id: req.params.id,
                name: a?.name || "",
                description: a?.description || "",
                thumbnail: pickThumb(a?.thumbnails),
                subscribers: a?.subscribers || null,
                topSongs: (a?.songs?.results || a?.songs || []).map(normalizeSong),
                albums: (a?.albums?.results || a?.albums || []).map((al) => ({
                    id: al.albumId || al.id,
                    name: al.name || al.title,
                    year: al.year || null,
                    thumbnail: pickThumb(al.thumbnails),
                })),
                singles: (a?.singles?.results || a?.singles || []).map((al) => ({
                    id: al.albumId || al.id,
                    name: al.name || al.title,
                    year: al.year || null,
                    thumbnail: pickThumb(al.thumbnails),
                })),
            };
        });
        res.json(data);
    } catch (err) {
        console.error("[/api/artist]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/playlist/:id", async (req, res) => {
    try {
        const data = await memoize(PLAYLIST_CACHE, req.params.id, async () => {
            const ytm = await getYTMusic();
            const pl = await ytm.getPlaylist(req.params.id);
            const videos = await ytm.getPlaylistVideos(req.params.id).catch(() => []);
            return {
                id: req.params.id,
                name: pl?.name || "",
                description: pl?.description || "",
                thumbnail: pickThumb(pl?.thumbnails),
                songs: (videos || []).map(normalizeSong),
            };
        });
        res.json(data);
    } catch (err) {
        console.error("[/api/playlist]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/album/:id", async (req, res) => {
    try {
        const data = await memoize(ALBUM_CACHE, req.params.id, async () => {
            const ytm = await getYTMusic();
            const album = await ytm.getAlbum(req.params.id);
            return {
                id: req.params.id,
                name: album?.name || "",
                artist: album?.artist?.name || "",
                year: album?.year ?? null,
                thumbnail: pickThumb(album?.thumbnails),
                songs: (album?.songs || []).map(normalizeSong),
            };
        });
        res.json(data);
    } catch (err) {
        console.error("[/api/album]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Home  (cached for 10 min, parallelized per shelf)
// ---------------------------------------------------------------------------
const HOME_QUERIES = [
    { title: "Trending Now", q: "top hits 2025" },
    { title: "Lo-fi & Chill", q: "lofi hip hop" },
    { title: "Workout Energy", q: "workout playlist" },
    { title: "Anime OST", q: "anime opening" },
    { title: "Jazz Café", q: "jazz cafe" },
    { title: "K-Pop Stars", q: "kpop hits" },
];

app.get("/api/home", async (_req, res) => {
    try {
        const data = await memoize(HOME_CACHE, "default", async () => {
            const ytm = await getYTMusic();
            const shelves = await Promise.all(
                HOME_QUERIES.map(async ({ title, q }) => {
                    try {
                        const songs = await ytm.searchSongs(q);
                        return {
                            title,
                            items: (songs || []).slice(0, 12).map(normalizeSong),
                        };
                    } catch {
                        return { title, items: [] };
                    }
                })
            );
            return { shelves };
        });
        res.setHeader("Cache-Control", "public, max-age=300");
        res.json(data);
    } catch (err) {
        console.error("[/api/home]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Stream proxy (audio + video) with keep-alive, coalescing, and abort cleanup
// ---------------------------------------------------------------------------
async function proxyStream(req, res, getEntry, fallbackMime) {
    const videoId = req.params.videoId;
    let nodeStream = null;
    let aborted = false;
    req.on("close", () => { aborted = true; if (nodeStream) try { nodeStream.destroy(); } catch { /**/ } });

    try {
        let { url, mime } = await getEntry(videoId);
        if (aborted) return;

        const headers = { "User-Agent": UA };
        if (req.headers.range) headers["Range"] = req.headers.range;

        let upstream = await undiciFetch(url, { headers, dispatcher: upstreamAgent });

        // googlevideo URLs expire — if we hit 403 or 410, force-refresh.
        if ((upstream.status === 403 || upstream.status === 410)) {
            URL_CACHE.delete(videoId);
            VIDEO_URL_CACHE.delete(videoId);
            const fresh = await getEntry(videoId);
            if (aborted) return;
            upstream = await undiciFetch(fresh.url, { headers, dispatcher: upstreamAgent });
            mime = fresh.mime || mime;
        }

        res.status(upstream.status);
        res.setHeader("Content-Type", mime || fallbackMime);
        res.setHeader("Accept-Ranges", "bytes");
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Cache-Control", "no-store");

        const cl = upstream.headers.get("content-length");
        if (cl) res.setHeader("Content-Length", cl);
        const cr = upstream.headers.get("content-range");
        if (cr) res.setHeader("Content-Range", cr);

        if (!upstream.body) return res.end();

        nodeStream = Readable.fromWeb(upstream.body);
        nodeStream.on("error", (e) => {
            if (!aborted) console.error("[stream pipe]", e.message);
            try { res.end(); } catch { /* noop */ }
        });
        nodeStream.pipe(res);
    } catch (err) {
        if (aborted) return;
        console.error("[stream]", err.message);
        if (!res.headersSent) res.status(500).json({ error: err.message });
    }
}

app.get("/api/stream/:videoId", (req, res) => proxyStream(req, res, resolveAudio, "audio/mp4"));
app.get("/api/video/:videoId", (req, res) => proxyStream(req, res, resolveVideo, "video/mp4"));

// Prefetch hint — the SPA can call this to warm up the URL for the next track
// while the current one is still playing.  Returns immediately.
app.post("/api/prefetch/:videoId", (req, res) => {
    const { videoId } = req.params;
    if (!videoId) return res.status(400).json({ error: "Missing videoId" });
    // Fire & forget
    resolveAudio(videoId).catch(() => { /* noop */ });
    res.json({ ok: true });
});

app.post("/api/prefetch-video/:videoId", (req, res) => {
    const { videoId } = req.params;
    if (!videoId) return res.status(400).json({ error: "Missing videoId" });
    resolveVideo(videoId).catch(() => { /* noop */ });
    res.json({ ok: true });
});

// ---------------------------------------------------------------------------
// Lyrics — LRCLIB → YouTube subtitles fallback (cached)
// ---------------------------------------------------------------------------
async function lrclibLyrics({ title, artist, album, duration }) {
    const params = new URLSearchParams({
        track_name: String(title),
        artist_name: String(artist),
    });
    if (album) params.set("album_name", String(album));
    if (duration) params.set("duration", String(duration));

    const r = await undiciFetch(`https://lrclib.net/api/get?${params}`, {
        headers: { "User-Agent": "SakayoriMusicWeb/2.1 (+https://github.com/Sakayorii/sakayori-music)" },
    });
    if (r.status === 404) {
        const sr = await undiciFetch(
            `https://lrclib.net/api/search?${new URLSearchParams({
                track_name: String(title),
                artist_name: String(artist),
            })}`
        );
        const list = await sr.json();
        if (Array.isArray(list) && list.length) {
            return {
                found: true,
                plain: list[0].plainLyrics || "",
                synced: list[0].syncedLyrics || "",
                source: "LRCLIB",
            };
        }
        return { found: false };
    }
    const data = await r.json();
    return {
        found: true,
        plain: data.plainLyrics || "",
        synced: data.syncedLyrics || "",
        source: "LRCLIB",
    };
}

app.get("/api/lyrics", async (req, res) => {
    const { title, artist, album, duration, videoId, source } = req.query;
    const cacheKey = `${source || "auto"}::${videoId || ""}::${title || ""}::${artist || ""}`;
    try {
        const data = await memoize(LYRICS_CACHE, cacheKey, async () => {
            if (source === "youtube" && videoId) {
                const vtt = await ytDlpFetchSubs(String(videoId), "en");
                if (vtt) {
                    const synced = vttToLrc(vtt);
                    return {
                        found: true,
                        plain: synced.replace(/\[[\d:.]+\]/g, "").trim(),
                        synced,
                        source: "YouTube subtitles",
                    };
                }
                return { found: false };
            }

            if (!title || !artist) {
                throw Object.assign(new Error("title & artist required"), { status: 400 });
            }
            const lrc = await lrclibLyrics({ title, artist, album, duration });
            if (lrc.found && (lrc.synced || lrc.plain)) return lrc;

            if (videoId) {
                const vtt = await ytDlpFetchSubs(String(videoId), "en");
                if (vtt) {
                    const synced = vttToLrc(vtt);
                    return {
                        found: true,
                        plain: synced.replace(/\[[\d:.]+\]/g, "").trim(),
                        synced,
                        source: "YouTube subtitles (fallback)",
                    };
                }
            }
            return { found: false };
        });

        res.setHeader("Cache-Control", "public, max-age=600");
        res.json(data);
    } catch (err) {
        console.error("[/api/lyrics]", err);
        res.status(err.status || 500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// SPA fallback
// ---------------------------------------------------------------------------
app.get(/.*/, (_req, res) => {
    res.sendFile(path.join(__dirname, "public", "index.html"));
});

// ===========================================================================
// 8. Listen
// ===========================================================================
const server = app.listen(PORT, () => {
    console.log(`\n🎵 SakayoriMusic Web (optimized) on http://localhost:${PORT}\n`);
});
server.keepAliveTimeout = 65_000;
server.headersTimeout = 70_000;

// Graceful shutdown — stop accepting new connections, give in-flight 5 s.
function shutdown(sig) {
    console.log(`\n[server] ${sig} received, shutting down…`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 5000).unref();
}
process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
