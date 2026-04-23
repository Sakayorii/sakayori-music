# SakayoriMusic — Web Player

A Node.js + Express port of [SakayoriMusic](../README.md) — runs the same
listening experience as the Kotlin/Compose desktop & Android app inside a
browser. Single-page frontend (vanilla JS), streams YouTube Music via
`yt-dlp`, lyrics via LRCLIB + YouTube auto-captions.

> **Web Player Lead:** [@giangnam0201](https://github.com/giangnam0201) — primary contributor and maintainer of this module.

---

## Quick Start

```bash
cd web
npm install
npm start
```

Server binds to **`127.0.0.1:3000`** by default (localhost-only).

Open [http://127.0.0.1:3000](http://127.0.0.1:3000)

---

## Configuration

All via environment variables — no config file.

| Env Var             | Default       | Purpose                                                      |
| ------------------- | ------------- | ------------------------------------------------------------ |
| `HOST`              | `127.0.0.1`   | Bind address. Set `0.0.0.0` to expose on LAN / behind tunnel |
| `PORT`              | `3000`        | TCP port                                                     |
| `YT_DLP`            | `yt-dlp`      | Path to yt-dlp binary (`PATH` lookup by default)             |
| `YT_COOKIES`        | *(unset)*     | Single Netscape cookies.txt file path                        |
| `YT_COOKIES_BROWSER`| *(unset)*     | Pull cookies from browser (`chrome`/`firefox`/`edge`/…)      |

### Common setups

**Local dev (default):**
```bash
npm start
# → http://127.0.0.1:3000 (loopback only)
```

**Expose on LAN for phone testing:**
```bash
# PowerShell
$env:HOST="0.0.0.0"; npm start

# Bash
HOST=0.0.0.0 npm start
```

**Behind Cloudflare Tunnel** (stays on `127.0.0.1:3000`, tunnel handles rest):
```bash
npm start
# + separate cloudflared process mapping your-domain.dev → 127.0.0.1:3000
```

**Dev mode (auto-reload on save):**
```bash
npm run dev
```

---

## Requirements

- **Node 20+** (uses global `fetch`, `undici` Agent)
- **Python 3 + yt-dlp** on `PATH`
  ```bash
  pip install -U yt-dlp
  ```

### YouTube cookies (highly recommended)

YouTube increasingly blocks server IPs with *"Sign in to confirm you're not a bot"*. Supply cookies in priority order:

1. **Drop multiple files in `web/cookies/`** (recommended) — auto-rotation with dead-cookie detection.
   ```
   web/cookies/
     acct1.txt
     acct2.txt
     burner.txt
   ```
   - Each file is auto-sanitized (strips prefix garbage, enforces Netscape header)
   - Server marks a cookie dead when it returns bot-challenge; falls back to next
   - Pool resets when all dead
   - `GET /api/health` shows pool status
   - `POST /api/cookies/refresh` reloads folder without restart

2. **Single `cookies.txt`, `youtube-cookies.txt`, or `yt-cookies.txt`** next to `web/` — auto-detected.

3. **`YT_COOKIES=/path/to/cookies.txt`** — explicit single file.

4. **`YT_COOKIES_BROWSER=chrome`** — live-read from installed browser. Browser must be **closed** during read.

Export via [Get cookies.txt LOCALLY](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc) — log into youtube.com, click extension, save to `web/cookies/`.

---

## Project Structure

```
web/
├── README.md           ← this file
├── package.json        ← express, undici, lru-cache, ytmusic-api
├── server.js           ← 900-line Express backend (API + streaming proxy)
├── cookies/            ← (gitignored) multi-account cookie pool
└── public/
    ├── index.html      ← single SPA entry (hash-based router)
    ├── app.js          ← 1700-line SPA controller + Web Audio pipeline
    └── styles.css      ← 1700-line stylesheet (dark, Material-inspired)
```

The server is intentionally monolithic — one file, readable top-to-bottom, with section headers (`// === 0. Constants === //`, `// === 1. Cookie pool === //`, …). No bundler, no transpiler on the frontend — `app.js` is plain modern JS served as-is.

---

## Features

### Player UX
- Spinning vinyl disc with album art + swinging tonearm
- Blurred album-art backdrop in fullscreen player
- Video-mode toggle (watches MV via yt-dlp combined mp4)
- Audio visualizer — bars / waveform / ring (AnalyserNode)
- 3-band EQ (low / mid / high BiQuad shelves + peaking)
- Crossfade 0–10s (Web Audio gain ramp)
- Shuffle / repeat-all / repeat-one
- HTTP Range streaming — seek bar works mid-track
- Queue panel with auto-radio extension, drag-to-reorder
- Synced (LRC) lyrics — LRCLIB → YT auto-captions fallback
- MediaSession API — OS media keys, lock-screen controls, notification artwork
- Adaptive accent color sampled from current album art

### Library
- Liked songs (localStorage)
- Local playlists (create / rename / delete / reorder / drag-add)
- Recently played (auto-history, max 200)
- Home feed with shelves: Trending, Lo-fi, Workout, Anime OST, Jazz, K-Pop
- Right-click context menu on every song
- Hash-based router — bookmarkable, back/forward work
- Backup / restore (JSON export / import)

### Keyboard Shortcuts

| Key            | Action                |
| -------------- | --------------------- |
| `Space`        | Play / Pause          |
| `←` / `→`      | Previous / Next       |
| `↑` / `↓`      | Volume                |
| `M`            | Mute                  |
| `L`            | Like                  |
| `S`            | Shuffle               |
| `R`            | Repeat cycle          |
| `F`            | Fullscreen player     |
| `V`            | Toggle video mode     |
| `Esc`          | Close overlay         |
| `?`            | Show shortcut help    |

---

## API Endpoints

| Method | Path                                                         | Description                                |
| ------ | ------------------------------------------------------------ | ------------------------------------------ |
| GET    | `/api/health`                                                | Liveness + cookie pool status              |
| GET    | `/api/home`                                                  | Curated home shelves                       |
| GET    | `/api/search?q&type`                                         | Search (`songs`/`videos`/`albums`/…)       |
| GET    | `/api/suggest?q`                                             | Autocomplete                               |
| GET    | `/api/song/:videoId`                                         | Track metadata                             |
| GET    | `/api/up-next/:videoId`                                      | Auto-radio queue                           |
| GET    | `/api/artist/:id`                                            | Artist + top songs + albums + singles      |
| GET    | `/api/album/:id`                                             | Album contents                             |
| GET    | `/api/playlist/:id`                                          | Playlist contents                          |
| GET    | `/api/stream/:videoId`                                       | Audio stream (Range-aware)                 |
| GET    | `/api/video/:videoId`                                        | Video+audio mp4 (yt-dlp combined)          |
| GET    | `/api/lyrics?title&artist&album&duration&videoId&source`     | LRCLIB + YouTube subs (synced + plain)     |
| POST   | `/api/cookies/refresh`                                       | Reload `cookies/` folder without restart   |

---

## Performance Notes

The server bundles several latency optimizations:

1. **In-flight request coalescing** — concurrent calls for same `videoId` share one yt-dlp process
2. **LRU caches** for resolved URLs, search, home shelves, lyrics, metadata
3. **Keep-alive undici Agent** for upstream googlevideo (saves TCP+TLS handshake on every Range)
4. **Prefetch hint** endpoint warms next track's URL while current plays
5. **Cookie-pool rotation** with auto-sanitization and dead-cookie tracking
6. **Static-asset cache headers** (immutable + ETag, gzip + brotli)
7. **Graceful upstream cleanup** — aborted Range destroys upstream body → frees sockets

---

## Mapping to Parent KMP Project

| SakayoriMusic (KMP)             | This web port                                |
| ------------------------------- | -------------------------------------------- |
| `kotlinYtmusicScraper` service  | `ytmusic-api` npm package                    |
| Media3 (Android) / VLC (JVM)    | `yt-dlp` URL resolver → `<audio>`/`<video>`  |
| `lyricsService` LRCLIB path     | `/api/lyrics?source=lrclib`                  |
| `lyricsService` YT transcript   | `/api/lyrics?source=youtube` (yt-dlp subs)   |
| `FullscreenPlayer.kt`           | `index.html #fullPlayer` + styles            |
| `MiniPlayer.kt`                 | `footer.player`                              |
| `equalizer/` + `crossfade/`     | Web Audio EQ + gain ramps                    |
| `library/`                      | localStorage liked + recents + playlists     |
| `RecentlySongsScreen.kt`        | `#/recent`                                   |
| `EqualizerScreen.kt`            | Settings page                                |
| Backup / restore                | Settings → Export / Import                   |

---

## Not Ported

Features present in native apps but intentionally out-of-scope here:
- Discord Rich Presence
- Spotify Canvas
- Wear OS / Android Auto notifications
- Offline download manager
- OAuth sign-in / personalized feed
- Multi-source lyrics chain beyond LRCLIB + YT
- Full 31-language UI (web is English-only for now)

---

## License

MIT — same as parent [SakayoriMusic](../README.md) project.
