# Security Review — URL Shortener

**Date:** 2026-09-21  
**Scope:** Shortening logic, redirect mechanism, input validation

## 1. Open Redirect Risk

### Scenario

An attacker submits a URL like `https://example.com/redirect?target=https://malicious.com`.
When a user clicks the shortened link, they are redirected to `https://example.com/redirect?target=https://malicious.com`.
If `example.com/redirect` blindly follows the `target` parameter without validation, the user
ends up at `https://malicious.com`. The shortened link appears to come from a trusted domain,
but it leads the user away from it.

### Our defense

The URL shortener application **does not** implement any redirect logic of its own. It simply:

1. Accepts a URL from the user (the full, intended destination).
2. Stores it after validation and normalization.
3. When a short code is accessed, responds with an HTTP `302 Found` redirect to the stored `originalUrl`.

The redirection is performed by the **browser**, not by application logic. The browser will
respect the HTTP `Location` header and go directly to the stored `originalUrl`. There is no
intermediate redirect, no parameter parsing, and no additional redirection chain.

**Risk level:** ✅ **Not vulnerable.** The application is not an open-redirect vector because
it does not perform intermediate redirects or follow user-supplied parameters.

---

## 2. Unsafe Scheme Risk

### Scenario

An attacker submits a URL with an unsafe scheme, such as:

- `javascript:alert('XSS')`
- `data:text/html,<script>alert('XSS')</script>`
- `file:///etc/passwd`
- `ftp://attacker.com/malware.exe`
- `about:blank`

If the shortened link is placed in an HTML context without escaping (e.g., an href attribute
rendered without HTML-entity encoding), the browser may execute the scheme instead of treating
it as a URL to fetch.

### Our defense

**Validation layer (`UrlValidator`):** All URLs submitted to the shortener are validated
before persistence. Specifically:

1. **Scheme check:** Only `http` and `https` schemes are permitted. All other schemes
   (`javascript:`, `data:`, `file:`, `ftp:`, etc.) are rejected with a `400 Bad Request`.
2. **Absolute URL check:** The URL must be absolute and well-formed per RFC 3986.
3. **Host check:** The URL must have a valid, non-blank host.

**Example rejection:**

```
POST /api/v1/short-urls
Content-Type: application/json

{"url": "javascript:alert(1)"}

HTTP/1.1 400 Bad Request
{
  "status": 400,
  "error": "Bad Request",
  "message": "url must be an absolute http or https URL",
  "path": "/api/v1/short-urls"
}
```

**Redirect layer (`RedirectController`):** When the browser follows a short link (GET /{code}),
the response is an HTTP `302 Found` with a `Location` header set to the stored `originalUrl`.
Since this URL has already been validated to be `http` or `https`, the browser treats it as a
safe URL to navigate to.

If an attacker were to somehow bypass validation and insert a `javascript:` URL into the
database, the `Location` header would contain it, and the behavior depends on the browser:
modern browsers (Chrome, Firefox, Safari, Edge) reject `javascript:` in the `Location` header
and do not follow it. However, older or non-standard user agents might execute it.

**Risk level:** ✅ **Very low.** Unsafe schemes are rejected at the validation boundary,
before any shortened link is ever created. The only residual risk is if validation logic has
an unexpected gap (see "Validation robustness" below).

---

## 3. Validation Robustness

### Potential gaps and mitigations

- **Internationalized domain names (IDN):** Java's `java.net.URI` handles IDN, but edge cases
  (e.g., homograph attacks using lookalike Unicode) are not explicitly in scope for this
  prototype. A production system should review IDN handling or use a dedicated library.

- **Redirect loops:** No check prevents a user from shortening a URL that itself contains a
  shortened link from the same domain (e.g., shortening `https://short.example/abc123`).
  If a user follows such a chain, they could loop indefinitely. **Mitigation:** This is a
  user-error, not a security vulnerability; users are responsible for not creating redundant
  shortened links. A production system could add a check against self-referential links.

- **Data exfiltration via query strings:** An attacker could craft a URL like
  `https://attacker.com/log?userid=<stolen-data>` and trick a user into clicking it. This is
  not a shortener vulnerability — it is a general URL phishing risk. The shortener merely
  stores and serves URLs; it does not execute them or access their content.

---

## 4. Conclusion

The URL shortener application is **not an open-redirect vector** and **rejects unsafe schemes**
at the validation boundary. The residual risk is low, limited to:

1. Edge cases in URL parsing (IDN homographs, etc.) — mitigated by using standard Java libraries
   and comprehensive test coverage of the normalization rules.
2. User error in creating redundant shortened links — a UX concern, not a security vulnerability.

**Recommendation:** Proceed with deployment. Monitor for any new scheme or validation bypass
techniques reported by users or security researchers.
