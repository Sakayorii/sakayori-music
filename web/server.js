/**
 * SakayoriMusic — Web (Node.js port)
 *
 * Express server.  Mirrors what the Kotlin Multiplatform app does:
 *   • search YouTube Music (songs / videos / albums / playlists / artists)
 *   • resolve song details + suggested "watch next" radio queue
 *   • resolve artist / album / playlist details
 *   • stream the audio of a video (proxied googlevideo URL via yt-dlp)
 *   • lyrics from LRCLIB (synced) with YouTube auto-captions fallback (via yt-dlp)
 *   • home / explore feeds
 */

const path = require("path");
const fs = require("fs");
const os = require("os");
const { spawn } = require("child_process");
const express = require("express");
const cors = require("cors");
const compression = require("compression");

// ytmusic-api is ESM
let YTMusicCtor = null;
async function loadYTMusic() {
    if (YTMusicCtor) return YTMusicCtor;
    const mod = await import("ytmusic-api");
    YTMusicCtor = mod.default || mod.YTMusic || mod;
    return YTMusicCtor;
}

const YT_DLP = process.env.YT_DLP || "yt-dlp";

// ---------------------------------------------------------------------------
// Cookies — multiple sources, with auto-rotation when one dies
// ---------------------------------------------------------------------------
//
// Priority order:
//   1. A `cookies/` folder under web/  — drop as many cookies.txt files as you
//      like (any name) and the server will pick one, rotating to the next if
//      YouTube rejects it.  This is the recommended setup.
//   2. `YT_COOKIES=/path/to/file.txt`  — a single file, no rotation.
//   3. `YT_COOKIES_BROWSER=chrome`     — pulls cookies live from a browser.
//   4. Auto-detected `cookies.txt` / `youtube-cookies.txt` in web/.
//
// Each file in the pool is sanitized on load: any non-cookie header text
// (e.g. "Simple Checker" account-info preambles) is stripped, and the strict
// Netscape header is prepended so yt-dlp will accept it.
//
const YT_COOKIES = process.env.YT_COOKIES || "";
const YT_COOKIES_BROWSER = process.env.YT_COOKIES_BROWSER || "";
const COOKIES_DIR = path.join(__dirname, "cookies");
const COOKIES_TMP = path.join(os.tmpdir(), "smweb-cookies");

let cookiePool = [];           // [{ id, normalizedPath }]
let deadCookies = new Set();   // ids that produced auth errors recently

function sanitizeCookieText(text) {
    const lines = String(text || "").split(/\r?\n/);
    const out = [];
    for (const line of lines) {
        const t = line.trim();
        if (!t || t.startsWith("#")) continue;
        // Netscape cookie line: 7 tab-separated fields.
        const parts = line.split("\t");
        if (parts.length < 7) continue;
        // First field must look like a domain, second must be TRUE/FALSE.
        if (!/^\.?[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(parts[0])) continue;
        if (!/^(TRUE|FALSE)$/i.test(parts[1])) continue;
        out.push(line);
    }
    if (!out.length) return null;
    return (
        "# Netscape HTTP Cookie File\n" +
        "# Auto-normalized by SakayoriMusic Web\n\n" +
        out.join("\n") + "\n"
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

// Auto-detect a single cookies.txt next to web/ if neither pool nor env var.
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

// Run yt-dlp once with whichever cookie we currently prefer.  Returns stdout.
function runYtDlp(baseArgs, cookieEntry) {
    return new Promise((resolve, reject) => {
        const args = [...baseArgs, ...buildAuthArgs(cookieEntry)];
        const p = spawn(YT_DLP, args, { windowsHide: true });
        let out = "", err = "";
        p.stdout.on("data", (b) => (out += b.toString("utf8")));
        p.stderr.on("data", (b) => (err += b.toString("utf8")));
        p.on("error", reject);
        p.on("close", (code) => {
            if (code === 0 && out.trim()) resolve(out);
            else reject(new Error(err || `yt-dlp exited ${code}`));
        });
    });
}

// Run yt-dlp with the cookie pool, rotating on auth-style errors.
async function runYtDlpWithRotation(baseArgs) {
    const tried = new Set();
    let lastErr = null;

    // First pass: try every available cookie in the pool.
    while (true) {
        const cookie = pickCookie();
        if (cookie && tried.has(cookie.id)) break;
        try {
            return await runYtDlp(baseArgs, cookie);
        } catch (e) {
            lastErr = e;
            if (!cookie) break; // No cookie pool — give up
            tried.add(cookie.id);
            if (isAuthError(e.message)) {
                markCookieDead(cookie.id);
                continue;
            }
            break; // Non-auth failure — don't waste rotation
        }
    }

    // Last resort: env-var single cookie or browser extraction.
    if (lastErr && (process.env.YT_COOKIES || YT_COOKIES_BROWSER) && !cookiePool.length) {
        try { return await runYtDlp(baseArgs, null); } catch (e) { lastErr = e; }
    }

    throw lastErr || new Error("yt-dlp failed");
}

// Endpoint to manually refresh the pool (e.g. after dropping a new cookie file).
function refreshCookies() {
    loadCookiePool();
    return cookiePool.map((c) => c.id);
}

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

// Resolve a single combined-mp4 (video+audio) URL.  Browsers can only play
// progressive mp4 with both tracks muxed together, which limits us to ≤ 720p
// for most YouTube videos — that's what `best[ext=mp4]/best` gives us.
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




// Fetch YouTube auto-generated captions in a chosen language as VTT text.
// Returns null if no captions are available.
async function ytDlpFetchSubs(videoId, lang = "en") {
    const tmp = path.join(
        os.tmpdir(),
        `smweb-subs-${videoId}-${Date.now()}`
    );
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


// Convert WebVTT (the format yt-dlp dumps) to LRC.
function vttToLrc(vtt) {
    const lines = vtt.split(/\r?\n/);
    const out = [];
    let curStart = null;
    const seen = new Set();
    for (const raw of lines) {
        const line = raw.trim();
        if (!line || /^WEBVTT/i.test(line) || /^NOTE/i.test(line)) continue;
        const m = /^(\d{2}):(\d{2}):(\d{2})[.,](\d{3})\s*-->/.exec(line);
        if (m) {
            const h = parseInt(m[1], 10);
            const mn = parseInt(m[2], 10);
            const s = parseInt(m[3], 10);
            const ms = parseInt(m[4], 10);
            const total = h * 3600 + mn * 60 + s + ms / 1000;
            const lm = Math.floor(total / 60);
            const ls = (total - lm * 60).toFixed(2).padStart(5, "0");
            curStart = `[${String(lm).padStart(2, "0")}:${ls}]`;
            continue;
        }
        if (curStart && !/^\d/.test(line)) {
            // Strip VTT inline tags like <c>, <00:00:01.000>
            const clean = line.replace(/<[^>]+>/g, "").trim();
            if (clean) {
                const lrc = `${curStart}${clean}`;
                if (!seen.has(lrc)) {
                    seen.add(lrc);
                    out.push(lrc);
                }
            }
        }
    }
    return out.join("\n");
}

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(compression());
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

// ---------------------------------------------------------------------------
// YT Music client (lazy, single shared instance)
// ---------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function pickThumb(thumbnails) {
    if (!Array.isArray(thumbnails) || thumbnails.length === 0) return null;
    return thumbnails.reduce((best, t) => {
        if (!best) return t;
        return (t.width || 0) > (best.width || 0) ? t : best;
    }, null);
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
        album: s.album
            ? { name: s.album.name, albumId: s.album.albumId }
            : null,
        duration: s.duration ?? null,
        thumbnail: pickThumb(s.thumbnails) || s.thumbnail || null,
    };
}

// ---------------------------------------------------------------------------
// Health
// ---------------------------------------------------------------------------
app.get("/api/health", (_req, res) => {
    res.json({
        ok: true,
        name: "SakayoriMusic Web",
        version: "2.0.0",
        cookies: {
            pool: cookiePool.map((c) => c.id),
            dead: [...deadCookies],
            envFile: process.env.YT_COOKIES || null,
            envBrowser: YT_COOKIES_BROWSER || null,
        },
    });
});

// Reload the cookies/ folder without restarting the server.
app.post("/api/cookies/refresh", (_req, res) => {
    const ids = refreshCookies();
    res.json({ ok: true, loaded: ids });
});


// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------
app.get("/api/search", async (req, res) => {
    const q = (req.query.q || "").toString().trim();
    const type = (req.query.type || "songs").toString();
    if (!q) return res.status(400).json({ error: "Missing q" });

    try {
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
            if (r.type === "SONG" || r.type === "VIDEO" || r.videoId) {
                return normalizeSong(r);
            }
            return {
                type: r.type,
                id: r.albumId || r.playlistId || r.artistId || r.id,
                name: r.name || r.title,
                artist: r.artist?.name || r.artists?.[0]?.name || null,
                thumbnail: pickThumb(r.thumbnails),
            };
        });
        res.json({ query: q, type, results: normalized });
    } catch (err) {
        console.error("[/api/search]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Suggestions (for autocomplete)
// ---------------------------------------------------------------------------
app.get("/api/suggest", async (req, res) => {
    const q = (req.query.q || "").toString().trim();
    if (!q) return res.json({ suggestions: [] });
    try {
        const ytm = await getYTMusic();
        const list = await ytm.getSearchSuggestions(q).catch(() => []);
        res.json({ suggestions: list || [] });
    } catch (err) {
        res.json({ suggestions: [] });
    }
});

// ---------------------------------------------------------------------------
// Song details + radio
// ---------------------------------------------------------------------------
app.get("/api/song/:videoId", async (req, res) => {
    try {
        const ytm = await getYTMusic();
        const song = await ytm.getSong(req.params.videoId);
        res.json(normalizeSong(song));
    } catch (err) {
        console.error("[/api/song]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/up-next/:videoId", async (req, res) => {
    try {
        const ytm = await getYTMusic();
        const upNext = await ytm.getUpNexts(req.params.videoId);
        const songs = (upNext || []).map(normalizeSong).filter(Boolean);
        res.json({ videoId: req.params.videoId, songs });
    } catch (err) {
        console.error("[/api/up-next]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Artist / Album / Playlist
// ---------------------------------------------------------------------------
app.get("/api/artist/:id", async (req, res) => {
    try {
        const ytm = await getYTMusic();
        const a = await ytm.getArtist(req.params.id);
        res.json({
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
        });
    } catch (err) {
        console.error("[/api/artist]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/playlist/:id", async (req, res) => {
    try {
        const ytm = await getYTMusic();
        const pl = await ytm.getPlaylist(req.params.id);
        const videos = await ytm.getPlaylistVideos(req.params.id).catch(() => []);
        res.json({
            id: req.params.id,
            name: pl?.name || "",
            description: pl?.description || "",
            thumbnail: pickThumb(pl?.thumbnails),
            songs: (videos || []).map(normalizeSong),
        });
    } catch (err) {
        console.error("[/api/playlist]", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/album/:id", async (req, res) => {
    try {
        const ytm = await getYTMusic();
        const album = await ytm.getAlbum(req.params.id);
        res.json({
            id: req.params.id,
            name: album?.name || "",
            artist: album?.artist?.name || "",
            year: album?.year ?? null,
            thumbnail: pickThumb(album?.thumbnails),
            songs: (album?.songs || []).map(normalizeSong),
        });
    } catch (err) {
        console.error("[/api/album]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------
app.get("/api/home", async (_req, res) => {
    try {
        const ytm = await getYTMusic();
        const queries = [
            { title: "Trending Now", q: "top hits 2025" },
            { title: "Lo-fi & Chill", q: "lofi hip hop" },
            { title: "Workout Energy", q: "workout playlist" },
            { title: "Anime OST", q: "anime opening" },
            { title: "Jazz Café", q: "jazz cafe" },
            { title: "K-Pop Stars", q: "kpop hits" },
        ];
        const shelves = await Promise.all(
            queries.map(async ({ title, q }) => {
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
        res.json({ shelves });
    } catch (err) {
        console.error("[/api/home]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Streaming proxy via yt-dlp (cached 5 min, separate caches for audio/video)
// ---------------------------------------------------------------------------
const URL_CACHE = new Map();        // audio cache
const VIDEO_URL_CACHE = new Map();  // video cache
const URL_TTL_MS = 5 * 60 * 1000;

async function getStreamUrl(videoId) {
    const cached = URL_CACHE.get(videoId);
    if (cached && cached.exp > Date.now()) return cached;
    const url = await ytDlpResolveAudioUrl(videoId);
    const mime = /mime=audio%2Fwebm/i.test(url) ? "audio/webm" : "audio/mp4";
    const entry = { url, mime, exp: Date.now() + URL_TTL_MS };
    URL_CACHE.set(videoId, entry);
    return entry;
}

async function getVideoStreamUrl(videoId) {
    const cached = VIDEO_URL_CACHE.get(videoId);
    if (cached && cached.exp > Date.now()) return cached;
    const url = await ytDlpResolveVideoUrl(videoId);
    const mime = /mime=video%2Fwebm/i.test(url) ? "video/webm" : "video/mp4";
    const entry = { url, mime, exp: Date.now() + URL_TTL_MS };
    VIDEO_URL_CACHE.set(videoId, entry);
    return entry;
}

// Generic upstream proxy
async function proxyStream(req, res, getUrl, cacheMap, fallbackMime) {
    const videoId = req.params.videoId;
    try {
        const { url, mime } = await getUrl(videoId);
        const headers = {
            "User-Agent":
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        };
        if (req.headers.range) headers["Range"] = req.headers.range;

        let upstream = await fetch(url, { headers });
        if (upstream.status === 403) {
            cacheMap.delete(videoId);
            const fresh = await getUrl(videoId);
            upstream = await fetch(fresh.url, { headers });
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

        const { Readable } = require("stream");
        const nodeStream = Readable.fromWeb(upstream.body);
        nodeStream.on("error", (e) => {
            console.error("[stream pipe]", e.message);
            try { res.end(); } catch { /* noop */ }
        });
        req.on("close", () => {
            try { nodeStream.destroy(); } catch { /* noop */ }
        });
        nodeStream.pipe(res);
    } catch (err) {
        console.error("[stream]", err.message);
        if (!res.headersSent) res.status(500).json({ error: err.message });
    }
}

app.get("/api/stream/:videoId", (req, res) =>
    proxyStream(req, res, getStreamUrl, URL_CACHE, "audio/mp4")
);

// Browsers can play this with <video> directly (combined mp4).
app.get("/api/video/:videoId", (req, res) =>
    proxyStream(req, res, getVideoStreamUrl, VIDEO_URL_CACHE, "video/mp4")
);


// ---------------------------------------------------------------------------
// Lyrics — multi-source (LRCLIB + YouTube auto-captions fallback)
// ---------------------------------------------------------------------------
async function lrclibLyrics({ title, artist, album, duration }) {
    const params = new URLSearchParams({
        track_name: String(title),
        artist_name: String(artist),
    });
    if (album) params.set("album_name", String(album));
    if (duration) params.set("duration", String(duration));

    const url = `https://lrclib.net/api/get?${params}`;
    const r = await fetch(url, {
        headers: {
            "User-Agent":
                "SakayoriMusicWeb/2.0 (https://github.com/Sakayorii/sakayori-music)",
        },
    });
    if (r.status === 404) {
        const sr = await fetch(
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

    try {
        if (source === "youtube" && videoId) {
            const vtt = await ytDlpFetchSubs(String(videoId), "en");
            if (vtt) {
                const synced = vttToLrc(vtt);
                return res.json({
                    found: true,
                    plain: synced.replace(/\[[\d:.]+\]/g, "").trim(),
                    synced,
                    source: "YouTube subtitles",
                });
            }
            return res.json({ found: false });
        }

        if (!title || !artist) {
            return res.status(400).json({ error: "title & artist required" });
        }

        // Try LRCLIB first, fall back to YouTube auto-captions if available.
        const lrc = await lrclibLyrics({ title, artist, album, duration });
        if (lrc.found && (lrc.synced || lrc.plain)) return res.json(lrc);

        if (videoId) {
            const vtt = await ytDlpFetchSubs(String(videoId), "en");
            if (vtt) {
                const synced = vttToLrc(vtt);
                return res.json({
                    found: true,
                    plain: synced.replace(/\[[\d:.]+\]/g, "").trim(),
                    synced,
                    source: "YouTube subtitles (fallback)",
                });
            }
        }
        res.json({ found: false });
    } catch (err) {
        console.error("[/api/lyrics]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Fallback to SPA
// ---------------------------------------------------------------------------
app.get(/.*/, (_req, res) => {
    res.sendFile(path.join(__dirname, "public", "index.html"));
});

app.listen(PORT, () => {
    console.log(`\n🎵 SakayoriMusic Web running on http://localhost:${PORT}\n`);
});
