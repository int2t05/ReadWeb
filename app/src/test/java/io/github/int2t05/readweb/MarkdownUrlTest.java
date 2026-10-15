package io.github.int2t05.readweb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import org.junit.Test;

public final class MarkdownUrlTest {
    @Test
    public void detectsMarkdownExtensionsCaseInsensitively() {
        assertTrue(MarkdownUrl.isMarkdown(URI.create("https://example.com/README.MD")));
        assertTrue(MarkdownUrl.isMarkdown(URI.create("https://example.com/guide.markdown")));
    }

    @Test
    public void ignoresQueryAndFragmentWhenDetectingExtension() {
        assertTrue(MarkdownUrl.isMarkdown(URI.create("https://example.com/docs/guide.md?raw=1#intro")));
    }

    @Test
    public void doesNotTreatHtmlOrAPathSegmentAsMarkdown() {
        assertFalse(MarkdownUrl.isMarkdown(URI.create("https://example.com/guide.html")));
        assertFalse(MarkdownUrl.isMarkdown(URI.create("https://example.com/guide.md/page")));
        assertFalse(MarkdownUrl.isMarkdown(URI.create("https://example.com/readme")));
    }
}
