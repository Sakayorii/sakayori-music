# SakayoriMusic — Web (Node.js port)

A web port of the [SakayoriMusic](../README.md) Kotlin Multiplatform / Compose
desktop & Android app, rewritten as a small Node.js + Express backend with a
vanilla-JS single-page frontend.

It keeps the spirit of the original Android UI: home shelves, mini-player,
fullscreen "Now Playing" with a **spinning vinyl disc**, blurred album-art
backdrop, slide-up lyrics + queue panels, and the same keyboard shortcuts.

## What's implemented

- 🏠 **Home feed** with shelves (Trending, Lo-fi, Workout, Anime OST)
- 🔎 **Search** for songs, videos, albums, artists, playlists
- 💿 **Android-style fullscreen player** with a spinning vinyl record, swinging tonearm, and blurred album-art backdrop
- ▶️ **Streaming** of any YouTube / YouTube Music video (audio-only, with HTTP Range so the seek bar works)
- 📜 **Queue** (slide-up panel) with auto-radio extension via YT Music's "up next"
- 🔀 **Shuffle** / 🔁 **Repeat all/one**
- 🎤 **Synced (LRC) lyrics** with auto-scrolling highlight — same provider as the desktop client (LRCLIB)
- ❤️ **Liked songs** persisted in `localStorage`
- ⌨️ **Keyboard shortcuts** matching the desktop app (`Space`, `←/→`, `↑/↓`, `M`, `L`, `S`, `R`, `F`, `?`)
- 📻 **MediaSession API** — OS media keys, lock-screen controls, notification artwork

## Requirements

- **Node 18+** (we use the global `fetch`)
- **Python 3 + [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)** in your `PATH` —
  it's the only reliable way to get a working audio URL from YouTube in 2026
  (since YouTube enforces SABR / UMP / PoToken on pure-JS scrapers).

  Install it once:
  ```bash
  pip install -U yt-dlp
  ```

  If `yt-dlp` lives somewhere weird, set `YT_DLP=/path/to/yt-dlp` before running the server.

## Run

```bash
cd web
npm install
npm start
```

Then open <http://localhost:3000>.

`npm run dev` enables Node's built-in `--watch` for auto-reload during development.

## How it works

```
              ┌────────────────────────────┐
   Browser →  │  /api/stream/:videoId      │
              │  ↳ yt-dlp -g  → real URL   │   (cached 5 min)
              │  ↳ fetch(url, Range:…)     │
              │  ↳ pipe to <audio>         │
              └────────────────────────────┘

              ┌────────────────────────────┐
   Browser →  │  /api/search /api/home …   │  ← ytmusic-api (Innertube)
              │  /api/lyrics               │  ← LRCLIB
              └────────────────────────────┘
```

The Compose Multiplatform desktop app uses `kotlinYtmusicScraper` + an
in-process WebView for player JS; this web port shells out to `yt-dlp` because
it's the only thing currently winning the cat-and-mouse game with YouTube.

## Mapping vs. the original KMP project

| SakayoriMusic (KMP)              | This web port                         |
| -------------------------------- | ------------------------------------- |
| `kotlinYtmusicScraper` (service) | [`ytmusic-api`](https://www.npmjs.com/package/ytmusic-api) |
| Media3 / VLC playback            | `yt-dlp` URL resolver → server-side proxy → `<audio>` |
| LRCLIB lyrics provider           | Same — proxied through `/api/lyrics`  |
| Compose `FullscreenPlayer.kt`    | `index.html#fullPlayer` + `styles.css` |
| Compose `MiniPlayer.kt`          | `footer.player` (mini bar)            |
| Koin DI                          | (none, single small file)             |

### Endpoints exposed by `server.js`

| Method | Path                      | Description                              |
| ------ | ------------------------- | ---------------------------------------- |
| GET    | `/api/health`             | Liveness probe                           |
| GET    | `/api/home`               | Curated home shelves                     |
| GET    | `/api/search?q&type`      | Search (`songs`/`videos`/`albums`/...)   |
| GET    | `/api/song/:videoId`      | Track metadata                           |
| GET    | `/api/up-next/:videoId`   | Auto-generated radio queue               |
| GET    | `/api/playlist/:id`       | Playlist contents                        |
| GET    | `/api/album/:id`          | Album contents                           |
| GET    | `/api/stream/:videoId`    | **Audio stream** (Range-aware proxy via yt-dlp) |
| GET    | `/api/lyrics?title&artist&album&duration` | LRCLIB lyrics (synced + plain) |

## Limitations vs. the native apps

The KMP project has hundreds of features (Discord RPC, Spotify Canvas, Wear OS
notifications, downloads, sleep timer, 31 languages, multi-source lyrics
fallback chain, etc.). This port focuses on the **core listening experience**
and is intentionally a single self-contained subproject so it can run anywhere
Node 18+ and Python+yt-dlp are installed.

If you want to contribute, obvious next steps:

- **Sign-in / personalized feed** (port the OAuth flow from the KMP service)
- **Downloads** (cache the stream proxy responses to disk)
- **Multi-source lyrics** (YouTube transcript + BetterLyrics fallbacks)
- **Spotify Canvas** background videos
- Better artist / album / playlist detail views

## License

MIT — same as the parent project.
