---
title: Self-hosting the dashboard gateway
sidebar_position: 9
---

# Self-hosting the dashboard gateway

The web dashboard runs through a gateway. Your servers open one outbound connection to it, and players open the
dashboard in their browser there. Eldoria hosts a gateway, so most servers never need this page.

You can run your own gateway instead. It needs no Lyna key: it creates its own server token. One gateway can serve
several networks, one token each.

## What you need

- A machine with [Docker](https://docs.docker.com/engine/install/) and Docker Compose.
- A domain with HTTPS that your Minecraft servers **and** your players can reach, like `https://dash.example.com`.
  A web server such as [Caddy](https://caddyserver.com) or a [Cloudflare Tunnel](#cloudflare-tunnel) gives you both.

## Set it up

1. Make a folder for the gateway and go into it.
2. Download the compose file:

   ```bash
   curl -o compose.yaml https://raw.githubusercontent.com/eldoriarpg/companies/main/.docker/compose.prod.yaml
   ```

3. Create a `.env` file next to it:

   ```bash
   PUBLIC_URL=https://dash.example.com
   DB_PASSWORD=a-long-random-password
   PORT=4000
   COMPANIES_VERSION=latest
   ```

4. Start it:

   ```bash
   docker compose up -d
   ```

5. Make `PUBLIC_URL` lead to this machine's `PORT`, see [Behind a web server](#behind-a-web-server).

On its first start the gateway prints a server token. It is shown only this once:

```bash
docker compose logs backend | grep -A1 "Server token"
```

## Connect your Minecraft servers

Point every server of the network at your gateway and give it the token. In `config.yml`:

```yaml
dashboard:
  enabled: true
  gateway: wss://dash.example.com
```

Then enter the token once, in the console or in game. It is stored in the database for all servers of the network and
never written to a file:

```
/companyadmin dashboard key cst_...
```

Run `/companyadmin reload` and check `/companyadmin dashboard status`.

## Server tokens

Each network needs its own token. Manage them with the gateway's token command:

```bash
docker compose exec backend /app/bin/web-backend token list
docker compose exec backend /app/bin/web-backend token create my-other-network
docker compose exec backend /app/bin/web-backend token regenerate default
docker compose exec backend /app/bin/web-backend token delete my-other-network
```

`create` and `regenerate` print the new token once. After `regenerate` the old token can no longer connect; give the
new one to the network with `/companyadmin dashboard key`. Its players stay logged in. Only the token's hash is
stored.

## Settings

All settings go into `.env`.

| Setting | Default | What it is |
|---|---|---|
| `PUBLIC_URL` | | The address players open. Login links are this plus a short id. No `/` at the end. |
| `DB_PASSWORD` | | A password for the database. Only the gateway uses it. |
| `PORT` | `4000` | The port the dashboard listens on, on this machine. It only listens on `127.0.0.1`, for a web server on the same machine. |
| `COMPANIES_VERSION` | `latest` | Which gateway version to run. |
| `TRUSTED_PROXIES` | private addresses | Proxies whose `X-Forwarded-For` is believed, comma separated. See [Behind a web server](#behind-a-web-server). |
| `CLOUDFLARE` | `false` | `true` behind Cloudflare: the client address is taken from `CF-Connecting-IP`. See [Cloudflare Tunnel](#cloudflare-tunnel). |
| `TURNSTILE_SECRET` | empty | Switches on [Turnstile](#turnstile) for the login form. |
| `TURNSTILE_SITEKEY` | empty | The Turnstile site key that belongs to the secret. |

The gateway itself has a few more settings. Add them to the `backend` service's `environment` in `compose.yaml` to
change them:

| Setting | Default | What it is |
|---|---|---|
| `SESSION_IDLE_MINUTES` | `60` | A login ends when it is not used for this long. |
| `SESSION_MAX_HOURS` | `24` | A login ends after this long in any case. |
| `SERVER_GRACE_MINUTES` | `10` | Logins survive this long while none of the network's servers is connected. |
| `CACHE_IDLE_MINUTES` | `15` | Dashboard data stays in memory this long after its last use. |
| `LOGINS_PER_ADDRESS` | `10` | Login attempts per address in ten minutes. Each code also allows only 5 attempts. |
| `READS_PER_SESSION` | `10` | Searches and pages per login in ten seconds. |

## Behind a web server

Only the `frontend` service is published. It serves the web app and passes `/api` and the websockets on to the
gateway. Your web server must pass websockets through as well. With Caddy:

```
dash.example.com {
    reverse_proxy localhost:4000
}
```

The dashboard listens on `127.0.0.1` only. If your web server runs on another machine, change the `ports` of
`frontend` in `compose.yaml` to `"${PORT:-4000}:4000"` and allow only the web server to reach it.

The gateway limits login attempts per address. To know who is asking, it trusts the addresses a proxy passes on, but
only from proxies in `TRUSTED_PROXIES`. The default covers private addresses, which includes the web app and a web
server on the same machine. If your web server is somewhere else, add its address.

## Cloudflare Tunnel

A tunnel needs no open port at all. Add `cloudflared` to `compose.yaml`:

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel run
    environment:
      TUNNEL_TOKEN: ${TUNNEL_TOKEN}
```

Put the tunnel's token into `.env` as `TUNNEL_TOKEN`, and set `CLOUDFLARE=true`. In the Cloudflare dashboard, give
the tunnel a public hostname for your domain with the service `http://frontend:4000`. Then remove the `ports` of
`frontend`.

With `CLOUDFLARE=true` the dashboard must be reachable only through Cloudflare: through the tunnel, or through a web
server that accepts only [Cloudflare's addresses](https://www.cloudflare.com/ips/), which you then add to
`TRUSTED_PROXIES`. The gateway believes `CF-Connecting-IP` only when the request passed the web app and a trusted
proxy before it, but anything that can reach the dashboard directly could still slip past the login limits.

If you use Bot Fight Mode or managed challenges, skip them for `/api/v1/servers/*`. Your Minecraft servers are not
browsers and cannot solve a challenge; that path is protected by the server token.

## Turnstile

[Turnstile](https://developers.cloudflare.com/turnstile/) adds a check to the login form that most players never
see. It is optional and off by default; codes are already limited to 5 attempts. Create a widget for your domain in
the Cloudflare dashboard and put its keys into `.env`:

```bash
TURNSTILE_SITEKEY=0x4AAAAAAA...
TURNSTILE_SECRET=0x4AAAAAAA...
```

## Updating

```bash
docker compose pull
docker compose up -d
```

Logins stay; they live in the database volume. Each gateway release knows which Companies versions it supports.
Update the gateway when you update Companies.

## What the gateway stores

- Logins: the player's id and name, the server, the browser's description, and hashes of the cookie and the code.
- Servers: their id, name and icon.
- Server tokens, as hashes.

Dashboard data, such as orders, members and balances, is kept in memory only while a player looks at it.
