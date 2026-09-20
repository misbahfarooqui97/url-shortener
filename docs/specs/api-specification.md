# API Specification

## Status

Approved. This is the binding contract for implementation. Controller and service code must conform to this document; any deviation requires an update here first, plus an entry in the decision log in `docs/engineering-summary.md` if it changes behavior.

## Conventions

- All request/response bodies are JSON (`application/json`).
- All timestamps are ISO-8601 UTC.
- Error responses share a single shape (see [Error format](#error-format)).

## 1. Create a short URL

`POST /api/v1/short-urls`

**Request body**

```json
{
  "url": "https://example.com/products/123"
}
```

**Validation**

- `url` is required, must be an absolute URL, and must use `http` or `https`.
- Maximum accepted length: 2048 characters.

**Success response — `201 Created`**

```json
{
  "code": "aB91xY",
  "shortUrl": "http://localhost:8080/aB91xY",
  "originalUrl": "https://example.com/products/123",
  "createdAt": "2026-09-20T15:30:00Z"
}
```

**Failure responses**

| Condition | Status | Notes |
|---|---|---|
| Missing or blank `url` | `400` | See [Error format](#error-format) |
| Malformed URL or disallowed scheme | `400` | See [Error format](#error-format) |
| URL exceeds maximum length | `400` | See [Error format](#error-format) |

## 2. Redirect

`GET /{code}`

**Success response**

- `302 Found` with `Location` header set to the original URL.
- A click/access event is recorded before the redirect response is returned.

**Failure responses**

| Condition | Status | Notes |
|---|---|---|
| Unknown code | `404` | See [Error format](#error-format) |

## 3. Get analytics for a short code

`GET /api/v1/short-urls/{code}/analytics`

**Success response — `200 OK`**

```json
{
  "code": "aB91xY",
  "originalUrl": "https://example.com/products/123",
  "totalClicks": 42,
  "firstAccessedAt": "2026-09-20T15:31:10Z",
  "lastAccessedAt": "2026-09-21T09:02:44Z"
}
```

A short code with zero clicks returns `totalClicks: 0` and `null` for both timestamp fields.

**Failure responses**

| Condition | Status | Notes |
|---|---|---|
| Unknown code | `404` | See [Error format](#error-format) |

## 4. Health check

`GET /actuator/health`

Standard Spring Boot Actuator health payload. Used by the Docker Compose healthcheck and by reviewers to confirm the service is up.

## Error format

All `4xx`/`5xx` responses use this shape:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "url must be an absolute http or https URL",
  "path": "/api/v1/short-urls",
  "timestamp": "2026-09-20T15:30:00Z"
}
```

No stack traces, internal exception class names, or persistence details are ever included in a response body.
