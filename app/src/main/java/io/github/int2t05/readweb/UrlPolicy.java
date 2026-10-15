package io.github.int2t05.readweb;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** 将用户输入规范化为不含凭据的 HTTP(S) 地址。 */
public final class UrlPolicy {
    public static final int MAX_URL_LENGTH = 8192;
    private static final Pattern SCHEME_PREFIX = Pattern.compile("^[a-z][a-z0-9+.-]*:.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_PORT = Pattern.compile("^[^/:?#\\s]+:\\d+([/?#].*)?$");

    private UrlPolicy() {}

    public static URI normalize(String input) {
        if (input == null) throw new IllegalArgumentException("Address is required.");
        String value = input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Address is required.");
        if (value.length() > MAX_URL_LENGTH) throw new IllegalArgumentException("Address is too long.");

        String candidate;
        if (value.matches("(?i)^https?://.*")) {
            candidate = value;
        } else if (SCHEME_PREFIX.matcher(value).matches() && !HOST_PORT.matcher(value).matches()) {
            throw new IllegalArgumentException("Only HTTP and HTTPS addresses are supported.");
        } else {
            candidate = "https://" + value;
        }

        try {
            URI parsed = new URI(candidate);
            String scheme = parsed.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only HTTP and HTTPS addresses are supported.");
            }
            String authority = parsed.getRawAuthority();
            if (authority == null || authority.isEmpty() || authority.indexOf('@') >= 0) {
                throw new IllegalArgumentException("Address must include a host and cannot include credentials.");
            }

            HostPort hostPort = parseAuthority(authority);
            String path = parsed.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            StringBuilder normalized = new StringBuilder(scheme.toLowerCase(Locale.ROOT))
                    .append("://").append(hostPort.host);
            if (hostPort.port >= 0) normalized.append(':').append(hostPort.port);
            normalized.append(path);
            if (parsed.getRawQuery() != null) normalized.append('?').append(parsed.getRawQuery());
            if (parsed.getRawFragment() != null) normalized.append('#').append(parsed.getRawFragment());
            return new URI(normalized.toString());
        } catch (URISyntaxException | IllegalArgumentException error) {
            if (error instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Address is malformed.", error);
        }
    }

    public static URI resolveRedirect(URI base, String location) {
        if (base == null || location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Redirect location is missing.");
        }
        try {
            URI relative = new URI(location.trim());
            URI resolved = normalize(base.resolve(relative).toString());
            if (base.getScheme() != null && base.getScheme().equalsIgnoreCase("https")
                    && resolved.getScheme().equalsIgnoreCase("http")) {
                throw new IllegalArgumentException("HTTPS redirects cannot downgrade to HTTP.");
            }
            return resolved;
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Redirect location is malformed.", error);
        }
    }

    private static HostPort parseAuthority(String authority) {
        String host;
        int port = -1;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) throw new IllegalArgumentException("IPv6 host is malformed.");
            host = authority.substring(0, close + 1).toLowerCase(Locale.ROOT);
            String remainder = authority.substring(close + 1);
            if (!remainder.isEmpty()) {
                if (!remainder.startsWith(":")) throw new IllegalArgumentException("Host is malformed.");
                port = parsePort(remainder.substring(1));
            }
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                if (authority.indexOf(':') != colon) throw new IllegalArgumentException("IPv6 hosts must use brackets.");
                host = authority.substring(0, colon);
                port = parsePort(authority.substring(colon + 1));
            } else {
                host = authority;
            }
            if (host.isEmpty()) throw new IllegalArgumentException("Address must include a host.");
            try {
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Host is malformed.", error);
            }
        }
        return new HostPort(host, port);
    }

    private static int parsePort(String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("Port is malformed.");
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Port is out of range.");
            return port;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Port is malformed.", error);
        }
    }

    private static final class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
