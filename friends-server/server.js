'use strict';
/*
 * BushLaunch - serwer znajomych (Node.js 18+, bez zależności).
 *
 *  - obecność: launcher co ~20 s wysyła "ping" (online / w grze); brak pingu przez 75 s = offline
 *  - znajomi: zaproszenie musi zostać zaakceptowane w launcherze drugiej osoby
 *  - tożsamość: potwierdzana przez serwer sesji Mojang (hasJoined) - token gry NIGDY nie trafia do tego serwera
 *
 * Uruchomienie:  node server.js     (zmienne: PORT, HOST, DB_FILE, MOJANG_SESSION_URL)
 * Wystaw go przez HTTPS (nginx / Caddy / Cloudflare Tunnel) i wpisz adres w launcherze.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = parseInt(process.env.PORT || '8787', 10);
const HOST = process.env.HOST || '0.0.0.0';
const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'data.json');
const MOJANG = (process.env.MOJANG_SESSION_URL || 'https://sessionserver.mojang.com').replace(/\/+$/, '');

const ONLINE_MS = 75 * 1000;          // tyle po ostatnim pingu uznajemy gracza za online
const SESSION_S = 12 * 3600;          // ważność sesji wydanej launcherowi
const CHALLENGE_MS = 60 * 1000;
const MAX_FRIENDS = 100;
const MAX_OUTGOING = 30;
const NICK = /^[A-Za-z0-9_]{3,16}$/;
const UUID = /^[0-9a-f]{32}$/;

// ------------------------------------------------------------------ dane

let db = { users: {}, friends: {}, requests: [] };
try {
  db = Object.assign(db, JSON.parse(fs.readFileSync(DB_FILE, 'utf8')));
} catch (e) {
  if (e.code !== 'ENOENT') console.error('Nie udało się wczytać bazy:', e.message);
}

let saveTimer = null;
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    const tmp = DB_FILE + '.tmp';
    try {
      fs.writeFileSync(tmp, JSON.stringify(db));
      fs.renameSync(tmp, DB_FILE);
    } catch (e) {
      console.error('Zapis bazy nie powiódł się:', e.message);
    }
  }, 400);
}
function saveNow() {
  try {
    fs.writeFileSync(DB_FILE + '.tmp', JSON.stringify(db));
    fs.renameSync(DB_FILE + '.tmp', DB_FILE);
  } catch (e) { /* trudno */ }
}

const sessions = new Map();    // token -> { uuid, exp }
const challenges = new Map();  // serverId -> { name (lower), exp }
const rate = new Map();        // klucz -> { m, n }

setInterval(() => {
  const now = Date.now();
  for (const [k, v] of sessions) if (v.exp < now) sessions.delete(k);
  for (const [k, v] of challenges) if (v.exp < now) challenges.delete(k);
  const m = Math.floor(now / 60000);
  for (const [k, v] of rate) if (v.m < m) rate.delete(k);
}, 60 * 1000).unref();

function limited(key, max) {
  const m = Math.floor(Date.now() / 60000);
  const r = rate.get(key);
  if (!r || r.m !== m) {
    rate.set(key, { m, n: 1 });
    return false;
  }
  return ++r.n > max;
}

// ------------------------------------------------------------------ logika

const lower = (s) => String(s).toLowerCase();

function uuidByName(name) {
  const l = lower(name);
  for (const [u, x] of Object.entries(db.users)) if (lower(x.name) === l) return u;
  return null;
}

function areFriends(a, b) {
  return (db.friends[a] || []).includes(b);
}

function link(a, b) {
  for (const [x, y] of [[a, b], [b, a]]) {
    const l = db.friends[x] || (db.friends[x] = []);
    if (!l.includes(y)) l.push(y);
  }
}

function unlink(a, b) {
  for (const [x, y] of [[a, b], [b, a]]) {
    if (db.friends[x]) db.friends[x] = db.friends[x].filter((u) => u !== y);
  }
}

function presence(uuid) {
  const u = db.users[uuid];
  if (!u || !u.seen || Date.now() - u.seen > ONLINE_MS) return { state: 'offline', info: '' };
  return { state: u.state === 'game' ? 'game' : 'online', info: u.info || '' };
}

function snapshot(me) {
  const my = db.users[me];
  const myLower = lower(my.name);
  return {
    me: { uuid: me, name: my.name },
    friends: (db.friends[me] || []).map((u) => {
      const x = db.users[u] || { name: u };
      const p = presence(u);
      return { uuid: u, name: x.name, state: p.state, info: p.info };
    }),
    incoming: db.requests
      .filter((r) => r.to === myLower)
      .map((r) => ({ uuid: r.from, name: (db.users[r.from] || {}).name || r.fromName, ts: r.ts })),
    outgoing: db.requests.filter((r) => r.from === me).map((r) => ({ name: r.toName, ts: r.ts })),
  };
}

class HttpError extends Error {
  constructor(status, message, extra) {
    super(message);
    this.status = status;
    this.extra = extra;
  }
}

// ------------------------------------------------------------------ obsługa żądań

const routes = {};

routes['GET /health'] = async () => ({ ok: true });

routes['GET /v1/auth/challenge'] = async (ctx) => {
  const name = ctx.url.searchParams.get('name') || '';
  if (!NICK.test(name)) throw new HttpError(400, 'Błędny nick.');
  const serverId = crypto.randomBytes(16).toString('hex');
  challenges.set(serverId, { name: lower(name), exp: Date.now() + CHALLENGE_MS });
  return { serverId };
};

routes['POST /v1/auth/login'] = async (ctx) => {
  const name = String(ctx.body.name || '');
  const serverId = String(ctx.body.serverId || '');
  const c = challenges.get(serverId);
  if (!NICK.test(name) || !c || c.exp < Date.now() || c.name !== lower(name)) {
    throw new HttpError(400, 'Wyzwanie logowania wygasło - spróbuj jeszcze raz.');
  }
  challenges.delete(serverId);

  let prof;
  try {
    const r = await fetch(
      `${MOJANG}/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${serverId}`,
      { signal: AbortSignal.timeout(8000) });
    if (r.status !== 200) throw new HttpError(401, 'Nie udało się potwierdzić konta Minecraft.');
    prof = await r.json();
  } catch (e) {
    if (e instanceof HttpError) throw e;
    throw new HttpError(502, 'Serwer sesji Mojang nie odpowiada. Spróbuj za chwilę.');
  }
  const uuid = lower(String(prof.id || '')).replace(/-/g, '');
  if (!UUID.test(uuid) || !NICK.test(String(prof.name || ''))) {
    throw new HttpError(401, 'Mojang zwrócił błędny profil.');
  }

  const user = db.users[uuid] || (db.users[uuid] = { seen: 0, state: 'online', info: '' });
  if (user.name !== prof.name) {
    user.name = prof.name;                                   // zmiana nicku
    for (const r of db.requests) if (r.from === uuid) r.fromName = prof.name;
  }
  save();

  const token = crypto.randomBytes(24).toString('base64url');
  sessions.set(token, { uuid, exp: Date.now() + SESSION_S * 1000 });
  return { token, expiresIn: SESSION_S };
};

routes['POST /v1/ping'] = async (ctx) => {
  const u = db.users[ctx.me];
  u.state = ctx.body.state === 'game' ? 'game' : 'online';
  u.info = String(ctx.body.info || '').slice(0, 60);
  u.seen = Date.now();
  save();
  return snapshot(ctx.me);
};

routes['POST /v1/offline'] = async (ctx) => {
  db.users[ctx.me].seen = 0;
  save();
  return { ok: true };
};

routes['GET /v1/social'] = async (ctx) => snapshot(ctx.me);

routes['POST /v1/friends/request'] = async (ctx) => {
  const me = ctx.me;
  const my = db.users[me];
  const name = String(ctx.body.name || '').trim();
  if (!NICK.test(name)) throw new HttpError(400, 'Nick: 3-16 znaków (litery, cyfry, _).');
  const nl = lower(name);
  if (nl === lower(my.name)) throw new HttpError(400, 'Nie możesz dodać samego siebie.');

  const target = uuidByName(name);
  if (target && areFriends(me, target)) throw new HttpError(409, 'Ten gracz już jest na Twojej liście.');
  if (db.requests.some((r) => r.from === me && r.to === nl)) {
    throw new HttpError(409, 'Zaproszenie już zostało wysłane.');
  }
  if ((db.friends[me] || []).length >= MAX_FRIENDS) throw new HttpError(409, 'Osiągnięto limit znajomych.');
  if (db.requests.filter((r) => r.from === me).length >= MAX_OUTGOING) {
    throw new HttpError(409, 'Za dużo oczekujących zaproszeń.');
  }

  // ta osoba już nas zaprosiła -> od razu zostajecie znajomymi
  const theirs = db.requests.find((r) => r.to === lower(my.name) && lower(r.fromName) === nl);
  if (theirs) {
    link(me, theirs.from);
    db.requests = db.requests.filter((r) => r !== theirs);
    save();
    return { ok: true, accepted: true };
  }

  db.requests.push({ from: me, fromName: my.name, to: nl, toName: name, ts: Date.now() });
  save();
  return { ok: true, accepted: false };
};

routes['POST /v1/friends/accept'] = async (ctx) => {
  const from = String(ctx.body.uuid || '');
  const myLower = lower(db.users[ctx.me].name);
  const req = db.requests.find((r) => r.from === from && r.to === myLower);
  if (!req) throw new HttpError(404, 'Nie ma takiego zaproszenia.');
  if ((db.friends[ctx.me] || []).length >= MAX_FRIENDS) throw new HttpError(409, 'Osiągnięto limit znajomych.');
  link(ctx.me, from);
  db.requests = db.requests.filter((r) => r !== req && !(r.from === ctx.me && r.to === lower((db.users[from] || {}).name || '')));
  save();
  return { ok: true };
};

routes['POST /v1/friends/decline'] = async (ctx) => {
  const from = String(ctx.body.uuid || '');
  const myLower = lower(db.users[ctx.me].name);
  db.requests = db.requests.filter((r) => !(r.from === from && r.to === myLower));
  save();
  return { ok: true };
};

routes['POST /v1/friends/cancel'] = async (ctx) => {
  const nl = lower(String(ctx.body.name || ''));
  db.requests = db.requests.filter((r) => !(r.from === ctx.me && r.to === nl));
  save();
  return { ok: true };
};

routes['POST /v1/friends/remove'] = async (ctx) => {
  const other = String(ctx.body.uuid || '');
  unlink(ctx.me, other);
  save();
  return { ok: true };
};

const PUBLIC = new Set(['GET /health', 'GET /v1/auth/challenge', 'POST /v1/auth/login']);

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > 4096) {
        reject(new HttpError(413, 'Za duże żądanie.'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        const v = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        resolve(v && typeof v === 'object' ? v : {});
      } catch (e) {
        reject(new HttpError(400, 'Błędny JSON.'));
      }
    });
    req.on('error', reject);
  });
}

function send(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    const key = `${req.method} ${url.pathname}`;
    const handler = routes[key];
    if (!handler) throw new HttpError(404, 'Nie znaleziono.');

    const ip = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').toString().split(',')[0].trim();
    if (limited('ip:' + ip, 240)) throw new HttpError(429, 'Za dużo żądań - zwolnij.');

    const ctx = { url, body: {}, me: null };
    if (!PUBLIC.has(key)) {
      const m = /^Bearer (\S+)$/.exec(req.headers.authorization || '');
      const s = m && sessions.get(m[1]);
      if (!s || s.exp < Date.now() || !db.users[s.uuid]) throw new HttpError(401, 'Sesja wygasła.');
      ctx.me = s.uuid;
      if (limited('u:' + ctx.me, 120)) throw new HttpError(429, 'Za dużo żądań - zwolnij.');
    } else if (key === 'POST /v1/auth/login' && limited('login:' + ip, 20)) {
      throw new HttpError(429, 'Za dużo prób logowania.');
    }
    if (req.method === 'POST') ctx.body = await readBody(req);

    send(res, 200, await handler(ctx));
  } catch (e) {
    if (e instanceof HttpError) return send(res, e.status, { error: e.message });
    console.error(e);
    send(res, 500, { error: 'Błąd serwera.' });
  }
});

server.listen(PORT, HOST, () => console.log(`BushLaunch friends server: http://${HOST}:${PORT}`));

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    saveNow();
    process.exit(0);
  });
}
