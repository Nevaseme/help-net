import { readFileSync, writeFileSync } from 'node:fs';
import { calibrateHeight } from '../../lab/web/tsunami-solver.mjs';
const path = process.argv[2];
if (!path) throw new Error('Usage: node tools/tsunami/review_browser_domain.mjs domain.json [height ...]');
const data = JSON.parse(readFileSync(path, 'utf8'));
for (const height of process.argv.slice(3).map(Number)) {
  const start = process.cpuUsage();
  const output = calibrateHeight(data.config, height, { onProgress: p => { if (p.run_complete) console.error(JSON.stringify(p)); } });
  const cpu = process.cpuUsage(start);
  const result = { domain_id: data.id, height, ...output, cpu_s: (cpu.user + cpu.system) / 1e6 };
  result.result = { ...output.result, depth: Array.from(output.result.depth), maxDepth: Array.from(output.result.maxDepth), xMomentum: undefined, yMomentum: undefined };
  writeFileSync(path.replace(/\.json$/, `-height-${height}-review.json`), JSON.stringify(result));
  console.log(JSON.stringify({ height, status: output.status, reason: output.reason, calibration: output.calibration, diagnostics: output.result.diagnostics, boundaryAudit: output.result.boundaryAudit, cpu_s: result.cpu_s }));
}
