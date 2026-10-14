// Runs the randomizer off the main thread. A fresh worker is used for every
// run and terminated afterwards, so all memory is returned to the system.

self.onmessage = async (event) => {
  const { rom, romName, settings, seed } = event.data;
  const started = performance.now();
  try {
    const engine = await import('./engine/randomizer.js');
    const ok = engine.randomize(new Int8Array(rom), romName, new Int8Array(settings), seed || '');
    if (!ok) {
      self.postMessage({ type: 'error', error: engine.getError() || 'Unbekannter Fehler', romName: engine.getRomName() });
      return;
    }
    const view = engine.getOutput();
    const size = engine.getOutputSize();
    const result = {
      type: 'done',
      buffer: view.buffer,
      byteOffset: view.byteOffset,
      size,
      extension: engine.getOutputExtension(),
      romName: engine.getRomName(),
      log: engine.getLog(),
      warnings: engine.getWarnings(),
      ms: Math.round(performance.now() - started),
    };
    engine.release();
    self.postMessage(result, [view.buffer]);
  } catch (e) {
    self.postMessage({ type: 'error', error: String((e && e.stack) || e) });
  }
};
