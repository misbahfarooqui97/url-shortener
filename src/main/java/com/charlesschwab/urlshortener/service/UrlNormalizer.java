package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Normalizes an already-validated URL into the canonical form used to detect duplicates
 * for the ADR-001 reuse policy. Callers must run {@link UrlValidator#validate(String)}
 * first; this class assumes the input is a well-formed, absolute http/https URL.
 *
 * <p>Normalization rules (see ADR-001 consequences):
 * <ul>
 *   <li>Leading/trailing whitespace is trimmed.</li>
 *   <li>Scheme and host are lower-cased (both are case-insensitive per RFC 3986).</li>
 *   <li>The default port for the scheme (80 for http, 443 for https) is dropped.</li>
 *   <li>An empty path is treated as {@code "/"}; a trailing slash beyond the root path is
 *       stripped, so {@code /products/123/} and {@code /products/123} are equivalent.</li>
 *   <li>The query string is preserved exactly, including parameter order (an explicit
 *       decision recorded in ADR-001, not left implicit).</li>
 *   <li>The fragment is dropped: it is never sent to the server, so it cannot affect the
 *       redirect destination and must not affect duplicate detection.</li>
 * </ul>
 */
@Component
public class UrlNormalizer {

    public String normalize(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a well-formed URI");
        }

        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        boolean isDefaultPort = port == -1
                || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        } else if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        StringBuilder normalized = new StringBuilder();
        normalized.append(scheme).append("://").append(host);
        if (!isDefaultPort) {
            normalized.append(':').append(port);
        }
        normalized.append(path);
        if (uri.getRawQuery() != null) {
            normalized.append('?').append(uri.getRawQuery());
        }
        return normalized.toString();
    }
}
