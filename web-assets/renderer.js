(function (root) {
  'use strict';

  const MAX_SOURCE_BYTES = 2 * 1024 * 1024;
  const ALLOWED_SCHEMES = new Set(['http', 'https', 'mailto']);
  const isCommonJs = typeof module === 'object' && module.exports;
  const MarkdownIt = isCommonJs ? require('markdown-it') : root.markdownit;

  function createRenderer(MarkdownItConstructor) {
    if (typeof MarkdownItConstructor !== 'function') {
      throw new TypeError('A markdown-it constructor is required.');
    }

    const parser = new MarkdownItConstructor({
      html: false,
      linkify: true,
      typographer: false,
      breaks: false
    });

    parser.linkify.set({
      fuzzyLink: false,
      fuzzyEmail: false,
      fuzzyIP: false
    });

    const defaultValidateLink = parser.validateLink.bind(parser);
    parser.validateLink = (url) => {
      const match = /^([a-z][a-z0-9+.-]*):/i.exec(url);
      if (match && !ALLOWED_SCHEMES.has(match[1].toLowerCase())) {
        return false;
      }
      return defaultValidateLink(url);
    };

    return Object.freeze({
      maxSourceBytes: MAX_SOURCE_BYTES,
      render(markdown) {
        if (typeof markdown !== 'string') {
          throw new TypeError('Markdown source must be a string.');
        }
        if (new TextEncoder().encode(markdown).byteLength > MAX_SOURCE_BYTES) {
          throw new RangeError('Markdown source exceeds 2 MiB.');
        }
        return parser.render(markdown);
      }
    });
  }

  if (isCommonJs) {
    module.exports = { createRenderer, MAX_SOURCE_BYTES };
  } else {
    root.ReadWebMarkdown = createRenderer(MarkdownIt);
  }
})(typeof globalThis === 'undefined' ? this : globalThis);
