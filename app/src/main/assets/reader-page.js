(() => {
  'use strict';

  const article = document.getElementById('content');
  const sourceLabel = document.getElementById('source');
  const maxBytes = 2 * 1024 * 1024;
  const maxOutlineHeadings = 300;
  const maxOutlineTitleLength = 200;

  function resolveReferences(sourceUrl) {
    for (const image of article.querySelectorAll('img[src]')) {
      try {
        const target = new URL(image.getAttribute('src'), sourceUrl);
        if (target.protocol !== 'https:' && target.protocol !== 'http:') {
          image.removeAttribute('src');
          continue;
        }
        image.src = target.href;
        image.loading = 'lazy';
      } catch (_) {
        image.removeAttribute('src');
      }
    }

    for (const link of article.querySelectorAll('a[href]')) {
      const original = link.getAttribute('href');
      if (original.startsWith('#')) continue;
      try {
        const target = new URL(original, sourceUrl);
        if (target.protocol !== 'https:' && target.protocol !== 'http:' && target.protocol !== 'mailto:') {
          link.removeAttribute('href');
          continue;
        }
        link.href = target.href;
      } catch (_) {
        link.removeAttribute('href');
      }
    }
  }

  function headings() {
    return article.querySelectorAll('h1, h2, h3, h4, h5, h6');
  }

  window.ReadWebMarkdownDocument = Object.freeze({
    setBase64(sourceUrl, encoded) {
      const bytes = Uint8Array.from(atob(encoded), character => character.charCodeAt(0));
      if (bytes.byteLength > maxBytes) throw new RangeError('Markdown source exceeds 2 MiB.');
      const markdown = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
      const source = new URL(sourceUrl);
      if (source.protocol !== 'http:' && source.protocol !== 'https:') throw new TypeError('Unsupported source URL.');
      article.innerHTML = window.ReadWebMarkdown.render(markdown);
      sourceLabel.textContent = source.href;
      resolveReferences(source.href);
      const heading = article.querySelector('h1');
      document.title = heading && heading.textContent.trim() ? heading.textContent.trim() : source.hostname;
    },

    getOutline() {
      const nodes = headings();
      const outline = [];
      for (let i = 0; i < nodes.length && i < maxOutlineHeadings; i++) {
        const heading = nodes[i];
        outline.push({
          level: Number(heading.tagName.slice(1)),
          title: heading.textContent.trim().slice(0, maxOutlineTitleLength)
        });
      }
      return outline;
    },

    scrollToHeading(index) {
      if (!Number.isInteger(index) || index < 0) return false;
      const heading = headings()[index];
      if (!heading) return false;
      heading.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return true;
    },

    setFontSize(size) {
      const next = Math.max(14, Math.min(28, Number(size) || 18));
      document.documentElement.style.setProperty('--reader-size', `${next}px`);
      return next;
    },

    adjustFont(delta) {
      const current = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--reader-size')) || 18;
      const next = Math.max(14, Math.min(28, current + delta));
      this.setFontSize(next);
      return next;
    }
  });
})();
