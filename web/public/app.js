// SakayoriMusic Web — frontend SPA
// Mirrors the Compose Multiplatform app's player UX (queue, shuffle, repeat,
// like, lyrics, keyboard shortcuts, MediaSession integration).

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
    history: [], // played videoIds
    lastVolume: 0.8,
    lyrics: null, // { lines: [{t, text}], plain }
};

// -----------------------------------------------------------------------------
// DOM
// -----------------------------------------------------------------------------
const $ = (sel) => document.querySelector(sel);
const audio = $("#audio");
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
const seek = $("#seek");
const curTime = $("#curTime");
const durTime = $("#durTime");
const vol = $("#vol");

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
                                <img loading="lazy" src="${s.thumbnail?.url || ""
                                }" alt=""/>
                                <div class="t">${escapeHtml(s.name)}</div>
                                <div class="a">${escapeHtml(
                                    artistsText(s.artists)
                                )}</div>
                            </div>`
                        )
                        .join("")}
                    </div>
                </section>`;
            })
            .join("");
        $("#shelves").innerHTML = html || `<div class="empty">No content</div>`;
        $("#homeLoading").remove();

        // wire cards
        view.querySelectorAll(".card[data-vid]").forEach((el) => {
            el.addEventListener("click", () => {
                const vid = el.dataset.vid;
                const shelf = el.closest(".shelf");
                const all = [...shelf.querySelectorAll(".card[data-vid]")];
                const queue = all.map((card) => ({
                    videoId: card.dataset.vid,
                    name: card.querySelector(".t").textContent,
                    artists: [{ name: card.querySelector(".a").textContent }],
                    thumbnail: { url: card.querySelector("img").src },
                }));
                playQueue(queue, queue.findIndex((s) => s.videoId === vid));
            });
        });
    } catch (e) {
        $("#shelves").innerHTML = `<div class="empty">Failed to load home: ${escapeHtml(
            e.message
        )}</div>`;
        $("#homeLoading")?.remove();
    }
}

async function renderSearch(q, type = "songs") {
    State.view = "search";
    setActiveNav("search");
    view.innerHTML = `<h1 class="section-title">Searching “${escapeHtml(
        q
    )}”…</h1>`;
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
                        <div class="meta-a">${escapeHtml(r.type)} · ${escapeHtml(
                    r.artist || ""
                )}</div>
                    </div>
                    <div class="dur"></div>
                    <div></div>
                `;
                list.appendChild(div);
            }
        });
    } catch (e) {
        view.innerHTML = `<div class="empty">Search failed: ${escapeHtml(
            e.message
        )}</div>`;
    }
}

function songRow(s, idx, queue) {
    const div = document.createElement("div");
    div.className = "row";
    if (State.queueIndex >= 0 && State.queue[State.queueIndex]?.videoId === s.videoId) {
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
        <button class="icon-btn" title="Like">${State.liked.has(s.videoId) ? "♥" : "♡"
        }</button>
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
    const items = [...State.liked].map((vid) => {
        // We may not have full metadata; try to find from queue/history.
        return (
            State.queue.find((s) => s.videoId === vid) || {
                videoId: vid,
                name: vid,
                artists: [],
            }
        );
    });
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

async function renderLyrics() {
    State.view = "lyrics";
    setActiveNav("lyrics");
    const cur = State.queue[State.queueIndex];
    if (!cur) {
        view.innerHTML = `<div class="empty">Play a song to see lyrics.</div>`;
        return;
    }
    view.innerHTML = `
        <h1 class="section-title">${escapeHtml(cur.name)}</h1>
        <div class="muted" style="margin-bottom:14px">${escapeHtml(
        artistsText(cur.artists)
    )}</div>
        <div class="lyrics-wrap" id="lyricsWrap"><div class="empty">Loading lyrics…</div></div>
    `;
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
        $("#lyricsWrap").innerHTML = `<div class="empty">Failed: ${escapeHtml(
            e.message
        )}</div>`;
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
    const wrap = $("#lyricsWrap");
    if (!wrap) return;
    if (!State.lyrics) {
        wrap.innerHTML = `<div class="empty">No lyrics found.</div>`;
        return;
    }
    wrap.innerHTML = State.lyrics.lines
        .map(
            (l, i) =>
                `<div class="lyrics-line" data-i="${i}">${escapeHtml(l.text) || "&nbsp;"
                }</div>`
        )
        .join("");
}

function tickLyrics() {
    if (State.view !== "lyrics" || !State.lyrics?.synced) return;
    const t = audio.currentTime;
    const lines = State.lyrics.lines;
    let activeIdx = -1;
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].t <= t) activeIdx = i;
        else break;
    }
    document.querySelectorAll(".lyrics-line").forEach((el, i) => {
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
// Player
// -----------------------------------------------------------------------------
async function playQueue(queue, startIndex) {
    State.queue = queue.slice();
    State.queueIndex = startIndex;
    await playCurrent();
    // Pre-fetch radio/up-next to extend the queue.
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

    npTitle.textContent = s.name;
    npArtist.textContent = artistsText(s.artists);
    npArt.src = s.thumbnail?.url || "";
    likeBtn.textContent = State.liked.has(s.videoId) ? "♥" : "♡";

    // Refresh playing-row highlight
    if (State.view === "queue") renderQueue();
    if (State.view === "lyrics") renderLyrics();

    // MediaSession integration (so OS media keys & lock screen work)
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
        navigator.mediaSession.setActionHandler("nexttrack", playNext);
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
    if (State.liked.has(song.videoId)) {
        State.liked.delete(song.videoId);
        showToast("Removed from liked");
    } else {
        State.liked.add(song.videoId);
        showToast("Added to liked");
    }
    persistLiked();
    likeBtn.textContent =
        State.queue[State.queueIndex]?.videoId === song.videoId
            ? State.liked.has(song.videoId) ? "♥" : "♡"
            : likeBtn.textContent;
}

// -----------------------------------------------------------------------------
// Audio events
// -----------------------------------------------------------------------------
audio.addEventListener("play", () => (playBtn.textContent = "⏸"));
audio.addEventListener("pause", () => (playBtn.textContent = "▶"));
audio.addEventListener("ended", () => playNext(true));
audio.addEventListener("timeupdate", () => {
    if (audio.duration) {
        seek.value = String((audio.currentTime / audio.duration) * 1000);
    }
    curTime.textContent = fmt(audio.currentTime);
    durTime.textContent = fmt(audio.duration);
    tickLyrics();
});

seek.addEventListener("input", () => {
    if (audio.duration) {
        audio.currentTime = (seek.value / 1000) * audio.duration;
    }
});

vol.addEventListener("input", () => {
    audio.volume = vol.value / 100;
    State.lastVolume = audio.volume;
    muteBtn.textContent = audio.volume === 0 ? "🔇" : "🔊";
});
audio.volume = vol.value / 100;

// -----------------------------------------------------------------------------
// Buttons
// -----------------------------------------------------------------------------
playBtn.addEventListener("click", () => {
    if (audio.paused) audio.play();
    else audio.pause();
});
prevBtn.addEventListener("click", playPrev);
nextBtn.addEventListener("click", () => playNext(false));

shuffleBtn.addEventListener("click", () => {
    State.shuffle = !State.shuffle;
    shuffleBtn.classList.toggle("active", State.shuffle);
    showToast("Shuffle " + (State.shuffle ? "on" : "off"));
});

repeatBtn.addEventListener("click", () => {
    State.repeat =
        State.repeat === "off" ? "all" : State.repeat === "all" ? "one" : "off";
    repeatBtn.classList.toggle("active", State.repeat !== "off");
    repeatBtn.textContent = State.repeat === "one" ? "🔂" : "🔁";
    showToast("Repeat: " + State.repeat);
});

likeBtn.addEventListener("click", () => {
    const s = State.queue[State.queueIndex];
    if (s) toggleLike(s);
});

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
        else if (v === "lyrics") renderLyrics();
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
// Keyboard shortcuts (matches the README table)
// -----------------------------------------------------------------------------
window.addEventListener("keydown", (e) => {
    // Ignore typing in inputs
    if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement ||
        e.target instanceof HTMLSelectElement
    ) {
        return;
    }
    switch (e.key) {
        case " ":
            e.preventDefault();
            playBtn.click();
            break;
        case "ArrowRight":
            nextBtn.click();
            break;
        case "ArrowLeft":
            prevBtn.click();
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
            likeBtn.click();
            break;
        case "s":
        case "S":
            shuffleBtn.click();
            break;
        case "r":
        case "R":
            repeatBtn.click();
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
