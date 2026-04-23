// SakayoriMusic Web — full SPA
// Recreates the Android/Compose app's core experience for the browser.
//
// Architecture overview:
//   • Hash-based router (#/home, #/search?q=, #/album/:id, …)
//   • Web Audio chain:
//        <audio> → MediaElementSource → 3-band EQ → Gain (crossfade) → Analyser → Destination
//        <video>  shares the same chain when video mode is on
//   • localStorage persistence for liked, recents, playlists, settings
//   • MediaSession integration for OS media keys
//   • Right-click / "..." context menu on song rows
//   • Drag-to-reorder queue
//   • Video mode (combined-mp4 from /api/video/:videoId)

// =========================================================================
// State
// =========================================================================
const State = {
    queue: [],
    queueIndex: -1,
    shuffle: false,
    repeat: "off", // off | all | one
    history: [],
    liked: new Set(JSON.parse(localStorage.getItem("liked") || "[]")),
    likedMeta: JSON.parse(localStorage.getItem("likedMeta") || "{}"), // videoId -> song
    playlists: JSON.parse(localStorage.getItem("playlists") || "{}"), // id -> { name, songs[] }
    recents: JSON.parse(localStorage.getItem("recents") || "[]"),     // [{song, when}]
    lyrics: null,
    lastVolume: 0.8,
    sleepTimerId: null,
    mode: "audio", // audio | video
    settings: JSON.parse(localStorage.getItem("settings") || "{}"),
    forwardStack: [],
};

// Default settings
const DEFAULTS = {
    crossfade: 0,            // seconds
    theme: "cyan",
    eq: [0, 0, 0],           // dB low/mid/high
    visualizer: "bars",
    autoLyrics: true,
    sleepMin: 0,
    lyricsSource: "auto",
};
for (const k in DEFAULTS) {
    if (!(k in State.settings)) State.settings[k] = DEFAULTS[k];
}
function saveSettings() {
    localStorage.setItem("settings", JSON.stringify(State.settings));
}

// =========================================================================
// DOM
// =========================================================================
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

const audio = $("#audio");
const video = $("#video");
const view = $("#view");

const npArt = $("#npArt");
const npTitle = $("#npTitle");
const npArtist = $("#npArtist");
const playBtn = $("#playBtn");
const prevBtn = $("#prevBtn");
const nextBtn = $("#nextBtn");
const shuffleBtn = $("#shuffleBtn");
const repeatBtn = $("#repeatBtn");
const likeBtn = $("#likeBtn");
const muteBtn = $("#muteBtn");
const expandBtn = $("#expandBtn");
const videoModeBtn = $("#videoModeBtn");
const seek = $("#seek");
const bufferBar = $("#bufferBar");
const curTime = $("#curTime");
const durTime = $("#durTime");
const vol = $("#vol");

const fullPlayer = $("#fullPlayer");
const fpBackdrop = $("#fpBackdrop");
const fpClose = $("#fpClose");
const fpTitle = $("#fpTitle");
const fpArtist = $("#fpArtist");
const fpSeek = $("#fpSeek");
const fpCurTime = $("#fpCurTime");
const fpDurTime = $("#fpDurTime");
const fpPlay = $("#fpPlay");
const fpPrev = $("#fpPrev");
const fpNext = $("#fpNext");
const fpShuffle = $("#fpShuffle");
const fpRepeat = $("#fpRepeat");
const fpLike = $("#fpLike");
const fpLyricsToggle = $("#fpLyricsToggle");
const fpQueueBtn = $("#fpQueue");
const fpVideoToggle = $("#fpVideoToggle");
const fpVizToggle = $("#fpVizToggle");
const fpLyricsPanel = $("#fpLyrics");
const fpLyricsInner = $("#fpLyricsInner");
const fpLyricsClose = $("#fpLyricsClose");
const lyricsSourceSelect = $("#lyricsSourceSelect");
const fpQueuePanel = $("#fpQueuePanel");
const fpQueueList = $("#fpQueueList");
const fpQueueClose = $("#fpQueueClose");
const clearQueueBtn = $("#clearQueueBtn");
const fpStage = $("#fpStage");
const vinylWrap = $("#vinylWrap");
const vinyl = $("#vinyl");
const vinylArt = $("#vinylArt");
const tonearm = $("#tonearm");
const visualizerCanvas = $("#visualizer");

const toast = $("#toast");
const ctxMenu = $("#ctxMenu");
const modal = $("#modal");
const modalCard = $("#modalCard");
const sidebarPlaylists = $("#sidebarPlaylists");
const newPlaylistBtn = $("#newPlaylistBtn");
const suggestList = $("#suggestList");
const searchInput = $("#searchInput");
const searchTypeSel = $("#searchType");
const searchForm = $("#searchForm");
const backBtn = $("#backBtn");
const fwdBtn = $("#fwdBtn");

// =========================================================================
// Utilities
// =========================================================================
const fmt = (s) => {
    if (!Number.isFinite(s)) return "0:00";
    s = Math.max(0, Math.floor(s));
    const m = Math.floor(s / 60);
    const sec = String(s % 60).padStart(2, "0");
    return `${m}:${sec}`;
};
const escapeHtml = (s) =>
    String(s ?? "").replace(/[&<>"]/g, (c) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;",
    }[c]));
const artistsText = (a) =>
    Array.isArray(a) ? a.map((x) => x.name).filter(Boolean).join(", ") : "";
const showToast = (msg) => {
    toast.textContent = msg;
    toast.classList.remove("hidden");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => toast.classList.add("hidden"), 1800);
};
function persistLiked() {
    localStorage.setItem("liked", JSON.stringify([...State.liked]));
    localStorage.setItem("likedMeta", JSON.stringify(State.likedMeta));
}
function persistPlaylists() {
    localStorage.setItem("playlists", JSON.stringify(State.playlists));
}
function persistRecents() {
    localStorage.setItem("recents", JSON.stringify(State.recents));
}

function extractAccentFromImage(url) {
    return new Promise((resolve) => {
        if (!url) return resolve(null);
        const img = new Image();
        img.crossOrigin = "anonymous";
        img.onload = () => {
            try {
                const c = document.createElement("canvas");
                c.width = c.height = 8;
                const ctx = c.getContext("2d");
                ctx.drawImage(img, 0, 0, 8, 8);
                const px = ctx.getImageData(0, 0, 8, 8).data;
                let r = 0, g = 0, b = 0, n = 0;
                for (let i = 0; i < px.length; i += 4) {
                    r += px[i]; g += px[i + 1]; b += px[i + 2]; n++;
                }
                resolve(`rgb(${(r / n) | 0}, ${(g / n) | 0}, ${(b / n) | 0})`);
            } catch { resolve(null); }
        };
        img.onerror = () => resolve(null);
        img.src = url;
    });
}

// =========================================================================
// API
// =========================================================================
const api = {
    home: () => fetch("/api/home").then((r) => r.json()),
    search: (q, type = "songs") =>
        fetch(`/api/search?q=${encodeURIComponent(q)}&type=${type}`).then((r) => r.json()),
    suggest: (q) => fetch(`/api/suggest?q=${encodeURIComponent(q)}`).then((r) => r.json()),
    upNext: (videoId) => fetch(`/api/up-next/${videoId}`).then((r) => r.json()),
    artist: (id) => fetch(`/api/artist/${id}`).then((r) => r.json()),
    album: (id) => fetch(`/api/album/${id}`).then((r) => r.json()),
    playlist: (id) => fetch(`/api/playlist/${id}`).then((r) => r.json()),
    lyrics: (q) => fetch(`/api/lyrics?${new URLSearchParams(q)}`).then((r) => r.json()),
};

// =========================================================================
// Web Audio chain (EQ + visualizer + crossfade gain)
// =========================================================================
let audioCtx = null;
let audioSrcNode = null;
let videoSrcNode = null;
let eqLow, eqMid, eqHigh, masterGain, analyser;

function ensureAudioGraph() {
    if (audioCtx) return;
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();

    eqLow = audioCtx.createBiquadFilter();
    eqLow.type = "lowshelf"; eqLow.frequency.value = 320;
    eqMid = audioCtx.createBiquadFilter();
    eqMid.type = "peaking"; eqMid.frequency.value = 1000; eqMid.Q.value = 1;
    eqHigh = audioCtx.createBiquadFilter();
    eqHigh.type = "highshelf"; eqHigh.frequency.value = 3200;

    masterGain = audioCtx.createGain();
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 1024;

    audioSrcNode = audioCtx.createMediaElementSource(audio);
    videoSrcNode = audioCtx.createMediaElementSource(video);

    [audioSrcNode, videoSrcNode].forEach((src) => {
        src.connect(eqLow);
    });
    eqLow.connect(eqMid);
    eqMid.connect(eqHigh);
    eqHigh.connect(masterGain);
    masterGain.connect(analyser);
    analyser.connect(audioCtx.destination);

    applyEqFromSettings();
}

function applyEqFromSettings() {
    if (!eqLow) return;
    const [lo, mi, hi] = State.settings.eq;
    eqLow.gain.value = lo;
    eqMid.gain.value = mi;
    eqHigh.gain.value = hi;
}

// =========================================================================
// Router
// =========================================================================
window.addEventListener("hashchange", () => router(false));
function navigate(path, push = true) {
    if (push && location.hash !== `#${path}`) {
        location.hash = path;
    } else {
        router(false);
    }
}
backBtn.addEventListener("click", () => history.back());
fwdBtn.addEventListener("click", () => history.forward());

async function router(initial = true) {
    const hash = location.hash || "#/home";
    const [_, route, ...rest] = hash.slice(1).split("/");
    setActiveSidebar(`/${route}`);
    view.scrollTop = 0;

    try {
        switch (route) {
            case "home": case "": return renderHome();
            case "search": return renderSearch(decodeURIComponent(rest.join("/")));
            case "library": return renderLibrary();
            case "recent": return renderRecents();
            case "playlists": return renderLocalPlaylists();
            case "playlist": return renderLocalPlaylist(rest.join("/"));
            case "ytplaylist": return renderRemotePlaylist(rest.join("/"));
            case "album": return renderAlbum(rest.join("/"));
            case "artist": return renderArtist(rest.join("/"));
            case "settings": return renderSettings();
            case "shortcuts": return renderShortcuts();
            default: return renderHome();
        }
    } catch (e) {
        view.innerHTML = `<div class="empty">Error: ${escapeHtml(e.message)}</div>`;
    }
}

function setActiveSidebar(path) {
    $$(".nav-item").forEach((el) => {
        const href = el.getAttribute("href");
        el.classList.toggle("active", href === `#${path}`);
    });
}

// =========================================================================
// Skeleton card helper
// =========================================================================
function skeletonRow(n = 8) {
    return Array.from({ length: n }, () =>
        `<div class="card"><div class="skeleton skel-card"></div>
        <div class="t skeleton" style="height:14px;margin-top:10px"></div>
        <div class="a skeleton" style="height:11px;margin-top:6px"></div></div>`
    ).join("");
}

// =========================================================================
// Views
// =========================================================================
async function renderHome() {
    view.innerHTML = `
        <h1 class="section-title">Welcome back</h1>
        <div id="shelves">
            ${[1, 2, 3].map(() => `
                <section class="shelf">
                    <h2 class="skeleton" style="width:200px;height:18px"></h2>
                    <div class="shelf-row">${skeletonRow(6)}</div>
                </section>`).join("")}
        </div>`;
    const { shelves } = await api.home();
    const map = new Map();
    shelves.forEach((sh) => sh.items.forEach((it) => map.set(it.videoId, it)));
    $("#shelves").innerHTML = shelves
        .filter((s) => s.items?.length)
        .map((shelf) => `
            <section class="shelf">
                <div class="shelf-head"><h2>${escapeHtml(shelf.title)}</h2></div>
                <div class="shelf-row">
                    ${shelf.items.map((s) => songCard(s)).join("")}
                </div>
            </section>`).join("");
    wireCards(map);
}

function songCard(s) {
    return `
        <div class="card" data-vid="${s.videoId}">
            <img loading="lazy" src="${s.thumbnail?.url || ""}" alt=""/>
            <button class="card-play" title="Play">▶</button>
            <div class="t">${escapeHtml(s.name)}</div>
            <div class="a">${escapeHtml(artistsText(s.artists))}</div>
        </div>`;
}

function wireCards(metaMap) {
    $$(".card[data-vid]").forEach((el) => {
        const vid = el.dataset.vid;
        const meta = metaMap.get(vid);
        const playBtn = el.querySelector(".card-play");
        el.addEventListener("click", (e) => {
            if (e.target === playBtn) return;
            const shelf = el.closest(".shelf, .grid");
            if (!shelf) { playQueue([meta], 0); return; }
            const cards = $$(".card[data-vid]", shelf);
            const queue = cards.map((c) => metaMap.get(c.dataset.vid) || { videoId: c.dataset.vid });
            playQueue(queue, queue.findIndex((s) => s.videoId === vid));
        });
        playBtn?.addEventListener("click", (e) => {
            e.stopPropagation();
            playQueue([meta], 0);
        });
        el.addEventListener("contextmenu", (e) => {
            e.preventDefault();
            openContextMenu(e, meta);
        });
    });
}

async function renderSearch(q) {
    if (!q) {
        view.innerHTML = `<h1 class="section-title">Search</h1>
            <div class="empty">Type something in the search bar above.</div>`;
        return;
    }
    searchInput.value = q;
    view.innerHTML = `<h1 class="section-title">Searching “${escapeHtml(q)}”…</h1>
        <div class="list">${Array.from({ length: 6 }, () => `
            <div class="row"><div class="num skeleton" style="height:14px"></div>
            <div class="skeleton" style="width:48px;height:48px;border-radius:6px"></div>
            <div class="meta"><div class="meta-t skeleton" style="height:14px;width:60%"></div>
            <div class="meta-a skeleton" style="height:11px;width:40%;margin-top:4px"></div></div>
            <div></div><div class="dur skeleton" style="height:11px;width:30px"></div><div></div></div>
        `).join("")}</div>`;

    const type = searchTypeSel.value;
    const { results } = await api.search(q, type);
    if (!results.length) { view.innerHTML = `<div class="empty">No results.</div>`; return; }
    const songish = results.filter((r) => r.videoId);
    const cardish = results.filter((r) => !r.videoId);

    let html = `<h1 class="section-title">Results for “${escapeHtml(q)}”</h1>`;

    if (cardish.length) {
        html += `<div class="grid">${cardish.map((r) => `
            <div class="card ${r.type === "ARTIST" ? "artist" : ""}" data-${r.type.toLowerCase()}-id="${r.id}">
                <img loading="lazy" src="${r.thumbnail?.url || ""}" alt=""/>
                <div class="t">${escapeHtml(r.name)}</div>
                <div class="a">${escapeHtml(r.type)}${r.artist ? " · " + escapeHtml(r.artist) : ""}</div>
            </div>`).join("")}</div>`;
    }
    if (songish.length) {
        html += `<h2 class="section-title" style="font-size:18px;margin-top:24px">Songs</h2>
            <div class="list" id="searchList"></div>`;
    }

    view.innerHTML = html;

    // Wire the card grid to navigate to detail pages
    $$(".card[data-album-id]").forEach((c) => c.addEventListener("click", () => navigate(`/album/${c.dataset.albumId}`)));
    $$(".card[data-artist-id]").forEach((c) => c.addEventListener("click", () => navigate(`/artist/${c.dataset.artistId}`)));
    $$(".card[data-playlist-id]").forEach((c) => c.addEventListener("click", () => navigate(`/ytplaylist/${c.dataset.playlistId}`)));

    if (songish.length) {
        const list = $("#searchList");
        songish.forEach((s, i) => list.appendChild(songRow(s, i, songish)));
    }
}

function songRow(s, idx, queue, opts = {}) {
    const div = document.createElement("div");
    div.className = "row";
    div.dataset.vid = s.videoId;
    if (opts.draggable) {
        div.draggable = true;
        div.dataset.idx = idx;
    }
    if (
        State.queueIndex >= 0 &&
        State.queue[State.queueIndex]?.videoId === s.videoId
    ) {
        div.classList.add("playing");
    }
    div.innerHTML = `
        <div class="num">${idx + 1}</div>
        <img loading="lazy" src="${s.thumbnail?.url || ""}" alt=""/>
        <div class="meta">
            <div class="meta-t">${escapeHtml(s.name)}</div>
            <div class="meta-a">${escapeHtml(artistsText(s.artists))}</div>
        </div>
        <div class="album-col">${escapeHtml(s.album?.name || "")}</div>
        <div class="dur">${s.duration ? fmt(s.duration) : ""}</div>
        <div class="row-actions">
            <button class="icon-btn" title="More" data-action="more">⋯</button>
        </div>
    `;
    div.addEventListener("click", (e) => {
        if (e.target.closest("button")) return;
        if (opts.onPlay) opts.onPlay(idx);
        else playQueue(queue, idx);
    });
    div.querySelector('[data-action="more"]').addEventListener("click", (e) => {
        e.stopPropagation();
        const r = e.currentTarget.getBoundingClientRect();
        openContextMenu({ clientX: r.left, clientY: r.bottom }, s, opts);
    });
    div.addEventListener("contextmenu", (e) => {
        e.preventDefault();
        openContextMenu(e, s, opts);
    });

    if (opts.draggable) wireDrag(div, queue, opts);
    return div;
}

// Hero detail (album / playlist / artist)
function renderHero({ kind, name, subtitle, image, songs, isArtist, onPlayAll }) {
    return `
        <div class="hero">
            <div class="hero-bg" style="background-image:url('${image || ''}')"></div>
            <img class="${isArtist ? 'artist' : ''}" src="${image || ''}" alt=""/>
            <div>
                <div class="crumbs">${escapeHtml(kind)}</div>
                <h1>${escapeHtml(name)}</h1>
                <div class="meta">${escapeHtml(subtitle)}</div>
                <div class="actions">
                    <button class="btn-pill" id="heroPlay">▶ Play All</button>
                    <button class="btn-ghost" id="heroShuffle">🔀 Shuffle</button>
                    ${songs.length ? `<button class="btn-ghost" id="heroAddQueue">＋ Add to queue</button>` : ""}
                </div>
            </div>
        </div>`;
}

async function renderAlbum(id) {
    view.innerHTML = `<div class="empty">Loading…</div>`;
    const a = await api.album(id);
    view.innerHTML = renderHero({
        kind: `Album · ${a.year ?? ""}`.trim(),
        name: a.name,
        subtitle: `${a.artist} · ${a.songs.length} tracks`,
        image: a.thumbnail?.url,
        songs: a.songs,
    }) + `<div class="list" id="songList"></div>`;
    const list = $("#songList");
    a.songs.forEach((s, i) => list.appendChild(songRow(s, i, a.songs)));
    wireHero(a.songs);
}

async function renderRemotePlaylist(id) {
    view.innerHTML = `<div class="empty">Loading…</div>`;
    const p = await api.playlist(id);
    view.innerHTML = renderHero({
        kind: "Playlist",
        name: p.name,
        subtitle: `${p.songs.length} tracks`,
        image: p.thumbnail?.url,
        songs: p.songs,
    }) + `<div class="list" id="songList"></div>`;
    const list = $("#songList");
    p.songs.forEach((s, i) => list.appendChild(songRow(s, i, p.songs)));
    wireHero(p.songs);
}

async function renderArtist(id) {
    view.innerHTML = `<div class="empty">Loading…</div>`;
    const a = await api.artist(id);
    let html = renderHero({
        kind: "Artist",
        name: a.name,
        subtitle: a.subscribers ? `${a.subscribers} subscribers` : "",
        image: a.thumbnail?.url,
        songs: a.topSongs,
        isArtist: true,
    });

    if (a.topSongs?.length) {
        html += `<h2 class="section-title" style="font-size:18px;margin-top:8px">Top Songs</h2>
            <div class="list" id="topSongs"></div>`;
    }
    if (a.albums?.length) {
        html += `<h2 class="section-title" style="font-size:18px;margin-top:24px">Albums</h2>
            <div class="grid">${a.albums.map((al) => `
                <div class="card" data-album-id="${al.id}">
                    <img loading="lazy" src="${al.thumbnail?.url || ""}" alt=""/>
                    <div class="t">${escapeHtml(al.name)}</div>
                    <div class="a">${al.year || ""}</div>
                </div>`).join("")}</div>`;
    }
    if (a.singles?.length) {
        html += `<h2 class="section-title" style="font-size:18px;margin-top:24px">Singles</h2>
            <div class="grid">${a.singles.map((al) => `
                <div class="card" data-album-id="${al.id}">
                    <img loading="lazy" src="${al.thumbnail?.url || ""}" alt=""/>
                    <div class="t">${escapeHtml(al.name)}</div>
                    <div class="a">${al.year || ""}</div>
                </div>`).join("")}</div>`;
    }

    view.innerHTML = html;
    if (a.topSongs?.length) {
        const list = $("#topSongs");
        a.topSongs.forEach((s, i) => list.appendChild(songRow(s, i, a.topSongs)));
    }
    $$(".card[data-album-id]").forEach((c) =>
        c.addEventListener("click", () => navigate(`/album/${c.dataset.albumId}`))
    );
    wireHero(a.topSongs || []);
}

function wireHero(songs) {
    $("#heroPlay")?.addEventListener("click", () => playQueue(songs, 0));
    $("#heroShuffle")?.addEventListener("click", () => {
        State.shuffle = true; updateShuffleUi();
        playQueue([...songs].sort(() => Math.random() - 0.5), 0);
    });
    $("#heroAddQueue")?.addEventListener("click", () => {
        State.queue = [...State.queue, ...songs];
        showToast(`Added ${songs.length} tracks to queue`);
        renderFpQueue();
    });
}

function renderLibrary() {
    const items = [...State.liked].map(
        (vid) => State.likedMeta[vid] || { videoId: vid, name: vid, artists: [] }
    );
    if (!items.length) {
        view.innerHTML = `<h1 class="section-title">Liked Songs</h1>
            <div class="empty">You haven't liked anything yet. Press <kbd>L</kbd> while a song plays.</div>`;
        return;
    }
    view.innerHTML = `<h1 class="section-title">Liked Songs <span class="muted">· ${items.length}</span></h1>
        <div class="actions" style="margin-bottom:12px">
            <button class="btn-pill" id="heroPlay">▶ Play All</button>
            <button class="btn-ghost" id="heroShuffle">🔀 Shuffle</button>
        </div>
        <div class="list" id="libList"></div>`;
    const list = $("#libList");
    items.forEach((s, i) => list.appendChild(songRow(s, i, items)));
    wireHero(items);
}

function renderRecents() {
    const items = State.recents.slice(0, 100).map((r) => r.song);
    if (!items.length) {
        view.innerHTML = `<h1 class="section-title">Recently Played</h1>
            <div class="empty">Nothing played yet.</div>`;
        return;
    }
    view.innerHTML = `<h1 class="section-title">Recently Played</h1>
        <div class="actions" style="margin-bottom:12px">
            <button class="btn-pill" id="heroPlay">▶ Play All</button>
            <button class="btn-ghost" id="heroShuffle">🔀 Shuffle</button>
        </div>
        <div class="list" id="recentList"></div>`;
    const list = $("#recentList");
    items.forEach((s, i) => list.appendChild(songRow(s, i, items)));
    wireHero(items);
}

function renderLocalPlaylists() {
    const ids = Object.keys(State.playlists);
    view.innerHTML = `<h1 class="section-title">My Playlists</h1>
        ${ids.length === 0
            ? `<div class="empty">No playlists yet. Click "+ New playlist" in the sidebar.</div>`
            : `<div class="grid">${ids.map((id) => {
                const p = State.playlists[id];
                const cover = p.songs[0]?.thumbnail?.url || "";
                return `<div class="card" data-pl="${id}">
                    <img loading="lazy" src="${cover}" alt=""/>
                    <div class="t">${escapeHtml(p.name)}</div>
                    <div class="a">${p.songs.length} tracks</div>
                </div>`;
            }).join("")}</div>`}`;
    $$(".card[data-pl]").forEach((c) =>
        c.addEventListener("click", () => navigate(`/playlist/${c.dataset.pl}`))
    );
}

function renderLocalPlaylist(id) {
    const p = State.playlists[id];
    if (!p) {
        view.innerHTML = `<div class="empty">Playlist not found.</div>`;
        return;
    }
    const cover = p.songs[0]?.thumbnail?.url || "";
    view.innerHTML = renderHero({
        kind: "Local Playlist",
        name: p.name,
        subtitle: `${p.songs.length} tracks`,
        image: cover,
        songs: p.songs,
    }) + `
        <div class="actions" style="margin: -10px 0 14px">
            <button class="btn-ghost" id="renamePlaylistBtn">✏ Rename</button>
            <button class="btn-ghost" id="deletePlaylistBtn" style="color:var(--danger)">🗑 Delete</button>
        </div>
        <div class="list" id="songList"></div>`;
    const list = $("#songList");
    p.songs.forEach((s, i) =>
        list.appendChild(songRow(s, i, p.songs, {
            playlistId: id,
            draggable: true,
            onReorder: (from, to) => {
                const [moved] = p.songs.splice(from, 1);
                p.songs.splice(to, 0, moved);
                persistPlaylists();
                renderLocalPlaylist(id);
            },
        }))
    );
    wireHero(p.songs);
    $("#renamePlaylistBtn").addEventListener("click", () => promptRenamePlaylist(id));
    $("#deletePlaylistBtn").addEventListener("click", () => {
        if (confirm(`Delete "${p.name}"?`)) {
            delete State.playlists[id];
            persistPlaylists();
            renderSidebarPlaylists();
            navigate("/playlists");
        }
    });
}

function renderShortcuts() {
    view.innerHTML = `<h1 class="section-title">Keyboard Shortcuts</h1>
        <table class="shortcuts" style="border-collapse:collapse;font-size:14px">
            ${[
            ["Space", "Play / Pause"],
            ["←", "Previous track"],
            ["→", "Next track"],
            ["↑", "Volume up"],
            ["↓", "Volume down"],
            ["M", "Mute"],
            ["L", "Like / Unlike"],
            ["S", "Shuffle"],
            ["R", "Cycle repeat"],
            ["F", "Toggle fullscreen player"],
            ["V", "Toggle video mode"],
            ["Esc", "Close fullscreen / panels"],
            ["?", "This page"],
        ].map(([k, d]) =>
            `<tr><td style="padding:6px 12px"><kbd>${k}</kbd></td>
                <td style="padding:6px 12px">${d}</td></tr>`).join("")}
        </table>`;
}

// =========================================================================
// Settings page
// =========================================================================
function renderSettings() {
    const s = State.settings;
    view.innerHTML = `
        <div class="settings">
            <h1>Settings</h1>

            <div class="setting">
                <div>
                    <div class="setting-title">Theme</div>
                    <div class="setting-desc">Pick an accent color (or use the album art).</div>
                </div>
                <div class="theme-swatches">
                    ${["cyan", "purple", "rose", "lime", "amber", "art"].map((t) => `
                        <div class="theme-swatch ${t === s.theme ? "active" : ""}"
                            data-theme="${t}"
                            style="background:${t === "cyan" ? "#00bcd4" :
            t === "purple" ? "#b388ff" :
                t === "rose" ? "#ff6f9c" :
                    t === "lime" ? "#a5d651" :
                        t === "amber" ? "#ffb74d" :
                            "linear-gradient(135deg,#f9d423,#ff4e50)"
        }"></div>
                    `).join("")}
                </div>
            </div>

            <div class="setting">
                <div>
                    <div class="setting-title">Crossfade</div>
                    <div class="setting-desc">Fade between tracks (0–10 s).</div>
                </div>
                <input type="range" id="setCrossfade" min="0" max="10" step="0.5" value="${s.crossfade}" />
            </div>

            <div class="setting">
                <div>
                    <div class="setting-title">Sleep Timer</div>
                    <div class="setting-desc">Pause playback after N minutes (0 = off).</div>
                </div>
                <input type="number" id="setSleep" min="0" max="240" value="${s.sleepMin}" />
            </div>

            <div class="setting" style="grid-template-columns:1fr">
                <div>
                    <div class="setting-title">Equalizer (3-band)</div>
                    <div class="setting-desc">Adjust low / mid / high frequencies.</div>
                </div>
                <div style="margin-top:6px">
                    ${["Low", "Mid", "High"].map((label, i) => `
                        <div class="eq-band">
                            <label>${label}</label>
                            <input type="range" min="-12" max="12" step="0.5" value="${s.eq[i]}" data-eq="${i}"/>
                            <div class="val">${s.eq[i].toFixed(1)} dB</div>
                        </div>
                    `).join("")}
                </div>
            </div>

            <div class="setting">
                <div>
                    <div class="setting-title">Lyrics source</div>
                    <div class="setting-desc">Where to load lyrics from.</div>
                </div>
                <select id="setLyricsSrc">
                    <option value="auto" ${s.lyricsSource === "auto" ? "selected" : ""}>Auto (LRCLIB → YouTube)</option>
                    <option value="lrclib" ${s.lyricsSource === "lrclib" ? "selected" : ""}>LRCLIB only</option>
                    <option value="youtube" ${s.lyricsSource === "youtube" ? "selected" : ""}>YouTube subtitles</option>
                </select>
            </div>

            <div class="setting">
                <div>
                    <div class="setting-title">Visualizer style</div>
                    <div class="setting-desc">Used in the fullscreen player.</div>
                </div>
                <select id="setViz">
                    <option value="bars" ${s.visualizer === "bars" ? "selected" : ""}>Bars</option>
                    <option value="wave" ${s.visualizer === "wave" ? "selected" : ""}>Waveform</option>
                    <option value="ring" ${s.visualizer === "ring" ? "selected" : ""}>Ring</option>
                </select>
            </div>

            <div class="setting">
                <div>
                    <div class="setting-title">Backup / Restore</div>
                    <div class="setting-desc">Export or import your liked songs and playlists.</div>
                </div>
                <div style="display:flex;gap:8px">
                    <button class="btn secondary" id="exportBtn">Export</button>
                    <button class="btn secondary" id="importBtn">Import</button>
                </div>
            </div>
        </div>`;

    $$(".theme-swatch").forEach((el) =>
        el.addEventListener("click", () => {
            State.settings.theme = el.dataset.theme;
            saveSettings(); applyTheme();
            renderSettings();
        })
    );
    $("#setCrossfade").addEventListener("input", (e) => {
        State.settings.crossfade = +e.target.value; saveSettings();
    });
    $("#setSleep").addEventListener("change", (e) => {
        State.settings.sleepMin = +e.target.value; saveSettings();
        scheduleSleepTimer();
    });
    $$("[data-eq]").forEach((el) =>
        el.addEventListener("input", (e) => {
            const i = +el.dataset.eq;
            State.settings.eq[i] = +e.target.value;
            saveSettings(); applyEqFromSettings();
            el.parentElement.querySelector(".val").textContent = State.settings.eq[i].toFixed(1) + " dB";
        })
    );
    $("#setLyricsSrc").addEventListener("change", (e) => {
        State.settings.lyricsSource = e.target.value; saveSettings();
        lyricsSourceSelect.value = e.target.value;
    });
    $("#setViz").addEventListener("change", (e) => {
        State.settings.visualizer = e.target.value; saveSettings();
    });
    $("#exportBtn").addEventListener("click", exportData);
    $("#importBtn").addEventListener("click", importData);
}

function exportData() {
    const data = {
        liked: [...State.liked],
        likedMeta: State.likedMeta,
        playlists: State.playlists,
        recents: State.recents,
        settings: State.settings,
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "sakayori-backup.json";
    a.click();
}
function importData() {
    const input = document.createElement("input");
    input.type = "file"; input.accept = "application/json";
    input.onchange = async () => {
        const f = input.files[0]; if (!f) return;
        try {
            const data = JSON.parse(await f.text());
            if (data.liked) State.liked = new Set(data.liked);
            if (data.likedMeta) State.likedMeta = data.likedMeta;
            if (data.playlists) State.playlists = data.playlists;
            if (data.recents) State.recents = data.recents;
            if (data.settings) State.settings = { ...State.settings, ...data.settings };
            persistLiked(); persistPlaylists(); persistRecents(); saveSettings();
            applyTheme(); applyEqFromSettings();
            renderSidebarPlaylists();
            showToast("Backup restored");
            renderSettings();
        } catch (e) { showToast("Invalid file"); }
    };
    input.click();
}

// =========================================================================
// Theme
// =========================================================================
function applyTheme() {
    document.documentElement.setAttribute("data-theme", State.settings.theme);
}

// =========================================================================
// Sidebar playlists
// =========================================================================
function renderSidebarPlaylists() {
    const ids = Object.keys(State.playlists);
    sidebarPlaylists.innerHTML = ids.map((id) =>
        `<a href="#/playlist/${id}">${escapeHtml(State.playlists[id].name)}</a>`
    ).join("") || `<div class="empty" style="padding:8px 0;font-size:12px">No playlists</div>`;
}

function newPlaylist(name) {
    const id = "pl_" + Date.now().toString(36);
    State.playlists[id] = { name, songs: [] };
    persistPlaylists();
    renderSidebarPlaylists();
    return id;
}

function promptNewPlaylist() {
    openModal({
        title: "New playlist",
        body: `<label>Name<input type="text" id="plName" placeholder="My playlist"/></label>`,
        ok: "Create",
        onOk: () => {
            const name = $("#plName").value.trim();
            if (!name) return;
            const id = newPlaylist(name);
            navigate(`/playlist/${id}`);
        },
    });
    setTimeout(() => $("#plName")?.focus(), 50);
}
function promptRenamePlaylist(id) {
    const p = State.playlists[id];
    openModal({
        title: "Rename playlist",
        body: `<label>Name<input type="text" id="plName" value="${escapeHtml(p.name)}"/></label>`,
        ok: "Save",
        onOk: () => {
            const name = $("#plName").value.trim();
            if (!name) return;
            p.name = name; persistPlaylists();
            renderSidebarPlaylists(); renderLocalPlaylist(id);
        },
    });
    setTimeout(() => $("#plName")?.focus(), 50);
}
newPlaylistBtn.addEventListener("click", promptNewPlaylist);

// =========================================================================
// Modal (generic)
// =========================================================================
function openModal({ title, body, ok = "OK", cancel = "Cancel", onOk }) {
    modalCard.innerHTML = `
        <h2>${escapeHtml(title)}</h2>
        ${body}
        <div class="modal-actions">
            <button class="btn secondary" data-act="cancel">${escapeHtml(cancel)}</button>
            <button class="btn" data-act="ok">${escapeHtml(ok)}</button>
        </div>`;
    modal.classList.remove("hidden");
    modalCard.querySelector('[data-act="cancel"]').onclick = closeModal;
    modalCard.querySelector('[data-act="ok"]').onclick = () => { onOk?.(); closeModal(); };
}
function closeModal() { modal.classList.add("hidden"); }
modal.addEventListener("click", (e) => { if (e.target === modal) closeModal(); });

// =========================================================================
// Context menu
// =========================================================================
function openContextMenu(e, song, opts = {}) {
    closeContextMenu();
    const items = [
        { label: "▶ Play now", run: () => playQueue([song], 0) },
        {
            label: "＋ Add to queue", run: () => {
                State.queue.push(song); showToast("Added to queue"); renderFpQueue();
            }
        },
        {
            label: "◇ Play next", run: () => {
                State.queue.splice(State.queueIndex + 1, 0, song); showToast("Playing next"); renderFpQueue();
            }
        },
        { divider: true },
        {
            label: State.liked.has(song.videoId) ? "♥ Unlike" : "♡ Like",
            run: () => toggleLike(song)
        },
        {
            label: "→ Add to playlist", submenu: Object.keys(State.playlists).map((id) => ({
                label: State.playlists[id].name,
                run: () => {
                    State.playlists[id].songs.push(song); persistPlaylists();
                    showToast(`Added to ${State.playlists[id].name}`);
                },
            })).concat([
                { divider: true },
                { label: "+ New playlist…", run: () => promptNewPlaylist() },
            ])
        },
    ];

    if (opts.playlistId) {
        items.push({ divider: true });
        items.push({
            label: "Remove from this playlist", run: () => {
                const p = State.playlists[opts.playlistId];
                p.songs = p.songs.filter((s) => s.videoId !== song.videoId);
                persistPlaylists();
                renderLocalPlaylist(opts.playlistId);
            }
        });
    }
    items.push({ divider: true });
    if (song.album?.albumId) {
        items.push({ label: "Go to album", run: () => navigate(`/album/${song.album.albumId}`) });
    }
    if (song.artists?.[0]?.artistId) {
        items.push({ label: "Go to artist", run: () => navigate(`/artist/${song.artists[0].artistId}`) });
    }
    items.push({ label: "Open on YouTube", run: () => window.open(`https://youtu.be/${song.videoId}`, "_blank") });

    ctxMenu.innerHTML = items.map((it, i) => {
        if (it.divider) return `<div class="ctx-divider"></div>`;
        if (it.submenu) {
            return `<div class="ctx-item" data-i="${i}">
                ${escapeHtml(it.label)}
                <span class="ctx-sub-arrow">▸</span></div>`;
        }
        return `<div class="ctx-item" data-i="${i}">${escapeHtml(it.label)}</div>`;
    }).join("");

    ctxMenu.classList.remove("hidden");
    const x = Math.min(e.clientX, window.innerWidth - 240);
    const y = Math.min(e.clientY, window.innerHeight - 300);
    ctxMenu.style.left = x + "px";
    ctxMenu.style.top = y + "px";

    ctxMenu.querySelectorAll(".ctx-item").forEach((el) => {
        const it = items[+el.dataset.i];
        el.addEventListener("click", (ev) => {
            ev.stopPropagation();
            if (it.submenu) {
                openSubmenu(el, it.submenu);
            } else {
                it.run();
                closeContextMenu();
            }
        });
    });
}
function openSubmenu(parent, submenu) {
    const sub = document.createElement("div");
    sub.className = "ctx-menu";
    sub.style.position = "fixed";
    sub.innerHTML = submenu.map((it, i) =>
        it.divider ? `<div class="ctx-divider"></div>` :
            `<div class="ctx-item" data-i="${i}">${escapeHtml(it.label)}</div>`
    ).join("");
    document.body.appendChild(sub);
    const r = parent.getBoundingClientRect();
    sub.style.left = (r.right + 4) + "px";
    sub.style.top = r.top + "px";
    sub.querySelectorAll(".ctx-item").forEach((el) => {
        const it = submenu[+el.dataset.i];
        el.addEventListener("click", () => {
            it.run?.();
            sub.remove();
            closeContextMenu();
        });
    });
    document.addEventListener("click", () => sub.remove(), { once: true });
}
function closeContextMenu() { ctxMenu.classList.add("hidden"); }
document.addEventListener("click", (e) => {
    if (!ctxMenu.contains(e.target)) closeContextMenu();
});
document.addEventListener("scroll", closeContextMenu, true);

// =========================================================================
// Drag-to-reorder
// =========================================================================
function wireDrag(div, queue, opts) {
    div.addEventListener("dragstart", (e) => {
        div.classList.add("dragging");
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/idx", div.dataset.idx);
    });
    div.addEventListener("dragend", () => {
        div.classList.remove("dragging");
        $$(".row.drag-over").forEach((el) => el.classList.remove("drag-over"));
    });
    div.addEventListener("dragover", (e) => {
        e.preventDefault();
        div.classList.add("drag-over");
    });
    div.addEventListener("dragleave", () => div.classList.remove("drag-over"));
    div.addEventListener("drop", (e) => {
        e.preventDefault();
        const from = +e.dataTransfer.getData("text/idx");
        const to = +div.dataset.idx;
        div.classList.remove("drag-over");
        if (from === to) return;
        opts.onReorder?.(from, to);
    });
}

// =========================================================================
// Lyrics
// =========================================================================
async function loadLyricsForCurrent() {
    const cur = State.queue[State.queueIndex];
    if (!cur) return;
    fpLyricsInner.innerHTML = `<div class="empty">Loading lyrics…</div>`;
    try {
        const sourcePref = State.settings.lyricsSource;
        const data = await api.lyrics({
            title: cur.name,
            artist: artistsText(cur.artists),
            album: cur.album?.name || "",
            duration: cur.duration || "",
            videoId: cur.videoId,
            ...(sourcePref === "youtube" ? { source: "youtube" } : {}),
        });
        State.lyrics = parseLyrics(data);
        renderLyricLines();
    } catch (e) {
        fpLyricsInner.innerHTML = `<div class="empty">Failed: ${escapeHtml(e.message)}</div>`;
    }
}
function parseLyrics(data) {
    if (!data?.found) return null;
    if (data.synced) {
        const lines = [];
        for (const raw of data.synced.split(/\r?\n/)) {
            const m = /^\[(\d+):(\d+(?:\.\d+)?)\](.*)$/.exec(raw);
            if (m) {
                const t = parseInt(m[1], 10) * 60 + parseFloat(m[2]);
                lines.push({ t, text: m[3].trim() });
            }
        }
        return { synced: true, lines, source: data.source };
    }
    return {
        synced: false,
        lines: (data.plain || "").split(/\r?\n/).map((text) => ({ t: -1, text })),
        source: data.source,
    };
}
function renderLyricLines() {
    if (!State.lyrics) {
        fpLyricsInner.innerHTML = `<div class="empty">No lyrics found.</div>`;
        return;
    }
    fpLyricsInner.innerHTML =
        `<div class="muted" style="font-size:11px">Source: ${escapeHtml(State.lyrics.source || "")}</div>` +
        State.lyrics.lines.map((l, i) =>
            `<div class="lyric-line" data-i="${i}">${escapeHtml(l.text) || "&nbsp;"}</div>`
        ).join("");
}
function tickLyrics() {
    if (fpLyricsPanel.classList.contains("hidden") || !State.lyrics?.synced) return;
    const t = mediaEl().currentTime;
    let activeIdx = -1;
    const lines = State.lyrics.lines;
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].t <= t) activeIdx = i; else break;
    }
    let activeEl = null;
    fpLyricsInner.querySelectorAll(".lyric-line").forEach((el, i) => {
        if (i === activeIdx) {
            activeEl = el;
            if (!el.classList.contains("active")) el.classList.add("active");
        } else el.classList.remove("active");
    });

    // IMPORTANT: do NOT use el.scrollIntoView() — it walks up every
    // scrollable ancestor (including the main view / window), which
    // shoves the rest of the page out of view.  Scroll just the panel
    // container manually so only the lyrics list moves.
    if (activeEl && !tickLyrics._lastActive !== activeEl) {
        tickLyrics._lastActive = activeEl;
        const container = fpLyricsInner;
        const target =
            activeEl.offsetTop - container.clientHeight / 2 + activeEl.clientHeight / 2;
        container.scrollTo({ top: Math.max(0, target), behavior: "smooth" });
    }
}

lyricsSourceSelect.addEventListener("change", (e) => {
    State.settings.lyricsSource = e.target.value;
    saveSettings();
    loadLyricsForCurrent();
});

// =========================================================================
// Player
// =========================================================================
function mediaEl() { return State.mode === "video" ? video : audio; }

async function playQueue(queue, startIndex, opts = {}) {
    State.queue = queue.slice();
    State.queueIndex = startIndex;
    await playCurrent();

    // Auto-open the fullscreen "Now Playing" screen when the user actively
    // chose a song (the default).  Pass { openFullscreen: false } from
    // background flows like queue-extension or shuffle-mid-playback.
    if (opts.openFullscreen !== false) openFullPlayer();

    const cur = State.queue[State.queueIndex];
    if (cur?.videoId) {
        api.upNext(cur.videoId).then(({ songs }) => {
            if (!songs?.length) return;
            const ids = new Set(State.queue.map((s) => s.videoId));
            for (const s of songs) if (!ids.has(s.videoId)) State.queue.push(s);
            renderFpQueue();
        }).catch(() => { });
    }
}


async function playCurrent() {
    ensureAudioGraph();
    if (audioCtx.state === "suspended") audioCtx.resume();

    const s = State.queue[State.queueIndex];
    if (!s) return;

    // Prefetch the *next* track's URL on the server so when we switch
    // to it the resolve is already cached.  Fire-and-forget; the server
    // returns 200 immediately and warms the cache in the background.
    const next = State.queue[State.queueIndex + 1];
    if (next?.videoId) {
        const path = State.mode === "video" ? "prefetch-video" : "prefetch";
        fetch(`/api/${path}/${next.videoId}`, { method: "POST" }).catch(() => { });
    }


    // Track recents
    const item = { song: s, when: Date.now() };
    State.recents = [item, ...State.recents.filter((r) => r.song.videoId !== s.videoId)].slice(0, 200);
    persistRecents();

    // Switch elements
    audio.pause();
    video.pause();
    if (State.mode === "video") {
        video.src = `/api/video/${s.videoId}`;
        video.load();
        video.play().catch((e) => showToast("Playback failed: " + e.message));
        video.classList.remove("hidden");
        vinylWrap.classList.add("hidden");
        fpStage.classList.add("video-mode");
    } else {
        audio.src = `/api/stream/${s.videoId}`;
        audio.load();
        audio.play().catch((e) => showToast("Playback failed: " + e.message));
        video.classList.add("hidden");
        vinylWrap.classList.remove("hidden");
        fpStage.classList.remove("video-mode");
    }

    // UI
    npTitle.textContent = s.name;
    npArtist.textContent = artistsText(s.artists);
    if (s.thumbnail?.url) npArt.src = s.thumbnail.url; else npArt.removeAttribute("src");
    likeBtn.textContent = State.liked.has(s.videoId) ? "♥" : "♡";
    syncFullPlayerUi();
    if (s.thumbnail?.url) {
        const c = await extractAccentFromImage(s.thumbnail.url);
        if (c) document.documentElement.style.setProperty("--art-color", c);
    }

    // Lyrics auto-load if panel is open or "auto" enabled
    if (!fpLyricsPanel.classList.contains("hidden")) loadLyricsForCurrent();

    // MediaSession
    if ("mediaSession" in navigator) {
        navigator.mediaSession.metadata = new MediaMetadata({
            title: s.name,
            artist: artistsText(s.artists),
            album: s.album?.name || "",
            artwork: s.thumbnail?.url
                ? [{ src: s.thumbnail.url, sizes: "512x512", type: "image/jpeg" }]
                : [],
        });
        navigator.mediaSession.setActionHandler("play", () => mediaEl().play());
        navigator.mediaSession.setActionHandler("pause", () => mediaEl().pause());
        navigator.mediaSession.setActionHandler("nexttrack", () => playNext(false));
        navigator.mediaSession.setActionHandler("previoustrack", playPrev);
    }

    // Re-mark current row everywhere
    $$(".row").forEach((r) => {
        r.classList.toggle("playing", r.dataset.vid === s.videoId);
    });
}

function playNext(auto = false) {
    if (!State.queue.length) return;
    if (State.repeat === "one" && auto) {
        mediaEl().currentTime = 0;
        mediaEl().play();
        return;
    }
    let next;
    if (State.shuffle) {
        next = Math.floor(Math.random() * State.queue.length);
    } else {
        next = State.queueIndex + 1;
        if (next >= State.queue.length) {
            if (State.repeat === "all") next = 0;
            else { mediaEl().pause(); return; }
        }
    }
    State.queueIndex = next;
    playCurrent();
}

function playPrev() {
    if (!State.queue.length) return;
    if (mediaEl().currentTime > 3) { mediaEl().currentTime = 0; return; }
    State.queueIndex = Math.max(0, State.queueIndex - 1);
    playCurrent();
}

function toggleLike(song) {
    if (!song?.videoId) return;
    if (State.liked.has(song.videoId)) {
        State.liked.delete(song.videoId);
        delete State.likedMeta[song.videoId];
        showToast("Removed from liked");
    } else {
        State.liked.add(song.videoId);
        State.likedMeta[song.videoId] = song;
        showToast("Added to liked");
    }
    persistLiked();
    const cur = State.queue[State.queueIndex];
    if (cur?.videoId === song.videoId) {
        likeBtn.textContent = State.liked.has(song.videoId) ? "♥" : "♡";
        fpLike.classList.toggle("active", State.liked.has(song.videoId));
    }
}

// =========================================================================
// Fullscreen player
// =========================================================================
function openFullPlayer() {
    fullPlayer.classList.remove("hidden");
    fullPlayer.setAttribute("aria-hidden", "false");
    syncFullPlayerUi();
}
function closeFullPlayer() {
    fullPlayer.classList.add("hidden");
    fullPlayer.setAttribute("aria-hidden", "true");
    fpLyricsPanel.classList.add("hidden");
    fpQueuePanel.classList.add("hidden");
}
function toggleFullPlayer() {
    fullPlayer.classList.contains("hidden") ? openFullPlayer() : closeFullPlayer();
}

function syncFullPlayerUi() {
    const s = State.queue[State.queueIndex];
    if (!s) {
        fpTitle.textContent = "Nothing playing";
        fpArtist.textContent = "";
        return;
    }
    fpTitle.textContent = s.name || "";
    fpArtist.textContent = artistsText(s.artists);
    if (s.thumbnail?.url) {
        vinylArt.src = s.thumbnail.url;
        fpBackdrop.style.backgroundImage = `url("${s.thumbnail.url}")`;
    }
    fpLike.classList.toggle("active", State.liked.has(s.videoId));
    fpShuffle.classList.toggle("active", State.shuffle);
    fpRepeat.classList.toggle("active", State.repeat !== "off");
    fpRepeat.textContent = State.repeat === "one" ? "🔂" : "🔁";
    fpVideoToggle.classList.toggle("active", State.mode === "video");
    renderFpQueue();
}

function renderFpQueue() {
    fpQueueList.innerHTML = "";
    State.queue.forEach((s, i) => {
        const r = songRow(s, i, State.queue, {
            draggable: true,
            onPlay: (idx) => { State.queueIndex = idx; playCurrent(); },
            onReorder: (from, to) => {
                const [moved] = State.queue.splice(from, 1);
                State.queue.splice(to, 0, moved);
                if (State.queueIndex === from) State.queueIndex = to;
                else if (from < State.queueIndex && to >= State.queueIndex) State.queueIndex--;
                else if (from > State.queueIndex && to <= State.queueIndex) State.queueIndex++;
                renderFpQueue();
            },
        });
        fpQueueList.appendChild(r);
    });
}

clearQueueBtn.addEventListener("click", () => {
    State.queue = []; State.queueIndex = -1;
    audio.pause(); video.pause();
    renderFpQueue();
    syncFullPlayerUi();
});

// =========================================================================
// Audio events (shared between <audio> and <video>)
// =========================================================================
function attachMediaEvents(el) {
    el.addEventListener("play", () => {
        playBtn.textContent = "⏸"; fpPlay.textContent = "⏸";
        vinyl.classList.add("playing"); tonearm.classList.add("playing");
        if (State.settings.visualizer) startVisualizer();
    });
    el.addEventListener("pause", () => {
        playBtn.textContent = "▶"; fpPlay.textContent = "▶";
        vinyl.classList.remove("playing"); tonearm.classList.remove("playing");
    });
    el.addEventListener("ended", () => playNext(true));
    el.addEventListener("timeupdate", () => {
        if (el !== mediaEl()) return;
        if (el.duration) {
            const pct = (el.currentTime / el.duration) * 1000;
            seek.value = String(pct); fpSeek.value = String(pct);
            // Update buffered bar
            try {
                if (el.buffered.length) {
                    const end = el.buffered.end(el.buffered.length - 1);
                    bufferBar.style.width = `${(end / el.duration) * 100}%`;
                }
            } catch { }
        }
        curTime.textContent = fmt(el.currentTime);
        durTime.textContent = fmt(el.duration);
        fpCurTime.textContent = fmt(el.currentTime);
        fpDurTime.textContent = fmt(el.duration);
        tickLyrics();

        // Crossfade: if within crossfade window of end, fade out and start next.
        const cf = State.settings.crossfade;
        if (cf > 0 && el.duration && el.currentTime > el.duration - cf) {
            const remain = Math.max(0, el.duration - el.currentTime);
            if (masterGain) masterGain.gain.value = Math.max(0, remain / cf);
        } else if (masterGain) {
            masterGain.gain.value = 1;
        }
    });
    el.addEventListener("error", () => {
        showToast("Playback failed: trying to recover…");
        setTimeout(() => playNext(false), 800);
    });
}
attachMediaEvents(audio);
attachMediaEvents(video);

// =========================================================================
// Visualizer
// =========================================================================
let vizFrame = null;
function startVisualizer() {
    if (visualizerCanvas.classList.contains("hidden")) return;
    if (vizFrame) cancelAnimationFrame(vizFrame);
    const ctx = visualizerCanvas.getContext("2d");
    const dpr = devicePixelRatio || 1;
    function resize() {
        const r = visualizerCanvas.getBoundingClientRect();
        visualizerCanvas.width = r.width * dpr;
        visualizerCanvas.height = r.height * dpr;
    }
    resize();
    addEventListener("resize", resize);

    const buf = new Uint8Array(analyser.frequencyBinCount);
    const accent = getComputedStyle(document.documentElement).getPropertyValue("--accent").trim() || "#00bcd4";

    function draw() {
        vizFrame = requestAnimationFrame(draw);
        if (!analyser) return;
        const w = visualizerCanvas.width, h = visualizerCanvas.height;
        ctx.clearRect(0, 0, w, h);
        const style = State.settings.visualizer;
        if (style === "wave") {
            analyser.getByteTimeDomainData(buf);
            ctx.lineWidth = 2 * dpr; ctx.strokeStyle = accent;
            ctx.beginPath();
            const step = w / buf.length;
            for (let i = 0; i < buf.length; i++) {
                const y = (buf[i] / 128.0) * (h / 2);
                if (i === 0) ctx.moveTo(0, y); else ctx.lineTo(i * step, y);
            }
            ctx.stroke();
        } else if (style === "ring") {
            analyser.getByteFrequencyData(buf);
            const cx = w / 2, cy = h / 2, r0 = Math.min(w, h) * 0.32;
            for (let i = 0; i < buf.length; i += 2) {
                const a = (i / buf.length) * Math.PI * 2;
                const len = (buf[i] / 255) * r0;
                const x1 = cx + Math.cos(a) * r0;
                const y1 = cy + Math.sin(a) * r0;
                const x2 = cx + Math.cos(a) * (r0 + len);
                const y2 = cy + Math.sin(a) * (r0 + len);
                ctx.strokeStyle = accent; ctx.lineWidth = 2 * dpr;
                ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
            }
        } else {
            // bars
            analyser.getByteFrequencyData(buf);
            const bars = 64;
            const barW = w / bars;
            for (let i = 0; i < bars; i++) {
                const v = buf[Math.floor(i * buf.length / bars)] / 255;
                const bh = v * h * 0.85;
                ctx.fillStyle = accent;
                ctx.fillRect(i * barW + 1, h - bh, barW - 2, bh);
            }
        }
    }
    draw();
}
function stopVisualizer() { if (vizFrame) cancelAnimationFrame(vizFrame); vizFrame = null; }

// =========================================================================
// Mini-player + fullscreen control wiring
// =========================================================================
const onSeekMain = (sliderEl) => () => {
    const m = mediaEl();
    if (m.duration) m.currentTime = (sliderEl.value / 1000) * m.duration;
};
seek.addEventListener("input", onSeekMain(seek));
fpSeek.addEventListener("input", onSeekMain(fpSeek));

vol.addEventListener("input", () => {
    const v = vol.value / 100;
    audio.volume = v; video.volume = v;
    State.lastVolume = v;
    muteBtn.textContent = v === 0 ? "🔇" : "🔊";
});
audio.volume = vol.value / 100; video.volume = vol.value / 100;

const togglePlay = () => {
    const m = mediaEl();
    if (m.paused) m.play(); else m.pause();
};
playBtn.addEventListener("click", togglePlay);
fpPlay.addEventListener("click", togglePlay);
prevBtn.addEventListener("click", playPrev);
fpPrev.addEventListener("click", playPrev);
nextBtn.addEventListener("click", () => playNext(false));
fpNext.addEventListener("click", () => playNext(false));

function updateShuffleUi() {
    shuffleBtn.classList.toggle("active", State.shuffle);
    fpShuffle.classList.toggle("active", State.shuffle);
}
const toggleShuffle = () => {
    State.shuffle = !State.shuffle; updateShuffleUi();
    showToast("Shuffle " + (State.shuffle ? "on" : "off"));
};
shuffleBtn.addEventListener("click", toggleShuffle);
fpShuffle.addEventListener("click", toggleShuffle);

const cycleRepeat = () => {
    State.repeat = State.repeat === "off" ? "all" : State.repeat === "all" ? "one" : "off";
    const sym = State.repeat === "one" ? "🔂" : "🔁";
    repeatBtn.textContent = sym; fpRepeat.textContent = sym;
    repeatBtn.classList.toggle("active", State.repeat !== "off");
    fpRepeat.classList.toggle("active", State.repeat !== "off");
    showToast("Repeat: " + State.repeat);
};
repeatBtn.addEventListener("click", cycleRepeat);
fpRepeat.addEventListener("click", cycleRepeat);

const likeCurrent = () => toggleLike(State.queue[State.queueIndex]);
likeBtn.addEventListener("click", likeCurrent);
fpLike.addEventListener("click", likeCurrent);

muteBtn.addEventListener("click", () => {
    const v = audio.volume;
    if (v > 0) {
        State.lastVolume = v;
        audio.volume = 0; video.volume = 0; vol.value = 0;
        muteBtn.textContent = "🔇";
    } else {
        const nv = State.lastVolume || 0.8;
        audio.volume = nv; video.volume = nv;
        vol.value = nv * 100;
        muteBtn.textContent = "🔊";
    }
});

$("#npClickArea").addEventListener("click", openFullPlayer);
expandBtn.addEventListener("click", openFullPlayer);
fpClose.addEventListener("click", closeFullPlayer);

// Video mode toggle
const toggleVideoMode = () => {
    if (!State.queue[State.queueIndex]) {
        showToast("Play something first"); return;
    }
    State.mode = State.mode === "video" ? "audio" : "video";
    showToast("Video mode " + (State.mode === "video" ? "on" : "off"));
    videoModeBtn.classList.toggle("active", State.mode === "video");
    fpVideoToggle.classList.toggle("active", State.mode === "video");
    // Continue from same time
    const t = mediaEl().currentTime;
    playCurrent().then(() => {
        const m = mediaEl();
        const setTime = () => {
            try { m.currentTime = t; } catch { }
            m.removeEventListener("loadedmetadata", setTime);
        };
        m.addEventListener("loadedmetadata", setTime);
    });
};
videoModeBtn.addEventListener("click", toggleVideoMode);
fpVideoToggle.addEventListener("click", toggleVideoMode);

// Update the parent class so the stage shifts left when any side panel
// is open (CSS handles the transition).
function updatePanelLayout() {
    const open =
        !fpLyricsPanel.classList.contains("hidden") ||
        !fpQueuePanel.classList.contains("hidden");
    fullPlayer.classList.toggle("has-panel", open);
}

// Lyrics + queue panels — only one open at a time.
fpLyricsToggle.addEventListener("click", () => {
    const wasHidden = fpLyricsPanel.classList.contains("hidden");
    fpQueuePanel.classList.add("hidden"); fpQueueBtn.classList.remove("active");
    fpLyricsPanel.classList.toggle("hidden");
    fpLyricsToggle.classList.toggle("active", wasHidden);
    if (wasHidden) loadLyricsForCurrent();
    updatePanelLayout();
});
fpLyricsClose.addEventListener("click", () => {
    fpLyricsPanel.classList.add("hidden");
    fpLyricsToggle.classList.remove("active");
    updatePanelLayout();
});
fpQueueBtn.addEventListener("click", () => {
    const wasHidden = fpQueuePanel.classList.contains("hidden");
    fpLyricsPanel.classList.add("hidden"); fpLyricsToggle.classList.remove("active");
    fpQueuePanel.classList.toggle("hidden");
    fpQueueBtn.classList.toggle("active", wasHidden);
    if (wasHidden) renderFpQueue();
    updatePanelLayout();
});
fpQueueClose.addEventListener("click", () => {
    fpQueuePanel.classList.add("hidden"); fpQueueBtn.classList.remove("active");
    updatePanelLayout();
});

fpVizToggle.addEventListener("click", () => {
    const willShow = visualizerCanvas.classList.contains("hidden");
    visualizerCanvas.classList.toggle("hidden");
    fpVizToggle.classList.toggle("active", willShow);
    if (willShow) startVisualizer(); else stopVisualizer();
});

// =========================================================================
// Search bar wiring (with autocomplete)
// =========================================================================
let suggestActive = -1;
let suggestTimer = null;
searchInput.addEventListener("input", () => {
    clearTimeout(suggestTimer);
    const q = searchInput.value.trim();
    if (!q) { suggestList.classList.add("hidden"); return; }
    suggestTimer = setTimeout(async () => {
        try {
            const { suggestions } = await api.suggest(q);
            if (!suggestions.length) { suggestList.classList.add("hidden"); return; }
            suggestList.innerHTML = suggestions.slice(0, 8).map((s, i) =>
                `<div class="suggest-item" data-i="${i}" data-q="${escapeHtml(s)}">🔎 ${escapeHtml(s)}</div>`
            ).join("");
            suggestList.classList.remove("hidden");
            suggestActive = -1;
            suggestList.querySelectorAll(".suggest-item").forEach((el) => {
                el.addEventListener("click", () => {
                    searchInput.value = el.dataset.q;
                    suggestList.classList.add("hidden");
                    navigate(`/search/${encodeURIComponent(el.dataset.q)}`);
                });
            });
        } catch { }
    }, 200);
});
searchInput.addEventListener("keydown", (e) => {
    const items = suggestList.querySelectorAll(".suggest-item");
    if (e.key === "ArrowDown" && items.length) {
        e.preventDefault(); suggestActive = (suggestActive + 1) % items.length;
        items.forEach((el, i) => el.classList.toggle("active", i === suggestActive));
    } else if (e.key === "ArrowUp" && items.length) {
        e.preventDefault(); suggestActive = (suggestActive - 1 + items.length) % items.length;
        items.forEach((el, i) => el.classList.toggle("active", i === suggestActive));
    } else if (e.key === "Escape") {
        suggestList.classList.add("hidden");
    }
});
document.addEventListener("click", (e) => {
    if (!searchForm.contains(e.target)) suggestList.classList.add("hidden");
});

searchForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const q = searchInput.value.trim();
    if (!q) return;
    suggestList.classList.add("hidden");
    navigate(`/search/${encodeURIComponent(q)}`);
});

// =========================================================================
// Sleep timer
// =========================================================================
function scheduleSleepTimer() {
    if (State.sleepTimerId) clearTimeout(State.sleepTimerId);
    State.sleepTimerId = null;
    const m = State.settings.sleepMin;
    if (!m) return;
    State.sleepTimerId = setTimeout(() => {
        mediaEl().pause();
        showToast("Sleep timer triggered. Goodnight 💤");
    }, m * 60 * 1000);
}

// =========================================================================
// Keyboard shortcuts
// =========================================================================
window.addEventListener("keydown", (e) => {
    if (e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement ||
        e.target instanceof HTMLSelectElement) return;
    switch (e.key) {
        case " ": e.preventDefault(); togglePlay(); break;
        case "ArrowRight": playNext(false); break;
        case "ArrowLeft": playPrev(); break;
        case "ArrowUp": {
            e.preventDefault();
            const v = Math.min(100, +vol.value + 5);
            vol.value = String(v); vol.dispatchEvent(new Event("input"));
            showToast(`Volume ${v}%`); break;
        }
        case "ArrowDown": {
            e.preventDefault();
            const v = Math.max(0, +vol.value - 5);
            vol.value = String(v); vol.dispatchEvent(new Event("input"));
            showToast(`Volume ${v}%`); break;
        }
        case "m": case "M": muteBtn.click(); break;
        case "l": case "L": likeCurrent(); break;
        case "s": case "S": toggleShuffle(); break;
        case "r": case "R": cycleRepeat(); break;
        case "f": case "F": toggleFullPlayer(); break;
        case "v": case "V": toggleVideoMode(); break;
        case "Escape":
            if (!ctxMenu.classList.contains("hidden")) closeContextMenu();
            else if (!modal.classList.contains("hidden")) closeModal();
            else if (!fullPlayer.classList.contains("hidden")) closeFullPlayer();
            break;
        case "?": navigate("/shortcuts"); break;
    }
});

// =========================================================================
// Boot
// =========================================================================
applyTheme();
renderSidebarPlaylists();
router(true);
