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
// Matches web.leaderboard-size in the plugin's config.yml; only used when reading stats from disk.
const LEADERBOARD_SIZE = 15;

const PUBLIC_DIR = path.join(__dirname, 'public');
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript',
                '.png': 'image/png', '.gif': 'image/gif', '.ico': 'image/x-icon',
                // robots.txt and sitemap.xml: crawlers ignore them served as octet-stream.
                '.txt': 'text/plain', '.xml': 'application/xml',
                '.svg': 'image/svg+xml', '.mp4': 'video/mp4' };

// ---------------------------------------------------------------- live status cache

// offline:true until the first successful poll, and again whenever the game server stops
// answering — the site stays up either way and says so instead of erroring.
let status = { offline: true };
// The Minecraft version outlives the game server: the homepage keeps showing the last one
// seen while the server is between maps.
let version = null;

async function poll() {
  try {
    const res = await fetch(`${MC_API}/api/status`, { signal: AbortSignal.timeout(5000) });
    status = { ...(await res.json()), offline: false, fetchedAt: Date.now() };
    version = status.version || version;
  } catch {
    // Between maps the plugin is down; the player total still comes off disk.
    const all = statsEntries();
    status = { offline: true, version, fetchedAt: Date.now(),
               ...(all ? { totalPlayers: all.length } : {}) };
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

// ---------------------------------------------------------------- films a player is in

// The film pipeline (mcmovie) writes one record per published video, with the players who
// appear in it, the moment it uploads. Read straight from its file; re-parsed when it changes.
const FILMS_JSON = process.env.FILMS_JSON ||
  require('path').join(require('os').homedir(), 'github', 'mcmovie', 'automation', 'films.json');
let filmsFile = { mtime: -1, films: [] };
function films() {
  try {
    const mtime = fs.statSync(FILMS_JSON).mtimeMs;
    if (mtime !== filmsFile.mtime) {
      const data = JSON.parse(fs.readFileSync(FILMS_JSON, 'utf8'));
      filmsFile = { mtime, films: Array.isArray(data.films) ? data.films : [] };
    }
  } catch { /* no films yet, or mid-write: serve whatever we had */ }
  // only what anyone can watch: unlisted and private uploads stay off the site
  return filmsFile.films.filter((f) => f && f.url && (f.privacy || 'public') === 'public');
}
function filmsOf(player) {
  const want = String(player).toLowerCase();
  return films()
    .filter((f) => (f.players || []).some((n) => String(n).toLowerCase() === want))
    .slice(0, 100)
    // links: every place the film can be watched (the pipeline adds Instagram and TikTok as
    // those uploads land); url stays the YouTube one
    .map((f) => ({ url: f.url, links: { youtube: f.url, ...(f.links || {}) }, title: f.title, kind: f.kind, short: !!f.short, published: f.published, played: f.played || '' }));
}

// ---------------------------------------------------------------- clips for Instagram and TikTok

// Those two do not take an upload: they fetch a finished clip themselves from a public https
// address. The film pipeline drops each one in its automation/clips/ as <id>.mp4 for a couple
// of days and hands them https://<site>/clips/<id>.mp4. Served whole or by byte range, never
// redirected (TikTok refuses redirects), and nothing but those files can be reached here.
const CLIPS_DIR = process.env.CLIPS_DIR ||
  require('path').join(require('os').homedir(), 'github', 'mcmovie', 'automation', 'clips');
function serveClip(req, res, name) {
  const notFound = () => { res.writeHead(404, { 'Content-Type': 'text/plain' }); res.end('not found'); };
  // TikTok's ownership check for the /clips/ prefix: a small text file it names, kept in
  // public/clips/ (nothing else is served from there)
  if (/^[A-Za-z0-9_-]{1,80}\.txt$/.test(name)) {
    const f = path.join(PUBLIC_DIR, 'clips', name);
    if (!fs.existsSync(f)) return notFound();
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Cache-Control': 'no-cache' });
    return res.end(fs.readFileSync(f));
  }
  if (!/^[A-Za-z0-9_-]{1,64}\.mp4$/.test(name)) return notFound();
  const file = path.join(CLIPS_DIR, name);
  let size;
  try { const st = fs.statSync(file); if (!st.isFile()) return notFound(); size = st.size; } catch { return notFound(); }
  const head = { 'Content-Type': 'video/mp4', 'Accept-Ranges': 'bytes', 'Cache-Control': 'public, max-age=3600' };
  const m = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || '');
  if (m && (m[1] || m[2])) {
    let start = m[1] ? parseInt(m[1], 10) : size - parseInt(m[2], 10);
    let end = m[1] && m[2] ? parseInt(m[2], 10) : size - 1;
    if (!(start >= 0)) start = 0;
    if (end >= size) end = size - 1;
    if (start > end) { res.writeHead(416, { 'Content-Range': `bytes */${size}` }); return res.end(); }
    res.writeHead(206, { ...head, 'Content-Range': `bytes ${start}-${end}/${size}`, 'Content-Length': end - start + 1 });
    if (req.method === 'HEAD') return res.end();
    return fs.createReadStream(file, { start, end }).pipe(res);
  }
  res.writeHead(200, { ...head, 'Content-Length': size });
  if (req.method === 'HEAD') return res.end();
  fs.createReadStream(file).pipe(res);
}

// ---------------------------------------------------------------- admin: the clip publisher

// A small page for us (hardcorepvp.com/taborgreatadmin, HTTP basic auth, http/admin-secret.json):
// connect the TikTok and Instagram accounts the clips go to, see which account is connected
// (name and avatar), and send the newest clip by hand. Everything it does runs the film
// pipeline's own uploader (mcmovie bin/mcpublish.js) as a separate process, so tokens stay in
// that pipeline's config on this machine and this server never sees a secret.
const MCMOVIE_DIR = process.env.MCMOVIE_DIR || require('path').join(require('os').homedir(), 'github', 'mcmovie');
// The page lives at an address only we know, with a user name and password of Tabor's choosing
// (http/admin-secret.json: {"user": ..., "password": ...}).
const ADMIN_PATH = process.env.ADMIN_PATH || '/taborgreatadmin';
const ADMIN_SECRET_FILE = path.join(__dirname, 'admin-secret.json');
function adminSecret() { try { return JSON.parse(fs.readFileSync(ADMIN_SECRET_FILE, 'utf8')); } catch { return null; } }
function adminOk(req) {
  const secret = adminSecret();
  if (!secret || !secret.user || !secret.password) return false;
  const h = req.headers.authorization || '';
  if (!h.startsWith('Basic ')) return false;
  const [user, pass] = Buffer.from(h.slice(6), 'base64').toString('utf8').split(/:(.*)/s);
  return user === secret.user && pass === secret.password;
}
function needAuth(res) { res.writeHead(401, { 'WWW-Authenticate': 'Basic realm="hardcorepvp admin"', 'Content-Type': 'text/plain' }); res.end('sign in'); }
// run the uploader; its last line of stdout is JSON
function publisher(args, timeoutMs = 15 * 60_000) {
  return new Promise((resolve) => {
    require('child_process').execFile(process.execPath, ['bin/mcpublish.js', ...args], { cwd: MCMOVIE_DIR, timeout: timeoutMs, maxBuffer: 8 * 1024 * 1024 }, (err, stdout, stderr) => {
      const out = String(stdout || '');
      let data = null;
      const start = out.indexOf('{');
      if (start >= 0) { try { data = JSON.parse(out.slice(start)); } catch { /* not JSON */ } }
      const notes = String(stderr || '').split('\n').filter((l) => l.startsWith('[social]')).map((l) => l.slice(9));
      if (err && !data) return resolve({ ok: false, error: (String(stderr || '').match(/error: (.*)/) || [, err.message])[1].slice(0, 300), notes });
      resolve({ ok: !err, ...(data || {}), notes });
    });
  });
}
const oauthStates = new Map(); // state -> { platform, expires }
const adminJobs = new Map(); // id -> { done, result }
function readBody(req) {
  return new Promise((resolve) => { let b = ''; req.on('data', (c) => { b += c; if (b.length > 65536) req.destroy(); }); req.on('end', () => { try { resolve(b ? JSON.parse(b) : {}); } catch { resolve({}); } }); });
}
async function admin(req, res, url) {
  if (url.pathname === ADMIN_PATH) {
    if (!adminOk(req)) return needAuth(res);
    res.writeHead(200, { 'Content-Type': 'text/html', 'Cache-Control': 'no-store', 'X-Robots-Tag': 'noindex' });
    return res.end(fs.readFileSync(path.join(__dirname, 'admin.html')));
  }
  // the sign-in page finishing a sign-in: no password here, the one-time state is the proof
  if (url.pathname === '/api/oauth/finish' && req.method === 'POST') {
    const { code, state } = await readBody(req);
    const pending = oauthStates.get(String(state || ''));
    for (const [k, v] of oauthStates) if (v.expires < Date.now()) oauthStates.delete(k);
    if (!pending || pending.expires < Date.now() || !code) return json(res, 400, JSON.stringify({ ok: false, error: 'this sign-in was not started from the admin page, or it took too long; start it again' }));
    oauthStates.delete(String(state));
    return json(res, 200, JSON.stringify(await publisher(['social', 'finish', pending.platform, '--code', String(code)], 120_000)));
  }
  if (!url.pathname.startsWith('/api/admin/')) return false;
  if (!adminOk(req)) return needAuth(res);
  if (url.pathname === '/api/admin/status') return json(res, 200, JSON.stringify(await publisher(['social', 'status'], 60_000)));
  const connect = /^\/api\/admin\/connect\/(tiktok|instagram)$/.exec(url.pathname);
  if (connect && req.method === 'POST') {
    const state = connect[1] + '.' + require('crypto').randomBytes(12).toString('hex');
    oauthStates.set(state, { platform: connect[1], expires: Date.now() + 15 * 60_000 });
    return json(res, 200, JSON.stringify(await publisher(['social', 'connect', connect[1], '--state', state], 60_000)));
  }
  if (url.pathname === '/api/admin/send' && req.method === 'POST') {
    const body = await readBody(req);
    const to = Array.isArray(body.to) ? body.to.filter((p) => p === 'tiktok' || p === 'instagram') : [];
    const id = require('crypto').randomBytes(6).toString('hex');
    const job = { id, started: new Date().toISOString(), done: false, result: null };
    adminJobs.set(id, job);
    publisher(['social', 'send', ...(to.length ? ['--to', to.join(',')] : []), ...(body.again ? ['--again'] : [])]).then((r) => { job.done = true; job.result = r; });
    return json(res, 200, JSON.stringify({ ok: true, job: id }));
  }
  const jobm = /^\/api\/admin\/job\/([a-f0-9]{12})$/.exec(url.pathname);
  if (jobm) { const j = adminJobs.get(jobm[1]); return json(res, j ? 200 : 404, JSON.stringify(j || { error: 'no such job' })); }
  return json(res, 404, '{"error":"unknown admin call"}');
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
    .sort((a, b) => (b.wins || 0) - (a.wins || 0) || (b.kills || 0) - (a.kills || 0)
                 || (b.games || 0) - (a.games || 0))
    .slice(0, LEADERBOARD_SIZE);
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
    if (url.pathname === '/api/films') {
      const player = url.searchParams.get('player') || '';
      if (!player) return json(res, 400, '{"error":"pass ?player=<name>"}');
      return json(res, 200, JSON.stringify(filmsOf(player)));
    }
    if (url.pathname.startsWith('/clips/')) return serveClip(req, res, url.pathname.slice('/clips/'.length));
    if (url.pathname === ADMIN_PATH || url.pathname.startsWith('/api/admin/') || url.pathname === '/api/oauth/finish') { if (await admin(req, res, url) !== false) return; }
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
  // Clean URLs: /privacy serves privacy.html, /terms serves terms.html, and so on.
  if (!path.extname(pathname) && fs.existsSync(path.join(PUBLIC_DIR, path.normalize(pathname + '.html')))) pathname += '.html';
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
