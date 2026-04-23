

const path = require("path");
const fs = require("fs");
const os = require("os");
const { spawn } = require("child_process");
const { Readable } = require("stream");

const express = require("express");
const cors = require("cors");
const compression = require("compression");
const { LRUCache } = require("lru-cache");
const { Agent, fetch: undiciFetch } = require("undici");

// ---------------------------------------------------------------------------
// i18n bootstrap
//
// We mirror the Android Compose Multiplatform translations
// (composeApp/src/commonMain/composeResources/values*/strings.xml) into
// web-friendly JSON files at web/public/locales/<bcp47>.json.  The web app
// reads them at runtime — so there's literally a single source of truth
// across Android, Desktop and Web for every UI string.
// ---------------------------------------------------------------------------
const LOCALES_DIR = path.join(__dirname, "public", "locales");
const COMPOSE_RES_DIR = path.resolve(
  __dirname, "..",
  "composeApp", "src", "commonMain", "composeResources"
);

// values-<android> → BCP-47 we expose to the browser
const ANDROID_TO_BCP47 = {
  "values": "en",
  "values-ar": "ar",
  "values-az": "az",
  "values-bg": "bg",
  "values-ca": "ca",
  "values-cs": "cs",
  "values-de": "de",
  "values-el": "el",
  "values-en-rGG": "en-GG",
  "values-en-rIO": "en-IO",
  "values-en-rLC": "en-LC",
  "values-en-rMS": "en-MS",
  "values-en-rPH": "en-PH",
  "values-es": "es",
  "values-fa": "fa",
  "values-fi": "fi",
  "values-fr": "fr",
  "values-hi": "hi",
  "values-hu": "hu",
  "values-in": "id",   // Android legacy alias
  "values-it": "it",
  "values-iw": "he",   // Android legacy alias
  "values-ja": "ja",
  "values-ko": "ko",
  "values-nl": "nl",
  "values-pl": "pl",
  "values-pt": "pt",
  "values-ro": "ro",
  "values-ru": "ru",
  "values-sv": "sv",
  "values-th": "th",
  "values-tr": "tr",
  "values-uk": "uk",
  "values-vi": "vi",
  "values-zh": "zh",
  "values-zh-rTW": "zh-TW",
};

const LOCALE_NATIVE_NAMES = {
  "en": "English",
  "ar": "العربية",
  "az": "Azərbaycan",
  "bg": "Български",
  "ca": "Català",
  "cs": "Čeština",
  "de": "Deutsch",
  "el": "Ελληνικά",
  "en-GG": "English (Guernsey)",
  "en-IO": "English (BIOT)",
  "en-LC": "English (Saint Lucia)",
  "en-MS": "English (Montserrat)",
  "en-PH": "English (Philippines)",
  "es": "Español",
  "fa": "فارسی",
  "fi": "Suomi",
  "fr": "Français",
  "hi": "हिन्दी",
  "hu": "Magyar",
  "id": "Bahasa Indonesia",
  "it": "Italiano",
  "he": "עברית",
  "ja": "日本語",
  "ko": "한국어",
  "nl": "Nederlands",
  "pl": "Polski",
  "pt": "Português",
  "ro": "Română",
  "ru": "Русский",
  "sv": "Svenska",
  "th": "ไทย",
  "tr": "Türkçe",
  "uk": "Українська",
  "vi": "Tiếng Việt",
  "zh": "中文",
  "zh-TW": "繁體中文",
};

const RTL_LOCALES = new Set(["ar", "fa", "he"]);

// Tiny standalone <string name="key">value</string> parser. Handles XML
// entities, escaped apostrophes (\'), CDATA blocks, and translatable=false.
function parseAndroidStringsXml(xml) {
  const out = {};
  const decode = (s) =>
    s
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&amp;/g, "&")
      .replace(/&quot;/g, '"')
      .replace(/&apos;/g, "'")
      .replace(/\\'/g, "'")
      .replace(/\\"/g, '"')
      .replace(/\\n/g, "\n");

  // Match each <string name="..."> ... </string>; .*? with /s flag.
  const re = /<string\s+([^>]*?)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(xml))) {
    const attrs = m[1];
    const inner = m[2];

    const nameMatch = /name\s*=\s*"([^"]+)"/.exec(attrs);
    if (!nameMatch) continue;

    let body = inner;
    const cdata = /<!\[CDATA\[([\s\S]*?)\]\]>/.exec(body);
    if (cdata) body = cdata[1];
    out[nameMatch[1]] = decode(body).trim();
  }
  return out;
}

const I18N_INDEX = { default: "en", locales: [] };

function bootstrapLocales() {
  try {
    fs.mkdirSync(LOCALES_DIR, { recursive: true });
  } catch { /* noop */ }

  if (!fs.existsSync(COMPOSE_RES_DIR)) {
    console.warn(
      `[i18n] composeResources not found at ${COMPOSE_RES_DIR}; ` +
      `falling back to whatever's already in web/public/locales/`
    );
    return;
  }

  let total = 0;
  const exposed = [];
  for (const [androidKey, bcp47] of Object.entries(ANDROID_TO_BCP47)) {
    const xmlPath = path.join(COMPOSE_RES_DIR, androidKey, "strings.xml");
    if (!fs.existsSync(xmlPath)) continue;
    try {
      const text = fs.readFileSync(xmlPath, "utf8");
      const map = parseAndroidStringsXml(text);
      const json = {
        code: bcp47,
        name: LOCALE_NATIVE_NAMES[bcp47] || bcp47,
        rtl: RTL_LOCALES.has(bcp47),
        strings: map,
      };
      fs.writeFileSync(
        path.join(LOCALES_DIR, `${bcp47}.json`),
        JSON.stringify(json, null, 0) + "\n"
      );
      exposed.push({
        code: bcp47,
        name: LOCALE_NATIVE_NAMES[bcp47] || bcp47,
        rtl: RTL_LOCALES.has(bcp47),
        strings: Object.keys(map).length,
      });
      total += Object.keys(map).length;
    } catch (e) {
      console.warn(`[i18n] failed ${androidKey}: ${e.message}`);
    }
  }

  exposed.sort((a, b) => a.name.localeCompare(b.name));
  I18N_INDEX.locales = exposed;
  fs.writeFileSync(
    path.join(LOCALES_DIR, "_index.json"),
    JSON.stringify(I18N_INDEX, null, 0) + "\n"
  );
  console.log(
    `[i18n] published ${exposed.length} locale(s) (${total} strings total) ` +
    `→ web/public/locales/`
  );
}
bootstrapLocales();


const HOST = process.env.HOST || "0.0.0.0";
const PORT = Number(process.env.PORT || 3000);
let YT_DLP = process.env.YT_DLP || "yt-dlp";

// ---------------------------------------------------------------------------
// yt-dlp bootstrap
//
// If `yt-dlp` (or whatever YT_DLP points at) isn't on PATH, we download the
// official standalone binary from the latest GitHub release into web/bin/
// and use that.  This works on Windows / macOS / Linux without needing
// Python at all (the binary is a PyInstaller-bundled CPython).
// ---------------------------------------------------------------------------
const BIN_DIR = path.join(__dirname, "bin");
function ytDlpAssetName() {
  if (process.platform === "win32") return "yt-dlp.exe";
  if (process.platform === "darwin") return "yt-dlp_macos";
  return "yt-dlp"; // Linux / everything else
}
function checkYtDlp(cmd) {
  return new Promise((resolve) => {
    try {
      const p = spawn(cmd, ["--version"], { windowsHide: true });
      p.on("error", () => resolve(false));
      p.on("close", (code) => resolve(code === 0));
    } catch { resolve(false); }
  });
}
async function ensureYtDlp() {
  if (await checkYtDlp(YT_DLP)) return true;
  console.warn(`[yt-dlp] '${YT_DLP}' not found on PATH — bootstrapping…`);
  try { fs.mkdirSync(BIN_DIR, { recursive: true }); } catch { /* noop */ }
  const dst = path.join(BIN_DIR, ytDlpAssetName());
  if (fs.existsSync(dst) && (await checkYtDlp(dst))) {
    YT_DLP = dst;
    console.log(`[yt-dlp] using cached binary at ${dst}`);
    return true;
  }
  const url = `https://github.com/yt-dlp/yt-dlp/releases/latest/download/${ytDlpAssetName()}`;
  console.log(`[yt-dlp] downloading ${url}`);
  try {
    const r = await undiciFetch(url, {
      headers: { "User-Agent": "SakayoriMusicWeb/2.1" },
    });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const ab = await r.arrayBuffer();
    fs.writeFileSync(dst, Buffer.from(ab));
    if (process.platform !== "win32") {
      try { fs.chmodSync(dst, 0o755); } catch { /* noop */ }
    }
    if (await checkYtDlp(dst)) {
      YT_DLP = dst;
      console.log(`[yt-dlp] ready at ${dst}`);
      return true;
    }
    console.error("[yt-dlp] downloaded binary failed to run");
  } catch (e) {
    console.error(`[yt-dlp] download failed: ${e.message}`);
    console.error('[yt-dlp]   → install manually:  pip install -U yt-dlp');
  }
  return false;
}


const upstreamAgent = new Agent({
  keepAliveTimeout: 60_000,
  keepAliveMaxTimeout: 600_000,
  connections: 64,
  pipelining: 1
});

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
  "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

const YT_COOKIES = process.env.YT_COOKIES || "";
const YT_COOKIES_BROWSER = process.env.YT_COOKIES_BROWSER || "";
const COOKIES_DIR = path.join(__dirname, "cookies");
const COOKIES_TMP = path.join(os.tmpdir(), "smweb-cookies");

let cookiePool = [];
let deadCookies = new Set();

function sanitizeCookieText(text) {
  const lines = String(text || "").split(/\r?\n/);
  const out = [];
  for (const line of lines) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const parts = line.split("\t");
    if (parts.length < 7) continue;
    if (!/^\.?[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(parts[0])) continue;
    if (!/^(TRUE|FALSE)$/i.test(parts[1])) continue;
    out.push(line);
  }
  if (!out.length) return null;
  return (
    "# Netscape HTTP Cookie File\n" +
    "# Auto-normalized by SakayoriMusic Web\n\n" +
    out.join("\n") +
    "\n");

}

function loadCookiePool() {
  cookiePool = [];
  deadCookies.clear();
  if (!fs.existsSync(COOKIES_DIR)) return;
  try { fs.mkdirSync(COOKIES_TMP, { recursive: true }); } catch { }

  let files = [];
  try { files = fs.readdirSync(COOKIES_DIR); } catch { return; }

  for (const f of files) {
    const src = path.join(COOKIES_DIR, f);
    try {
      const stat = fs.statSync(src);
      if (!stat.isFile()) continue;
      const text = fs.readFileSync(src, "utf8");
      const norm = sanitizeCookieText(text);
      if (!norm) {
        console.warn(`[cookies] skipped ${f}: no valid cookie lines`);
        continue;
      }
      const dst = path.join(
        COOKIES_TMP,
        f.replace(/[^a-z0-9._-]/gi, "_") + ".cookies.txt"
      );
      fs.writeFileSync(dst, norm);
      cookiePool.push({ id: f, normalizedPath: dst });
    } catch (e) {
      console.warn(`[cookies] skipped ${f}: ${e.message}`);
    }
  }
  if (cookiePool.length) {
    console.log(
      `[cookies] loaded ${cookiePool.length} cookie file(s) from cookies/ ` +
      `(rotation enabled)`
    );
  }
}
loadCookiePool();

(function autoDetectSingleCookie() {
  if (cookiePool.length || YT_COOKIES || YT_COOKIES_BROWSER) return;
  const candidates = [
    path.join(__dirname, "cookies.txt"),
    path.join(__dirname, "youtube-cookies.txt"),
    path.join(__dirname, "yt-cookies.txt")];

  for (const c of candidates) {
    try {
      if (fs.existsSync(c)) {
        process.env.YT_COOKIES = c;
        console.log(`[cookies] using single file: ${c}`);
        return;
      }
    } catch { }
  }
})();

// ---------------------------------------------------------------------------
// Auto-fetch a community cookies pack when the local pool is empty.
//   • Default URL is overrideable via env var YT_COOKIES_URL.
//   • Refreshed every COOKIES_REFRESH_MIN minutes (default: 60).
//   • The fetched file is dropped into web/cookies/ and the pool reloads.
// ---------------------------------------------------------------------------
const REMOTE_COOKIES_URL =
  process.env.YT_COOKIES_URL ||
  "https://gist.githubusercontent.com/giangnam0201/1354209f7de870423e24b5e597b15e02/raw/95062eeaf75a95ff3f2cb0b299b7004e390645ae/gistfile1.txt";

const REMOTE_COOKIES_REFRESH_MIN =
  parseInt(process.env.YT_COOKIES_REFRESH_MIN || "60", 10);

async function fetchRemoteCookies({ silent = false } = {}) {
  if (!REMOTE_COOKIES_URL) return false;
  try {
    if (!silent) console.log(`[cookies] fetching ${REMOTE_COOKIES_URL} …`);
    const r = await undiciFetch(REMOTE_COOKIES_URL, {
      headers: { "User-Agent": UA },
    });
    if (!r.ok) {
      if (!silent) console.warn(`[cookies] remote fetch failed: HTTP ${r.status}`);
      return false;
    }
    const text = await r.text();
    if (!text || !text.trim()) {
      if (!silent) console.warn("[cookies] remote file is empty");
      return false;
    }
    try { fs.mkdirSync(COOKIES_DIR, { recursive: true }); } catch { /* noop */ }
    const dst = path.join(COOKIES_DIR, "_remote.txt");
    fs.writeFileSync(dst, text);
    loadCookiePool();
    if (cookiePool.length) {
      console.log(`[cookies] remote pool ready (${cookiePool.length} accounts)`);
    }
    return cookiePool.length > 0;
  } catch (e) {
    if (!silent) console.warn(`[cookies] remote fetch failed: ${e.message}`);
    return false;
  }
}

// On startup, if there's no local pool / env-var cookie / browser cookie,
// pull the community pack so the very first request has cookies to use.
(async function autoFetchOnBoot() {
  if (cookiePool.length || process.env.YT_COOKIES || YT_COOKIES_BROWSER) return;
  await fetchRemoteCookies();
})();

// Periodically refresh the remote pack so dead/expired accounts get rotated.
if (REMOTE_COOKIES_REFRESH_MIN > 0) {
  setInterval(
    () => { fetchRemoteCookies({ silent: true }); },
    REMOTE_COOKIES_REFRESH_MIN * 60 * 1000
  ).unref();
}


function pickCookie() {
  for (const c of cookiePool) if (!deadCookies.has(c.id)) return c;
  return null;
}
// How long a "dead" cookie stays in the penalty box before being retried.
// (Cookies can be flagged as bad temporarily — IP throttling, captchas, etc.
// — so we shouldn't burn them forever after a single failure.)
const DEAD_COOKIE_TTL_MS = 5 * 60 * 1000;
function markCookieDead(id) {
  deadCookies.add(id);
  const remain = cookiePool.length - deadCookies.size;
  console.warn(`[cookies] '${id}' looks dead — ${remain} cookie(s) remaining`);
  // Auto-revive after the TTL so a transient blip doesn't kill the pool.
  setTimeout(() => {
    if (deadCookies.delete(id)) {
      console.log(`[cookies] '${id}' revived after ${DEAD_COOKIE_TTL_MS / 60000}min cooldown`);
    }
  }, DEAD_COOKIE_TTL_MS).unref();
  if (remain === 0 && cookiePool.length) {
    console.warn(`[cookies] all cookies failed; resetting pool to retry from top`);
    deadCookies.clear();
  }
}

function isAuthError(msg) {
  // Tight signals that the *cookie itself* is bad / expired / blocked.
  // Be careful: yt-dlp's error message often contains the help URL
  //   "https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp"
  // and we don't want the substring "cookies" inside that URL to trigger
  // a false positive.  Also note that "Sign in to confirm you're not a
  // bot" is sometimes just IP-rate-limit, not an actual cookie problem,
  // so we only treat it as a cookie failure if we have a cookie pool to
  // rotate through (handled in `runYtDlpWithRotation`).
  const s = String(msg || "");
  if (/HTTP Error 401|HTTP Error 403/i.test(s)) return true;
  if (/Sign in to confirm/i.test(s)) return true;
  if (/account.*?(suspended|terminated|disabled)/i.test(s)) return true;
  if (/cookies are no longer valid|invalid.*cookies/i.test(s)) return true;
  return false;
}

function buildAuthArgs(cookieEntry) {
  if (cookieEntry) return ["--cookies", cookieEntry.normalizedPath];
  if (process.env.YT_COOKIES) return ["--cookies", process.env.YT_COOKIES];
  if (YT_COOKIES_BROWSER) return ["--cookies-from-browser", YT_COOKIES_BROWSER];
  return [];
}

function runYtDlp(baseArgs, cookieEntry) {
  return new Promise((resolve, reject) => {
    const args = [...baseArgs, ...buildAuthArgs(cookieEntry)];
    const p = spawn(YT_DLP, args, { windowsHide: true });
    let out = "";
    let err = "";
    p.stdout.on("data", (b) => out += b.toString("utf8"));
    p.stderr.on("data", (b) => err += b.toString("utf8"));
    p.on("error", reject);
    p.on("close", (code) => {
      if (code === 0 && out.trim()) resolve(out); else
        reject(new Error(err || `yt-dlp exited ${code}`));
    });
  });
}

async function runYtDlpWithRotation(baseArgs) {
  const tried = new Set();
  let lastErr = null;
  while (true) {
    const cookie = pickCookie();
    if (cookie && tried.has(cookie.id)) break;
    try {
      return await runYtDlp(baseArgs, cookie);
    } catch (e) {
      lastErr = e;
      if (!cookie) break;
      tried.add(cookie.id);
      if (isAuthError(e.message)) {
        markCookieDead(cookie.id);
        continue;
      }
      break;
    }
  }
  if (lastErr && (process.env.YT_COOKIES || YT_COOKIES_BROWSER) && !cookiePool.length) {
    try { return await runYtDlp(baseArgs, null); } catch (e) { lastErr = e; }
  }
  throw lastErr || new Error("yt-dlp failed");
}

function refreshCookies() {
  loadCookiePool();
  return cookiePool.map((c) => c.id);
}

const URL_CACHE = new LRUCache({
  max: 200,
  ttl: 5 * 60 * 1000

});
const VIDEO_URL_CACHE = new LRUCache({ max: 200, ttl: 5 * 60 * 1000 });

const inflightAudio = new Map();
const inflightVideo = new Map();

// Extra yt-dlp flags that help bypass the "Sign in to confirm you're not a
// bot" challenge.  Using multiple `player_client`s makes yt-dlp try the TV
// and Android Music players first (which don't require PoToken / botguard
// in most cases), then fall back to web.
// Try multiple yt-dlp player clients to bypass the bot challenge.
// Order matters — we go from most-likely-to-bypass to fallback:
//   • tv         — TV-tier client, no PoToken needed in most cases
//   • ios        — iOS client, often returns clean URLs
//   • android    — Android client (good for music URLs)
//   • web        — last resort, may demand PoToken
//
// We attempt each one in sequence; first one that yields a URL wins.
// (Doing this in JS rather than the `--extractor-args player_client=a,b`
// chaining because that can still error if ANY of the clients hits the
// bot challenge.)
const PLAYER_CLIENT_FALLBACKS = ["tv", "ios", "android", "web"];

async function tryClients(videoId, formatSelector) {
  let lastErr = null;
  for (const client of PLAYER_CLIENT_FALLBACKS) {
    try {
      const out = await runYtDlpWithRotation([
        "-q",
        "--no-warnings",
        "-f", formatSelector,
        "-g",
        "--no-playlist",
        "--extractor-args", `youtube:player_client=${client}`,
        `https://www.youtube.com/watch?v=${videoId}`,
      ]);
      const url = out.split(/\r?\n/).filter(Boolean)[0];
      if (url) return url;
    } catch (e) {
      lastErr = e;
      // If it's a bot challenge, try the next client.
      if (/Sign in to confirm|HTTP Error 403|Requested format is not available/i.test(e.message)) continue;
      // Other errors — bail out.
      throw e;
    }
  }
  throw lastErr || new Error("yt-dlp: no client could resolve");
}

function ytDlpResolveAudioUrl(videoId) {
  // Wide selector — m4a preferred, but accept anything audio-only,
  // then any combined stream as last resort.
  return tryClients(videoId, "bestaudio[ext=m4a]/bestaudio/best[acodec!=none]/best");
}

function ytDlpResolveVideoUrl(videoId) {
  return tryClients(videoId, "best[ext=mp4][acodec!=none][vcodec!=none]/best[acodec!=none][vcodec!=none]/best");
}



async function resolveAudio(videoId) {
  const cached = URL_CACHE.get(videoId);
  if (cached) return cached;
  if (inflightAudio.has(videoId)) return inflightAudio.get(videoId);
  const p = (async () => {
    try {
      const url = await ytDlpResolveAudioUrl(videoId);
      const mime = /mime=audio%2Fwebm/i.test(url) ? "audio/webm" : "audio/mp4";
      const entry = { url, mime };
      URL_CACHE.set(videoId, entry);
      return entry;
    } finally {
      inflightAudio.delete(videoId);
    }
  })();
  inflightAudio.set(videoId, p);
  return p;
}

async function resolveVideo(videoId) {
  const cached = VIDEO_URL_CACHE.get(videoId);
  if (cached) return cached;
  if (inflightVideo.has(videoId)) return inflightVideo.get(videoId);
  const p = (async () => {
    try {
      const url = await ytDlpResolveVideoUrl(videoId);
      const mime = /mime=video%2Fwebm/i.test(url) ? "video/webm" : "video/mp4";
      const entry = { url, mime };
      VIDEO_URL_CACHE.set(videoId, entry);
      return entry;
    } finally {
      inflightVideo.delete(videoId);
    }
  })();
  inflightVideo.set(videoId, p);
  return p;
}

async function ytDlpFetchSubs(videoId, lang = "en") {
  const tmp = path.join(os.tmpdir(), `smweb-subs-${videoId}-${Date.now()}`);
  const outTmpl = `${tmp}.%(ext)s`;
  const baseArgs = [
    "--skip-download",
    "--write-auto-subs",
    "--write-subs",
    "--sub-format", "vtt",
    "--sub-langs", `${lang},${lang}.*`,
    "-o", outTmpl,
    "--no-warnings",
    "--no-playlist",
    "-q",
    `https://www.youtube.com/watch?v=${videoId}`];

  try {
    await runYtDlpWithRotation(baseArgs).catch(() => null);
    const dir = path.dirname(tmp);
    const base = path.basename(tmp);
    const file = fs.readdirSync(dir).find(
      (f) => f.startsWith(base) && f.endsWith(".vtt")
    );
    if (!file) return null;
    const full = path.join(dir, file);
    const text = fs.readFileSync(full, "utf8");
    fs.unlink(full, () => { });
    return text;
  } catch {
    return null;
  }
}

function vttToLrc(vtt) {
  const out = [];
  let curStart = null;
  const seen = new Set();
  // Lines we want to strip from auto-caption garbage:
  //   [Music], [Applause], [Laughter], (music), ♪ ♪, "so", filler
  // — these come from YouTube's auto-caption ML which spams them on
  // instrumental sections.
  const NOISE = /^(\[?(music|applause|laughter|chuckle|sigh|sound|noise|silence|inaudible)\]?|♪+|\(.*\))$/i;
  for (const raw of vtt.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || /^WEBVTT/i.test(line) || /^NOTE/i.test(line)) continue;
    const m = /^(\d{2}):(\d{2}):(\d{2})[.,](\d{3})\s*-->/.exec(line);
    if (m) {
      const total =
        parseInt(m[1], 10) * 3600 +
        parseInt(m[2], 10) * 60 +
        parseInt(m[3], 10) +
        parseInt(m[4], 10) / 1000;
      const lm = Math.floor(total / 60);
      const ls = (total - lm * 60).toFixed(2).padStart(5, "0");
      curStart = `[${String(lm).padStart(2, "0")}:${ls}]`;
      continue;
    }
    if (curStart && !/^\d/.test(line)) {
      const clean = line.replace(/<[^>]+>/g, "").trim();
      if (!clean) continue;
      if (NOISE.test(clean)) continue;          // drop [Music] / (noise) / etc.
      if (clean.length < 2) continue;            // drop "so", "uh"
      const lrc = `${curStart}${clean}`;
      if (seen.has(lrc)) continue;
      seen.add(lrc);
      out.push(lrc);
    }
  }
  return out.join("\n");
}


let YTMusicCtor = null;
async function loadYTMusic() {
  if (YTMusicCtor) return YTMusicCtor;
  const mod = await import("ytmusic-api");
  YTMusicCtor = mod.default || mod.YTMusic || mod;
  return YTMusicCtor;
}

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

function pickThumb(thumbnails) {
  if (!Array.isArray(thumbnails) || thumbnails.length === 0) return null;
  return thumbnails.reduce((best, t) =>
    !best ? t : (t.width || 0) > (best.width || 0) ? t : best, null
  );
}

function normalizeArtist(a) {
  if (!a) return null;
  if (typeof a === "string") return { name: a, artistId: null };
  return {
    name: typeof a.name === "string" ? a.name : a.name?.name || "",
    artistId: a.artistId ?? a.name?.artistId ?? null
  };
}

function normalizeSong(s) {
  if (!s) return null;
  let artists = [];
  if (Array.isArray(s.artists) && s.artists.length) {
    artists = s.artists.map(normalizeArtist).filter(Boolean);
  } else if (s.artist) {
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
    thumbnail: pickThumb(s.thumbnails) || s.thumbnail || null
  };
}

const SEARCH_CACHE = new LRUCache({ max: 500, ttl: 5 * 60 * 1000 });
const SUGGEST_CACHE = new LRUCache({ max: 1000, ttl: 30 * 60 * 1000 });
const SONG_CACHE = new LRUCache({ max: 500, ttl: 30 * 60 * 1000 });
const UPNEXT_CACHE = new LRUCache({ max: 200, ttl: 30 * 60 * 1000 });
const ARTIST_CACHE = new LRUCache({ max: 200, ttl: 60 * 60 * 1000 });
const ALBUM_CACHE = new LRUCache({ max: 500, ttl: 60 * 60 * 1000 });
const PLAYLIST_CACHE = new LRUCache({ max: 200, ttl: 30 * 60 * 1000 });
const HOME_CACHE = new LRUCache({ max: 1, ttl: 10 * 60 * 1000 });
const LYRICS_CACHE = new LRUCache({ max: 500, ttl: 6 * 60 * 60 * 1000 });

async function memoize(cache, key, fn) {
  const hit = cache.get(key);
  if (hit) return hit;
  const v = await fn();
  cache.set(key, v);
  return v;
}

const app = express();
app.disable("x-powered-by");
app.use(cors());
app.use(compression());
app.use(express.json());

app.use(express.static(path.join(__dirname, "public"), {
  etag: true,
  lastModified: true,
  maxAge: "1d",
  setHeaders(res, filePath) {
    if (filePath.endsWith(".html")) {
      res.setHeader("Cache-Control", "no-cache");
    }
  }
}));

app.get("/api/health", (_req, res) => {
  res.json({
    ok: true,
    name: "SakayoriMusic Web",
    version: "2.1.0-optimized",
    cookies: {
      pool: cookiePool.map((c) => c.id),
      dead: [...deadCookies],
      envFile: process.env.YT_COOKIES || null,
      envBrowser: YT_COOKIES_BROWSER || null
    },
    cache: {
      urls: URL_CACHE.size,
      videoUrls: VIDEO_URL_CACHE.size,
      search: SEARCH_CACHE.size,
      songs: SONG_CACHE.size,
      artists: ARTIST_CACHE.size,
      albums: ALBUM_CACHE.size,
      playlists: PLAYLIST_CACHE.size,
      lyrics: LYRICS_CACHE.size,
      home: HOME_CACHE.size
    }
  });
});

app.post("/api/cookies/refresh", async (req, res) => {
  if (req.query.remote === "1") await fetchRemoteCookies();
  else loadCookiePool();
  res.json({ ok: true, loaded: cookiePool.map((c) => c.id) });
});


app.get("/api/search", async (req, res) => {
  const q = (req.query.q || "").toString().trim();
  const type = (req.query.type || "songs").toString();
  if (!q) return res.status(400).json({ error: "Missing q" });

  try {
    const data = await memoize(SEARCH_CACHE, `${type}:${q.toLowerCase()}`, async () => {
      const ytm = await getYTMusic();
      let results;
      switch (type) {
        case "videos": results = await ytm.searchVideos(q); break;
        case "albums": results = await ytm.searchAlbums(q); break;
        case "artists": results = await ytm.searchArtists(q); break;
        case "playlists": results = await ytm.searchPlaylists(q); break;
        case "all": results = await ytm.search(q); break;
        case "songs":
        default: results = await ytm.searchSongs(q); break;
      }
      const normalized = (results || []).map((r) => {
        if (r.type === "SONG" || r.type === "VIDEO" || r.videoId) return normalizeSong(r);
        return {
          type: r.type,
          id: r.albumId || r.playlistId || r.artistId || r.id,
          name: r.name || r.title,
          artist: r.artist?.name || r.artists?.[0]?.name || null,
          thumbnail: pickThumb(r.thumbnails)
        };
      });
      return { query: q, type, results: normalized };
    });
    res.setHeader("Cache-Control", "public, max-age=60");
    res.json(data);
  } catch (err) {
    console.error("[/api/search]", err);
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/suggest", async (req, res) => {
  const q = (req.query.q || "").toString().trim();
  if (!q) return res.json({ suggestions: [] });
  try {
    const data = await memoize(SUGGEST_CACHE, q.toLowerCase(), async () => {
      const ytm = await getYTMusic();
      const list = await ytm.getSearchSuggestions(q).catch(() => []);
      return { suggestions: list || [] };
    });
    res.setHeader("Cache-Control", "public, max-age=300");
    res.json(data);
  } catch {
    res.json({ suggestions: [] });
  }
});

app.get("/api/song/:videoId", async (req, res) => {
  try {
    const data = await memoize(SONG_CACHE, req.params.videoId, async () => {
      const ytm = await getYTMusic();
      const song = await ytm.getSong(req.params.videoId);
      return normalizeSong(song);
    });
    res.json(data);
  } catch (err) {
    console.error("[/api/song]", err);
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/up-next/:videoId", async (req, res) => {
  try {
    const data = await memoize(UPNEXT_CACHE, req.params.videoId, async () => {
      const ytm = await getYTMusic();
      const upNext = await ytm.getUpNexts(req.params.videoId);
      return {
        videoId: req.params.videoId,
        songs: (upNext || []).map(normalizeSong).filter(Boolean)
      };
    });
    res.json(data);
  } catch (err) {
    console.error("[/api/up-next]", err);
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/artist/:id", async (req, res) => {
  try {
    const data = await memoize(ARTIST_CACHE, req.params.id, async () => {
      const ytm = await getYTMusic();
      const a = await ytm.getArtist(req.params.id);
      return {
        id: req.params.id,
        name: a?.name || "",
        description: a?.description || "",
        thumbnail: pickThumb(a?.thumbnails),
        subscribers: a?.subscribers || null,
        topSongs: (a?.songs?.results || a?.songs || []).map(normalizeSong),
        albums: (a?.albums?.results || a?.albums || []).map((al) => ({
          id: al.albumId || al.id,
          name: al.name || al.title,
          year: al.year || null,
          thumbnail: pickThumb(al.thumbnails)
        })),
        singles: (a?.singles?.results || a?.singles || []).map((al) => ({
          id: al.albumId || al.id,
          name: al.name || al.title,
          year: al.year || null,
          thumbnail: pickThumb(al.thumbnails)
        }))
      };
    });
    res.json(data);
  } catch (err) {
    console.error("[/api/artist]", err);
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/playlist/:id", async (req, res) => {
  try {
    const data = await memoize(PLAYLIST_CACHE, req.params.id, async () => {
      const ytm = await getYTMusic();
      const pl = await ytm.getPlaylist(req.params.id);
      const videos = await ytm.getPlaylistVideos(req.params.id).catch(() => []);
      return {
        id: req.params.id,
        name: pl?.name || "",
        description: pl?.description || "",
        thumbnail: pickThumb(pl?.thumbnails),
        songs: (videos || []).map(normalizeSong)
      };
    });
    res.json(data);
  } catch (err) {
    console.error("[/api/playlist]", err);
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/album/:id", async (req, res) => {
  try {
    const data = await memoize(ALBUM_CACHE, req.params.id, async () => {
      const ytm = await getYTMusic();
      const album = await ytm.getAlbum(req.params.id);
      return {
        id: req.params.id,
        name: album?.name || "",
        artist: album?.artist?.name || "",
        year: album?.year ?? null,
        thumbnail: pickThumb(album?.thumbnails),
        songs: (album?.songs || []).map(normalizeSong)
      };
    });
    res.json(data);
  } catch (err) {
    console.error("[/api/album]", err);
    res.status(500).json({ error: err.message });
  }
});

const HOME_QUERIES = [
  { title: "Trending Now", q: "top hits 2025" },
  { title: "Lo-fi & Chill", q: "lofi hip hop" },
  { title: "Workout Energy", q: "workout playlist" },
  { title: "Anime OST", q: "anime opening" },
  { title: "Jazz Café", q: "jazz cafe" },
  { title: "K-Pop Stars", q: "kpop hits" }];

app.get("/api/home", async (_req, res) => {
  try {
    const data = await memoize(HOME_CACHE, "default", async () => {
      const ytm = await getYTMusic();
      const shelves = await Promise.all(
        HOME_QUERIES.map(async ({ title, q }) => {
          try {
            const songs = await ytm.searchSongs(q);
            return {
              title,
              items: (songs || []).slice(0, 12).map(normalizeSong)
            };
          } catch {
            return { title, items: [] };
          }
        })
      );
      return { shelves };
    });
    res.setHeader("Cache-Control", "public, max-age=300");
    res.json(data);
  } catch (err) {
    console.error("[/api/home]", err);
    res.status(500).json({ error: err.message });
  }
});

async function proxyStream(req, res, getEntry, fallbackMime) {
  const videoId = req.params.videoId;
  let nodeStream = null;
  let aborted = false;
  req.on("close", () => { aborted = true; if (nodeStream) try { nodeStream.destroy(); } catch { } });

  try {
    let { url, mime } = await getEntry(videoId);
    if (aborted) return;

    const headers = { "User-Agent": UA };
    if (req.headers.range) headers["Range"] = req.headers.range;

    let upstream = await undiciFetch(url, { headers, dispatcher: upstreamAgent });

    if (upstream.status === 403 || upstream.status === 410) {
      URL_CACHE.delete(videoId);
      VIDEO_URL_CACHE.delete(videoId);
      const fresh = await getEntry(videoId);
      if (aborted) return;
      upstream = await undiciFetch(fresh.url, { headers, dispatcher: upstreamAgent });
      mime = fresh.mime || mime;
    }

    res.status(upstream.status);
    res.setHeader("Content-Type", mime || fallbackMime);
    res.setHeader("Accept-Ranges", "bytes");
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Cache-Control", "no-store");

    const cl = upstream.headers.get("content-length");
    if (cl) res.setHeader("Content-Length", cl);
    const cr = upstream.headers.get("content-range");
    if (cr) res.setHeader("Content-Range", cr);

    if (!upstream.body) return res.end();

    nodeStream = Readable.fromWeb(upstream.body);
    nodeStream.on("error", (e) => {
      if (!aborted) console.error("[stream pipe]", e.message);
      try { res.end(); } catch { }
    });
    nodeStream.pipe(res);
  } catch (err) {
    if (aborted) return;
    console.error("[stream]", err.message);
    if (!res.headersSent) res.status(500).json({ error: err.message });
  }
}

app.get("/api/stream/:videoId", (req, res) => proxyStream(req, res, resolveAudio, "audio/mp4"));
app.get("/api/video/:videoId", (req, res) => proxyStream(req, res, resolveVideo, "video/mp4"));

app.post("/api/prefetch/:videoId", (req, res) => {
  const { videoId } = req.params;
  if (!videoId) return res.status(400).json({ error: "Missing videoId" });

  resolveAudio(videoId).catch(() => { });
  res.json({ ok: true });
});

app.post("/api/prefetch-video/:videoId", (req, res) => {
  const { videoId } = req.params;
  if (!videoId) return res.status(400).json({ error: "Missing videoId" });
  resolveVideo(videoId).catch(() => { });
  res.json({ ok: true });
});

// ---------------------------------------------------------------------------
// Multi-source lyrics chain
//   1. LRCLIB             (best LRC quality, free)
//   2. NetEase Cloud      (huge for CJK & K-pop, often LRC)
//   3. Genius             (English-heavy, plain only — scraped)
//   4. KuGou              (Chinese provider, returns LRC base64)
//   5. YouTube subtitles  (yt-dlp auto-captions, last resort)
//
// Each provider returns either `null` (clean miss) or:
//     { found: true, plain, synced, source }
// Synced takes priority — first provider to deliver synced wins;
// otherwise the first plain hit wins.  All HTTP failures are
// swallowed per-provider so one rate-limited backend can't kill the chain.
// ---------------------------------------------------------------------------
const LYRICS_UA =
  "SakayoriMusicWeb/2.1 (+https://github.com/Sakayorii/sakayori-music)";

const LYRICS_PROVIDERS = ["lrclib", "netease", "genius", "kugou", "youtube"];

async function lrclibLyrics({ title, artist, album, duration }) {
  const params = new URLSearchParams({
    track_name: String(title),
    artist_name: String(artist),
  });
  if (album) params.set("album_name", String(album));
  if (duration) params.set("duration", String(duration));

  let r = await undiciFetch(`https://lrclib.net/api/get?${params}`, {
    headers: { "User-Agent": LYRICS_UA },
  });

  if (r.status === 404) {
    const sr = await undiciFetch(
      `https://lrclib.net/api/search?${new URLSearchParams({
        track_name: String(title),
        artist_name: String(artist),
      })}`,
      { headers: { "User-Agent": LYRICS_UA } }
    );
    const list = await sr.json().catch(() => []);
    if (Array.isArray(list) && list.length) {
      return {
        found: true,
        plain: list[0].plainLyrics || "",
        synced: list[0].syncedLyrics || "",
        source: "LRCLIB",
      };
    }
    return null;
  }
  if (!r.ok) return null;
  const data = await r.json().catch(() => null);
  if (!data) return null;
  if (!data.plainLyrics && !data.syncedLyrics) return null;
  return {
    found: true,
    plain: data.plainLyrics || "",
    synced: data.syncedLyrics || "",
    source: "LRCLIB",
  };
}

// NetEase Cloud Music — public unauthenticated endpoints (no app key needed
// for search + lyric).  We use the legacy "music.163.com/api/" surface, which
// is stable and CORS-clean from server side.
async function neteaseLyrics({ title, artist }) {
  const q = `${title} ${artist}`.trim();
  const sUrl = `https://music.163.com/api/search/get?type=1&limit=5&s=${encodeURIComponent(q)}`;
  const sr = await undiciFetch(sUrl, {
    headers: {
      "User-Agent": LYRICS_UA,
      "Referer": "https://music.163.com",
    },
  });
  if (!sr.ok) return null;
  const sj = await sr.json().catch(() => null);
  const songs = sj?.result?.songs;
  if (!Array.isArray(songs) || !songs.length) return null;

  // Try top 3 hits — first one to have any lyric content wins.
  for (const song of songs.slice(0, 3)) {
    const lr = await undiciFetch(
      `https://music.163.com/api/song/lyric?id=${song.id}&lv=1&kv=1&tv=-1`,
      {
        headers: {
          "User-Agent": LYRICS_UA,
          "Referer": "https://music.163.com",
        },
      }
    );
    if (!lr.ok) continue;
    const lj = await lr.json().catch(() => null);
    const synced = lj?.lrc?.lyric || "";
    const plain = (lj?.tlyric?.lyric || synced || "")
      .replace(/\[[\d:.]+\]/g, "")
      .trim();
    if (synced || plain) {
      return { found: true, plain, synced, source: "NetEase" };
    }
  }
  return null;
}

// Genius — search via JSON API, scrape lyric div.  No API token needed for
// the search endpoint.  Lyrics-only, no synced timing available.
async function geniusLyrics({ title, artist }) {
  const q = `${title} ${artist}`.trim();
  const sr = await undiciFetch(
    `https://genius.com/api/search/multi?per_page=5&q=${encodeURIComponent(q)}`,
    { headers: { "User-Agent": LYRICS_UA, "Accept": "application/json" } }
  );
  if (!sr.ok) return null;
  const sj = await sr.json().catch(() => null);
  const sections = sj?.response?.sections || [];
  let url = null;
  for (const sec of sections) {
    const hits = sec.hits || [];
    for (const h of hits) {
      if (h.type === "song" && h.result?.url) { url = h.result.url; break; }
    }
    if (url) break;
  }
  if (!url) return null;

  const pr = await undiciFetch(url, { headers: { "User-Agent": LYRICS_UA } });
  if (!pr.ok) return null;
  const html = await pr.text();

  // Genius wraps lyrics in <div data-lyrics-container="true">…</div> blocks.
  const blocks = [];
  const re = /<div[^>]+data-lyrics-container="true"[^>]*>([\s\S]*?)<\/div>/g;
  let m;
  while ((m = re.exec(html))) blocks.push(m[1]);
  if (!blocks.length) return null;

  const decode = (s) =>
    s
      .replace(/<br\s*\/?>/gi, "\n")
      .replace(/<[^>]+>/g, "")
      .replace(/&amp;/g, "&")
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&quot;/g, '"')
      .replace(/&#x27;/g, "'")
      .replace(/&apos;/g, "'");

  const plain = blocks.map(decode).join("\n\n").replace(/\n{3,}/g, "\n\n").trim();
  if (!plain) return null;
  return { found: true, plain, synced: "", source: "Genius" };
}

// KuGou — Chinese music site, exposes a lyrics API that returns
// base64-encoded LRC content.  Two-step lookup: keyword → hash → krc.
async function kugouLyrics({ title, artist, duration }) {
  const q = `${title} ${artist}`.trim();
  // Step 1: search returns candidate hashes
  const sr = await undiciFetch(
    `https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${encodeURIComponent(
      q
    )}${duration ? `&duration=${Math.round(Number(duration) * 1000)}` : ""}`,
    { headers: { "User-Agent": LYRICS_UA } }
  );
  if (!sr.ok) return null;
  const sj = await sr.json().catch(() => null);
  const cand = sj?.candidates?.[0];
  if (!cand) return null;

  // Step 2: download the LRC (fmt=lrc returns LRC instead of KRC karaoke)
  const dr = await undiciFetch(
    `https://krcs.kugou.com/download?ver=1&man=yes&client=pc&fmt=lrc&charset=utf8&accesskey=${cand.accesskey}&id=${cand.id}`,
    { headers: { "User-Agent": LYRICS_UA } }
  );
  if (!dr.ok) return null;
  const dj = await dr.json().catch(() => null);
  if (!dj?.content) return null;
  let synced = "";
  try { synced = Buffer.from(dj.content, "base64").toString("utf8"); }
  catch { return null; }
  if (!synced) return null;
  const plain = synced.replace(/\[[\d:.]+\]/g, "").trim();
  return { found: true, plain, synced, source: "KuGou" };
}

async function youtubeLyrics({ videoId }) {
  if (!videoId) return null;
  const vtt = await ytDlpFetchSubs(String(videoId), "en");
  if (!vtt) return null;
  const synced = vttToLrc(vtt);
  if (!synced) return null;
  return {
    found: true,
    plain: synced.replace(/\[[\d:.]+\]/g, "").trim(),
    synced,
    source: "YouTube subtitles",
  };
}

const PROVIDER_FNS = {
  lrclib: lrclibLyrics,
  netease: neteaseLyrics,
  genius: geniusLyrics,
  kugou: kugouLyrics,
  youtube: youtubeLyrics,
};

async function chainLyrics(args, requested) {
  // requested === "auto" → walk the full chain.  Otherwise honour the
  // single-source request strictly so users can A/B individual providers.
  const providers =
    requested && requested !== "auto"
      ? [requested]
      : LYRICS_PROVIDERS;

  // First pass: try to land any synced result (best UX).
  let bestPlain = null;
  for (const p of providers) {
    try {
      const fn = PROVIDER_FNS[p];
      if (!fn) continue;
      const out = await fn(args);
      if (!out || !out.found) continue;
      if (out.synced && out.synced.trim()) {
        return { ...out, source: out.source + (requested === "auto" ? "" : "") };
      }
      if (!bestPlain && out.plain) bestPlain = out;
    } catch (e) {
      // Swallow — one bad backend shouldn't sink the chain.
      console.warn(`[lyrics] ${p} failed: ${e.message}`);
    }
  }
  return bestPlain || { found: false };
}


// List the supported lyrics providers (used by Settings → "Lyrics source").
app.get("/api/lyrics/sources", (_req, res) => {
  res.json({
    auto: { id: "auto", label: "Auto (full chain)" },
    providers: LYRICS_PROVIDERS.map((id) => ({
      id,
      label: {
        lrclib: "LRCLIB",
        netease: "NetEase Cloud Music",
        genius: "Genius",
        kugou: "KuGou",
        youtube: "YouTube subtitles",
      }[id] || id,
    })),
  });
});

app.get("/api/lyrics", async (req, res) => {
  const { title, artist, album, duration, videoId, source } = req.query;
  const requested = (source || "auto").toString().toLowerCase();
  const cacheKey =
    `${requested}::${videoId || ""}::${(title || "").toString().toLowerCase()}` +
    `::${(artist || "").toString().toLowerCase()}::${album || ""}`;

  try {
    const data = await memoize(LYRICS_CACHE, cacheKey, async () => {
      // YouTube-only short-circuit doesn't need title/artist.
      if (requested === "youtube") {
        return (await youtubeLyrics({ videoId })) || { found: false };
      }
      if (!title || !artist) {
        throw Object.assign(
          new Error("title & artist required"),
          { status: 400 }
        );
      }
      return chainLyrics(
        { title, artist, album, duration, videoId },
        requested
      );
    });

    res.setHeader("Cache-Control", "public, max-age=600");
    res.json(data);
  } catch (err) {
    // 4xx errors are user errors — quiet them down so the console isn't
    // noisy from the SPA's own probes (e.g. /api/lyrics on Settings page).
    if (!err.status || err.status >= 500) console.error("[/api/lyrics]", err.message);
    res.status(err.status || 500).json({ error: err.message });
  }
});



app.get(/.*/, (_req, res) => {
  res.sendFile(path.join(__dirname, "public", "index.html"));
});

let server;
(async function start() {
  // Bootstrap yt-dlp (downloads the standalone binary if not on PATH)
  // BEFORE we start serving traffic, otherwise the very first /api/stream
  // request would just emit `spawn yt-dlp ENOENT` until the user installs
  // it themselves.  Keeps existing behaviour for users who already have it.
  await ensureYtDlp();

  server = app.listen(PORT, HOST, () => {
    console.log(`\n[SakayoriMusic Web] Listening on http://${HOST}:${PORT}\n`);
  });
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;
})();


function shutdown(sig) {
  console.log(`\n[server] ${sig} received, shutting down…`);
  if (server) server.close(() => process.exit(0));
  else process.exit(0);
  setTimeout(() => process.exit(1), 5000).unref();
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));