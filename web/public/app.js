// SakayoriMusic Web — frontend SPA
// Recreates the Android/Compose app's UX: shelves on the home screen, mini
// player at the bottom, and a fullscreen "Now Playing" with a spinning vinyl
// disc, blurred album-art backdrop, slide-up lyrics + queue panels.

// -----------------------------------------------------------------------------
// State
// -----------------------------------------------------------------------------
const State = {
    view: "home",
    queue: [],
    queueIndex: -1,
    shuffle: false,
    repeat: "off", // "off" | "all" | "one"
    liked: new Set(JSON.parse(localStorage.getItem("liked") || "[]")),
    history: [],
    lastVolume: 0.8,
    lyrics: null, // { synced, lines: [{t, text}] }
};

// -----------------------------------------------------------------------------
// DOM
// -----------------------------------------------------------------------------
const $ = (sel) => document.querySelector(sel);
const audio = $("#audio");
const view = $("#view");

// Mini player
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
const seek = $("#seek");
const curTime = $("#curTime");
const durTime = $("#durTime");
const vol = $("#vol");

// Fullscreen player
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
const fpLyricsPanel = $("#fpLyrics");
const fpLyricsInner = $("#fpLyricsInner");
const fpQueuePanel = $("#fpQueuePanel");
const fpQueueList = $("#fpQueueList");
const fpQueueClose = $("#fpQueueClose");
const vinyl = $("#vinyl");
const vinylArt = $("#vinylArt");
const tonearm = $("#tonearm");

const toast = $("#toast");
const shortcutsModal = $("#shortcutsModal");

// -----------------------------------------------------------------------------
// Utils
// -----------------------------------------------------------------------------
const fmt = (s) => {
    if (!Number.isFinite(s)) return "0:00";
    s = Math.max(0, Math.floor(s));
    const m = Math.floor(s / 60);
    const sec = String(s % 60).padStart(2, "0");
    return `${m}:${sec}`;
};

const showToast = (msg) => {
    toast.textContent = msg;
    toast.classList.remove("hidden");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => toast.classList.add("hidden"), 1600);
};

const persistLiked = () =>
    localStorage.setItem("liked", JSON.stringify([...State.liked]));

const escapeHtml = (s) =>
    String(s ?? "").replace(/[&<>"]/g, (c) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;",
    }[c]));

const artistsText = (a) =>
    Array.isArray(a) ? a.map((x) => x.name).filter(Boolean).join(", ") : "";

// Pick a vibrant accent color from the album art (sample center pixel via canvas).
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
            } catch (_e) {
                resolve(null);
            }
        };
        img.onerror = () => resolve(null);
        img.src = url;
    });
}

// -----------------------------------------------------------------------------
// API
// -----------------------------------------------------------------------------
const api = {
    home: () => fetch("/api/home").then((r) => r.json()),
    search: (q, type = "songs") =>
        fetch(`/api/search?q=${encodeURIComponent(q)}&type=${type}`).then((r) =>
            r.json()
        ),
    upNext: (videoId) =>
        fetch(`/api/up-next/${videoId}`).then((r) => r.json()),
    lyrics: ({ title, artist, album, duration }) => {
        const p = new URLSearchParams({ title, artist });
        if (album) p.set("album", album);
        if (duration) p.set("duration", duration);
        return fetch(`/api/lyrics?${p}`).then((r) => r.json());
    },
};

// -----------------------------------------------------------------------------
// Views
// -----------------------------------------------------------------------------
async function renderHome() {
    State.view = "home";
    setActiveNav("home");
    view.innerHTML = `
        <h1 class="section-title">Welcome back</h1>
        <div id="shelves"></div>
        <div class="empty" id="homeLoading">Loading…</div>
    `;
    try {
        const { shelves } = await api.home();
        const html = shelves
            .map((shelf) => {
                if (!shelf.items?.length) return "";
                return `
                <section class="shelf">
                    <h2>${escapeHtml(shelf.title)}</h2>
                    <div class="shelf-row">
                        ${shelf.items
                        .map(
                            (s) => `
                            <div class="card" data-vid="${s.videoId}">
                                <img loading="lazy" src="${s.thumbnail?.url || ""}" alt=""/>
                                <div class="t">${escapeHtml(s.name)}</div>
                                <div class="a">${escapeHtml(artistsText(s.artists))}</div>
                            </div>`
                        )
                        .join("")}
                    </div>
                </section>`;
            })
            .join("");
        $("#shelves").innerHTML = html || `<div class="empty">No content</div>`;
        $("#homeLoading")?.remove();

        // Build a richer queue object so we don't lose metadata.
        const shelfMap = new Map();
        shelves.forEach((sh) =>
            sh.items.forEach((it) => shelfMap.set(it.videoId, it))
        );
        view.querySelectorAll(".card[data-vid]").forEach((el) => {
            el.addEventListener("click", () => {
                const vid = el.dataset.vid;
                const shelf = el.closest(".shelf");
                const cards = [...shelf.querySelectorAll(".card[data-vid]")];
                const queue = cards.map(
                    (c) => shelfMap.get(c.dataset.vid) || { videoId: c.dataset.vid }
                );
                playQueue(queue, queue.findIndex((s) => s.videoId === vid));
            });
        });
    } catch (e) {
        $("#shelves").innerHTML = `<div class="empty">Failed to load home: ${escapeHtml(e.message)}</div>`;
    }
}

async function renderSearch(q, type = "songs") {
    State.view = "search";
    setActiveNav("search");
    view.innerHTML = `<h1 class="section-title">Searching “${escapeHtml(q)}”…</h1>`;
    try {
        const { results } = await api.search(q, type);
        if (!results.length) {
            view.innerHTML = `<div class="empty">No results.</div>`;
            return;
        }
        const songish = results.filter((r) => r.videoId);
        view.innerHTML = `
            <h1 class="section-title">Results for “${escapeHtml(q)}”</h1>
            <div class="list" id="searchList"></div>
        `;
        const list = $("#searchList");
        results.forEach((r, i) => {
            if (r.videoId) {
                list.appendChild(songRow(r, i, songish));
            } else {
                const div = document.createElement("div");
                div.className = "row";
                div.innerHTML = `
                    <div class="num">${i + 1}</div>
                    <img src="${r.thumbnail?.url || ""}" alt=""/>
                    <div>
                        <div class="meta-t">${escapeHtml(r.name)}</div>
                        <div class="meta-a">${escapeHtml(r.type)} · ${escapeHtml(r.artist || "")}</div>
                    </div>
                    <div class="dur"></div>
                    <div></div>
                `;
                list.appendChild(div);
            }
        });
    } catch (e) {
        view.innerHTML = `<div class="empty">Search failed: ${escapeHtml(e.message)}</div>`;
    }
}

function songRow(s, idx, queue) {
    const div = document.createElement("div");
    div.className = "row";
    if (
        State.queueIndex >= 0 &&
        State.queue[State.queueIndex]?.videoId === s.videoId
    ) {
        div.classList.add("playing");
    }
    div.innerHTML = `
        <div class="num">${idx + 1}</div>
        <img loading="lazy" src="${s.thumbnail?.url || ""}" alt=""/>
        <div>
            <div class="meta-t">${escapeHtml(s.name)}</div>
            <div class="meta-a">${escapeHtml(artistsText(s.artists))}</div>
        </div>
        <div class="dur">${s.duration ? fmt(s.duration) : ""}</div>
        <button class="icon-btn" title="Like">${State.liked.has(s.videoId) ? "♥" : "♡"}</button>
    `;
    div.addEventListener("click", (e) => {
        if (e.target.tagName === "BUTTON") return;
        playQueue(queue, idx);
    });
    div.querySelector("button").addEventListener("click", (e) => {
        e.stopPropagation();
        toggleLike(s);
        e.target.textContent = State.liked.has(s.videoId) ? "♥" : "♡";
    });
    return div;
}

function renderQueue() {
    State.view = "queue";
    setActiveNav("queue");
    if (!State.queue.length) {
        view.innerHTML = `<div class="empty">Queue is empty. Play something to fill it up.</div>`;
        return;
    }
    view.innerHTML = `<h1 class="section-title">Queue</h1><div class="list" id="qList"></div>`;
    const list = $("#qList");
    State.queue.forEach((s, i) => list.appendChild(songRow(s, i, State.queue)));
}

function renderLibrary() {
    State.view = "library";
    setActiveNav("library");
    const items = [...State.liked].map(
        (vid) =>
            State.queue.find((s) => s.videoId === vid) || {
                videoId: vid,
                name: vid,
                artists: [],
            }
    );
    if (!items.length) {
        view.innerHTML = `<div class="empty">You haven't liked anything yet. Press <kbd>L</kbd> while a song plays.</div>`;
        return;
    }
    view.innerHTML = `<h1 class="section-title">Liked Songs</h1><div class="list" id="libList"></div>`;
    const list = $("#libList");
    items.forEach((s, i) => list.appendChild(songRow(s, i, items)));
}

function renderShortcuts() {
    setActiveNav("shortcuts");
    shortcutsModal.classList.remove("hidden");
}

// -----------------------------------------------------------------------------
// Lyrics (rendered inside the fullscreen player's slide-up panel)
// -----------------------------------------------------------------------------
async function loadLyricsForCurrent() {
    const cur = State.queue[State.queueIndex];
    if (!cur) return;
    fpLyricsInner.innerHTML = `<div class="empty">Loading lyrics…</div>`;
    try {
        const data = await api.lyrics({
            title: cur.name,
            artist: artistsText(cur.artists),
            album: cur.album?.name,
            duration: cur.duration,
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
        return { synced: true, lines };
    }
    return {
        synced: false,
        lines: (data.plain || "").split(/\r?\n/).map((text) => ({ t: -1, text })),
    };
}

function renderLyricLines() {
    if (!State.lyrics) {
        fpLyricsInner.innerHTML = `<div class="empty">No lyrics found for this track.</div>`;
        return;
    }
    fpLyricsInner.innerHTML = State.lyrics.lines
        .map(
            (l, i) =>
                `<div class="lyric-line" data-i="${i}">${escapeHtml(l.text) || "&nbsp;"}</div>`
        )
        .join("");
}

function tickLyrics() {
    if (fpLyricsPanel.classList.contains("hidden") || !State.lyrics?.synced) return;
    const t = audio.currentTime;
    const lines = State.lyrics.lines;
    let activeIdx = -1;
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].t <= t) activeIdx = i;
        else break;
    }
    const els = fpLyricsInner.querySelectorAll(".lyric-line");
    els.forEach((el, i) => {
        if (i === activeIdx) {
            if (!el.classList.contains("active")) {
                el.classList.add("active");
                el.scrollIntoView({ behavior: "smooth", block: "center" });
            }
        } else {
            el.classList.remove("active");
        }
    });
}

// -----------------------------------------------------------------------------
// Fullscreen player open/close
// -----------------------------------------------------------------------------
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
        vinylArt.removeAttribute("src");
        fpBackdrop.style.backgroundImage = "";
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
    renderFpQueue();
}

function renderFpQueue() {
    fpQueueList.innerHTML = "";
    State.queue.forEach((s, i) => {
        const r = songRow(s, i, State.queue);
        // re-bind click so it doesn't navigate to our normal view, just play
        const fresh = r.cloneNode(true);
        fresh.addEventListener("click", (e) => {
            if (e.target.tagName === "BUTTON") return;
            State.queueIndex = i;
            playCurrent();
        });
        fresh.querySelector("button")?.addEventListener("click", (e) => {
            e.stopPropagation();
            toggleLike(s);
            e.target.textContent = State.liked.has(s.videoId) ? "♥" : "♡";
        });
        fpQueueList.appendChild(fresh);
    });
}

// -----------------------------------------------------------------------------
// Player
// -----------------------------------------------------------------------------
async function playQueue(queue, startIndex) {
    State.queue = queue.slice();
    State.queueIndex = startIndex;
    await playCurrent();

    // Auto-extend the queue with YT Music's "up next" radio.
    const cur = State.queue[State.queueIndex];
    if (cur) {
        api.upNext(cur.videoId)
            .then(({ songs }) => {
                if (!songs?.length) return;
                const ids = new Set(State.queue.map((s) => s.videoId));
                for (const s of songs) {
                    if (!ids.has(s.videoId)) State.queue.push(s);
                }
                if (State.view === "queue") renderQueue();
                if (!fullPlayer.classList.contains("hidden")) renderFpQueue();
            })
            .catch(() => { });
    }
}

async function playCurrent() {
    const s = State.queue[State.queueIndex];
    if (!s) return;
    State.history.push(s.videoId);

    audio.src = `/api/stream/${s.videoId}`;
    audio.play().catch((err) => showToast("Playback failed: " + err.message));

    // Mini player
    npTitle.textContent = s.name;
    npArtist.textContent = artistsText(s.artists);
    npArt.src = s.thumbnail?.url || "";
    likeBtn.textContent = State.liked.has(s.videoId) ? "♥" : "♡";

    // Refresh visible views.
    if (State.view === "queue") renderQueue();

    // Fullscreen player + theme color.
    syncFullPlayerUi();
    if (s.thumbnail?.url) {
        const c = await extractAccentFromImage(s.thumbnail.url);
        if (c) document.documentElement.style.setProperty("--art-color", c);
    }

    // If fullscreen player is open and lyrics were showing, refresh them.
    if (!fpLyricsPanel.classList.contains("hidden")) loadLyricsForCurrent();

    // MediaSession (lock screen / OS media keys)
    if ("mediaSession" in navigator) {
        navigator.mediaSession.metadata = new MediaMetadata({
            title: s.name,
            artist: artistsText(s.artists),
            album: s.album?.name || "",
            artwork: s.thumbnail?.url
                ? [{ src: s.thumbnail.url, sizes: "512x512", type: "image/jpeg" }]
                : [],
        });
        navigator.mediaSession.setActionHandler("play", () => audio.play());
        navigator.mediaSession.setActionHandler("pause", () => audio.pause());
        navigator.mediaSession.setActionHandler("nexttrack", () => playNext(false));
        navigator.mediaSession.setActionHandler("previoustrack", playPrev);
    }
}

function playNext(auto = false) {
    if (!State.queue.length) return;
    if (State.repeat === "one" && auto) {
        audio.currentTime = 0;
        audio.play();
        return;
    }
    let next;
    if (State.shuffle) {
        next = Math.floor(Math.random() * State.queue.length);
    } else {
        next = State.queueIndex + 1;
        if (next >= State.queue.length) {
            if (State.repeat === "all") next = 0;
            else {
                audio.pause();
                return;
            }
        }
    }
    State.queueIndex = next;
    playCurrent();
}

function playPrev() {
    if (!State.queue.length) return;
    if (audio.currentTime > 3) {
        audio.currentTime = 0;
        return;
    }
    State.queueIndex = Math.max(0, State.queueIndex - 1);
    playCurrent();
}

function toggleLike(song) {
    if (!song?.videoId) return;
    if (State.liked.has(song.videoId)) {
        State.liked.delete(song.videoId);
        showToast("Removed from liked");
    } else {
        State.liked.add(song.videoId);
        showToast("Added to liked");
    }
    persistLiked();
    const cur = State.queue[State.queueIndex];
    if (cur?.videoId === song.videoId) {
        likeBtn.textContent = State.liked.has(song.videoId) ? "♥" : "♡";
        fpLike.classList.toggle("active", State.liked.has(song.videoId));
    }
}

// -----------------------------------------------------------------------------
// Audio events
// -----------------------------------------------------------------------------
audio.addEventListener("play", () => {
    playBtn.textContent = "⏸";
    fpPlay.textContent = "⏸";
    vinyl.classList.add("playing");
    tonearm.classList.add("playing");
});
audio.addEventListener("pause", () => {
    playBtn.textContent = "▶";
    fpPlay.textContent = "▶";
    vinyl.classList.remove("playing");
    tonearm.classList.remove("playing");
});
audio.addEventListener("ended", () => playNext(true));
audio.addEventListener("timeupdate", () => {
    if (audio.duration) {
        const pct = (audio.currentTime / audio.duration) * 1000;
        seek.value = String(pct);
        fpSeek.value = String(pct);
    }
    curTime.textContent = fmt(audio.currentTime);
    durTime.textContent = fmt(audio.duration);
    fpCurTime.textContent = fmt(audio.currentTime);
    fpDurTime.textContent = fmt(audio.duration);
    tickLyrics();
});
audio.addEventListener("error", () => {
    showToast("Playback failed: trying to recover…");
    // Try the next track instead of getting stuck.
    setTimeout(() => playNext(false), 800);
});

const onSeek = (sliderEl) => () => {
    if (audio.duration) {
        audio.currentTime = (sliderEl.value / 1000) * audio.duration;
    }
};
seek.addEventListener("input", onSeek(seek));
fpSeek.addEventListener("input", onSeek(fpSeek));

vol.addEventListener("input", () => {
    audio.volume = vol.value / 100;
    State.lastVolume = audio.volume;
    muteBtn.textContent = audio.volume === 0 ? "🔇" : "🔊";
});
audio.volume = vol.value / 100;

// -----------------------------------------------------------------------------
// Buttons (mini + fullscreen share handlers)
// -----------------------------------------------------------------------------
const togglePlay = () => (audio.paused ? audio.play() : audio.pause());
playBtn.addEventListener("click", togglePlay);
fpPlay.addEventListener("click", togglePlay);

prevBtn.addEventListener("click", playPrev);
fpPrev.addEventListener("click", playPrev);

nextBtn.addEventListener("click", () => playNext(false));
fpNext.addEventListener("click", () => playNext(false));

const toggleShuffle = () => {
    State.shuffle = !State.shuffle;
    shuffleBtn.classList.toggle("active", State.shuffle);
    fpShuffle.classList.toggle("active", State.shuffle);
    showToast("Shuffle " + (State.shuffle ? "on" : "off"));
};
shuffleBtn.addEventListener("click", toggleShuffle);
fpShuffle.addEventListener("click", toggleShuffle);

const cycleRepeat = () => {
    State.repeat =
        State.repeat === "off" ? "all" : State.repeat === "all" ? "one" : "off";
    const sym = State.repeat === "one" ? "🔂" : "🔁";
    repeatBtn.textContent = sym;
    fpRepeat.textContent = sym;
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
    if (audio.volume > 0) {
        State.lastVolume = audio.volume;
        audio.volume = 0;
        vol.value = 0;
        muteBtn.textContent = "🔇";
    } else {
        audio.volume = State.lastVolume || 0.8;
        vol.value = audio.volume * 100;
        muteBtn.textContent = "🔊";
    }
});

// Open fullscreen by clicking on mini player art / title area, or button.
$("#npClickArea").addEventListener("click", openFullPlayer);
expandBtn.addEventListener("click", openFullPlayer);
fpClose.addEventListener("click", closeFullPlayer);

// Lyrics + queue panels
fpLyricsToggle.addEventListener("click", () => {
    const wasHidden = fpLyricsPanel.classList.contains("hidden");
    fpQueuePanel.classList.add("hidden");
    fpLyricsPanel.classList.toggle("hidden");
    fpLyricsToggle.classList.toggle("active", wasHidden);
    fpQueueBtn.classList.remove("active");
    if (wasHidden) loadLyricsForCurrent();
});
fpQueueBtn.addEventListener("click", () => {
    const wasHidden = fpQueuePanel.classList.contains("hidden");
    fpLyricsPanel.classList.add("hidden");
    fpQueuePanel.classList.toggle("hidden");
    fpQueueBtn.classList.toggle("active", wasHidden);
    fpLyricsToggle.classList.remove("active");
    if (wasHidden) renderFpQueue();
});
fpQueueClose.addEventListener("click", () => {
    fpQueuePanel.classList.add("hidden");
    fpQueueBtn.classList.remove("active");
});

// -----------------------------------------------------------------------------
// Sidebar nav
// -----------------------------------------------------------------------------
function setActiveNav(name) {
    document.querySelectorAll(".nav-item").forEach((el) =>
        el.classList.toggle("active", el.dataset.view === name)
    );
}
document.querySelectorAll(".nav-item").forEach((el) => {
    el.addEventListener("click", () => {
        const v = el.dataset.view;
        if (v === "home") renderHome();
        else if (v === "search") {
            $("#searchInput").focus();
            setActiveNav("search");
        } else if (v === "queue") renderQueue();
        else if (v === "library") renderLibrary();
        else if (v === "shortcuts") renderShortcuts();
    });
});

$("#searchForm").addEventListener("submit", (e) => {
    e.preventDefault();
    const q = $("#searchInput").value.trim();
    if (!q) return;
    renderSearch(q, $("#searchType").value);
});

$("#closeShortcuts").addEventListener("click", () =>
    shortcutsModal.classList.add("hidden")
);
shortcutsModal.addEventListener("click", (e) => {
    if (e.target === shortcutsModal) shortcutsModal.classList.add("hidden");
});

// -----------------------------------------------------------------------------
// Keyboard shortcuts
// -----------------------------------------------------------------------------
window.addEventListener("keydown", (e) => {
    if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement ||
        e.target instanceof HTMLSelectElement
    ) return;

    switch (e.key) {
        case " ":
            e.preventDefault();
            togglePlay();
            break;
        case "ArrowRight":
            playNext(false);
            break;
        case "ArrowLeft":
            playPrev();
            break;
        case "ArrowUp": {
            e.preventDefault();
            const v = Math.min(100, +vol.value + 5);
            vol.value = String(v);
            vol.dispatchEvent(new Event("input"));
            showToast(`Volume ${v}%`);
            break;
        }
        case "ArrowDown": {
            e.preventDefault();
            const v = Math.max(0, +vol.value - 5);
            vol.value = String(v);
            vol.dispatchEvent(new Event("input"));
            showToast(`Volume ${v}%`);
            break;
        }
        case "m":
        case "M":
            muteBtn.click();
            break;
        case "l":
        case "L":
            likeCurrent();
            break;
        case "s":
        case "S":
            toggleShuffle();
            break;
        case "r":
        case "R":
            cycleRepeat();
            break;
        case "f":
        case "F":
            toggleFullPlayer();
            break;
        case "Escape":
            if (!fullPlayer.classList.contains("hidden")) closeFullPlayer();
            break;
        case "?":
            shortcutsModal.classList.toggle("hidden");
            break;
    }
});

// -----------------------------------------------------------------------------
// Boot
// -----------------------------------------------------------------------------
renderHome();
