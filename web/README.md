# SakayoriMusic — Web Player

A Node.js + Express port of [SakayoriMusic](../README.md) — runs the same
listening experience as the Kotlin/Compose desktop & Android app inside a
browser. Single-page frontend (vanilla JS), streams YouTube Music via
`yt-dlp`.

> **Web Player Lead:** [@giangnam0201](https://github.com/giangnam0201) — primary contributor and maintainer of this module.

---

## Quick Start

```bash
cd web
npm install
npm start
```

Server binds to **`0.0.0.0:3000`** by default.

Open [http://127.0.0.1:3000](http://127.0.0.1:3000)

---

## Configuration

All via environment variables — no config file.

| Env Var             | Default       | Purpose                                                      |
| ------------------- | ------------- | ------------------------------------------------------------ |
| `HOST`              | `0.0.0.0`     | Bind address                                                 |
| `PORT`              | `3000`        | TCP port                                                     |
| `YT_DLP`            | `yt-dlp`      | Path to yt-dlp binary (auto-bootstrapped if missing)         |
| `YT_COOKIES`        | *(unset)*     | Single Netscape cookies.txt file path                        |
| `YT_COOKIES_BROWSER`| *(unset)*     | Pull cookies from browser (`chrome`/`firefox`/`edge`/…)      |
| `YT_COOKIES_URL`    | *(community)* | Remote Netscape cookies pack to auto-fetch                   |

---

## Requirements

- **Node 20+** (uses global `fetch`, `undici` Agent)
- **`yt-dlp`** — auto-downloaded into `web/bin/` if not on PATH
  ```bash
  pip install -U yt-dlp     # optional; the server will bootstrap if missing
  ```

### YouTube cookies (highly recommended)

YouTube increasingly blocks server IPs with *"Sign in to confirm you're not a bot"*. Supply cookies in priority order:

1. **Drop multiple files in `web/cookies/`** — auto-rotation with dead-cookie detection.
2. **Single `cookies.txt`** next to `web/` — auto-detected.
3. **`YT_COOKIES=/path/to/cookies.txt`** — explicit single file.
4. **`YT_COOKIES_BROWSER=chrome`** — live-read from installed browser.

---

## Project Structure

```
web/
├── README.md
├── package.json            ← express, undici, lru-cache, ytmusic-api
├── server.js               ← monolithic Express backend (≈1.4k lines)
├── cookies/                ← (gitignored) multi-account cookie pool
└── public/
    ├── index.html          ← inline-SVG icon sprite + Liquid Glass shell
    ├── app.js              ← SPA controller (i18n, offline, lyrics chain, player)
    ├── styles.css          ← Liquid Glass stylesheet
    └── locales/            ← (auto-generated) JSON locale files
```

The server is intentionally monolithic — one file, readable top-to-bottom,
with section headers (`// === 0. Constants === //`, `// === 1. Cookie pool === //`, …).
No bundler, no transpiler on the frontend — `app.js` is plain modern JS served as-is.

---

## Features

### Liquid Glass UI
- Translucent layered chrome (sidebar, topbar, player, panels, menus, toasts)
  with `backdrop-filter` blur + saturate, hairline borders, inset specular bevels
- **Zero emoji**: every glyph is an inline-SVG `<use href="#ic-*">` reference,
  inheriting `currentColor` so theme tinting cascades automatically
- Adaptive accent color sampled from current album art (`art` theme)
- 6 built-in themes (cyan / purple / rose / lime / amber / art-color)
- Spinning vinyl with swinging tonearm; blurred album-art backdrop on fullscreen
- Audio visualizer (bars / waveform / ring) via `AnalyserNode`
- 3-band EQ + 0–10 s crossfade (Web Audio gain ramp)

### Internationalization (31 locales)
- Server reads `composeApp/src/commonMain/composeResources/values*/strings.xml`
  on boot, parses each one, writes JSON to `web/public/locales/<bcp47>.json`
  → **single source of truth** with the Android/Desktop apps
- Browser auto-detects from `navigator.languages`; topbar globe button +
  Settings page picker for explicit override
- BCP-47 codes shipped: `en, ar, az, bg, ca, cs, de, el, en-GG, en-IO, en-LC,
  en-MS, en-PH, es, fa, fi, fr, hi, hu, id, it, he, ja, ko, nl, pl, pt, ro,
  ru, sv, th, tr, uk, vi, zh, zh-TW`
- RTL-aware (Arabic, Persian, Hebrew flip via `dir="rtl"`)
- HTML uses `data-i18n="key"` / `data-i18n-attr="placeholder|key"` /
  `data-i18n-fb="key"` annotations; missing keys fall back to English then to
  the literal markup

### Offline downloads
- IndexedDB-backed download manager with a dedicated **Downloads** page
- Streams `/api/stream/:videoId` chunk-by-chunk via Fetch reader, throttles DB
  writes to 10 Hz, persists final Blob keyed by `videoId`
- Live progress bar + bytes/total counters, pause / resume / cancel,
  paused-on-page-close auto-recovery
- Player **transparently switches to `blob:` URL** when an offline copy exists
  — true offline playback (works with the network tab disabled)
- Sidebar nav-badge shows the count of in-flight downloads
- Per-row offline indicator (download icon while pending, check icon when ready)
- Hotkey: `D` = download current track

### Multi-source lyrics chain
Server walks `LRCLIB → NetEase Cloud → Genius → KuGou → YouTube subtitles`
and returns the first synced result, otherwise the first plain-text result.
Each provider is isolated — a rate-limited backend can't sink the chain.
Pick a single source explicitly from the lyrics-panel dropdown or Settings.

### Player UX (kept from previous build)
- HTTP Range streaming — seek bar works mid-track
- Queue panel with auto-radio extension, drag-to-reorder
- MediaSession API — OS media keys, lock-screen controls, notification artwork
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
| `D`            | Download current track|
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
| GET    | `/api/lyrics?title&artist&album&duration&videoId&source`     | Multi-source lyrics chain                  |
| GET    | `/api/lyrics/sources`                                        | List of supported lyrics providers         |
| GET    | `/locales/_index.json`                                       | List of available BCP-47 locales           |
| GET    | `/locales/<bcp47>.json`                                      | Strings for one locale                     |
| POST   | `/api/cookies/refresh`                                       | Reload `cookies/` folder without restart   |

---

## Mapping to Parent KMP Project

| SakayoriMusic (KMP)             | This web port                                |
| ------------------------------- | -------------------------------------------- |
| `kotlinYtmusicScraper` service  | `ytmusic-api` npm package                    |
| Media3 (Android) / VLC (JVM)    | `yt-dlp` URL resolver → `<audio>`/`<video>`  |
| `lyricsService` (multi-source)  | `chainLyrics()` (LRCLIB+NetEase+Genius+KuGou+YT) |
| `composeResources/values*/`     | `web/public/locales/*.json` (auto-generated) |
| `FullscreenPlayer.kt`           | `index.html #fullPlayer` + styles            |
| `MiniPlayer.kt`                 | `footer.player`                              |
| `equalizer/` + `crossfade/`     | Web Audio EQ + gain ramps                    |
| `library/`                      | localStorage liked + recents + playlists     |
| `RecentlySongsScreen.kt`        | `#/recent`                                   |
| `EqualizerScreen.kt`            | Settings page                                |
| Backup / restore                | Settings → Export / Import                   |
| Offline download manager        | `Offline` IndexedDB module + `#/downloads`   |

---

## License

MIT — same as parent [SakayoriMusic](../README.md) project.
