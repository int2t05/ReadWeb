'use strict';

const fs = require('node:fs');
const path = require('node:path');

const packageRoot = path.join(__dirname, 'node_modules', 'markdown-it');
const appAssets = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const browserBundle = path.join(packageRoot, 'dist', 'markdown-it.min.js');
const renderer = path.join(__dirname, 'renderer.js');

for (const source of [browserBundle, renderer]) {
  if (!fs.existsSync(source)) {
    throw new Error(`Required markdown-it asset is missing: ${source}. Run npm ci first.`);
  }
}

fs.mkdirSync(appAssets, { recursive: true });
fs.copyFileSync(browserBundle, path.join(appAssets, 'markdown-it.min.js'));
fs.copyFileSync(renderer, path.join(appAssets, 'markdown-renderer.js'));

const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, 'package.json'), 'utf8'));
const dependencies = Object.entries(packageJson.dependencies || {}).sort(([a], [b]) => a.localeCompare(b));
const packageLock = JSON.parse(fs.readFileSync(path.join(__dirname, 'package-lock.json'), 'utf8'));
const licensesDir = path.join(appAssets, 'licenses');
fs.mkdirSync(licensesDir, { recursive: true });
const bundledPackages = [['markdown-it', packageJson.version], ...dependencies.map(([name]) => {
  const entry = packageLock.packages[`node_modules/${name}`];
  if (!entry) throw new Error(`Missing locked package: ${name}`);
  return [name, entry.version];
})];
const notices = [
  '# Bundled JavaScript licenses',
  '',
  'ReadWeb bundles the following JavaScript packages and their licenses:',
  '',
  ...bundledPackages.map(([name, version]) => {
    const packageRoot = path.join(__dirname, 'node_modules', name);
    const entry = packageLock.packages[`node_modules/${name}`];
    const licenseFile = fs.readdirSync(packageRoot).find((file) => /^licen[cs]e(?:[-_.].*)?$/i.test(file));
    if (!licenseFile) throw new Error(`No license file found for ${name}`);
    const targetName = `${name.replace(/[^a-z0-9.-]/gi, '-')}-LICENSE.txt`;
    fs.copyFileSync(path.join(packageRoot, licenseFile), path.join(licensesDir, targetName));
    return `- ${name} ${version} — ${entry?.license || 'see bundled license text'}: [licenses/${targetName}](licenses/${targetName})`;
  }),
  '',
  'The exact dependency graph is pinned in `web-assets/package-lock.json`.'
];
fs.writeFileSync(path.join(appAssets, 'THIRD-PARTY-NOTICES.txt'), `${notices.join('\n')}\n`);
