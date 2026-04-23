# SakayoriMusic — Web (Node.js port)

A web port of the [SakayoriMusic](../README.md) Kotlin Multiplatform / Compose
desktop & Android app, rewritten as a Node.js + Express backend with a
vanilla-JS single-page frontend.

It keeps the spirit of the original Android UI: home shelves, mini-player,
fullscreen "Now Playing" with a **spinning vinyl disc** (or video), blurred
album-art backdrop, slide-up lyrics + queue panels, hero-style detail pages
for albums / artists / playlists, local playlists, EQ + visualizer, and the
same keyboard shortcuts.

## Feature list

### UI
- 🏠 **Home feed** with multiple shelves (Trending, Lo-fi, Workout, Anime OST, Jazz, K-Pop)
- 🔎 **Search** with autocomplete suggestions (songs/videos/albums/artists/playlists)
- 🎤 **Artist** pages with top songs + albums + singles
- 💿 **Album / playlist** detail pages with hero header and Play All / Shuffle
- 📚 **Local playlists** — create, rename, delete, drag-to-reorder, add-to/remove-from
- ⭐ **Liked songs** persisted in `localStorage`
- 🕘 **Recently played** auto-history (up to 200 tracks)
- ⚙️ **Settings**: theme picker (5 colors + "from album art"), 3-band EQ, crossfade, sleep timer, lyrics source, visualizer style, backup / restore
- ⌨️ **Keyboard shortcuts** matching the desktop app (Space, ←/→, ↑/↓, M, L, S, R, F, V, Esc, ?)
- 📱 **Responsive** layout collapses sidebar on mobile

### Player
- 💿 **Spinning vinyl record** with album art at the center + swinging tonearm
- 🌫️ **Blurred album-art backdrop** behind the fullscreen player
- 🎬 **Video mode** — toggle to watch the YouTube video instead of just the audio (`/api/video/:videoId` proxy via yt-dlp)
- 📊 **Audio visualizer** (bars / waveform / ring) using Web Audio AnalyserNode
- 🎚️ **3-band Equalizer** (low / mid / high shelf+peaking BiQuad filters)
- 🌗 **Crossfade** between tracks (0-10 s, via Web Audio gain ramp)
- 🔀 **Shuffle / repeat all / repeat one**
- ▶️ **Streaming** of any YouTube / YouTube Music video with HTTP Range so the seek bar works
- 📜 **Queue** (slide-up panel) with auto-radio extension via YT Music's "up next", drag-to-reorder, and "Clear" button
- 🎤 **Synced (LRC) lyrics** with auto-scrolling highlight, multi-source: **LRCLIB → YouTube auto-captions** (via yt-dlp)
- 📻 **MediaSession API** integration — OS media keys, lock-screen controls, notification artwork
- 🔁 **Buffer indicator** under the seek bar
- 🌈 **Adaptive accent color** sampled from the album art

### Niceties
- Right-click / "⋯" **context menu** on every song with: Play Now / Add to Queue / Play Next / Like / Add to Playlist (with submenu) / Go to Album / Go to Artist / Open on YouTube
- **Hash-based router** — back/forward buttons in the topbar work, and links are bookmarkable

## Requirements

- **Node 18+** (we use the global `fetch`)
- **Python 3 + [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)** in your `PATH`
  — the only reliable way to get a working audio/video URL from YouTube in 2026.

  ```bash
  pip install -U yt-dlp
  ```

  If `yt-dlp` lives somewhere weird, set `YT_DLP=/path/to/yt-dlp` before running the server.

### YouTube cookies (recommended)

YouTube increasingly throws **"Sign in to confirm you're not a bot"** at
servers that aren't logged in.  To bypass that, give yt-dlp some cookies
from a logged-in browser session.  Four options, in priority order:

1. ⭐ **Drop a folder of cookies in `web/cookies/`** — the recommended
   setup.  Throw in as many `.txt` files as you have, named whatever you
   want (`account1.txt`, `burner.txt`, etc.).  The server **rotates**
   through them automatically: when one starts getting "Sign in to
   confirm" errors, it's marked dead and the next one is used for the rest
   of the session.  When all of them die, the pool is reset.  Each file is
   **auto-sanitized** — any prefix garbage (e.g. account-info headers from
   "Simple Checker") is stripped and the proper Netscape header is
   prepended, so you can paste raw cookie dumps without editing them.

   ```
   web/
     cookies/
       acct1.txt
       acct2.txt
       acct3-throwaway.txt
       ...
   ```

   GET `/api/health` shows the current pool and which entries are
   considered dead.  POST `/api/cookies/refresh` reloads the folder
   without restarting the server (useful after dropping a new file in).

2. **A single cookies.txt next to `web/`** — auto-detected if named
   `cookies.txt`, `youtube-cookies.txt`, or `yt-cookies.txt`.

3. **`YT_COOKIES=/full/path/to/cookies.txt`** env var (single file, no
   rotation).

4. **`YT_COOKIES_BROWSER=chrome`** env var pulls cookies live from your
   installed browser (chrome / firefox / edge / brave / vivaldi / safari
   / opera / chromium).  Most browsers must be **closed** while yt-dlp
   reads the cookie DB.

To export a cookies.txt: install the
["Get cookies.txt LOCALLY"](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc)
extension, open <https://www.youtube.com> while logged in, click the
extension, save the file into `web/cookies/`.



## Run

```bash
cd web
npm install
npm start         # production
npm run dev       # auto-reload via node --watch
```

Then open <http://localhost:3000>.

## Architecture

```
              ┌────────────────────────────────────────┐
   Browser →  │  /api/stream/:videoId  (audio mp4)    │
              │  /api/video/:videoId   (video+audio mp4)
              │      ↳ yt-dlp -g  → real URL          │   (cached 5 min)
              │      ↳ fetch(url, Range:…)            │
              │      ↳ pipe to <audio> / <video>      │
              └────────────────────────────────────────┘

              ┌────────────────────────────────────────┐
   Browser →  │  /api/search /api/home /api/artist…   │  ← ytmusic-api
              │  /api/lyrics  (LRCLIB + YT subs)      │  ← LRCLIB + yt-dlp
              └────────────────────────────────────────┘

      Browser-side audio chain:
          <audio>/<video> → MediaElementSource
              → BiQuad (low) → BiQuad (mid) → BiQuad (high)
              → Gain (crossfade) → Analyser (viz) → Destination
```

### Endpoints exposed by `server.js`

| Method | Path                                       | Description                              |
| ------ | ------------------------------------------ | ---------------------------------------- |
| GET    | `/api/health`                              | Liveness probe                           |
| GET    | `/api/home`                                | Curated home shelves                     |
| GET    | `/api/search?q&type`                       | Search (`songs`/`videos`/`albums`/...)   |
| GET    | `/api/suggest?q`                           | Autocomplete suggestions                 |
| GET    | `/api/song/:videoId`                       | Track metadata                           |
| GET    | `/api/up-next/:videoId`                    | Auto-generated radio queue               |
| GET    | `/api/artist/:id`                          | Artist + top songs + albums + singles    |
| GET    | `/api/album/:id`                           | Album contents                           |
| GET    | `/api/playlist/:id`                        | Playlist contents                        |
| GET    | `/api/stream/:videoId`                     | **Audio stream** (Range-aware proxy)     |
| GET    | `/api/video/:videoId`                      | **Video stream** (combined-mp4)          |
| GET    | `/api/lyrics?title&artist&album&duration&videoId&source` | LRCLIB + YouTube subs (synced + plain) |

## Mapping vs. the original KMP project

| SakayoriMusic (KMP)              | This web port                                |
| -------------------------------- | -------------------------------------------- |
| `kotlinYtmusicScraper` service   | `ytmusic-api` (search/album/artist/playlist) |
| Media3 / VLC playback            | `yt-dlp` URL resolver → `<audio>`/`<video>`  |
| `lyricsService` (LRCLIB)         | `/api/lyrics?source=lrclib`                  |
| `lyricsService` (YT transcript)  | `/api/lyrics?source=youtube` (yt-dlp subs)   |
| `FullscreenPlayer.kt`            | `index.html#fullPlayer` + `styles.css`       |
| `MiniPlayer.kt`                  | `footer.player`                              |
| `equalizer/` + `crossfade/`      | Web Audio EQ + gain                          |
| `library/`                       | localStorage liked + recents + playlists     |
| `RecentlySongsScreen.kt`         | `#/recent`                                   |
| `EqualizerScreen.kt`             | Settings page                                |
| Backup / restore                 | Settings → Export / Import                   |

## Limitations vs. the native apps

The original KMP project still has features that aren't ported here (Discord
RPC, Spotify Canvas, Wear OS notifications, downloads, multi-source lyrics
chain beyond LRCLIB+YT, 31 languages, OAuth sign-in / personalized feed).
This port focuses on the **listening experience** in a self-contained Node
project so it runs anywhere with Node 18+ and Python+yt-dlp.

## License

MIT — same as the parent project.
