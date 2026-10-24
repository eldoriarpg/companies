# Web Backend

`:web-backend`: the dashboard gateway. Every node of a server holds a websocket to it; browsers log in with a short
link and a code and read their dashboard through it. The plugin answers every read; the gateway routes and caches.
Main class: `de.eldoria.companies.web.gateway.GatewayService`. Not published; shipped as the
`ghcr.io/eldoriarpg/companies-backend` image.

## Modes

- `GATEWAY_MODE=hosted` (default): nodes present a Lyna dashboard key (`lyd_…`), verified on every new connection
  with `POST {LYNA_URL}/api/v1/dashboard/key/verify` and the bearer `DASHBOARD_GATEWAYSECRET`. The server id is
  `lyna:<keyId>`, stable across rotations. A positive answer is reused for `LYNA_CACHE_MINUTES` only while Lyna
  cannot be reached.
- `GATEWAY_MODE=self-hosted`: nodes present a server token (`cst_…`). The first start creates the token `default`
  and prints it once; `web-backend token list|create|regenerate|delete <name>` manages more. The server id is
  `token:<id>`.

Settings are read from the environment, see `ServiceConfig`. The admin page `docs/admin/dashboard-gateway.md`
lists the ones a self-hoster needs.

## Node protocol

`/api/v1/servers/connect`, the messages of `:web-api`. `hello` → `welcome` with the protocol used, the gateway kind,
its features, the plugin version's status from `version-policy.json` (or `VERSION_POLICY`) and the deprecated parts
the node uses. A discontinued version gets its welcome and then only pongs. Branding icons are stored by hash
(`welcome.iconCached`; `branding.update` brings a missing one). Nodes are pinged after 30 s of silence and dropped
after 75 s.

## Browser API

Base `/api/v1`. Every answer carries `Cache-Control: no-store` except icons. Errors are
`{error, message?, attemptsLeft?, version?}` with `error` one of `UNKNOWN_SESSION`, `WRONG_CODE`,
`TOO_MANY_ATTEMPTS`, `TURNSTILE_FAILED`, `UNAUTHORIZED`, `DISCONTINUED`, `SERVER_OFFLINE`, `RATE_LIMITED`,
`INTERNAL`.

| Route | |
|---|---|
| `GET /s/{shortId}` | `{server: {name, iconUrl?}, expiresAt, attemptsLeft, turnstileSiteKey?, version}`; 404 `UNKNOWN_SESSION` |
| `POST /s/{shortId}/login` | `{code, turnstileToken?}` → 204 and the cookie `__Host-companies_session`; 401 `WRONG_CODE`, 410 `TOO_MANY_ATTEMPTS`, 403 `TURNSTILE_FAILED`, 429, 503, 410 `DISCONTINUED` |
| `GET /me/server` | `{server, features, limits, version, deprecated, online}` |
| `GET /me/snapshot` | the `snapshot` message with every patch since applied |
| `POST /me/actions` | `{name, args}` for a read action (`ReadRelay.READ_ACTIONS`) → the `action.result` message |
| `POST /me/logout` | 204 |
| `GET /icons/{hash}` | the PNG, cached for good |
| `GET /health` | `{status: up}`, or 503 while the database does not answer |
| `WS /me/stream` | `patch` (for this session only), `pong`, `session.closed`, `{type: resync}`, `{type: server, server}`; the browser sends `ping` every 20 s; closes with 4401 (session gone) or 4403 (discontinued) |

## Layout

Packages below `de.eldoria.companies.web.gateway`:

| Package | Purpose |
|---|---|
| (root) | `GatewayService` (entry, token command), `GatewayApp` (wiring and routes), `ServiceConfig`, `ClientAddress`, `RateLimiter`, `Secrets` |
| `node` | `NodeHub` (connections, handshake, dispatch), `NodeLink`, `LiveServer` (routing: primary first), authenticators, `Icons` |
| `policy` | `VersionPolicy`, `PluginVersion` |
| `session` | `Sessions` (create, login, end), `Turnstile`, `Devices` |
| `snapshot` | `SnapshotCache` (snapshots, patches, sequence gaps), `PatchApplier`, `ReadRelay` |
| `browser` | `BrowserApi`, `BrowserStreams`, `GatewayEvent`, `Views`, `ApiError` |
| `storage` | PostgreSQL through SADU: sessions, servers, tokens, icons |
