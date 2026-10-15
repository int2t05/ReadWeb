package io.github.int2t05.readweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class MarkdownFetcherTest {
    @Test
    public void followsRelativeRedirectAndReturnsFinalMarkdownSource() throws Exception {
        try (TestServer server = new TestServer(path -> path.equals("/start.md")
                ? Response.redirect("/docs/final.md")
                : Response.ok("text/markdown; charset=UTF-8", "# Ready\n".getBytes(StandardCharsets.UTF_8)))) {
            MarkdownFetcher.Result result = new MarkdownFetcher().fetch(
                    UrlPolicy.normalize(server.url("/start.md")));

            assertEquals(server.url("/docs/final.md"), result.finalUri.toString());
            assertEquals("# Ready\n", result.markdown);
            assertFalse(result.html);
        }
    }

    @Test
    public void returnsHtmlForNormalWebviewFallback() throws Exception {
        try (TestServer server = new TestServer(path -> Response.ok(
                "text/html; charset=utf-8", "<title>Page</title>".getBytes(StandardCharsets.UTF_8)))) {
            MarkdownFetcher.Result result = new MarkdownFetcher().fetch(
                    UrlPolicy.normalize(server.url("/README.md")));

            assertTrue(result.html);
            assertNull(result.markdown);
        }
    }

    @Test
    public void rejectsInvalidUtf8AndNonUtf8Charsets() throws Exception {
        try (TestServer server = new TestServer(path -> path.equals("/broken.md")
                ? Response.ok("text/plain; charset=utf-8", new byte[]{(byte) 0xc3, 0x28})
                : Response.ok("text/plain; charset=gbk", new byte[]{0x23}))) {
            MarkdownFetcher fetcher = new MarkdownFetcher();
            assertThrows(IOException.class,
                    () -> fetcher.fetch(UrlPolicy.normalize(server.url("/broken.md"))));
            assertThrows(IOException.class,
                    () -> fetcher.fetch(UrlPolicy.normalize(server.url("/charset.md"))));
        }
    }

    @Test
    public void rejectsOversizedSourcesBeforeParsing() throws Exception {
        byte[] oversized = new byte[MarkdownText.MAX_SOURCE_BYTES + 1];
        try (TestServer server = new TestServer(path -> Response.ok("text/markdown", oversized))) {
            assertThrows(IOException.class,
                    () -> new MarkdownFetcher().fetch(UrlPolicy.normalize(server.url("/large.md"))));
        }
    }

    @Test
    public void stopsAfterFiveRedirects() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (TestServer server = new TestServer(path -> {
            requests.incrementAndGet();
            int index = Integer.parseInt(path.substring(2));
            return Response.redirect("/r" + (index + 1));
        })) {
            assertThrows(IOException.class,
                    () -> new MarkdownFetcher().fetch(UrlPolicy.normalize(server.url("/r0"))));
            assertEquals(6, requests.get());
        }
    }

    @Test
    public void rejectsUnsafeRedirectSchemes() throws Exception {
        try (TestServer server = new TestServer(path -> Response.redirect("javascript:alert(1)"))) {
            assertThrows(IOException.class,
                    () -> new MarkdownFetcher().fetch(UrlPolicy.normalize(server.url("/start.md"))));
        }
    }

    private interface Handler { Response respond(String path); }

    private static final class Response {
        final int status;
        final String location;
        final String contentType;
        final byte[] body;

        private Response(int status, String location, String contentType, byte[] body) {
            this.status = status;
            this.location = location;
            this.contentType = contentType;
            this.body = body;
        }

        static Response ok(String type, byte[] body) { return new Response(200, null, type, body); }
        static Response redirect(String location) { return new Response(302, location, null, new byte[0]); }
    }

    private static final class TestServer implements AutoCloseable {
        private final ServerSocket listener;
        private final Handler handler;
        private final Thread worker;

        TestServer(Handler handler) throws IOException {
            this.handler = handler;
            listener = new ServerSocket(0);
            worker = new Thread(this::serve, "readweb-test-http-server");
            worker.setDaemon(true);
            worker.start();
        }

        String url(String path) { return "http://127.0.0.1:" + listener.getLocalPort() + path; }

        private void serve() {
            while (!listener.isClosed()) {
                try (Socket socket = listener.accept()) {
                    BufferedReader request = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.US_ASCII));
                    String line = request.readLine();
                    if (line == null) continue;
                    String[] parts = line.split(" ", 3);
                    while ((line = request.readLine()) != null && !line.isEmpty()) { }
                    Response response = handler.respond(parts.length > 1 ? parts[1] : "/");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    output.write(("HTTP/1.1 " + response.status + " " + (response.status == 200 ? "OK" : "Found")
                            + "\r\nConnection: close\r\n").getBytes(StandardCharsets.US_ASCII));
                    if (response.location != null) output.write(("Location: " + response.location + "\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    if (response.contentType != null) output.write(("Content-Type: " + response.contentType + "\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.write(("Content-Length: " + response.body.length + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.write(response.body);
                    socket.getOutputStream().write(output.toByteArray());
                    socket.getOutputStream().flush();
                } catch (IOException error) {
                    if (!listener.isClosed()) throw new AssertionError(error);
                }
            }
        }

        @Override public void close() throws Exception {
            listener.close();
            worker.join(1000);
        }
    }
}
