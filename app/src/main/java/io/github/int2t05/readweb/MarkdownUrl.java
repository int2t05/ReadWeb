package io.github.int2t05.readweb;

import java.net.URI;
import java.util.Locale;

public final class MarkdownUrl {
    private MarkdownUrl() {}

    public static boolean isMarkdown(URI uri) {
        if (uri == null) return false;
        String path = uri.getPath();
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }
}
