'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const MarkdownIt = require('markdown-it');
const { createRenderer, MAX_SOURCE_BYTES } = require('./renderer.js');

const renderer = createRenderer(MarkdownIt);

test('renders common reading elements and GFM tables', () => {
  const html = renderer.render('# 标题\n\n**重点**\n\n| 名称 | 值 |\n| --- | --- |\n| one | two |\n\n```java\nint n = 1;\n```');

  assert.match(html, /<h1>标题<\/h1>/);
  assert.match(html, /<strong>重点<\/strong>/);
  assert.match(html, /<table>/);
  assert.match(html, /<pre><code class="language-java">/);
});

test('keeps raw HTML inert and rejects dangerous URL schemes', () => {
  const html = renderer.render('<img src=x onerror=alert(1)>\n\n[click](javascript:alert(1))\n\n[image](data:text/html,boom)');

  assert.doesNotMatch(html, /<img\b/i);
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/i);
  assert.doesNotMatch(html, /<img\b/i);
  assert.doesNotMatch(html, /href="javascript:/i);
  assert.doesNotMatch(html, /href="data:/i);
});

test('linkifies explicit HTTP(S) URLs but leaves fuzzy domains and email plain', () => {
  const html = renderer.render('https://example.com/docs example.com a@example.com');

  assert.match(html, /href="https:\/\/example\.com\/docs"/);
  assert.doesNotMatch(html, /href="https:\/\/example\.com"/);
  assert.doesNotMatch(html, /href="mailto:a@example\.com"/);
});

test('does not enable typographer or hard line break transformations', () => {
  const html = renderer.render('one\ntwo\n\n"plain" -- plain');

  assert.match(html, /one\ntwo/);
  assert.match(html, /&quot;plain&quot; -- plain/);
  assert.doesNotMatch(html, /<br\s*\/?\s*>/i);
});

test('enforces the UTF-8 source size limit before parsing', () => {
  assert.equal(MAX_SOURCE_BYTES, 2 * 1024 * 1024);
  assert.throws(() => renderer.render('你'.repeat(MAX_SOURCE_BYTES / 3 + 1)), /2 MiB/);
});

test('rejects non-string sources', () => {
  assert.throws(() => renderer.render(null), /must be a string/);
});
