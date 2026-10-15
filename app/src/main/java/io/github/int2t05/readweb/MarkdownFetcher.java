package io.github.int2t05.readweb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;

/** 按大小、超时和重定向规则获取在线 Markdown 文档。 */
public final class MarkdownFetcher {
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;
    public static final int MAX_REDIRECTS = 5;

    public Result fetch(URI initialUri) throws IOException {
        URI current = UrlPolicy.normalize(initialUri.toString());
        for (int redirects = 0; ; redirects++) {
            URLConnection opened = current.toURL().openConnection();
            if (!(opened instanceof HttpURLConnection connection)) {
                throw new IOException("Only HTTP and HTTPS Markdown sources are supported.");
            }
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "text/markdown, text/x-markdown, text/plain;q=0.9, text/html;q=0.5");
            connection.setRequestProperty("Accept-Charset", "utf-8");
            try {
                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    if (redirects >= MAX_REDIRECTS) throw new IOException("Markdown source exceeded the redirect limit.");
                    String location = connection.getHeaderField("Location");
                    try {
                        current = UrlPolicy.resolveRedirect(current, location);
                    } catch (IllegalArgumentException error) {
                        throw new IOException("Markdown redirect is invalid.", error);
                    }
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("Markdown source returned HTTP " + status + ".");

                String contentType = connection.getContentType();
                if (MarkdownText.isHtmlContentType(contentType)) return Result.html(current);
                if (!MarkdownText.isMarkdownContentType(contentType)) {
                    throw new IOException("Response is not a Markdown text document.");
                }
                if (MarkdownText.hasNonUtf8Charset(contentType)) {
                    throw new IOException("Markdown source must use UTF-8.");
                }
                long advertisedLength = connection.getContentLengthLong();
                if (advertisedLength > MarkdownText.MAX_SOURCE_BYTES) {
                    throw new IOException("Markdown source exceeds 2 MiB.");
                }
                byte[] body;
                try (InputStream input = connection.getInputStream()) {
                    body = readBounded(input, MarkdownText.MAX_SOURCE_BYTES);
                }
                final String markdown;
                try {
                    markdown = MarkdownText.decodeUtf8(body, MarkdownText.MAX_SOURCE_BYTES);
                } catch (IllegalArgumentException error) {
                    throw new IOException(error.getMessage(), error);
                }
                return Result.markdown(current, markdown);
            } finally {
                connection.disconnect();
            }
        }
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308;
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 16 * 1024));
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + (long) count > limit) throw new IOException("Markdown source exceeds 2 MiB.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static final class Result {
        public final URI finalUri;
        public final String markdown;
        public final boolean html;

        private Result(URI finalUri, String markdown, boolean html) {
            this.finalUri = finalUri;
            this.markdown = markdown;
            this.html = html;
        }

        static Result markdown(URI uri, String content) { return new Result(uri, content, false); }
        static Result html(URI uri) { return new Result(uri, null, true); }
    }
}
