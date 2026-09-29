import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

export const EXT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../extension');
export const read = (rel) => fs.readFileSync(path.join(EXT, rel), 'utf8');

/** Loads the classic scripts into a plain sandbox (core lib only). */
export function loadCore() {
  const ctx = vm.createContext({ URL, Intl, console });
  vm.runInContext(read('shared/pricecore.js'), ctx);
  return ctx.PricePulseCore;
}
