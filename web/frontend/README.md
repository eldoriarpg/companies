# Companies dashboard

The web app of the Companies dashboard: a Nuxt 4 app in which players follow their orders, their company and the
market of their Minecraft server, live, on a phone first and on a desktop.

Stack: Nuxt 4 (Vue 3 `<script setup lang="ts">`, TypeScript strict), Tailwind 4 (tokens in `src/style.css`, dark,
square corners), reka-ui, @vueuse, axios, @nuxtjs/i18n (English, German, French), IBM Plex Sans and Mono bundled from
@fontsource, Vitest and Playwright. The setup and the lint rules come from the Blood Night editor's frontend.

## How it connects to the gateway

Everything below `/api` belongs to the gateway (`web/backend`). The dev server runs on port 4000 and proxies `/api`,
HTTP and WebSocket alike, to `NUXT_BACKEND_URL` (default `http://localhost:8080`). The built server does the same at
runtime: `server/middleware/backend-proxy.ts` forwards HTTP, `server/routes/api/v1/me/stream.ts` relays the live
stream with the session cookie, and `server/routes/api/v1/servers/connect.ts` relays the plugin nodes' connection
(messages up to 4 MB), since only the frontend is published. Close codes pass through both ways. The address is read
per request, so one build serves any gateway.

`NUXT_TRUSTED_PROXIES` (addresses and networks separated by commas, none by default) names the proxies in front of
the frontend, such as the Cloudflare tunnel. Only from those is `CF-Connecting-IP` handed on to the gateway; from
anyone else it is dropped, since a browser could set it to any address.

A player runs `/company dashboard` in game and gets a short link `/<short id>` and a six letter code. The code page
sends the code (with a Cloudflare Turnstile token where the gateway names a site key); the gateway answers with an
HttpOnly cookie the page never reads. Every page renders in the browser only.

`src/api/gateway.ts` is the one module that knows the browser API: every path and JSON shape is typed there.

## Source layout

- `pages/`: thin route files. `/` asks for the short id, `/<short id>` for the code, `/app/...` is the dashboard.
- `views/`: one `<Name>View.vue` per page, its parts in `views/<name>view/`.
- `layouts/`: `default` (the login column) and `dashboard` (tab bar and menu on a phone, sidebar from tablet width,
  ticker, version banner, the page shown only as an explanation for a discontinued server).
- `components/`: the base components: buttons, inputs, typography, panels, sheets, charts, marks.
- `composables/`: `useDashboard` (server, snapshot, patches, stream with reconnect; patches are applied as the
  gateway's `PatchApplier` does, per node above the last number seen, and a `resync` reloads the snapshot only),
  `useLogin`, `useTurnstile`,
  `useOrderBook`, `useMarket`, `useInbox`, `useRankings`, `useTreasury`, `useWakeLock` and the small helpers.
- `util/`: pure logic with tests (patching the snapshot, the activity feed read from patches, formats, order totals)
  and the `*State.ts` modules holding shared state.
- `api/`: the gateway's calls (`gateway.ts`), the stream (`stream.ts`) and the generated protocol types.
- `public/`: the web app manifest, the icons and `sw.js`, a service worker that keeps the app shell and never data.

Features decide the UI: a section shows only when the server's plugin lists its feature (`util/navigation.ts`).

## Generated protocol types

`src/api/generated/protocol.ts` is generated from the protocol schema of `:web-api`
(`web/api/src/main/resources/schema/v1/protocol.schema.json`) with json-schema-to-typescript, committed and never
edited by hand. Every string enum also becomes a constant of the same name, and the feature names the constant
`Feature`. After the schema changed, run `npm run api-types`; the `api-types` linter fails when the file is stale.

## Commands

Run from this directory inside the repository's nix shell (`shell.nix`, or `direnv allow`), or with Node.js 24.

```bash
npm install          # dependencies
npm run dev          # dev server on http://localhost:4000
npm test             # unit tests, the lint rules' tests included
npm run lint         # every linter of scripts/linters.mjs; `node scripts/lint.mjs <name>` runs one
npm run build        # linters, type checks and the production build into .output
npm run e2e          # end-to-end stories against the build and the mock gateway
```

`npm run e2e` starts `e2e/mock/backend.mjs`, a mock of the gateway on port 8787 whose short ids each play a story
(`e2e/mock/data.mjs`: two nodes and a resync, a discontinued or locked login, Turnstile, an offline server, an ended
session, a node connection), and the built server on 4000 pointed at it, then runs the stories in Chromium, Firefox, a Pixel profile and an iPhone profile (WebKit).
`COMPANIES_E2E_URL` points the stories at a running dashboard instead.

## Linting

Findings are fixed, not suppressed.

- `eslint`: Nuxt's config plus accessibility, vue-i18n, layer import rules and the project's own rules (`eslint/`,
  prefix `companies/`), no warnings allowed.
- `duplication`: jscpd over `src/`.
- `square-corners`: no `rounded` class and no border radius other than zero.
- `browser-storage`: every `localStorage` key is declared in `browser-storage.json`; cookies and session storage are
  refused.
- `em-dash`: no em dash in any text file.
- `comments`: only doc comments and `TODO`s.
- `api-types`: the generated types match the protocol schema.

## Docker

`.docker/frontend.Dockerfile` builds the image from the repository root and runs `node server/index.mjs` on port 4000.
Set `NUXT_BACKEND_URL` to the gateway's address.
