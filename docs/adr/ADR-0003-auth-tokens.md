# ADR-0003: JWT access token + opaque rotating refresh token

**Status:** Accepted · 2026-10-06

**Context.** The SPA needs a stateless authenticator for API calls plus a way to survive
page reloads without exposing a long-lived credential to JavaScript.

**Decision.**
* Access token: short-lived (15 min) HS256 JWT held **in memory only**, carrying
  `sub`, `typ=access`, `jti`, `iat`, `nbf`, `exp` and an optional `kid`.
* Refresh token: opaque 256-bit random string stored **SHA-256 hashed**, delivered in an
  `httpOnly; SameSite=Strict` cookie, rotated on every use, with reuse detection that
  revokes the whole token family.
* CSRF: double-submit (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header) enforced **only** on
  the cookie-bearing routes `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout`.
* Passwords: BCrypt cost 12 behind `DelegatingPasswordEncoder`.

**Consequences.** Revocation latency for access tokens is bounded by the 15-minute TTL;
refresh theft is detected on the next rotation; the API stays stateless per request.
