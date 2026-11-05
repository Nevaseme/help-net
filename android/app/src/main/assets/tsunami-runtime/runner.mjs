import { computePrecomputedHeight } from './tsunami-precomputed.mjs';

let current;
const send = payload => TsunamiNative.postMessage(JSON.stringify(payload));
window.cancelTsunami = () => { current?.abort(); current = undefined; };
window.computeTsunami = async (id, height) => {
  window.cancelTsunami();
  const control = new AbortController(); current = control;
  let lastProgress = 0;
  try {
    const result = await computePrecomputedHeight(height, {
      signal: control.signal,
      onProgress: progress => {
        const now = performance.now();
        if (now - lastProgress > 150) { send({ type: 'progress', id, message: progress.message }); lastProgress = now; }
      }
    });
    if (!control.signal.aborted) send({ type: 'result', id, result });
  } catch (error) {
    if (!control.signal.aborted) send({ type: 'error', id, message: error.message });
  } finally { if (current === control) current = undefined; }
};
send({ type: 'ready' });
