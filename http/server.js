// Website backend. No dependencies — node:http and the plugin's own JSON API are enough.
//
// The Minecraft plugin serves a read-only API on localhost (web.port in the plugin config,
// 8085 by default). This process is the only thing that talks to it: browsers talk to this
// server, and this server polls the plugin every 30 seconds and serves the cached answer.
// That way a thousand people refreshing the homepage is still one request to the game server
// every 30 seconds, and the game port never has to be reachable from the internet.
//
//   PORT       - where this web server listens (default 8080; put 80/443 on a reverse proxy)
//   MC_API     - the plugin's API (default http://127.0.0.1:8085)
//   STATS_JSON - the plugin's stats file, read directly whenever the game server is down
//
// Stats outlive the game server on purpose: the plugin writes stats.json on every change,
// so that file IS the database. Live status honestly reads "offline" between matches, but
// the leaderboard and player pages keep answering from disk.

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = process.env.PORT || 8080;
const MC_API = process.env.MC_API || 'http://127.0.0.1:8085';
const STATS_JSON = process.env.STATS_JSON ||
  path.join(__dirname, '..', 'run', 'plugins', 'HardcoreGames', 'stats.json');
const KITS_CACHE_FILE = path.join(__dirname, 'kits-cache.json');
const POLL_MS = 30_000;

const PUBLIC_DIR = path.join(__dirname, 'public');
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript',
                '.png': 'image/png', '.ico': 'image/x-icon' };

// ---------------------------------------------------------------- live status cache

// offline:true until the first successful poll, and again whenever the game server stops
// answering — the site stays up either way and says so instead of erroring.
let status = { offline: true };

async function poll() {
  try {
    const res = await fetch(`${MC_API}/api/status`, { signal: AbortSignal.timeout(5000) });
    status = { ...(await res.json()), offline: false, fetchedAt: Date.now() };
  } catch {
    status = { offline: true, fetchedAt: Date.now() };
  }
}
poll();
setInterval(poll, POLL_MS);

// Kits never change while the plugin is running; refetch lazily at most every 5 minutes.
// The last good copy is kept on disk, so the kits page still renders after a restart of
// this process while the game server is down.
let kitsCache = { body: loadKitsCache(), at: 0 };
function loadKitsCache() {
  try { return fs.readFileSync(KITS_CACHE_FILE, 'utf8'); } catch { return '[]'; }
}
async function kits() {
  if (Date.now() - kitsCache.at > 300_000) {
    try {
      const res = await fetch(`${MC_API}/api/kits`, { signal: AbortSignal.timeout(5000) });
      const body = await res.text();
      if (res.ok && body !== kitsCache.body) {
        try { fs.writeFileSync(KITS_CACHE_FILE, body); } catch { /* cache only */ }
      }
      kitsCache = { body: res.ok ? body : kitsCache.body, at: Date.now() };
    } catch {
      kitsCache.at = Date.now(); // keep the stale copy, retry in five minutes
    }
  }
  return kitsCache.body;
}

// Stats are pass-through while the game server is up: they change on every kill and a
// search should be current.
async function proxy(apiPath) {
  const res = await fetch(`${MC_API}${apiPath}`, { signal: AbortSignal.timeout(5000) });
  return { code: res.status, body: await res.text() };
}

// ---------------------------------------------------------------- stats straight from disk

// Re-parsed only when the file's mtime moves; a torn read mid-save keeps the last copy.
let statsFile = { mtime: -1, data: null };
function statsEntries() {
  try {
    const mtime = fs.statSync(STATS_JSON).mtimeMs;
    if (mtime !== statsFile.mtime) {
      statsFile = { mtime, data: JSON.parse(fs.readFileSync(STATS_JSON, 'utf8')) };
    }
  } catch { /* no file yet, or mid-write: serve whatever we had */ }
  return statsFile.data && statsFile.data.players
    ? Object.values(statsFile.data.players)
    : null;
}

// Same shapes and ordering as the plugin's endpoints, so the pages cannot tell the difference.
function statsByNameFromDisk(name) {
  const all = statsEntries();
  if (!all) return null;
  const lower = name.toLowerCase();
  return { found: all.find(e => (e.name || '').toLowerCase() === lower) || null };
}
function statsTopFromDisk() {
  const all = statsEntries();
  if (!all) return null;
  return all
    .sort((a, b) => (b.wins || 0) - (a.wins || 0) || (b.kills || 0) - (a.kills || 0))
    .slice(0, 10);
}

// ---------------------------------------------------------------- the server

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  try {
    if (url.pathname === '/api/status') {
      return json(res, 200, JSON.stringify(status));
    }
    if (url.pathname === '/api/kits') {
      return json(res, 200, await kits());
    }
    if (url.pathname === '/api/stats/top') {
      try {
        const out = await proxy('/api/stats/top');
        return json(res, out.code, out.body);
      } catch {
        const top = statsTopFromDisk();
        if (top) return json(res, 200, JSON.stringify(top));
        throw new Error('no stats source'); // outer catch answers 502
      }
    }
    if (url.pathname === '/api/stats') {
      const player = url.searchParams.get('player') || '';
      try {
        const out = await proxy(`/api/stats?player=${encodeURIComponent(player)}`);
        return json(res, out.code, out.body);
      } catch {
        if (!player) return json(res, 400, '{"error":"pass ?player=<name>"}');
        const result = statsByNameFromDisk(player);
        if (!result) throw new Error('no stats source');
        if (!result.found) return json(res, 404, '{"error":"no such player"}');
        return json(res, 200, JSON.stringify(result.found));
      }
    }
    return serveStatic(url.pathname, res);
  } catch (e) {
    return json(res, 502, JSON.stringify({ error: 'game server unreachable' }));
  }
}).listen(PORT, () => console.log(`hg-web on :${PORT}, polling ${MC_API} every ${POLL_MS / 1000}s`));

function json(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}

function serveStatic(pathname, res) {
  if (pathname === '/') pathname = '/index.html';
  const file = path.join(PUBLIC_DIR, path.normalize(pathname));
  // Resolved path must sit strictly INSIDE public/ — the separator matters, or a sibling
  // directory that merely starts with the same name ("public-backup") would pass.
  if (!file.startsWith(PUBLIC_DIR + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    return res.end('not found');
  }
  res.writeHead(200, {
    'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream',
    // Pages revalidate quickly; the reverse proxy can layer more on top.
    'Cache-Control': path.extname(file) === '.html' ? 'no-cache' : 'public, max-age=300',
  });
  fs.createReadStream(file).pipe(res);
}
