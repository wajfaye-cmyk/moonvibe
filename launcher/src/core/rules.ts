// Mojangs "rules": bestimmen, ob eine Bibliothek oder ein Argument auf diesem System gilt.
import os from 'node:os';

export interface Rule {
  action: 'allow' | 'disallow';
  os?: { name?: string; arch?: string; version?: string };
  features?: Record<string, boolean>;
}

export type OsName = 'windows' | 'osx' | 'linux';

export function osName(): OsName {
  if (process.platform === 'win32') return 'windows';
  if (process.platform === 'darwin') return 'osx';
  return 'linux';
}

export function is64Bit(): boolean {
  return process.arch === 'x64' || process.arch === 'arm64';
}

function osMatches(rule: NonNullable<Rule['os']>): boolean {
  if (rule.name && rule.name !== osName()) return false;
  if (rule.arch === 'x86' && is64Bit()) return false;
  if (rule.arch && rule.arch !== 'x86' && rule.arch !== process.arch) return false;
  if (rule.version && !new RegExp(rule.version).test(os.release())) return false;
  return true;
}

export function rulesAllow(rules: Rule[] | undefined, features: Record<string, boolean> = {}): boolean {
  if (!rules || rules.length === 0) return true;
  let allowed = false;
  for (const rule of rules) {
    let matches = true;
    if (rule.os && !osMatches(rule.os)) matches = false;
    if (rule.features) {
      for (const [key, value] of Object.entries(rule.features)) {
        if ((features[key] ?? false) !== value) matches = false;
      }
    }
    if (matches) allowed = rule.action === 'allow';
  }
  return allowed;
}
