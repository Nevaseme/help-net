// Local validation harness only; production imports the same solver in a worker.
import { readFileSync } from 'node:fs';
import { simulate } from '../../lab/web/tsunami-solver.mjs';
const config = JSON.parse(readFileSync(0, 'utf8'));
const cpu = process.cpuUsage();
const result = simulate(config);
const used = process.cpuUsage(cpu);
console.log(JSON.stringify({ ...result, depth: Array.from(result.depth), maxDepth: Array.from(result.maxDepth),
  xMomentum: undefined, yMomentum: undefined, cpu_s: (used.user + used.system) / 1e6 }));
