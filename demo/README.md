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

## Secrets

Secrets are files in `secrets/`, never committed. `./secrets.sh` reads them from the 1Password references in
`secrets.op`; `./secrets.sh --local` fills them with random values and leaves the Lyna secret and the dashboard key
empty. Files that exist are kept, since the databases keep the passwords of their first start; `--force` replaces
them (remove the `mariadb` and `postgres` volumes then).

## Run it locally

```sh
./secrets.sh --local
docker compose -f compose.yml -f compose.local.yml up --build
```

Join `localhost:25565`. The local gateway runs self-hosted: it prints a server token once on its first start
(`docker compose logs gateway`); enter it with `/companyadmin dashboard key <token>` on `hub` and open
`http://localhost:4000`.

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

Build the worlds in game, then store them as the seed and commit the archives:

```sh
docker compose run --rm -v ./seed:/capture reset capture
```

This stops both nodes briefly and writes `seed/hub.tar.gz` and `seed/lands.tar.gz` from the `seed` paths in
`reset/paths`, leaving out the `wipe` paths. A missing seed path stops the capture. The archives are stored with Git
LFS (run `git lfs install` once); on rootful Docker they belong to root, `chown` them before committing. The next
image build of `companies-demo-reset` contains them.

## Deploy

1. Copy `.env.example` to `.env` and set the host and the Traefik network, entry point and certificate resolver.
   `GAME_PORT` is the host port Velocity is published on, `NODE_MEMORY` the heap of each node, `DOCKER_SOCKET` the
   socket of the Docker daemon running the demo (rootless Docker: `/run/user/<uid>/docker.sock`), which the reset
   uses to stop and start the nodes.
2. `./secrets.sh` (needs `op` signed in).
3. `docker compose up -d`.

The dashboard is reached through the existing Traefik; Velocity's port is published for the Minecraft proxy in front
of it, which routes players to the demo.
