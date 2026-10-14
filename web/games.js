// Games with a bundled preset, plus helpers to recognise a ROM from its header.

export const GAMES = [
  {
    id: 'gold',
    name: 'Gold',
    platform: 'Game Boy Color',
    gen: 2,
    preset: 'presets/gold.rnqs',
    accent: '#c99a1b',
    fileStem: 'Gold',
    matches: (info) => info.kind === 'gb' && info.title.startsWith('POKEMON_GLD'),
  },
  {
    id: 'emerald',
    name: 'Smaragd',
    platform: 'Game Boy Advance',
    gen: 3,
    preset: 'presets/emerald.rnqs',
    accent: '#1f9d6b',
    fileStem: 'Smaragd',
    matches: (info) => info.kind === 'gba' && info.code.startsWith('BPE'),
  },
  {
    id: 'platin',
    name: 'Platin',
    platform: 'Nintendo DS',
    gen: 4,
    preset: 'presets/platin.rnqs',
    accent: '#7a7f8c',
    fileStem: 'Platin',
    matches: (info) => info.kind === 'nds' && info.code.startsWith('CPU'),
  },
  {
    id: 'black',
    name: 'Schwarz',
    platform: 'Nintendo DS',
    gen: 5,
    preset: 'presets/black.rnqs',
    accent: '#3b3f47',
    fileStem: 'Schwarz',
    matches: (info) => info.kind === 'nds' && info.code.startsWith('IRB'),
  },
  {
    id: 'black2',
    name: 'Schwarz 2',
    platform: 'Nintendo DS',
    gen: 5,
    preset: 'presets/black2.rnqs',
    accent: '#2c5d8f',
    fileStem: 'Schwarz2',
    matches: (info) => info.kind === 'nds' && info.code.startsWith('IRE'),
  },
];

export const CUSTOM_GAME = {
  id: 'custom',
  name: 'Anderes Spiel',
  platform: 'Gen 1–5, eigene Settings',
  gen: 0,
  preset: null,
  accent: '#b4462f',
  fileStem: 'Pokemon',
  matches: () => true,
};

const KNOWN = {
  gb: {
    POKEMON_GLD: 'Pokémon Gold', POKEMON_SLV: 'Pokémon Silber', PM_CRYSTAL: 'Pokémon Kristall',
    'POKEMON RED': 'Pokémon Rot', 'POKEMON BLUE': 'Pokémon Blau', 'POKEMON YELLOW': 'Pokémon Gelb',
  },
  gba: {
    BPE: 'Pokémon Smaragd', AXV: 'Pokémon Rubin', AXP: 'Pokémon Saphir',
    BPR: 'Pokémon Feuerrot', BPG: 'Pokémon Blattgrün',
  },
  nds: {
    ADA: 'Pokémon Diamant', APA: 'Pokémon Perl', CPU: 'Pokémon Platin',
    IPK: 'Pokémon HeartGold', IPG: 'Pokémon SoulSilver',
    IRB: 'Pokémon Schwarz', IRA: 'Pokémon Weiß', IRE: 'Pokémon Schwarz 2', IRD: 'Pokémon Weiß 2',
  },
};

const LANGUAGES = { D: 'Deutsch', E: 'Englisch', F: 'Französisch', I: 'Italienisch', S: 'Spanisch', J: 'Japanisch', K: 'Koreanisch' };

function ascii(bytes, start, length) {
  let s = '';
  for (let i = start; i < start + length && i < bytes.length; i++) {
    const c = bytes[i];
    if (c === 0) break;
    s += c >= 0x20 && c < 0x7f ? String.fromCharCode(c) : '?';
  }
  return s;
}

function hasBytes(bytes, offset, expected) {
  return expected.every((b, i) => bytes[offset + i] === b);
}

// Nintendo logo fragments every licensed cartridge carries in its header.
const LOGO_GB = [0xce, 0xed, 0x66, 0x66];
const LOGO_GBA_NDS = [0x24, 0xff, 0xae, 0x51];

function sniffKind(head, ext) {
  if (hasBytes(head, 0xc0, LOGO_GBA_NDS)) return 'nds';
  if (hasBytes(head, 0x04, LOGO_GBA_NDS) && head[0xb2] === 0x96) return 'gba';
  if (hasBytes(head, 0x104, LOGO_GB)) return 'gb';
  if (ext === 'nds') return 'nds';
  if (ext === 'gba') return 'gba';
  if (ext === 'gb' || ext === 'gbc' || ext === 'sgb') return 'gb';
  return 'unknown';
}

/** Reads the first bytes of a ROM file and guesses which game it is. */
export async function identifyRom(file) {
  const head = new Uint8Array(await file.slice(0, 0x200).arrayBuffer());
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  const info = { kind: sniffKind(head, ext), code: '', title: '', label: '', language: '' };

  if (info.kind === 'nds') {
    info.code = ascii(head, 0x0c, 4);
    info.label = KNOWN.nds[info.code.slice(0, 3)] || '';
  } else if (info.kind === 'gba') {
    info.code = ascii(head, 0xac, 4);
    info.label = KNOWN.gba[info.code.slice(0, 3)] || '';
  } else if (info.kind === 'gb') {
    info.title = ascii(head, 0x134, 15);
    const key = Object.keys(KNOWN.gb).find((k) => info.title.startsWith(k));
    info.label = key ? KNOWN.gb[key] : '';
    // CGB-era titles end with a 4-letter game code (e.g. POKEMON_GLDAAUD)
    info.code = info.title.length === 15 ? info.title.slice(11) : '';
  }
  if (info.code.length === 4) {
    info.language = LANGUAGES[info.code.charAt(3)] || '';
  }
  return info;
}

export function describeRom(info) {
  if (!info || !info.label) return 'Unbekannte ROM';
  return info.language ? `${info.label} (${info.language})` : info.label;
}
