# Companies demo network

A Velocity proxy with two Paper servers, `hub` (primary) and `lands` (secondary), sharing MariaDB and Redis, and
the web dashboard. `compose.yml` is the blueprint of the public demo at `demo.companies.eldoria.de`.

| Service | Purpose |
|---|---|
| `velocity` | The game port; players land on `hub` and switch with `/server` |
| `hub` | Primary node: spawn, order boards, WorldGuard regions |
| `lands` | Secondary node: the GriefPrevention world `claims` and the PlotSquared world `plots` |
| `mariadb` | Databases `companies`, `luckperms` and `economy` (XConomy), shared by both nodes |
| `redis` | Node messaging of Companies and XConomy |
| `gateway`, `postgres` | The dashboard gateway and its database |
| `frontend` | The dashboard web app, served through the host's Traefik |
| `reset` | Puts the network back to its seed every night at `RESET_AT` |

The node image `ghcr.io/eldoriarpg/companies-demo-node` holds Companies, the pinned plugins of
`node/plugins.txt`, PlotSquared built from source and the config of both nodes (`node/hub`, `node/lands`, copied
over `/data` on every start; old plugin jars are removed first). EssentialsX 2.22.0 is its newest release and
does not list Minecraft 26.3 yet; it logs an unsupported version error on start. Images are tagged `dev` on every
push to `main` and `latest` on every release; `DEMO_TAG=dev` runs the `main` build.

## Starting state

The `CompaniesDemo` plugin (module `companies-demo`) seeds `hub` whenever the Companies database is empty, which it is
after every reset: the companies, members and item orders of `node/hub/plugins/CompaniesDemo/config.yml` through the
Companies API, without charging anyone, and the market history from 3 to 30 days ago with the material prices for the
dashboard, written over its own connection to the database (the last days come from the orders delivered since). A
seed that stopped halfway is completed on the next start; an order of the seed that was removed is placed again then.
The server orders come from `system-orders.yml`; `company.yml` makes new companies open, so visitors join them with
`/company join`. The demo players of the seed never join; each name gets the id an offline-mode server would give it,
and `usercache.json` of both nodes names them.

## Spawn and guide

`hub` sets its spawn to 0 0 0, facing the holograms, on every start; the WorldGuard region `spawn` keeps 24 blocks
around it from being built on. Three holograms of DecentHolograms stand there
(`node/hub/plugins/DecentHolograms/holograms`): what to try with the commands, the biggest companies, and the
visitor's own company and orders, the last two through Companies' placeholders. `/demo guide` gives the guide book of
the `guide` section of the `CompaniesDemo` config; a visitor joining `hub` for the first time gets it by themselves,
and since the reset forgets who joined, once a day.

## Visitors and the admin sandbox

Visitors use only the commands of `commands.allowed` in the `CompaniesDemo` config of each node: Companies' own,
`/demo`, the plot and economy commands they need, and chat. Every other command is refused and left out of tab
completion; `companiesdemo.commands.all` (ops by default) lifts the list.

`/demo admin` makes a visitor a member of the LuckPerms group `demo-admin` for 30 minutes (`sandbox.minutes`; a pause
before the next one with `sandbox.pauseMinutes`, none by default); `/demo admin off` ends it early. The group gets the
admin tools of Companies to look at orders, players, events, offers and job disputes, without deleting, reloading or
syncing, settings writes, owner transfers, nodes, the dashboard key, debug, grants, the level editor, renames, treasury
changes, area offers, the two bypasses and boards, so a demo admin can neither create money nor take other visitors'
land. Starting or ending the sandbox ends the player's dashboard sessions, so the next login shows what they may do now.
`hub` sets up the groups `default` (the chat commands of the allow list) and `demo-admin` from its config on every
start, since the reset empties the LuckPerms database. The nightly reset undoes what demo admins changed.

## Secrets

The compose file takes its secrets from environment variables and hands them to the containers as files:

| Variable | What it is |
|---|---|
| `DEMO_RCON` | RCON password of both nodes |
| `DEMO_FORWARDING` | Velocity's player info forwarding secret |
| `DEMO_DB_ROOT`, `DEMO_DB_PASSWORD` | MariaDB root and `demo` passwords |
| `DEMO_GATEWAY_DB` | Password of the gateway's PostgreSQL |
| `DEMO_DASHBOARD_KEY` | The fixed server token of the self-hosted gateway, `cst_` and at least 32 characters (e.g. `cst_` and `openssl rand -hex 24`); the gateway and `hub` both read it, so the dashboard needs no Lyna key. A malformed one stops the gateway on start |

Keep the values once the demo ran: MariaDB and PostgreSQL keep the passwords of their first start. On a host the
variables are injected into the environment of `docker compose`. For a local run `./secrets.sh` writes random values
into `secrets.env` (never committed); values already there are kept unless `--force` is given (remove the `mariadb`
and `postgres` volumes then).

## Run it locally

```sh
./secrets.sh
docker compose -f compose.yml -f compose.local.yml --env-file secrets.env up --build
```

Join `localhost:25565` and open the dashboard at `http://localhost:4000`. `--env-file` replaces the default `.env`:
to keep settings in `.env` as well, pass both, `--env-file .env --env-file secrets.env`. On rootless Docker the reset
needs `DOCKER_SOCKET=/run/user/<uid>/docker.sock` there or in the environment. The commands below take the same
`-f` and `--env-file` options locally.

## Reset

Every night at `RESET_AT` (Europe/Berlin, avoid 02:00 to 03:00, which is skipped once a year), after warnings 10 and 1
minutes before, the `reset` service stops both nodes, recreates the `companies`, `luckperms` and `economy` databases,
empties Redis, replaces the `seed` paths of `reset/paths` (worlds, regions, plots, claims, holograms, warps) with the
seed, deletes the `wipe` paths (player data, caches, ops and bans) and starts `hub`, then `lands`. A failed reset
still starts the nodes again. The gateway and its database are left alone.

The service controls the nodes through the Docker socket, which gives it full control of the Docker host. Run the
demo on a host or rootless Docker daemon of its own.

```sh
docker compose exec reset demo-reset.sh now
```

resets at once.

### Making the seed worlds

Build the worlds in game, then store them as the seed and commit the archives. Run the capture where the demo runs;
the archives land in `seed/` next to `compose.yml` and go into `demo/seed/` of a checkout of this repository:

```sh
docker compose run --rm -v ./seed:/capture reset capture
```

This stops both nodes briefly and writes `seed/hub.tar.gz` and `seed/lands.tar.gz` from the `seed` paths in
`reset/paths`, leaving out the `wipe` paths. A missing seed path stops the capture. The archives are stored with Git
LFS (run `git lfs install` once); on rootful Docker they belong to root, `chown` them before committing. The next
image build of `companies-demo-reset` contains them.

## Deploy

Every image carries its config, so the host needs no checkout of this repository: only `compose.yml` and the
environment.

1. Put `compose.yml` in a folder and set the settings below where the defaults do not fit, in a `.env` next to it or
   in the environment.
2. Inject the `DEMO_*` secrets of [Secrets](#secrets) into the environment.
3. `docker compose up -d`.

| Setting | Default | What it is |
|---|---|---|
| `DEMO_TAG` | `latest` | Image tag: `latest` for the newest release, `dev` for `main` |
| `DEMO_HOST` | `demo.companies.eldoria.de` | Host name of the dashboard |
| `TRAEFIK_NETWORK` | `traefik` | The existing Traefik's external Docker network |
| `TRAEFIK_ENTRYPOINT` | `websecure` | Traefik's HTTPS entry point |
| `TRAEFIK_CERTRESOLVER` | `letsencrypt` | Traefik's certificate resolver |
| `GAME_PORT` | `25565` | Host port Velocity is published on |
| `NODE_MEMORY` | `3G` | Heap of each node |
| `RESET_AT` | `04:00` | Time of the nightly reset |
| `COOKIE_SECURE` | `true` | Whether the dashboard cookie needs HTTPS |
| `DOCKER_SOCKET` | `/var/run/docker.sock` | Socket of the Docker daemon running the demo, which the reset uses to stop and start the nodes; rootless Docker: `/run/user/<uid>/docker.sock` |

The dashboard is reached through the existing Traefik; Velocity's port is published for the Minecraft proxy in front
of it, which routes players to the demo.
