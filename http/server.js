// Website backend. No dependencies — node:http and the plugin's own JSON API are enough.
//
// The Minecraft plugin serves a read-only API on localhost (web.port in the plugin config,
// 8085 by default). This process is the only thing that talks to it: browsers talk to this
// server, and this server polls the plugin every 30 seconds and serves the cached answer.
// That way a thousand people refreshing the homepage is still one request to the game server
// every 30 seconds, and the game port never has to be reachable from the internet.
//
//   PORT    - where this web server listens (default 8080; put 80/443 on a reverse proxy)
//   MC_API  - the plugin's API (default http://127.0.0.1:8085)

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = process.env.PORT || 8080;
const MC_API = process.env.MC_API || 'http://127.0.0.1:8085';
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
let kitsCache = { body: '[]', at: 0 };
async function kits() {
  if (Date.now() - kitsCache.at > 300_000) {
    try {
      const res = await fetch(`${MC_API}/api/kits`, { signal: AbortSignal.timeout(5000) });
      kitsCache = { body: await res.text(), at: Date.now() };
    } catch {
      kitsCache.at = Date.now(); // keep the stale copy, retry in five minutes
    }
  }
  return kitsCache.body;
}

// Stats are pass-through: they change on every kill and a search should be current.
async function proxy(apiPath) {
  const res = await fetch(`${MC_API}${apiPath}`, { signal: AbortSignal.timeout(5000) });
  return { code: res.status, body: await res.text() };
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
      const out = await proxy('/api/stats/top');
      return json(res, out.code, out.body);
    }
    if (url.pathname === '/api/stats') {
      const player = url.searchParams.get('player') || '';
      const out = await proxy(`/api/stats?player=${encodeURIComponent(player)}`);
      return json(res, out.code, out.body);
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
