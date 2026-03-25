/**
 * Security regression test:
 * Prevent storing/reading auth tokens in localStorage (XSS risk).
 *
 * This is intentionally a lightweight, framework-free test so it can run
 * in any CI without adding Jest/Vitest.
 *
 * Fails if any of the target files access localStorage for the "authToken".
 */

import fs from 'node:fs/promises';
import path from 'node:path';

const REPO_ROOT = path.resolve(process.cwd());

const TARGET_FILES = [
  'src/app/services/api.ts',
  'src/app/components/ProtectedRoute.tsx',
];

const BANNED_PATTERNS = [
  "localStorage.getItem('authToken')",
  'localStorage.getItem("authToken")',
  "localStorage.setItem('authToken'",
  'localStorage.setItem("authToken"',
  "localStorage.removeItem('authToken')",
  'localStorage.removeItem("authToken")',
];

function findAllOccurrences(haystack, needle) {
  const indices = [];
  let startIndex = 0;
  while (true) {
    const idx = haystack.indexOf(needle, startIndex);
    if (idx === -1) break;
    indices.push(idx);
    startIndex = idx + Math.max(1, needle.length);
  }
  return indices;
}

function indexToLineCol(text, index) {
  const upTo = text.slice(0, index);
  const lines = upTo.split('\n');
  return { line: lines.length, col: lines[lines.length - 1].length + 1 };
}

async function main() {
  const failures = [];

  for (const rel of TARGET_FILES) {
    const abs = path.join(REPO_ROOT, rel);
    let content;
    try {
      content = await fs.readFile(abs, 'utf8');
    } catch (e) {
      failures.push({
        file: rel,
        message: `Could not read file: ${e?.message || String(e)}`,
      });
      continue;
    }

    for (const pattern of BANNED_PATTERNS) {
      const matches = findAllOccurrences(content, pattern);
      for (const idx of matches) {
        const { line, col } = indexToLineCol(content, idx);
        failures.push({
          file: rel,
          message: `Banned token storage pattern found: ${pattern} at ${rel}:${line}:${col}`,
        });
      }
    }
  }

  if (failures.length > 0) {
    console.error('\n[security] localStorage auth token usage detected.\n');
    for (const f of failures) console.error(`- ${f.message}`);
    console.error('\nExpected: auth tokens are not read/written via localStorage.');
    console.error('Suggested fix: use httpOnly cookies or another non-localStorage mechanism.\n');
    process.exit(1);
  }

  console.log('[security] PASS: No localStorage auth token usage found in target files.');
}

await main();

