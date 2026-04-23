/**
 * SakayoriMusic — Web (Node.js port)
 *
 * Express server that mirrors what the Kotlin Multiplatform app does:
 *   • search YouTube Music (songs / videos / albums / playlists / artists)
 *   • resolve song details + suggested "watch next" queue
 *   • stream the audio of a video (server-side proxy, so the browser can play
 *     the otherwise-restricted googlevideo URL)
 *   • fetch lyrics (LRCLIB) — same provider used in the desktop client
 *   • home / explore feeds
 *
 * The frontend lives in ./public and is a single-page vanilla JS app that
 * recreates the SakayoriMusic player UI, including the keyboard shortcuts
 * documented in the project README.
 */

const path = require("path");
const { spawn } = require("child_process");
const express = require("express");
const cors = require("cors");
const compression = require("compression");

// ytmusic-api is an ESM module published as default-export class.
let YTMusicCtor = null;
async function loadYTMusic() {
    if (YTMusicCtor) return YTMusicCtor;
    const mod = await import("ytmusic-api");
    YTMusicCtor = mod.default || mod.YTMusic || mod;
    return YTMusicCtor;
}

// ---------------------------------------------------------------------------
// yt-dlp (Python) — resolves real, working googlevideo URLs.  We shell out
// instead of using youtubei.js because YouTube's SABR / UMP / PoToken
// requirements changed in early 2026 and pure-JS scrapers no longer get
// `url` fields back from the WEB / ANDROID players.
// ---------------------------------------------------------------------------
const YT_DLP = process.env.YT_DLP || "yt-dlp";

function ytDlpResolveUrl(videoId) {
    return new Promise((resolve, reject) => {
        const args = [
            "-q",
            "--no-warnings",
            "-f", "bestaudio[ext=m4a]/bestaudio/best",
            "-g", // print URL only
            "--no-playlist",
            `https://www.youtube.com/watch?v=${videoId}`,
        ];
        const p = spawn(YT_DLP, args, { windowsHide: true });
        let out = "", err = "";
        p.stdout.on("data", (b) => (out += b.toString("utf8")));
        p.stderr.on("data", (b) => (err += b.toString("utf8")));
        p.on("error", reject);
        p.on("close", (code) => {
            if (code === 0 && out.trim()) {
                // The first non-empty line is the audio URL.
                resolve(out.split(/\r?\n/).filter(Boolean)[0]);
            } else {
                reject(new Error(err || `yt-dlp exited ${code}`));
            }
        });
    });
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
    // Pick the largest available
    return thumbnails.reduce((best, t) => {
        if (!best) return t;
        return (t.width || 0) > (best.width || 0) ? t : best;
    }, null);
}

function normalizeArtist(a) {
    if (!a) return null;
    if (typeof a === "string") return { name: a, artistId: null };
    // ytmusic-api shapes: { artistId, name }
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
        // searchSongs returns `artist` (singular ArtistBasic)
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
        thumbnail: pickThumb(s.thumbnails) || (s.thumbnail || null),
    };
}

// ---------------------------------------------------------------------------
// Health
// ---------------------------------------------------------------------------
app.get("/api/health", (_req, res) => {
    res.json({ ok: true, name: "SakayoriMusic Web", version: "1.0.0" });
});

// ---------------------------------------------------------------------------
// Search
//   /api/search?q=...&type=songs|videos|albums|artists|playlists|all
// ---------------------------------------------------------------------------
app.get("/api/search", async (req, res) => {
    const q = (req.query.q || "").toString().trim();
    const type = (req.query.type || "songs").toString();
    if (!q) return res.status(400).json({ error: "Missing q" });

    try {
        const ytm = await getYTMusic();
        let results;
        switch (type) {
            case "videos":
                results = await ytm.searchVideos(q);
                break;
            case "albums":
                results = await ytm.searchAlbums(q);
                break;
            case "artists":
                results = await ytm.searchArtists(q);
                break;
            case "playlists":
                results = await ytm.searchPlaylists(q);
                break;
            case "all":
                results = await ytm.search(q);
                break;
            case "songs":
            default:
                results = await ytm.searchSongs(q);
                break;
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
// Song details
//   /api/song/:videoId   -> metadata
//   /api/up-next/:videoId -> auto-generated radio queue
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
        // ytmusic-api exposes getUpNexts
        const upNext = await ytm.getUpNexts(req.params.videoId);
        const songs = (upNext || []).map(normalizeSong).filter(Boolean);
        res.json({ videoId: req.params.videoId, songs });
    } catch (err) {
        console.error("[/api/up-next]", err);
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Home / Explore feed (uses search of trending words as a fallback)
// ---------------------------------------------------------------------------
app.get("/api/home", async (_req, res) => {
    try {
        const ytm = await getYTMusic();
        // Several "shelves" so the home screen feels alive.
        const queries = [
            { title: "Trending Now", q: "top hits 2024" },
            { title: "Lo-fi & Chill", q: "lofi hip hop" },
            { title: "Workout Energy", q: "workout playlist" },
            { title: "Anime OST", q: "anime opening" },
        ];
        const shelves = await Promise.all(
            queries.map(async ({ title, q }) => {
                try {
                    const songs = await ytm.searchSongs(q);
                    return {
                        title,
                        items: (songs || []).slice(0, 12).map(normalizeSong),
                    };
                } catch (e) {
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
// Playlist & Album
// ---------------------------------------------------------------------------
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
// Streaming proxy
//   /api/stream/:videoId  -> resolves a real googlevideo URL via yt-dlp,
//   then proxies the bytes through (forwarding the Range header so the
//   <audio> element can seek).
//
// We cache the resolved URL for ~5 minutes per video to avoid spawning yt-dlp
// for every Range request the browser makes while playing one song.
// ---------------------------------------------------------------------------
const URL_CACHE = new Map(); // videoId -> { url, mime, exp }
const URL_TTL_MS = 5 * 60 * 1000;

async function getStreamUrl(videoId) {
    const cached = URL_CACHE.get(videoId);
    if (cached && cached.exp > Date.now()) return cached;

    const url = await ytDlpResolveUrl(videoId);
    // The audio container is m4a/mp4 in 99% of cases yt-dlp picks first.
    const mime = /mime=audio%2Fwebm/i.test(url) ? "audio/webm" : "audio/mp4";
    const entry = { url, mime, exp: Date.now() + URL_TTL_MS };
    URL_CACHE.set(videoId, entry);
    return entry;
}

app.get("/api/stream/:videoId", async (req, res) => {
    const videoId = req.params.videoId;
    try {
        const { url, mime } = await getStreamUrl(videoId);

        // Forward Range header so the browser can seek.
        const headers = {
            "User-Agent":
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        };
        if (req.headers.range) headers["Range"] = req.headers.range;

        let upstream = await fetch(url, { headers });

        // If the cached URL expired (HTTP 403) re-resolve once.
        if (upstream.status === 403) {
            URL_CACHE.delete(videoId);
            const fresh = await getStreamUrl(videoId);
            upstream = await fetch(fresh.url, { headers });
        }

        res.status(upstream.status);
        res.setHeader("Content-Type", mime);
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
            try { res.end(); } catch { }
        });
        req.on("close", () => {
            try { nodeStream.destroy(); } catch { }
        });
        nodeStream.pipe(res);
    } catch (err) {
        console.error("[/api/stream]", err.message);
        if (!res.headersSent) res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Lyrics (LRCLIB — same provider used in the desktop app)
//   /api/lyrics?title=...&artist=...&album=...&duration=...
// ---------------------------------------------------------------------------
app.get("/api/lyrics", async (req, res) => {
    const { title, artist, album, duration } = req.query;
    if (!title || !artist) {
        return res.status(400).json({ error: "title & artist required" });
    }
    try {
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
                    "SakayoriMusicWeb/1.0 (https://github.com/Sakayorii/sakayori-music)",
            },
        });

        if (r.status === 404) {
            // fallback: search
            const sr = await fetch(
                `https://lrclib.net/api/search?${new URLSearchParams({
                    track_name: String(title),
                    artist_name: String(artist),
                })}`
            );
            const list = await sr.json();
            if (Array.isArray(list) && list.length) {
                return res.json({
                    found: true,
                    plain: list[0].plainLyrics || "",
                    synced: list[0].syncedLyrics || "",
                    source: "LRCLIB (search)",
                });
            }
            return res.json({ found: false });
        }

        const data = await r.json();
        res.json({
            found: true,
            plain: data.plainLyrics || "",
            synced: data.syncedLyrics || "",
            source: "LRCLIB",
        });
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
