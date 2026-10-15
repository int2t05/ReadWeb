package io.github.int2t05.readweb;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MarkdownText {
    public static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;

    private MarkdownText() {}

    public static String decodeUtf8(byte[] bytes, int maxBytes) {
        if (bytes == null) throw new IllegalArgumentException("Markdown response is missing.");
        if (maxBytes < 0 || bytes.length > maxBytes) throw new IllegalArgumentException("Markdown exceeds the size limit.");
        int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("Markdown must be valid UTF-8.", error);
        }
    }

    public static boolean isMarkdownContentType(String contentType) {
        if (contentType == null) return false;
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("text/markdown") || mediaType.equals("text/x-markdown")
                || mediaType.equals("text/plain");
    }

    public static boolean isHtmlContentType(String contentType) {
        if (contentType == null) return false;
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("text/html") || mediaType.equals("application/xhtml+xml");
    }

    public static boolean hasNonUtf8Charset(String contentType) {
        if (contentType == null) return false;
        for (String parameter : contentType.split(";")) {
            String[] pair = parameter.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equalsIgnoreCase("charset")) {
                String charset = pair[1].trim().replace("\"", "");
                return !charset.equalsIgnoreCase("utf-8") && !charset.equalsIgnoreCase("utf8");
            }
        }
        return false;
    }
}
