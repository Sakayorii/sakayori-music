/* =============================================================================
   SakayoriMusic — Web · SPA controller
   ─────────────────────────────────────────────────────────────────────────────
   Modules in this single file (no bundler required):
     • i18n       → loads /locales/<bcp47>.json from server (mirrors Android),
                    persists choice to localStorage, applies to every node
                    tagged with `data-i18n` / `data-i18n-attr` / `data-i18n-fb`.
     • Offline    → IndexedDB-backed download manager.  Audio bytes are pulled
                    from /api/stream via Range requests (chunked progress) and
                    persisted as Blob.  Playback transparently switches to
                    blob:// URL when an offline copy exists.
     • Lyrics     → fetches /api/lyrics?source=… for the multi-provider chain.
                    Source picker hydrates from /api/lyrics/sources.
     • UI         → SVG icon helper (`icon(name)`), Liquid-glass templates.
                    Zero emoji anywhere — every glyph is `<svg><use href="#ic-..."/>`.
   ========================================================================== */


// =====================================================================
// 0. Generic helpers
// =====================================================================
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

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
  })[c]);
const artistsText = (a) =>
  Array.isArray(a) ? a.map((x) => x.name).filter(Boolean).join(", ") : "";
const fmtBytes = (n) => {
  if (!Number.isFinite(n) || n <= 0) return "0 B";
  const u = ["B", "KB", "MB", "GB"];
  let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(n < 10 && i ? 1 : 0)} ${u[i]}`;
};

// SVG icon helper.  All UI glyphs go through here — never emoji.
//
// We bake `viewBox` + explicit `width`/`height` into every emitted <svg> so
// browsers don't fall back to the 300x150 SVG default while waiting for CSS
// to apply (which produces giant icons during paint, especially when the
// .ic class isn't yet matched — e.g. icons rendered into newly-created
// elements before stylesheets are computed).
function icon(name, cls = "ic") {
  const sz = cls.includes("ic-sm") ? 16 : cls.includes("ic-lg") ? 28 : cls.includes("ic-xl") ? 40 : 20;
  return `<svg class="${cls}" viewBox="0 0 24 24" width="${sz}" height="${sz}" aria-hidden="true"><use href="#ic-${name}"/></svg>`;
}
function setBtnIcon(el, name, cls = "ic") {
  if (!el) return;
  el.innerHTML = icon(name, cls);
}


// =====================================================================
// 1. State
// =====================================================================
const State = {
  queue: [],
  queueIndex: -1,
  shuffle: false,
  repeat: "off",
  history: [],
  liked: new Set(JSON.parse(localStorage.getItem("liked") || "[]")),
  likedMeta: JSON.parse(localStorage.getItem("likedMeta") || "{}"),
  playlists: JSON.parse(localStorage.getItem("playlists") || "{}"),
  recents: JSON.parse(localStorage.getItem("recents") || "[]"),
  lyrics: null,
  lastVolume: 0.8,
  sleepTimerId: null,
  mode: "audio",
  settings: JSON.parse(localStorage.getItem("settings") || "{}"),
  forwardStack: [],
};

const DEFAULTS = {
  crossfade: 0,
  theme: "cyan",
  eq: [0, 0, 0],
  visualizer: "bars",
  autoLyrics: true,
  sleepMin: 0,
  lyricsSource: "auto",
  locale: null,             // null → autodetect from browser
};
for (const k in DEFAULTS) {
  if (!(k in State.settings)) State.settings[k] = DEFAULTS[k];
}
function saveSettings() {
  localStorage.setItem("settings", JSON.stringify(State.settings));
}

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

// =====================================================================
// 2. i18n  (Android strings.xml → JSON, served as /locales/*.json)
// =====================================================================
const I18N = {
  code: "en",
  fallback: "en",
  rtl: false,
  map: Object.create(null),
  fallbackMap: Object.create(null),
  available: [],

  t(key, fb = key) {
    if (!key) return fb;
    return this.map[key] || this.fallbackMap[key] || fb;
  },

  async loadIndex() {
    try {
      const r = await fetch("/locales/_index.json");
      if (r.ok) {
        const j = await r.json();
        this.available = j.locales || [];
        this.fallback = j.default || "en";
      }
    } catch { /* offline → leave empty */ }
  },

  pickInitialLocale() {
    // Order: saved setting → browser pref → fallback (en)
    const saved = State.settings.locale;
    if (saved && this.available.some((l) => l.code === saved)) return saved;

    const candidates = (navigator.languages || [navigator.language || "en"])
      .filter(Boolean);
    for (const c of candidates) {
      // exact match (e.g. "zh-TW")
      if (this.available.some((l) => l.code.toLowerCase() === c.toLowerCase())) {
        return this.available.find((l) => l.code.toLowerCase() === c.toLowerCase()).code;
      }
      // language-only match (e.g. "zh-CN" → "zh")
      const lang = c.split("-")[0].toLowerCase();
      const hit = this.available.find((l) => l.code.toLowerCase() === lang);
      if (hit) return hit.code;
    }
    return this.fallback;
  },

  async load(code) {
    // Always load fallback (en) once so missing keys still resolve.
    if (!Object.keys(this.fallbackMap).length) {
      try {
        const r = await fetch(`/locales/${this.fallback}.json`);
        if (r.ok) this.fallbackMap = (await r.json()).strings || {};
      } catch { /* noop */ }
    }
    try {
      const r = await fetch(`/locales/${code}.json`);
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const j = await r.json();
      this.code = j.code;
      this.rtl = !!j.rtl;
      this.map = j.strings || {};
    } catch (e) {
      console.warn(`[i18n] failed to load ${code}, falling back: ${e.message}`);
      this.code = this.fallback;
      this.rtl = false;
      this.map = this.fallbackMap;
    }
    document.documentElement.setAttribute("lang", this.code);
    document.documentElement.setAttribute("dir", this.rtl ? "rtl" : "ltr");
  },

  apply(root = document) {
    // Text content via data-i18n="key"
    $$("[data-i18n]", root).forEach((el) => {
      const key = el.getAttribute("data-i18n");
      const val = this.t(key, el.textContent.trim() || key);
      // Only replace bare-text nodes; preserve nested icons/badges.
      const onlyText = el.children.length === 0;
      if (onlyText) el.textContent = val;
      else {
        // Update only the *last* text node child (icon-then-label pattern)
        for (const node of el.childNodes) {
          if (node.nodeType === Node.TEXT_NODE && node.textContent.trim()) {
            node.textContent = val;
          }
        }
        // If no text node existed, append one.
        if (![...el.childNodes].some((n) => n.nodeType === Node.TEXT_NODE && n.textContent.trim())) {
          el.appendChild(document.createTextNode(val));
        }
      }
    });
    // Fallback-text variant (still pull from English even when not key)
    $$("[data-i18n-fb]", root).forEach((el) => {
      const key = el.getAttribute("data-i18n-fb");
      el.textContent = this.t(key, el.textContent.trim());
    });
    // Attribute translations: data-i18n-attr="placeholder|search_for_..."
    $$("[data-i18n-attr]", root).forEach((el) => {
      const spec = el.getAttribute("data-i18n-attr");
      for (const pair of spec.split(",")) {
        const [attr, key] = pair.split("|").map((s) => s.trim());
        if (attr && key) el.setAttribute(attr, this.t(key, el.getAttribute(attr) || ""));
      }
    });
  },

  async setLocale(code) {
    State.settings.locale = code;
    saveSettings();
    await this.load(code);
    this.apply();
    // Re-render whatever route is active so dynamic strings re-translate.
    try { router(false); } catch { /* noop */ }
  },
};

// Tiny shorthand
const T = (k, fb) => I18N.t(k, fb);

// =====================================================================
// 3. Offline download manager (IndexedDB)
// =====================================================================
const Offline = {
  DB: null,
  DB_NAME: "sakayori-offline",
  DB_VER: 1,
  STORE: "tracks",
  listeners: new Set(),
  activeDownloads: new Map(),  // videoId -> AbortController

  async init() {
    if (!("indexedDB" in window)) {
      console.warn("[offline] IndexedDB unavailable");
      return;
    }
    this.DB = await new Promise((resolve, reject) => {
      const r = indexedDB.open(this.DB_NAME, this.DB_VER);
      r.onupgradeneeded = () => {
        const db = r.result;
        if (!db.objectStoreNames.contains(this.STORE)) {
          const os = db.createObjectStore(this.STORE, { keyPath: "videoId" });
          os.createIndex("status", "status", { unique: false });
          os.createIndex("addedAt", "addedAt", { unique: false });
        }
      };
      r.onsuccess = () => resolve(r.result);
      r.onerror = () => reject(r.error);
    }).catch((e) => {
      console.warn("[offline] init failed:", e);
      return null;
    });
    if (this.DB) {
      // Mark anything that was mid-download when the page closed as paused.
      const all = await this.list();
      for (const t of all) {
        if (t.status === "downloading") await this.put({ ...t, status: "paused" });
      }
      this.notify();
    }
  },

  on(fn) { this.listeners.add(fn); },
  off(fn) { this.listeners.delete(fn); },
  notify() { for (const fn of this.listeners) try { fn(); } catch { } },

  txStore(mode = "readonly") {
    if (!this.DB) return null;
    return this.DB.transaction(this.STORE, mode).objectStore(this.STORE);
  },

  get(videoId) {
    const os = this.txStore();
    if (!os) return Promise.resolve(null);
    return new Promise((resolve) => {
      const r = os.get(videoId);
      r.onsuccess = () => resolve(r.result || null);
      r.onerror = () => resolve(null);
    });
  },

  list() {
    const os = this.txStore();
    if (!os) return Promise.resolve([]);
    return new Promise((resolve) => {
      const r = os.getAll();
      r.onsuccess = () => resolve(r.result || []);
      r.onerror = () => resolve([]);
    });
  },

  put(rec) {
    const os = this.txStore("readwrite");
    if (!os) return Promise.resolve();
    return new Promise((resolve) => {
      const r = os.put(rec);
      r.onsuccess = () => { this.notify(); resolve(); };
      r.onerror = () => resolve();
    });
  },

  remove(videoId) {
    // Cancel inflight download too if any.
    const ac = this.activeDownloads.get(videoId);
    if (ac) { try { ac.abort(); } catch { } this.activeDownloads.delete(videoId); }
    const os = this.txStore("readwrite");
    if (!os) return Promise.resolve();
    return new Promise((resolve) => {
      const r = os.delete(videoId);
      r.onsuccess = () => { this.notify(); resolve(); };
      r.onerror = () => resolve();
    });
  },

  isReady(videoId) {
    return this.get(videoId).then((r) => r && r.status === "complete" ? r : null);
  },

  async toBlobUrl(videoId) {
    const rec = await this.isReady(videoId);
    if (!rec || !rec.blob) return null;
    return URL.createObjectURL(rec.blob);
  },

  /**
   * Start (or resume) a download for `song`.  Streams `/api/stream/:videoId`
   * in chunks via the Fetch reader, accumulates bytes, persists the final
   * Blob to IndexedDB.  Live progress is broadcast via this.notify().
   */
  async download(song) {
    if (!this.DB) {
      showToast(T("error", "Error") + ": IndexedDB unavailable");
      return;
    }
    const videoId = song.videoId;
    if (!videoId) return;
    if (this.activeDownloads.has(videoId)) {
      showToast(T("downloading", "Downloading") + "…");
      return;
    }
    const existing = await this.get(videoId);
    if (existing?.status === "complete") {
      showToast(T("downloaded", "Downloaded"));
      return;
    }

    const ac = new AbortController();
    this.activeDownloads.set(videoId, ac);

    await this.put({
      videoId,
      song,
      status: "downloading",
      received: 0,
      total: existing?.total || 0,
      addedAt: existing?.addedAt || Date.now(),
      error: null,
    });

    try {
      const res = await fetch(`/api/stream/${videoId}`, { signal: ac.signal });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const total = parseInt(res.headers.get("content-length") || "0", 10);
      const mime = res.headers.get("content-type") || "audio/mp4";

      const reader = res.body.getReader();
      const chunks = [];
      let received = 0;
      let lastTick = 0;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        received += value.length;

        // Throttle DB updates to ~10 Hz so we don't thrash IndexedDB.
        const now = performance.now();
        if (now - lastTick > 100) {
          lastTick = now;
          await this.put({
            videoId,
            song,
            status: "downloading",
            received,
            total,
            addedAt: existing?.addedAt || Date.now(),
            error: null,
          });
        }
      }

      const blob = new Blob(chunks, { type: mime });
      await this.put({
        videoId,
        song,
        status: "complete",
        received: blob.size,
        total: blob.size,
        blob,
        mime,
        addedAt: existing?.addedAt || Date.now(),
        completedAt: Date.now(),
        error: null,
      });
      showToast(`${T("downloaded", "Downloaded")}: ${song.name || videoId}`);
    } catch (e) {
      if (e.name === "AbortError") {
        await this.put({
          ...(await this.get(videoId)) || { videoId, song },
          status: "paused",
        });
      } else {
        console.warn("[offline] download failed:", e);
        await this.put({
          ...(await this.get(videoId)) || { videoId, song },
          status: "error",
          error: e.message || String(e),
        });
        showToast(`${T("error", "Error")}: ${e.message || e}`);
      }
    } finally {
      this.activeDownloads.delete(videoId);
      this.notify();
    }
  },

  async cancel(videoId) {
    const ac = this.activeDownloads.get(videoId);
    if (ac) { try { ac.abort(); } catch { } this.activeDownloads.delete(videoId); }
    await this.remove(videoId);
  },
};

// =====================================================================
// 4. DOM handles
// =====================================================================
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
const fpDownload = $("#fpDownload");
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
const dlBadge = $("#dlBadge");
const suggestList = $("#suggestList");
const searchInput = $("#searchInput");
const searchTypeSel = $("#searchType");
const searchForm = $("#searchForm");
const backBtn = $("#backBtn");
const fwdBtn = $("#fwdBtn");
const langBtn = $("#langBtn");
const langMenu = $("#langMenu");

// =====================================================================
// 5. Misc utilities
// =====================================================================
function showToast(msg) {
  toast.textContent = msg;
  toast.classList.remove("hidden");
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => toast.classList.add("hidden"), 1800);
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
        resolve(`rgb(${r / n | 0}, ${g / n | 0}, ${b / n | 0})`);
      } catch { resolve(null); }
    };
    img.onerror = () => resolve(null);
    img.src = url;
  });
}

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
  lyricsSources: () => fetch(`/api/lyrics/sources`).then((r) => r.json()),
};

// =====================================================================
// 6. Web Audio graph (EQ + visualizer)
// =====================================================================
let audioCtx = null;
let audioSrcNode = null;
let videoSrcNode = null;
let eqLow, eqMid, eqHigh, masterGain, analyser;

function ensureAudioGraph() {
  if (audioCtx) return;
  audioCtx = new (window.AudioContext || window.webkitAudioContext)();

  eqLow = audioCtx.createBiquadFilter(); eqLow.type = "lowshelf"; eqLow.frequency.value = 320;
  eqMid = audioCtx.createBiquadFilter(); eqMid.type = "peaking"; eqMid.frequency.value = 1000; eqMid.Q.value = 1;
  eqHigh = audioCtx.createBiquadFilter(); eqHigh.type = "highshelf"; eqHigh.frequency.value = 3200;

  masterGain = audioCtx.createGain();
  analyser = audioCtx.createAnalyser();
  analyser.fftSize = 1024;

  audioSrcNode = audioCtx.createMediaElementSource(audio);
  videoSrcNode = audioCtx.createMediaElementSource(video);

  [audioSrcNode, videoSrcNode].forEach((src) => src.connect(eqLow));
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

// =====================================================================
// 7. Routing
// =====================================================================
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

async function router(_initial = true) {
  const hash = location.hash || "#/home";
  const [, route, ...rest] = hash.slice(1).split("/");
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
      case "downloads": return renderDownloads();
      case "settings": return renderSettings();
      case "shortcuts": return renderShortcuts();
      default: return renderHome();
    }
  } catch (e) {
    view.innerHTML = `<div class="empty">${T("error", "Error")}: ${escapeHtml(e.message)}</div>`;
  }
}

function setActiveSidebar(path) {
  $$(".nav-item").forEach((el) => {
    const href = el.getAttribute("href");
    el.classList.toggle("active", href === `#${path}`);
  });
}

function skeletonRow(n = 8) {
  return Array.from({ length: n }, () =>
    `<div class="card"><div class="skeleton skel-card"></div>
            <div class="t skeleton" style="height:14px;margin-top:10px"></div>
            <div class="a skeleton" style="height:11px;margin-top:6px"></div></div>`
  ).join("");
}

// =====================================================================
// 8. Render: Home
// =====================================================================
async function renderHome() {
  view.innerHTML = `
        <h1 class="section-title">${T("good_evening", "Welcome back")}</h1>
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
            <button class="card-play" title="${T("now_playing", "Play")}">${icon("play")}</button>
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
      if (e.target === playBtn || e.target.closest(".card-play")) return;
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

// =====================================================================
// 9. Render: Search / list rows
// =====================================================================
async function renderSearch(q) {
  if (!q) {
    view.innerHTML = `<h1 class="section-title">${T("search", "Search")}</h1>
            <div class="empty">${T("search_for_songs_artists_albums_playlists_and_more",
      "Type something in the search bar above.")}</div>`;
    return;
  }
  searchInput.value = q;
  view.innerHTML = `<h1 class="section-title">${T("search", "Searching")} “${escapeHtml(q)}”…</h1>
        <div class="list">${Array.from({ length: 6 }, () => `
            <div class="row"><div class="num skeleton" style="height:14px"></div>
            <div class="skeleton" style="width:48px;height:48px;border-radius:6px"></div>
            <div class="meta"><div class="meta-t skeleton" style="height:14px;width:60%"></div>
            <div class="meta-a skeleton" style="height:11px;width:40%;margin-top:4px"></div></div>
            <div></div><div class="dur skeleton" style="height:11px;width:30px"></div><div></div></div>
        `).join("")}</div>`;

  const type = searchTypeSel.value;
  const { results } = await api.search(q, type);
  if (!results.length) { view.innerHTML = `<div class="empty">${T("error", "No results.")}</div>`; return; }
  const songish = results.filter((r) => r.videoId);
  const cardish = results.filter((r) => !r.videoId);

  let html = `<h1 class="section-title">${T("search", "Results")}: “${escapeHtml(q)}”</h1>`;

  if (cardish.length) {
    html += `<div class="grid">${cardish.map((r) => `
            <div class="card ${r.type === "ARTIST" ? "artist" : ""}" data-${r.type.toLowerCase()}-id="${r.id}">
                <img loading="lazy" src="${r.thumbnail?.url || ""}" alt=""/>
                <div class="t">${escapeHtml(r.name)}</div>
                <div class="a">${escapeHtml(r.type)}${r.artist ? " · " + escapeHtml(r.artist) : ""}</div>
            </div>`).join("")}</div>`;
  }
  if (songish.length) {
    html += `<h2 class="section-title" style="font-size:18px;margin-top:24px">${T("songs", "Songs")}</h2>
            <div class="list" id="searchList"></div>`;
  }

  view.innerHTML = html;

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
            <button class="icon-btn" title="${T("more", "More")}" data-action="more">${icon("more", "ic-sm")}</button>
        </div>`;

  // Reflect offline status with a small icon overlay
  Offline.get(s.videoId).then((rec) => {
    if (!rec) return;
    const dur = div.querySelector(".dur");
    if (!dur) return;
    if (rec.status === "complete") {
      dur.innerHTML = `${icon("check", "ic-sm")} ${dur.textContent}`;
    } else if (rec.status === "downloading" || rec.status === "paused") {
      dur.innerHTML = `${icon("download", "ic-sm")} ${dur.textContent}`;
    }
  });

  div.addEventListener("click", (e) => {
    if (e.target.closest("button")) return;
    if (opts.onPlay) opts.onPlay(idx); else playQueue(queue, idx);
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

// =====================================================================
// 10. Hero / Album / Artist / Remote playlist
// =====================================================================
function renderHero({ kind, name, subtitle, image, songs, isArtist }) {
  return `
        <div class="hero">
            <div class="hero-bg" style="background-image:url('${image || ''}')"></div>
            <img class="${isArtist ? 'artist' : ''}" src="${image || ''}" alt=""/>
            <div>
                <div class="crumbs">${escapeHtml(kind)}</div>
                <h1>${escapeHtml(name)}</h1>
                <div class="meta">${escapeHtml(subtitle)}</div>
                <div class="actions">
                    <button class="btn-pill" id="heroPlay">${icon("play")}<span>${T("now_playing", "Play All")}</span></button>
                    <button class="btn-ghost" id="heroShuffle">${icon("shuffle")}<span>${T("shuffle", "Shuffle")}</span></button>
                    ${songs.length ? `<button class="btn-ghost" id="heroAddQueue">${icon("plus")}<span>${T("queue", "Add to queue")}</span></button>` : ""}
                </div>
            </div>
        </div>`;
}

async function renderAlbum(id) {
  view.innerHTML = `<div class="empty">${T("loading", "Loading…")}</div>`;
  const a = await api.album(id);
  view.innerHTML = renderHero({
    kind: `${T("album", "Album")} · ${a.year ?? ""}`.trim(),
    name: a.name,
    subtitle: `${a.artist} · ${a.songs.length} ${T("songs", "tracks")}`,
    image: a.thumbnail?.url,
    songs: a.songs,
  }) + `<div class="list" id="songList"></div>`;
  const list = $("#songList");
  a.songs.forEach((s, i) => list.appendChild(songRow(s, i, a.songs)));
  wireHero(a.songs);
}

async function renderRemotePlaylist(id) {
  view.innerHTML = `<div class="empty">${T("loading", "Loading…")}</div>`;
  const p = await api.playlist(id);
  view.innerHTML = renderHero({
    kind: T("playlist", "Playlist"),
    name: p.name,
    subtitle: `${p.songs.length} ${T("songs", "tracks")}`,
    image: p.thumbnail?.url,
    songs: p.songs,
  }) + `<div class="list" id="songList"></div>`;
  const list = $("#songList");
  p.songs.forEach((s, i) => list.appendChild(songRow(s, i, p.songs)));
  wireHero(p.songs);
}

async function renderArtist(id) {
  view.innerHTML = `<div class="empty">${T("loading", "Loading…")}</div>`;
  const a = await api.artist(id);
  let html = renderHero({
    kind: T("artists", "Artist"),
    name: a.name,
    subtitle: a.subscribers ? T("subscribers", `${a.subscribers} subscribers`).replace("%1$s", a.subscribers) : "",
    image: a.thumbnail?.url,
    songs: a.topSongs,
    isArtist: true,
  });

  if (a.topSongs?.length) {
    html += `<h2 class="section-title" style="font-size:18px;margin-top:8px">${T("top_tracks", "Top Songs")}</h2>
            <div class="list" id="topSongs"></div>`;
  }
  if (a.albums?.length) {
    html += `<h2 class="section-title" style="font-size:18px;margin-top:24px">${T("albums", "Albums")}</h2>
            <div class="grid">${a.albums.map((al) => `
                <div class="card" data-album-id="${al.id}">
                    <img loading="lazy" src="${al.thumbnail?.url || ""}" alt=""/>
                    <div class="t">${escapeHtml(al.name)}</div>
                    <div class="a">${al.year || ""}</div>
                </div>`).join("")}</div>`;
  }
  if (a.singles?.length) {
    html += `<h2 class="section-title" style="font-size:18px;margin-top:24px">${T("singles", "Singles")}</h2>
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
    c.addEventListener("click", () => navigate(`/album/${c.dataset.albumId}`)));
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
    showToast(`${songs.length} ${T("added_to_playlist", "added to queue")}`);
    renderFpQueue();
  });
}

// =====================================================================
// 11. Library / Recents / Local playlists
// =====================================================================
function renderLibrary() {
  const items = [...State.liked].map(
    (vid) => State.likedMeta[vid] || { videoId: vid, name: vid, artists: [] }
  );
  if (!items.length) {
    view.innerHTML = `<h1 class="section-title">${T("liked", "Liked Songs")}</h1>
            <div class="empty">You haven't liked anything yet. Press <kbd>L</kbd> while a song plays.</div>`;
    return;
  }
  view.innerHTML = `<h1 class="section-title">${T("liked", "Liked Songs")} <span class="muted">· ${items.length}</span></h1>
        <div class="actions" style="margin-bottom:12px">
            <button class="btn-pill" id="heroPlay">${icon("play")}<span>${T("now_playing", "Play All")}</span></button>
            <button class="btn-ghost" id="heroShuffle">${icon("shuffle")}<span>${T("shuffle", "Shuffle")}</span></button>
        </div>
        <div class="list" id="libList"></div>`;
  const list = $("#libList");
  items.forEach((s, i) => list.appendChild(songRow(s, i, items)));
  wireHero(items);
}

function renderRecents() {
  const items = State.recents.slice(0, 100).map((r) => r.song);
  if (!items.length) {
    view.innerHTML = `<h1 class="section-title">${T("recently", "Recently Played")}</h1>
            <div class="empty">Nothing played yet.</div>`;
    return;
  }
  view.innerHTML = `<h1 class="section-title">${T("recently", "Recently Played")}</h1>
        <div class="actions" style="margin-bottom:12px">
            <button class="btn-pill" id="heroPlay">${icon("play")}<span>${T("now_playing", "Play All")}</span></button>
            <button class="btn-ghost" id="heroShuffle">${icon("shuffle")}<span>${T("shuffle", "Shuffle")}</span></button>
        </div>
        <div class="list" id="recentList"></div>`;
  const list = $("#recentList");
  items.forEach((s, i) => list.appendChild(songRow(s, i, items)));
  wireHero(items);
}

function renderLocalPlaylists() {
  const ids = Object.keys(State.playlists);
  view.innerHTML = `<h1 class="section-title">${T("your_playlists", "My Playlists")}</h1>
        ${ids.length === 0
      ? `<div class="empty">${T("no_playlists_added", "No playlists yet. Click \"+ New playlist\" in the sidebar.")}</div>`
      : `<div class="grid">${ids.map((id) => {
        const p = State.playlists[id];
        const cover = p.songs[0]?.thumbnail?.url || "";
        return `<div class="card" data-pl="${id}">
                    <img loading="lazy" src="${cover}" alt=""/>
                    <div class="t">${escapeHtml(p.name)}</div>
                    <div class="a">${p.songs.length} ${T("songs", "tracks")}</div>
                </div>`;
      }).join("")}</div>`}`;
  $$(".card[data-pl]").forEach((c) =>
    c.addEventListener("click", () => navigate(`/playlist/${c.dataset.pl}`)));
}

function renderLocalPlaylist(id) {
  const p = State.playlists[id];
  if (!p) {
    view.innerHTML = `<div class="empty">${T("error", "Playlist not found.")}</div>`;
    return;
  }
  const cover = p.songs[0]?.thumbnail?.url || "";
  view.innerHTML = renderHero({
    kind: T("playlist", "Local Playlist"),
    name: p.name,
    subtitle: `${p.songs.length} ${T("songs", "tracks")}`,
    image: cover,
    songs: p.songs,
  }) + `
        <div class="actions" style="margin: -10px 0 14px">
            <button class="btn-ghost" id="renamePlaylistBtn">${icon("edit")}<span>${T("edit_title", "Rename")}</span></button>
            <button class="btn-ghost" id="deletePlaylistBtn" style="color:var(--danger)">${icon("trash")}<span>${T("delete_playlist", "Delete")}</span></button>
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
    if (confirm(`${T("delete_playlist", "Delete")} "${p.name}"?`)) {
      delete State.playlists[id];
      persistPlaylists();
      renderSidebarPlaylists();
      navigate("/playlists");
    }
  });
}

// =====================================================================
// 12. Downloads page
// =====================================================================
async function renderDownloads() {
  view.innerHTML = `<h1 class="section-title">${T("downloaded", "Downloads")}</h1>
        <div id="dlList"></div>`;
  const renderList = async () => {
    const list = await Offline.list();
    list.sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));
    const wrap = $("#dlList");
    if (!wrap) return;
    if (!list.length) {
      wrap.innerHTML = `<div class="empty">${T("no_playlists_downloaded", "No downloads yet. Use the download button in the player or row context menu.")}</div>`;
      return;
    }
    wrap.innerHTML = list.map((rec) => {
      const s = rec.song || { name: rec.videoId, artists: [] };
      const pct = rec.total ? Math.min(100, (rec.received / rec.total) * 100) : 0;
      const status = rec.status === "complete"
        ? `${icon("check", "ic-sm")} ${T("downloaded", "Ready")} · ${fmtBytes(rec.received)}`
        : rec.status === "downloading"
          ? `${icon("spinner", "ic-sm")} ${T("downloading", "Downloading")} · ${fmtBytes(rec.received)} / ${rec.total ? fmtBytes(rec.total) : "?"}`
          : rec.status === "paused"
            ? `${T("preparing", "Paused")} · ${fmtBytes(rec.received)}`
            : `${icon("close", "ic-sm")} ${T("error", "Error")} · ${escapeHtml(rec.error || "")}`;
      return `<div class="dl-row" data-vid="${rec.videoId}">
                <img loading="lazy" src="${s.thumbnail?.url || ""}" alt=""/>
                <div>
                    <div class="dl-meta-t">${escapeHtml(s.name || rec.videoId)}</div>
                    <div class="dl-meta-a">${escapeHtml(artistsText(s.artists))}</div>
                </div>
                <div>
                    <div class="dl-progress"><div class="dl-progress-bar" style="width:${pct}%"></div></div>
                    <div class="dl-status ${rec.status === 'error' ? 'error' : ''}">${status}</div>
                </div>
                <div class="dl-actions">
                    <button class="icon-btn" data-act="play" title="${T("now_playing", "Play")}">${icon("play")}</button>
                    ${rec.status === "complete" ? "" :
          rec.status === "downloading"
            ? `<button class="icon-btn" data-act="pause" title="${T("pause", "Pause")}">${icon("pause")}</button>`
            : `<button class="icon-btn" data-act="resume" title="${T("download", "Resume")}">${icon("download")}</button>`}
                    <button class="icon-btn" data-act="remove" title="${T("delete_playlist", "Remove")}">${icon("trash")}</button>
                </div>
            </div>`;
    }).join("");

    $$(".dl-row").forEach((el) => {
      const vid = el.dataset.vid;
      el.querySelector('[data-act="play"]').addEventListener("click", async () => {
        const rec = await Offline.get(vid);
        if (rec?.song) playQueue([rec.song], 0);
      });
      el.querySelector('[data-act="pause"]')?.addEventListener("click", async () => {
        const ac = Offline.activeDownloads.get(vid);
        if (ac) ac.abort();
      });
      el.querySelector('[data-act="resume"]')?.addEventListener("click", async () => {
        const rec = await Offline.get(vid);
        if (rec) Offline.download(rec.song);
      });
      el.querySelector('[data-act="remove"]').addEventListener("click", async () => {
        await Offline.remove(vid);
        renderList();
      });
    });
  };
  await renderList();
  Offline.on(renderList);
  // When the route changes, drop the listener.
  const stop = () => { Offline.off(renderList); window.removeEventListener("hashchange", stop); };
  window.addEventListener("hashchange", stop);
}

// Sidebar badge for in-progress downloads
async function refreshDlBadge() {
  if (!Offline.DB) return;
  const list = await Offline.list();
  const active = list.filter((r) => r.status === "downloading" || r.status === "paused").length;
  if (active > 0) {
    dlBadge.textContent = String(active);
    dlBadge.classList.remove("hidden");
  } else {
    dlBadge.classList.add("hidden");
  }
}
Offline.on(refreshDlBadge);

// =====================================================================
// 13. Shortcuts page
// =====================================================================
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
      ["D", "Download current track"],
      ["Esc", "Close fullscreen / panels"],
      ["?", "This page"],
    ].map(([k, d]) =>
      `<tr><td style="padding:6px 12px"><kbd>${k}</kbd></td>
                <td style="padding:6px 12px">${d}</td></tr>`).join("")}
        </table>`;
}

// =====================================================================
// 14. Settings page
// =====================================================================
function renderSettings() {
  const s = State.settings;
  const localesHtml = I18N.available.map((l) =>
    `<option value="${l.code}" ${l.code === I18N.code ? "selected" : ""}>${escapeHtml(l.name)} (${l.code})</option>`
  ).join("") || `<option value="en">English</option>`;

  view.innerHTML = `
        <div class="settings">
            <h1>${T("settings", "Settings")}</h1>

            <div class="setting">
                <div>
                    <div class="setting-title">${T("language", "Language")}</div>
                    <div class="setting-desc">${I18N.available.length} locales available · sourced from the Android app's strings.xml</div>
                </div>
                <select id="setLocale">${localesHtml}</select>
            </div>

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
              "linear-gradient(135deg,#f9d423,#ff4e50)"}"></div>
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
                    <div class="setting-title">${T("equalizer", "Equalizer")} (3-band)</div>
                    <div class="setting-desc">Adjust low / mid / high frequencies.</div>
                </div>
                <div style="margin-top:6px">
                    ${["Low", "Mid", "High"].map((label, i) => `
                        <div class="eq-band">
                            <label>${label}</label>
                            <input type="range" min="-12" max="12" step="0.5" value="${s.eq[i]}" data-eq="${i}"/>
                            <div class="val">${s.eq[i].toFixed(1)} dB</div>
                        </div>`).join("")}
                </div>
            </div>

            <div class="setting">
                <div>
                    <div class="setting-title">${T("lyrics", "Lyrics")} source</div>
                    <div class="setting-desc">Multi-source chain: LRCLIB → NetEase → Genius → KuGou → YouTube.</div>
                </div>
                <select id="setLyricsSrc"></select>
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
                    <div class="setting-title">${T("backup_your_data", "Backup")} / ${T("restore_your_data", "Restore")}</div>
                    <div class="setting-desc">${T("save_all_your_playlist_data", "Export or import your liked songs and playlists.")}</div>
                </div>
                <div style="display:flex;gap:8px">
                    <button class="btn secondary" id="exportBtn">${T("backup", "Export")}</button>
                    <button class="btn secondary" id="importBtn">${T("restore_your_data", "Import")}</button>
                </div>
            </div>
        </div>`;

  // Wire lyrics source dropdown from server-listed providers.
  populateLyricsSourceSelect($("#setLyricsSrc"), s.lyricsSource);

  $$(".theme-swatch").forEach((el) =>
    el.addEventListener("click", () => {
      State.settings.theme = el.dataset.theme;
      saveSettings(); applyTheme();
      renderSettings();
    }));
  $("#setLocale").addEventListener("change", (e) => I18N.setLocale(e.target.value));
  $("#setCrossfade").addEventListener("input", (e) => { State.settings.crossfade = +e.target.value; saveSettings(); });
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
    }));
  $("#setLyricsSrc").addEventListener("change", (e) => {
    State.settings.lyricsSource = e.target.value; saveSettings();
    if (lyricsSourceSelect) lyricsSourceSelect.value = e.target.value;
  });
  $("#setViz").addEventListener("change", (e) => {
    State.settings.visualizer = e.target.value; saveSettings();
  });
  $("#exportBtn").addEventListener("click", exportData);
  $("#importBtn").addEventListener("click", importData);
}

// Static fallback list of lyrics providers — used as the synchronous source
// for both the dropdown and the empty-state quick-action buttons. Server's
// /api/lyrics/sources may extend it later (e.g. to disable a backend), but
// the UI is never blocked waiting on that fetch.
const LYRICS_SOURCES = [
  { id: "auto", label: "Auto (full chain)" },
  { id: "lrclib", label: "LRCLIB" },
  { id: "netease", label: "NetEase Cloud Music" },
  { id: "genius", label: "Genius" },
  { id: "kugou", label: "KuGou" },
  { id: "youtube", label: "YouTube subtitles" },
];

function fillLyricsSourceSelect(el, current, providers = LYRICS_SOURCES) {
  if (!el) return;
  el.innerHTML = providers.map((p) =>
    `<option value="${p.id}" ${p.id === current ? "selected" : ""}>${escapeHtml(p.label)}</option>`
  ).join("");
}

async function populateLyricsSourceSelect(el, current) {
  if (!el) return;
  // Render synchronously from the static list first so the dropdown is
  // immediately usable.
  fillLyricsSourceSelect(el, current, LYRICS_SOURCES);
  // Then refresh from the server in case it ships a different roster.
  try {
    const r = await api.lyricsSources();
    if (r?.providers) {
      const list = [r.auto, ...r.providers];
      fillLyricsSourceSelect(el, current, list);
    }
  } catch { /* server unreachable — keep the static list */ }
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
      showToast(T("restore_success", "Backup restored"));
      renderSettings();
    } catch { showToast(T("restore_failed", "Invalid file")); }
  };
  input.click();
}

function applyTheme() {
  document.documentElement.setAttribute("data-theme", State.settings.theme);
}

function renderSidebarPlaylists() {
  const ids = Object.keys(State.playlists);
  sidebarPlaylists.innerHTML = ids.map((id) =>
    `<a href="#/playlist/${id}">${escapeHtml(State.playlists[id].name)}</a>`
  ).join("") || `<div class="empty" style="padding:8px 0;font-size:12px">${T("no_playlists_added", "No playlists")}</div>`;
}

// =====================================================================
// 15. Modal / playlist prompts
// =====================================================================
function newPlaylist(name) {
  const id = "pl_" + Date.now().toString(36);
  State.playlists[id] = { name, songs: [] };
  persistPlaylists();
  renderSidebarPlaylists();
  return id;
}

function promptNewPlaylist() {
  openModal({
    title: T("playlist_name", "New playlist"),
    body: `<label>${T("playlist_name", "Name")}<input type="text" id="plName" placeholder="${T("playlist_name", "My playlist")}"/></label>`,
    ok: T("create", "Create"),
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
    title: T("edit_title", "Rename playlist"),
    body: `<label>${T("playlist_name", "Name")}<input type="text" id="plName" value="${escapeHtml(p.name)}"/></label>`,
    ok: T("save_playback_state", "Save"),
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

function openModal({ title, body, ok = "OK", cancel = T("cancel", "Cancel"), onOk }) {
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

// =====================================================================
// 16. Context menu
// =====================================================================
function openContextMenu(e, song, opts = {}) {
  closeContextMenu();
  const isLiked = State.liked.has(song.videoId);
  const items = [
    { i: "play", label: T("now_playing", "Play now"), run: () => playQueue([song], 0) },
    { i: "plus", label: T("queue", "Add to queue"), run: () => { State.queue.push(song); showToast(T("added_to_playlist", "Added to queue")); renderFpQueue(); } },
    { i: "next", label: T("up_next", "Play next") || "Play next", run: () => { State.queue.splice(State.queueIndex + 1, 0, song); showToast(T("up_next", "Playing next")); renderFpQueue(); } },
    { divider: true },
    { i: isLiked ? "heart-fill" : "heart", label: isLiked ? T("liked", "Unlike") : T("like", "Like"), run: () => toggleLike(song) },
    { i: "download", label: T("download", "Download"), run: () => Offline.download(song) },
    {
      i: "list",
      label: T("add_to_a_playlist", "Add to playlist"),
      submenu: Object.keys(State.playlists).map((id) => ({
        label: State.playlists[id].name,
        run: () => {
          State.playlists[id].songs.push(song); persistPlaylists();
          showToast(`${T("added_to_playlist", "Added to")} ${State.playlists[id].name}`);
        },
      })).concat([
        { divider: true },
        { i: "plus", label: T("playlist_name", "+ New playlist…"), run: () => promptNewPlaylist() },
      ]),
    },
  ];

  if (opts.playlistId) {
    items.push({ divider: true });
    items.push({
      i: "trash", label: T("delete_song_from_playlist", "Remove from this playlist"), run: () => {
        const p = State.playlists[opts.playlistId];
        p.songs = p.songs.filter((s) => s.videoId !== song.videoId);
        persistPlaylists();
        renderLocalPlaylist(opts.playlistId);
      },
    });
  }
  items.push({ divider: true });
  if (song.album?.albumId) {
    items.push({ i: "music", label: T("album", "Go to album"), run: () => navigate(`/album/${song.album.albumId}`) });
  }
  if (song.artists?.[0]?.artistId) {
    items.push({ i: "mic", label: T("artists", "Go to artist"), run: () => navigate(`/artist/${song.artists[0].artistId}`) });
  }
  items.push({ i: "video", label: T("youtube_url", "Open on YouTube"), run: () => window.open(`https://youtu.be/${song.videoId}`, "_blank") });

  ctxMenu.innerHTML = items.map((it, i) => {
    if (it.divider) return `<div class="ctx-divider"></div>`;
    if (it.submenu) {
      return `<div class="ctx-item" data-i="${i}">
                ${icon(it.i || "list", "ic-sm")} ${escapeHtml(it.label)}
                <span class="ctx-sub-arrow">${icon("chev-right", "ic-sm")}</span></div>`;
    }
    return `<div class="ctx-item" data-i="${i}">${icon(it.i || "more", "ic-sm")} ${escapeHtml(it.label)}</div>`;
  }).join("");

  ctxMenu.classList.remove("hidden");
  const x = Math.min(e.clientX, window.innerWidth - 240);
  const y = Math.min(e.clientY, window.innerHeight - 320);
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
  sub.className = "ctx-menu glass-strong";
  sub.style.position = "fixed";
  sub.innerHTML = submenu.map((it, i) =>
    it.divider ? `<div class="ctx-divider"></div>` :
      `<div class="ctx-item" data-i="${i}">${it.i ? icon(it.i, "ic-sm") : ""} ${escapeHtml(it.label)}</div>`
  ).join("");
  document.body.appendChild(sub);
  const r = parent.getBoundingClientRect();
  sub.style.left = r.right + 4 + "px";
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

// =====================================================================
// 17. Drag-to-reorder
// =====================================================================
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

// =====================================================================
// 18. Lyrics
// =====================================================================
async function loadLyricsForCurrent() {
  const cur = State.queue[State.queueIndex];
  if (!cur) {
    fpLyricsInner.innerHTML = `<div class="empty">${T("hello_blank_fragment", "Play something first")}</div>`;
    return;
  }
  fpLyricsInner.innerHTML = `<div class="empty">${icon("spinner", "ic-sm")} ${T("loading", "Loading lyrics…")}</div>`;
  try {
    const sourcePref = State.settings.lyricsSource || "auto";
    const data = await api.lyrics({
      title: cur.name,
      artist: artistsText(cur.artists),
      album: cur.album?.name || "",
      duration: cur.duration || "",
      videoId: cur.videoId,
      source: sourcePref,
    });
    State.lyrics = parseLyrics(data);
    renderLyricLines();
  } catch (e) {
    fpLyricsInner.innerHTML = renderLyricsEmpty(`${T("error", "Failed")}: ${escapeHtml(e.message)}`);
    wireLyricsEmptyActions();
  }
}

// Empty / error state with quick "try another source" buttons so the user
// can recover without hunting through Settings.
function renderLyricsEmpty(reason) {
  const cur = State.queue[State.queueIndex];
  const buttons = LYRICS_SOURCES
    .filter((p) => p.id !== "auto" && p.id !== State.settings.lyricsSource)
    .map((p) =>
      `<button class="btn-ghost" data-try="${p.id}" type="button">${escapeHtml(p.label)}</button>`
    ).join("");
  return `<div class="empty">
        <div>${escapeHtml(reason)}</div>
        <div class="muted" style="margin-top:8px">${escapeHtml(cur?.name || "")}</div>
        <div class="lyrics-empty-actions">${buttons}</div>
    </div>`;
}
function wireLyricsEmptyActions() {
  fpLyricsInner.querySelectorAll("[data-try]").forEach((b) =>
    b.addEventListener("click", () => {
      State.settings.lyricsSource = b.dataset.try;
      saveSettings();
      if (lyricsSourceSelect) lyricsSourceSelect.value = b.dataset.try;
      loadLyricsForCurrent();
    })
  );
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
    fpLyricsInner.innerHTML = `<div class="empty">${T("error", "No lyrics found.")}</div>`;
    return;
  }
  fpLyricsInner.innerHTML =
    `<div class="muted" style="font-size:11px">${T("share_url", "Source")}: ${escapeHtml(State.lyrics.source || "")}</div>` +
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

  if (activeEl && tickLyrics._lastActive !== activeEl) {
    tickLyrics._lastActive = activeEl;
    const container = fpLyricsInner;
    const target =
      activeEl.offsetTop - container.clientHeight / 2 + activeEl.clientHeight / 2;
    container.scrollTo({ top: Math.max(0, target), behavior: "smooth" });
  }
}

// Hydrate lyrics dropdown in the fullscreen panel from server providers.
populateLyricsSourceSelect(lyricsSourceSelect, State.settings.lyricsSource).then(() => {
  lyricsSourceSelect.addEventListener("change", (e) => {
    State.settings.lyricsSource = e.target.value;
    saveSettings();
    loadLyricsForCurrent();
  });
});

// =====================================================================
// 19. Player core
// =====================================================================
function mediaEl() { return State.mode === "video" ? video : audio; }

async function playQueue(queue, startIndex, opts = {}) {
  State.queue = queue.slice();
  State.queueIndex = startIndex;
  await playCurrent();

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

  // Prefetch next track (server warms yt-dlp cache) — but only if we're not
  // already going to use an offline copy for it.
  const next = State.queue[State.queueIndex + 1];
  if (next?.videoId) {
    Offline.isReady(next.videoId).then((rec) => {
      if (rec) return; // already on disk
      const path = State.mode === "video" ? "prefetch-video" : "prefetch";
      fetch(`/api/${path}/${next.videoId}`, { method: "POST" }).catch(() => { });
    });
  }

  const item = { song: s, when: Date.now() };
  State.recents = [item, ...State.recents.filter((r) => r.song.videoId !== s.videoId)].slice(0, 200);
  persistRecents();

  audio.pause();
  video.pause();

  // Choose source: offline blob if available, else stream from server.
  let audioSrc = `/api/stream/${s.videoId}`;
  let videoSrc = `/api/video/${s.videoId}`;
  if (State.mode === "audio") {
    const blobUrl = await Offline.toBlobUrl(s.videoId);
    if (blobUrl) {
      audioSrc = blobUrl;
      // Revoke the previous one (if we created any) to free memory.
      if (playCurrent._lastBlobUrl) URL.revokeObjectURL(playCurrent._lastBlobUrl);
      playCurrent._lastBlobUrl = blobUrl;
    } else if (playCurrent._lastBlobUrl) {
      URL.revokeObjectURL(playCurrent._lastBlobUrl);
      playCurrent._lastBlobUrl = null;
    }
  }

  if (State.mode === "video") {
    video.src = videoSrc;
    video.load();
    video.play().catch((e) => showToast(T("error", "Playback failed") + ": " + e.message));
    video.classList.remove("hidden");
    vinylWrap.classList.add("hidden");
    fpStage.classList.add("video-mode");
  } else {
    audio.src = audioSrc;
    audio.load();
    audio.play().catch((e) => showToast(T("error", "Playback failed") + ": " + e.message));
    video.classList.add("hidden");
    vinylWrap.classList.remove("hidden");
    fpStage.classList.remove("video-mode");
  }

  npTitle.textContent = s.name;
  npArtist.textContent = artistsText(s.artists);
  if (s.thumbnail?.url) npArt.src = s.thumbnail.url; else npArt.removeAttribute("src");
  setBtnIcon(likeBtn, State.liked.has(s.videoId) ? "heart-fill" : "heart");
  syncFullPlayerUi();
  if (s.thumbnail?.url) {
    const c = await extractAccentFromImage(s.thumbnail.url);
    if (c) document.documentElement.style.setProperty("--art-color", c);
  }

  if (!fpLyricsPanel.classList.contains("hidden")) loadLyricsForCurrent();

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
    showToast(T("removed_download", "Removed from liked"));
  } else {
    State.liked.add(song.videoId);
    State.likedMeta[song.videoId] = song;
    showToast(T("liked", "Added to liked"));
  }
  persistLiked();
  const cur = State.queue[State.queueIndex];
  if (cur?.videoId === song.videoId) {
    setBtnIcon(likeBtn, State.liked.has(song.videoId) ? "heart-fill" : "heart");
    fpLike.classList.toggle("active", State.liked.has(song.videoId));
    // Update icon inside fpLike too.
    const useEl = fpLike.querySelector("use");
    if (useEl) useEl.setAttribute("href", State.liked.has(song.videoId) ? "#ic-heart-fill" : "#ic-heart");
  }
}

// =====================================================================
// 20. Fullscreen player UI
// =====================================================================
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
    fpTitle.textContent = T("hello_blank_fragment", "Nothing playing");
    fpArtist.textContent = "";
    return;
  }
  fpTitle.textContent = s.name || "";
  fpArtist.textContent = artistsText(s.artists);
  if (s.thumbnail?.url) {
    vinylArt.src = s.thumbnail.url;
    fpBackdrop.style.backgroundImage = `url("${s.thumbnail.url}")`;
  }
  const liked = State.liked.has(s.videoId);
  fpLike.classList.toggle("active", liked);
  const fpLikeUse = fpLike.querySelector("use");
  if (fpLikeUse) fpLikeUse.setAttribute("href", liked ? "#ic-heart-fill" : "#ic-heart");

  fpShuffle.classList.toggle("active", State.shuffle);
  fpRepeat.classList.toggle("active", State.repeat !== "off");
  const repUse = fpRepeat.querySelector("use");
  if (repUse) repUse.setAttribute("href", State.repeat === "one" ? "#ic-repeat-one" : "#ic-repeat");

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

// =====================================================================
// 21. Media element events + visualizer
// =====================================================================
function attachMediaEvents(el) {
  el.addEventListener("play", () => {
    setBtnIcon(playBtn, "pause", "ic"); setBtnIcon(fpPlay, "pause", "ic");
    vinyl.classList.add("playing"); tonearm.classList.add("playing");
    if (State.settings.visualizer) startVisualizer();
  });
  el.addEventListener("pause", () => {
    setBtnIcon(playBtn, "play", "ic"); setBtnIcon(fpPlay, "play", "ic");
    vinyl.classList.remove("playing"); tonearm.classList.remove("playing");
  });
  el.addEventListener("ended", () => playNext(true));
  el.addEventListener("timeupdate", () => {
    if (el !== mediaEl()) return;
    if (el.duration) {
      const pct = el.currentTime / el.duration * 1000;
      seek.value = String(pct); fpSeek.value = String(pct);

      try {
        if (el.buffered.length) {
          const end = el.buffered.end(el.buffered.length - 1);
          bufferBar.style.width = `${end / el.duration * 100}%`;
        }
      } catch { }
    }
    curTime.textContent = fmt(el.currentTime);
    durTime.textContent = fmt(el.duration);
    fpCurTime.textContent = fmt(el.currentTime);
    fpDurTime.textContent = fmt(el.duration);
    tickLyrics();

    const cf = State.settings.crossfade;
    if (cf > 0 && el.duration && el.currentTime > el.duration - cf) {
      const remain = Math.max(0, el.duration - el.currentTime);
      if (masterGain) masterGain.gain.value = Math.max(0, remain / cf);
    } else if (masterGain) {
      masterGain.gain.value = 1;
    }
  });
  el.addEventListener("error", () => {
    showToast(T("error", "Playback failed: trying to recover…"));
    setTimeout(() => playNext(false), 800);
  });
}
attachMediaEvents(audio);
attachMediaEvents(video);

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
        const y = buf[i] / 128.0 * (h / 2);
        if (i === 0) ctx.moveTo(0, y); else ctx.lineTo(i * step, y);
      }
      ctx.stroke();
    } else if (style === "ring") {
      analyser.getByteFrequencyData(buf);
      const cx = w / 2, cy = h / 2, r0 = Math.min(w, h) * 0.32;
      for (let i = 0; i < buf.length; i += 2) {
        const a = i / buf.length * Math.PI * 2;
        const len = buf[i] / 255 * r0;
        const x1 = cx + Math.cos(a) * r0;
        const y1 = cy + Math.sin(a) * r0;
        const x2 = cx + Math.cos(a) * (r0 + len);
        const y2 = cy + Math.sin(a) * (r0 + len);
        ctx.strokeStyle = accent; ctx.lineWidth = 2 * dpr;
        ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
      }
    } else {
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

// =====================================================================
// 22. Wiring: seek, volume, transport, panels, language picker
// =====================================================================
const onSeekMain = (sliderEl) => () => {
  const m = mediaEl();
  if (m.duration) m.currentTime = sliderEl.value / 1000 * m.duration;
};
seek.addEventListener("input", onSeekMain(seek));
fpSeek.addEventListener("input", onSeekMain(fpSeek));

vol.addEventListener("input", () => {
  const v = vol.value / 100;
  audio.volume = v; video.volume = v;
  State.lastVolume = v;
  setBtnIcon(muteBtn, v === 0 ? "volume-mute" : "volume");
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
  showToast(`${T("shuffle", "Shuffle")} ${State.shuffle ? "on" : "off"}`);
};
shuffleBtn.addEventListener("click", toggleShuffle);
fpShuffle.addEventListener("click", toggleShuffle);

const cycleRepeat = () => {
  State.repeat = State.repeat === "off" ? "all" : State.repeat === "all" ? "one" : "off";
  const useEl = repeatBtn.querySelector("use");
  if (useEl) useEl.setAttribute("href", State.repeat === "one" ? "#ic-repeat-one" : "#ic-repeat");
  const fpUseEl = fpRepeat.querySelector("use");
  if (fpUseEl) fpUseEl.setAttribute("href", State.repeat === "one" ? "#ic-repeat-one" : "#ic-repeat");
  repeatBtn.classList.toggle("active", State.repeat !== "off");
  fpRepeat.classList.toggle("active", State.repeat !== "off");
  showToast(`${T("repeat", "Repeat")}: ${State.repeat}`);
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
    setBtnIcon(muteBtn, "volume-mute");
  } else {
    const nv = State.lastVolume || 0.8;
    audio.volume = nv; video.volume = nv;
    vol.value = nv * 100;
    setBtnIcon(muteBtn, "volume");
  }
});

$("#npClickArea").addEventListener("click", openFullPlayer);
expandBtn.addEventListener("click", openFullPlayer);
fpClose.addEventListener("click", closeFullPlayer);

const toggleVideoMode = () => {
  if (!State.queue[State.queueIndex]) {
    showToast(T("hello_blank_fragment", "Play something first"));
    return;
  }
  State.mode = State.mode === "video" ? "audio" : "video";
  showToast(`${T("videos", "Video mode")} ${State.mode === "video" ? "on" : "off"}`);
  videoModeBtn.classList.toggle("active", State.mode === "video");
  fpVideoToggle.classList.toggle("active", State.mode === "video");

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

fpDownload.addEventListener("click", () => {
  const cur = State.queue[State.queueIndex];
  if (!cur) { showToast(T("hello_blank_fragment", "Play something first")); return; }
  Offline.download(cur);
});

function updatePanelLayout() {
  const open =
    !fpLyricsPanel.classList.contains("hidden") ||
    !fpQueuePanel.classList.contains("hidden");
  fullPlayer.classList.toggle("has-panel", open);
}

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

// Language picker (topbar)
function renderLangMenu() {
  langMenu.innerHTML = I18N.available.map((l) =>
    `<div class="lang-item ${l.code === I18N.code ? "active" : ""}" data-code="${l.code}">
            <span>${escapeHtml(l.name)}</span>${icon("check", "ic-sm")}
        </div>`).join("") || `<div class="empty">No locales</div>`;
  $$(".lang-item", langMenu).forEach((el) => {
    el.addEventListener("click", async () => {
      await I18N.setLocale(el.dataset.code);
      renderLangMenu();
      langMenu.classList.add("hidden");
    });
  });
}
langBtn.addEventListener("click", (e) => {
  e.stopPropagation();
  if (langMenu.classList.contains("hidden")) renderLangMenu();
  langMenu.classList.toggle("hidden");
});
document.addEventListener("click", (e) => {
  if (!langMenu.classList.contains("hidden") && !langMenu.contains(e.target) && e.target !== langBtn) {
    langMenu.classList.add("hidden");
  }
});

// Search suggestions
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
        `<div class="suggest-item" data-i="${i}" data-q="${escapeHtml(s)}">${icon("search", "ic-sm")} ${escapeHtml(s)}</div>`
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

// =====================================================================
// 23. Sleep timer + global keyboard shortcuts
// =====================================================================
function scheduleSleepTimer() {
  if (State.sleepTimerId) clearTimeout(State.sleepTimerId);
  State.sleepTimerId = null;
  const m = State.settings.sleepMin;
  if (!m) return;
  State.sleepTimerId = setTimeout(() => {
    mediaEl().pause();
    showToast(T("save_last_played", "Sleep timer triggered. Goodnight"));
  }, m * 60 * 1000);
}

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
    case "d": case "D": {
      const cur = State.queue[State.queueIndex];
      if (cur) Offline.download(cur);
      break;
    }
    case "Escape":
      if (!ctxMenu.classList.contains("hidden")) closeContextMenu();
      else if (!modal.classList.contains("hidden")) closeModal();
      else if (!fullPlayer.classList.contains("hidden")) closeFullPlayer();
      break;
    case "?": navigate("/shortcuts"); break;
  }
});

// =====================================================================
// 24. Boot
// =====================================================================
(async function boot() {
  applyTheme();
  renderSidebarPlaylists();

  // Initial icon states
  setBtnIcon(playBtn, "play");
  setBtnIcon(fpPlay, "play");
  setBtnIcon(muteBtn, "volume");

  // i18n
  await I18N.loadIndex();
  const initial = I18N.pickInitialLocale();
  await I18N.load(initial);
  I18N.apply();

  // Offline DB
  await Offline.init();
  refreshDlBadge();

  router(true);
})();
