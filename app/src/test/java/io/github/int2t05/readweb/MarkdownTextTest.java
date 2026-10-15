package io.github.int2t05.readweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class MarkdownTextTest {
    @Test
    public void decodesUtf8AndStripsBom() throws Exception {
        byte[] prefix = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "# 你好\n".getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        System.arraycopy(body, 0, input, prefix.length, body.length);

        assertEquals("# 你好\n", MarkdownText.decodeUtf8(input, 1024));
    }

    @Test
    public void rejectsOversizedAndMalformedUtf8() {
        assertThrows(IllegalArgumentException.class,
                () -> MarkdownText.decodeUtf8(new byte[5], 4));
        assertThrows(IllegalArgumentException.class,
                () -> MarkdownText.decodeUtf8(new byte[]{(byte) 0xC3, 0x28}, 8));
    }

    @Test
    public void allowsMarkdownTextButRejectsHtmlAndUnknownContentTypes() {
        assertTrue(MarkdownText.isMarkdownContentType("text/markdown; charset=utf-8"));
        assertTrue(MarkdownText.isMarkdownContentType("text/plain"));
        assertFalse(MarkdownText.isMarkdownContentType("text/html; charset=utf-8"));
        assertFalse(MarkdownText.isMarkdownContentType("application/zip"));
    }
}
