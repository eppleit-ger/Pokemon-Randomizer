import { GAMES, CUSTOM_GAME, identifyRom, describeRom } from './games.js';

const ALL_GAMES = [...GAMES, CUSTOM_GAME];
// Replaced with the commit hash at deploy time, so updated engine files bypass stale caches.
const BUILD = '__BUILD__';
const $ = (id) => document.getElementById(id);

// ---------------------------------------------------------------- storage --
// ROMs are kept in IndexedDB on this device only, keyed by game id.

const db = {
  _open: null,
  open() {
    if (!this._open) {
      this._open = new Promise((resolve, reject) => {
        const req = indexedDB.open('nuzlocke-randomizer', 1);
        req.onupgradeneeded = () => req.result.createObjectStore('roms', { keyPath: 'id' });
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
    }
    return this._open;
  },
  async tx(mode, fn) {
    const conn = await this.open();
    return new Promise((resolve, reject) => {
      const t = conn.transaction('roms', mode);
      const req = fn(t.objectStore('roms'));
      t.oncomplete = () => resolve(req && req.result);
      t.onerror = () => reject(t.error);
      t.onabort = () => reject(t.error);
    });
  },
  get(id) { return this.tx('readonly', (s) => s.get(id)).catch(() => undefined); },
  keys() { return this.tx('readonly', (s) => s.getAllKeys()).catch(() => []); },
  put(entry) { return this.tx('readwrite', (s) => s.put(entry)); },
  delete(id) { return this.tx('readwrite', (s) => s.delete(id)).catch(() => undefined); },
};

// ------------------------------------------------------------------ state --

const state = {
  game: null,
  rom: null,        // { blob, name, size, info, stored }
  settings: null,   // { name, buffer } custom .rnqs, overrides the preset
  result: null,     // { url, logUrl, file }
};

// -------------------------------------------------------------- utilities --

function formatSize(bytes) {
  if (bytes >= 1 << 20) return `${(bytes / (1 << 20)).toFixed(bytes >= 100 << 20 ? 0 : 1)} MB`;
  return `${Math.round(bytes / 1024)} KB`;
}

function stamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`;
}

function clearResult() {
  if (state.result) {
    URL.revokeObjectURL(state.result.url);
    URL.revokeObjectURL(state.result.logUrl);
  }
  state.result = null;
  $('result').hidden = true;
  $('error').hidden = true;
}

// ------------------------------------------------------------------- home --

async function renderHome() {
  const saved = new Set(await db.keys());
  const grid = $('games');
  grid.textContent = '';
  for (const game of ALL_GAMES) {
    const btn = document.createElement('button');
    btn.className = 'game';
    btn.style.setProperty('--accent', game.accent);
    btn.innerHTML = `
      ${saved.has(game.id) ? '<span class="saved">ROM gemerkt</span>' : ''}
      ${game.gen ? `<span class="gen">Gen ${game.gen}</span>` : ''}
      <span class="name"></span>
      <span class="platform"></span>`;
    btn.querySelector('.name').textContent = game.name;
    btn.querySelector('.platform').textContent = game.platform;
    btn.addEventListener('click', () => { location.hash = `#/${game.id}`; });
    grid.appendChild(btn);
  }
}

// ------------------------------------------------------------------- game --

async function openGame(game) {
  state.game = game;
  state.rom = null;
  state.settings = null;
  clearResult();

  $('hero').style.setProperty('--accent', game.accent);
  $('hero-name').textContent = game.id === 'custom' ? 'Anderes Spiel' : `Pokémon ${game.name}`;
  $('hero-platform').textContent = game.gen ? `Gen ${game.gen} · ${game.platform}` : game.platform;
  $('seed').value = '';
  $('settings-name').textContent = '';
  $('settings-input').value = '';
  $('advanced').open = game.id === 'custom';
  $('preset-hint').textContent = game.preset
    ? 'Verwendet dein gespeichertes Nuzlocke-Preset für dieses Spiel.'
    : 'Wähle unter „Erweitert“ eine Settings-Datei (.rnqs) aus dem Universal Pokemon Randomizer ZX.';

  const stored = await db.get(game.id);
  if (stored && state.game === game) {
    const info = await identifyRom(stored.blob);
    state.rom = { blob: stored.blob, name: stored.name, size: stored.blob.size, info, stored: true };
  }
  renderRom();
}

function renderRom() {
  const rom = state.rom;
  $('rom-empty').hidden = !!rom;
  $('rom-loaded').hidden = !rom;
  if (rom) {
    $('rom-name').textContent = rom.name;
    $('rom-meta').textContent = `${describeRom(rom.info)} · ${formatSize(rom.size)}${rom.stored ? ' · gemerkt' : ''}`;
    $('forget').hidden = !rom.stored;
    $('remember-row').hidden = rom.stored;

    const game = state.game;
    const mismatch = game.id !== 'custom' && !game.matches(rom.info);
    $('rom-mismatch').hidden = !mismatch;
    if (mismatch) {
      $('rom-mismatch').textContent =
        `Das sieht nicht nach Pokémon ${game.name} aus (erkannt: ${describeRom(rom.info)}). ` +
        'Das Preset wird trotzdem angewendet, falls du fortfährst.';
    }
  }
  updateGoButton();
}

function updateGoButton() {
  const needsSettings = !state.game?.preset && !state.settings;
  $('go').disabled = !state.rom || needsSettings;
}

async function pickRom(file) {
  if (!file) return;
  clearResult();
  const info = await identifyRom(file);
  state.rom = { blob: file, name: file.name, size: file.size, info, stored: false };
  renderRom();
}

async function rememberRomIfWanted() {
  const rom = state.rom;
  if (!rom || rom.stored || !$('remember').checked) return;
  try {
    if (navigator.storage && navigator.storage.persist) {
      await navigator.storage.persist().catch(() => false);
    }
    await db.put({ id: state.game.id, name: rom.name, blob: rom.blob, savedAt: Date.now() });
    rom.stored = true;
    renderRom();
  } catch (e) {
    console.warn('Could not store ROM', e);
  }
}

// -------------------------------------------------------------- randomize --

let wakeLock = null;
async function keepAwake(on) {
  try {
    if (on && 'wakeLock' in navigator) {
      wakeLock = await navigator.wakeLock.request('screen');
    } else if (!on && wakeLock) {
      await wakeLock.release();
      wakeLock = null;
    }
  } catch {
    wakeLock = null;
  }
}

function showBusy(on, isDs) {
  $('busy').hidden = !on;
  if (!on) return;
  $('busy-hint').textContent = isDs
    ? 'DS-Spiele brauchen etwas länger (ca. 1–2 Minuten). Bitte die Seite geöffnet lassen.'
    : 'Bitte die Seite geöffnet lassen.';
}

async function loadSettings() {
  if (state.settings) return state.settings.buffer.slice(0);
  const res = await fetch(state.game.preset);
  if (!res.ok) throw new Error(`Preset konnte nicht geladen werden (${res.status})`);
  return res.arrayBuffer();
}

function runWorker(message, transfer) {
  return new Promise((resolve) => {
    const worker = new Worker(new URL(`./worker.js?v=${BUILD}`, import.meta.url), { type: 'module' });
    const finish = (data) => { worker.terminate(); resolve(data); };
    worker.onmessage = (e) => finish(e.data);
    worker.onerror = (e) => finish({ type: 'error', error: e.message || 'Worker-Fehler' });
    worker.onmessageerror = () => finish({ type: 'error', error: 'Nachricht vom Worker konnte nicht gelesen werden' });
    worker.postMessage(message, transfer);
  });
}

async function randomize() {
  const game = state.game;
  const rom = state.rom;
  if (!rom) return;
  clearResult();

  const isDs = rom.info.kind === 'nds' || rom.size > 64 << 20;
  showBusy(true, isDs);
  keepAwake(true);
  const started = Date.now();
  const timer = setInterval(() => {
    $('busy-time').textContent = `${Math.round((Date.now() - started) / 1000)} s`;
  }, 500);
  $('busy-time').textContent = '0 s';

  try {
    // Remember the ROM first, so a crash during randomizing doesn't lose it.
    await rememberRomIfWanted();
    $('busy-title').textContent = 'ROM wird gelesen …';
    const [romBuffer, settingsBuffer] = await Promise.all([rom.blob.arrayBuffer(), loadSettings()]);
    $('busy-title').textContent = 'Randomizen …';

    const reply = await runWorker(
      { rom: romBuffer, romName: rom.name, settings: settingsBuffer, seed: $('seed').value.trim() },
      [romBuffer, settingsBuffer],
    );

    if (reply.type !== 'done') {
      showError(reply.error, reply.romName);
      return;
    }

    const bytes = new Uint8Array(reply.buffer, reply.byteOffset || 0, reply.size);
    const fileName = `${game.fileStem}-random-${stamp()}.${reply.extension}`;
    const file = new File([bytes], fileName, { type: 'application/octet-stream' });
    const logFile = new File([reply.log], fileName.replace(/\.[^.]+$/, '') + '.log.txt', { type: 'text/plain' });
    const seed = (reply.log.match(/Random Seed:\s*(-?\d+)/) || [])[1] || '';

    state.result = { file, url: URL.createObjectURL(file), logUrl: URL.createObjectURL(logFile) };
    $('download').href = state.result.url;
    $('download').download = fileName;
    $('download-log').href = state.result.logUrl;
    $('download-log').download = logFile.name;
    $('share').hidden = !(navigator.canShare && navigator.canShare({ files: [file] }));
    $('result-meta').textContent =
      `${reply.romName} · ${formatSize(reply.size)} · ${(reply.ms / 1000).toFixed(1)} s` + (seed ? ` · Seed ${seed}` : '');
    $('result-warnings').hidden = !reply.warnings;
    $('result-warnings').textContent = reply.warnings || '';
    $('result').hidden = false;
    $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (e) {
    showError(String((e && e.message) || e));
  } finally {
    clearInterval(timer);
    showBusy(false);
    keepAwake(false);
  }
}

function showError(message, romName) {
  const text = String(message || 'Unbekannter Fehler');
  const firstLine = text.split('\n')[0];
  let friendly = firstLine;
  if (/out of memory|allocation|RangeError|Array buffer/i.test(text)) {
    friendly = 'Dem Browser ist der Speicher ausgegangen. Schließe andere Tabs/Apps und versuche es erneut.';
  }
  $('error-message').textContent = friendly;
  $('error-details').textContent =
    `Spiel: ${state.game?.id}\nROM: ${state.rom?.name} (${describeRom(state.rom?.info)}, ${state.rom?.size} Bytes)` +
    (romName ? `\nErkannt: ${romName}` : '') +
    `\nBrowser: ${navigator.userAgent}\n\n${text}`;
  $('error').hidden = false;
  $('error').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ---------------------------------------------------------------- routing --

function route() {
  const id = location.hash.replace(/^#\/?/, '');
  const game = ALL_GAMES.find((g) => g.id === id);
  $('view-home').hidden = !!game;
  $('view-game').hidden = !game;
  $('back').hidden = !game;
  window.scrollTo(0, 0);
  if (game) {
    openGame(game);
  } else {
    state.game = null;
    renderHome();
  }
}

// ----------------------------------------------------------------- wiring --

$('back').addEventListener('click', () => {
  if (history.length > 1) history.back();
  else location.hash = '';
});
$('rom-input').addEventListener('change', (e) => pickRom(e.target.files[0]));
$('rom-input-change').addEventListener('change', (e) => pickRom(e.target.files[0]));
$('forget').addEventListener('click', async () => {
  await db.delete(state.game.id);
  state.rom = null;
  $('rom-input').value = '';
  $('rom-input-change').value = '';
  renderRom();
});
$('settings-input').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  state.settings = file ? { name: file.name, buffer: await file.arrayBuffer() } : null;
  $('settings-name').textContent = file ? `Verwendet: ${file.name}` : '';
  updateGoButton();
});
$('go').addEventListener('click', randomize);
$('share').addEventListener('click', async () => {
  if (!state.result) return;
  try {
    await navigator.share({ files: [state.result.file] });
  } catch (e) {
    if (e && e.name !== 'AbortError') showError(`Teilen fehlgeschlagen: ${e.message || e}`);
  }
});
$('copy-error').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText($('error-details').textContent);
    $('copy-error').textContent = 'Kopiert ✓';
  } catch {
    $('copy-error').textContent = 'Kopieren nicht möglich';
  }
});

window.addEventListener('hashchange', route);
route();

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
fetch('version.txt').then((r) => (r.ok ? r.text() : '')).then((v) => {
  if (v.trim()) $('version').textContent = `· Build ${v.trim()}`;
}).catch(() => {});
