# Nuzlocke Randomizer (Web)

Pokémon-ROMs direkt im Browser randomizen – auch auf dem iPhone, ohne Java, ohne PC, ohne Installation.

**Ablauf:** Spiel antippen → ROM auswählen (wird auf Wunsch auf dem Gerät gemerkt) → *Randomizen* → ROM herunterladen oder per *Teilen* direkt an den Emulator (z. B. Delta) schicken.

Die ROM verlässt das Gerät nie: Der Randomizer läuft komplett im Browser. Es werden keine ROMs bereitgestellt – verwende nur Spiele, die du selbst besitzt.

## Wie funktioniert das?

Der eigentliche Randomizer ist der unveränderte Java-Code des
[Universal Pokemon Randomizer ZX](https://github.com/Ajarmar/universal-pokemon-randomizer-zx) (v4.6.1).
Er wird mit [TeaVM](https://teavm.org) von Java nach JavaScript übersetzt und läuft in einem Web Worker.
Damit sind Ergebnisse und Settings-Dateien (`.rnqs`) kompatibel zur PC-Version.

```
web/            statische Website (GitHub Pages)
  presets/      mitgelieferte Settings (.rnqs) je Spiel
  engine/       wird beim Build erzeugt (randomizer.js)
engine/         Maven-Projekt: Java → JavaScript
  upr/          Quellcode des UPR ZX (GPLv3), siehe engine/PATCHES.md
  src/          Brücke zwischen Browser und Randomizer
test/smoke.mjs  Kurztest für die gebaute Engine
```

Unterstützt: Gen 1–5 (GB, GBC, GBA, NDS). 3DS-Spiele (Gen 6/7) sind bewusst nicht enthalten.

## Presets

| Spiel | Datei |
|---|---|
| Gold (GBC) | `web/presets/gold.rnqs` |
| Smaragd (GBA) | `web/presets/emerald.rnqs` |
| Platin (NDS) | `web/presets/platin.rnqs` |
| Schwarz (NDS) | `web/presets/black.rnqs` |
| Schwarz 2 (NDS) | `web/presets/black2.rnqs` |

Neues Preset: im UPR ZX am PC *Save Settings* → Datei in `web/presets/` legen und in `web/games.js` eintragen.
Ohne Code-Änderung geht es über *Anderes Spiel* bzw. *Erweitert → Eigene Settings-Datei*.

## Lokal bauen

Voraussetzungen: JDK 11+ (getestet mit 21), Maven, Node 18+.

```sh
mvn -f engine/pom.xml package     # erzeugt web/engine/randomizer.js
node test/smoke.mjs
cd web && python3 -m http.server 8000   # http://localhost:8000
```

## Veröffentlichen

Jeder Push auf `main` baut die Seite per GitHub Actions und veröffentlicht sie auf GitHub Pages
(*Settings → Pages → Source: GitHub Actions*).

## Lizenz

GPLv3, wie der Universal Pokemon Randomizer ZX, auf dem dieses Projekt basiert.
Pokémon und alle zugehörigen Namen sind Marken von Nintendo / Game Freak / The Pokémon Company.
