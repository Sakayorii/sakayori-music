# SakayoriMusic — Web (Node.js port)

A web port of the [SakayoriMusic](../README.md) Kotlin Multiplatform / Compose
desktop & Android app, rewritten as a small Node.js + Express backend with a
vanilla-JS single-page frontend.

It keeps the spirit of the original app while running 100% in your browser:

| SakayoriMusic (KMP)              | This web port                         |
| -------------------------------- | ------------------------------------- |
| `kotlinYtmusicScraper` (service) | [`ytmusic-api`](https://www.npmjs.com/package/ytmusic-api) |
| Media3 / VLC playback            | Server-side `ytdl-core` proxy → `<audio>` |
| LRCLIB lyrics provider           | Same — proxied through `/api/lyrics`  |
| Compose UI                       | Vanilla HTML/CSS/JS SPA               |
| Koin DI                          | (none, single small file)             |

## What's implemented

- 🏠 **Home feed** with multiple shelves (Trending, Lo-fi, Workout, Anime OST)
- 🔎 **Search** for songs, videos, albums, artists, playlists
- ▶️ **Streaming** of any YouTube / YouTube Music video (audio-only, with HTTP Range so the seek bar works)
- 📜 **Queue** with auto-radio extension via YT Music's "up next"
- 🔀 **Shuffle**, 🔁 **Repeat all/one**
- 🎤 **Synced (LRC) lyrics** with auto-scrolling highlight — same provider as the desktop client (LRCLIB)
- ❤️ **Liked songs** persisted in `localStorage`
- ⌨️ **Keyboard shortcuts** matching the desktop app (`Space`, `←/→`, `↑/↓`, `M`, `L`, `S`, `R`, `?`)
- 📻 **MediaSession API** — OS media keys, lock-screen controls, notification artwork

## Run

```bash
cd web
npm install
npm start
```

Then open <http://localhost:3000>.

`npm run dev` enables Node's built-in `--watch` for auto-reload during development.

## Architecture

```
web/
├── server.js          Express server (search / song / stream / lyrics / home)
├── package.json
└── public/
    ├── index.html     SPA shell
    ├── styles.css     "Liquid Glass" cyan theme inspired by the desktop app
    └── app.js         All UI logic + player + shortcuts
```

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
| GET    | `/api/stream/:videoId`    | **Audio stream** (Range-aware proxy)     |
| GET    | `/api/lyrics?title&artist&album&duration` | LRCLIB lyrics (synced + plain) |

## Limitations vs. the native apps

The KMP project has hundreds of features (Discord RPC, Spotify Canvas, Wear OS
notifications, downloads, sleep timer, 31 languages, multi-source lyrics
fallback chain, mini player, etc.). This port focuses on the **core listening
experience** and is intentionally a single self-contained subproject so it can
run anywhere Node 18+ is installed.

If you want to contribute, the obvious next steps would be:

- **Sign-in / personalized feed** (port the OAuth flow from the KMP service)
- **Downloads** (pipe the stream proxy to disk + serve from cache)
- **Multi-source lyrics** (YouTube transcript + BetterLyrics fallbacks)
- **Spotify Canvas** background videos
- Better artist / album / playlist detail views

## License

MIT — same as the parent project.
