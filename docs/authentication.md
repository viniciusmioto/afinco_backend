# Authentication architecture

Afinco uses database-backed users and server-side HTTP sessions. This is a
deliberately small design for the single-workspace homelab deployment: the
backend is the security boundary, the browser only holds an opaque session ID,
and no signing key or access token is stored in browser JavaScript storage.

## Request flow

1. The frontend obtains a CSRF token from `GET /api/v1/auth/csrf`.
2. It sends the token in `X-XSRF-TOKEN` when posting the email and password to
   `POST /api/v1/auth/login`.
3. Spring Security loads the normalized email from SQLite and verifies the
   BCrypt hash. A successful login rotates the session ID and the CSRF token.
4. The backend returns an HTTP-only `JSESSIONID` cookie. The Next.js same-origin
   proxy relays this cookie and all later auth/CSRF headers.
5. Every API except the CSRF, session-probe, and login endpoints requires the
   server-side session. Unsafe methods also require a matching CSRF cookie and header.
6. Logout invalidates the server-side session and expires both cookies.

The Next.js route guard only improves navigation UX. A cookie's presence is not
treated as proof of authentication; `GET /api/v1/auth/session` verifies the
session before protected screens render, and the backend enforces access on
every finance endpoint. The probe answers `200` either way, so a stale cookie
left behind by a backend restart redirects to login without a browser-console
`401`.

## SQLite user schema

Flyway migration `V3__add_users.sql` creates `users` with:

- a case-insensitive, unique, normalized email (maximum 254 characters);
- a 60-character BCrypt password hash;
- an enabled flag for disabling access without deleting the audit identity;
- UTC creation and update timestamps.

The password itself is never persisted, returned by an API, or logged. JPA
entities remain inside the domain and persistence layers; auth responses only
contain `id` and `email`.

Migration V3 is intentional: transaction categorization owns V2 and is the
parent commit of this backend branch, so Flyway applies both features in order.

## API contract

| Method | Path | Authentication | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/auth/csrf` | Public | Sets `XSRF-TOKEN` and returns `{ "token": "..." }` |
| `POST` | `/api/v1/auth/login` | Public + CSRF | Accepts `{ "email": "...", "password": "..." }` |
| `GET` | `/api/v1/auth/session` | Public | Returns `{ "authenticated": true, "user": { "id": 1, "email": "..." } }` or `{ "authenticated": false, "user": null }` |
| `GET` | `/api/v1/auth/me` | Session | Returns `{ "id": 1, "email": "..." }` |
| `POST` | `/api/v1/auth/logout` | Session + CSRF | Invalidates the session; returns `204` |

Bad email and bad password produce the same `401` response to avoid user
enumeration. After five failed attempts from one client address in 15 minutes,
the in-memory limiter returns `429` with `Retry-After`. This limiter fits one
local backend instance; replace it with a shared limiter only if Afinco is ever
scaled horizontally.

## Cookie and browser protections

- `JSESSIONID`: HTTP-only, `SameSite=Strict`, 30-minute inactivity timeout.
- `XSRF-TOKEN`: readable by the frontend so it can echo the value in the
  `X-XSRF-TOKEN` header; `SameSite=Strict` and never used as authentication.
- Successful and failed auth responses use `Cache-Control: no-store`.
- The frontend sends a restrictive content security policy, denies framing,
  disables MIME sniffing, and limits browser permissions.
- The backend returns JSON `401`/`403` responses instead of redirecting to an
  HTML login form.

## Initial local account

The migration creates the requested temporary account:

```text
Email: test@test.com
Password: 123@Test
```

The committed value is a BCrypt cost-12 hash, not plaintext. The credential is
still publicly known from the project history and must be replaced before the
service is reachable from an untrusted network. A password-management screen is
outside this initial single-user scope; until it exists, generate a new BCrypt
cost-12 hash and update only `users.password_hash` in a stopped, backed-up
SQLite database.

Do not edit an already-applied Flyway migration. That changes its checksum and
breaks startup validation. Credential rotation changes the data row, not the
migration file.

## Homelab deployment

Plain HTTP is sufficient only on a trusted, isolated LAN. For access through a
reverse proxy or outside the LAN:

1. terminate HTTPS at the reverse proxy;
2. set `AFINCO_SECURE_COOKIES=true` for the backend container;
3. expose the frontend, not port 8080, to clients;
4. restrict both services with the host firewall and never forward port 8080
   from the router;
5. back up the Docker volume containing `/data/afinco.db` before migrations;
6. replace the temporary credential.

Sessions intentionally live in backend memory, so a container restart signs
users out while leaving the SQLite user and finance data intact. This avoids
storing reusable session secrets in the database and is appropriate for one
homelab instance.

## Verification

`mvn clean verify` covers the migration and hash, successful and failed login,
session restoration, the public session probe, an import persisted across
logout and login, unauthenticated endpoint rejection, CSRF enforcement,
logout invalidation, and login throttling. Tests use temporary SQLite files and
never read or modify the runtime database.
