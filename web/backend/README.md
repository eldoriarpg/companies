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

Every answer carries `Cache-Control: no-store` except icons. Errors are `{error, message, attemptsLeft?}`.

| Route | |
|---|---|
| `GET /api/v1/config` | `{gateway, turnstileSiteKey?}` |
| `GET /api/v1/s/{shortId}` | `{shortId, server, expiresAt}`; `server` is `{name?, icon?, online, version?, deprecated}` |
| `POST /api/v1/s/{shortId}/login` | `{code, turnstile?}` → the `me` view and the cookie `companies_session`; 401 `wrong-code` with `attemptsLeft`, 404, 409, 410 `discontinued`, 429, 503 |
| `GET /api/v1/me` | `{player, server, features, expiresAt}` |
| `POST /api/v1/me/logout` | 204 |
| `GET /api/v1/me/snapshot` | `{revision, snapshot}` |
| `GET /api/v1/me/members?page&size&search` | `{page, pages, total, entries}` from the snapshot |
| `POST /api/v1/me/actions/{name}` | read actions only (`ReadRelay.READ_ACTIONS`), body = args → `{ok, reason?, message?, data?}` |
| `GET /api/v1/icons/{hash}` | the PNG, cached for good |
| `WS /api/v1/me/stream` | `patch` (with `revision`), `reload`, `server` (`online`), `closed` (`reason`), `pong`; the browser sends `{"type":"ping"}` every 20 s |

## Layout

Packages below `de.eldoria.companies.web.gateway`:

| Package | Purpose |
|---|---|
| (root) | `GatewayService` (entry, token command), `GatewayApp` (wiring and routes), `ServiceConfig`, `ClientAddress`, `RateLimiter`, `Secrets` |
| `node` | `NodeHub` (connections, handshake, dispatch), `NodeLink`, `LiveServer` (routing: primary first), authenticators, `Icons` |
| `policy` | `VersionPolicy`, `PluginVersion` |
| `session` | `Sessions` (create, login, end), `Turnstile`, `Devices` |
| `snapshot` | `SnapshotCache` (snapshots, patches, sequence gaps), `PatchApplier`, `ReadRelay` |
| `browser` | `BrowserApi`, `BrowserStreams`, `StreamEvent`, `Views` |
| `storage` | PostgreSQL through SADU: sessions, servers, tokens, icons |
