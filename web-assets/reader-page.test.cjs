'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

test('exposes a safe heading outline, index-based navigation, and bounded font controls', () => {
  const headings = [
    { tagName: 'H1', textContent: '  Guide  ', scrollIntoView(options) { this.options = options; } },
    { tagName: 'H3', textContent: ' Details ', scrollIntoView(options) { this.options = options; } }
  ];
  const styleValues = new Map([['--reader-size', '18px']]);
  const article = {
    innerHTML: '',
    querySelectorAll(selector) {
      return selector === 'h1, h2, h3, h4, h5, h6' ? headings : [];
    },
    querySelector(selector) { return selector === 'h1' ? headings[0] : null; }
  };
  const sourceLabel = { textContent: '' };
  const document = {
    title: '',
    documentElement: {
      style: { setProperty(name, value) { styleValues.set(name, value); } }
    },
    getElementById(id) { return id === 'content' ? article : sourceLabel; }
  };
  const window = { ReadWebMarkdown: { render(source) { return `<p>${source}</p>`; } } };
  const context = {
    document,
    window,
    URL,
    Uint8Array,
    TextDecoder,
    atob,
    getComputedStyle() { return { getPropertyValue(name) { return styleValues.get(name) || ''; } }; }
  };

  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../app/src/main/assets/reader-page.js'), 'utf8'), context);
  const api = window.ReadWebMarkdownDocument;
  api.setBase64('https://example.com/guide.md', Buffer.from('# Guide').toString('base64'));

  assert.deepEqual(JSON.parse(JSON.stringify(api.getOutline())), [
    { level: 1, title: 'Guide' },
    { level: 3, title: 'Details' }
  ]);
  assert.equal(api.scrollToHeading(1), true);
  assert.deepEqual(JSON.parse(JSON.stringify(headings[1].options)), { behavior: 'smooth', block: 'start' });
  assert.equal(api.scrollToHeading(-1), false);
  assert.equal(api.scrollToHeading(2), false);
  assert.equal(api.scrollToHeading(1.5), false);
  assert.equal(api.setFontSize(100), 28);
  assert.equal(styleValues.get('--reader-size'), '28px');
  assert.equal(api.adjustFont(-50), 14);
  assert.equal(styleValues.get('--reader-size'), '14px');

  headings[0].textContent = 'x'.repeat(240);
  headings.push(...Array.from({ length: 300 }, () => ({ tagName: 'H2', textContent: 'Extra' })));
  assert.equal(api.getOutline().length, 300);
  assert.equal(api.getOutline()[0].title.length, 200);
});
