package io.github.int2t05.readweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import org.junit.Test;

public final class UrlPolicyTest {
    @Test
    public void bareHostDefaultsToHttpsAndKeepsQueryAndFragment() {
        URI actual = UrlPolicy.normalize("  example.com/articles?id=3#top  ");

        assertEquals("https://example.com/articles?id=3#top", actual.toString());
    }

    @Test
    public void acceptsExplicitHttpForLanPages() {
        assertEquals("http://192.168.1.20:8080/readme.md", UrlPolicy.normalize(
                "http://192.168.1.20:8080/readme.md").toString());
    }

    @Test
    public void acceptsLocalhostWithPortWithoutTreatingItAsAScheme() {
        assertEquals("https://localhost:8443/notes", UrlPolicy.normalize("localhost:8443/notes").toString());
    }

    @Test
    public void normalizesSchemeAndInternationalHost() {
        assertEquals("https://xn--fsqu00a.xn--0zwm56d/", UrlPolicy.normalize("HTTPS://例子.测试").toString());
    }

    @Test
    public void rejectsNonWebSchemesAndCredentials() {
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("ftp://example.com/file"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("https://user:pass@example.com/"));
    }

    @Test
    public void rejectsMalformedAddressesAndOutOfRangePorts() {
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("https:///missing-host"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("example.com contains spaces"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize("http://example.com:70000/"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.normalize(""));
        assertThrows(IllegalArgumentException.class,
                () -> UrlPolicy.normalize("https://example.com/" + "a".repeat(UrlPolicy.MAX_URL_LENGTH)));
    }

    @Test
    public void resolvesRelativeRedirectsButKeepsSchemePolicy() {
        URI base = URI.create("https://example.com/dir/start.md");

        assertEquals("https://example.com/final.md", UrlPolicy.resolveRedirect(base, "../final.md").toString());
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.resolveRedirect(base, "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> UrlPolicy.resolveRedirect(base, "http://example.com/final.md"));
    }
}
