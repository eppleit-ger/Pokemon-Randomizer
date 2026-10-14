// Quick check that the compiled engine loads, parses every bundled preset and
// rejects a non-ROM cleanly. Real ROMs can't be shipped, so this is as far as
// CI can go.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const web = fileURLToPath(new URL('../web/', import.meta.url));
const engine = await import(new URL('../web/engine/randomizer.js', import.meta.url));

let failed = false;
for (const name of readdirSync(web + 'presets')) {
  const settings = new Int8Array(readFileSync(web + 'presets/' + name));
  const ok = engine.randomize(new Int8Array(1024), 'dummy.gba', settings, '');
  const error = engine.getError();
  const expected = !ok && error.startsWith('Diese ROM wird nicht unterstützt');
  console.log(`${expected ? 'ok  ' : 'FAIL'} ${name}: ${error.split('\n')[0]}`);
  if (!expected) failed = true;
}
process.exit(failed ? 1 : 0);
