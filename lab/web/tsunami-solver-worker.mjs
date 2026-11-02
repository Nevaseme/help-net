import { calibrateHeight } from './tsunami-solver.mjs';

// The owner cancels by terminating this dedicated worker. This keeps cancellation
// immediate even while the numerical loop is running, with no stale result reuse.
self.onmessage = ({ data }) => {
  if (data.type !== 'run') return;
  const { id, config, targetHeight } = data;
  try {
    const output = calibrateHeight(config, targetHeight, { onProgress: progress => self.postMessage({ type: 'progress', id, progress }) });
    const r = output.result;
    self.postMessage({ type: 'result', id, ...output }, [r.maxDepth.buffer, r.depth.buffer, r.xMomentum.buffer, r.yMomentum.buffer]);
  } catch (error) { self.postMessage({ type: 'error', id, message: error.message }); }
};
