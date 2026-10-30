---
title: jobs.yml
sidebar_position: 7
---

# jobs.yml

[Jobs](../../players/jobs.md): work a company takes like an order, in an area of a player's land or described in
words. This file says
whether players may post them, where they may lie, how large they may be and how long they run. It is shared by every
server of a network. The jobs themselves are stored in the database.

```yaml
enabled: true
unownedWorlds: []
digMaxVolume: 32768
terraformMaxVolume: 32768
harvestMaxVolume: 16384
buildMaxVolume: 32768
buildMatchPercent: 95
maxChunks: 64
maxOpenPerOwner: 5
maxDueHours: 168
openDays: 7
approveHours: 72
```

| Key | Values | Default | Description | Takes effect |
|-----|--------|---------|-------------|--------------|
| `enabled` | true/false | `true` | Players may post new jobs. Running jobs go on either way. | reload |
| `unownedWorlds` | list of world names | `[]` | Worlds where a job may lie on land of nobody (no plot, no region). Off everywhere unless a world is listed. Only WorldGuard and PlotSquared are asked whose land an area is; list only worlds no other claim plugin protects. | reload |
| `digMaxVolume` | number (blocks) | `32768` | The most blocks the area of a dig job may hold. The area is checked block by block on the main thread when a company finishes it. | reload |
| `terraformMaxVolume` | number (blocks) | `32768` | The most blocks the area of a terraform job may hold, checked like a dig job's. | reload |
| `harvestMaxVolume` | number (blocks) | `16384` | The most blocks the field of a harvest job may hold. | reload |
| `buildMaxVolume` | number (blocks) | `32768` | The most blocks the area of a build job may hold. | reload |
| `buildMatchPercent` | number (percent) | `95` | The share of a blueprint's blocks that must match for a build job to be done. Only block types are compared. | reload |
| `maxChunks` | number (chunks) | `64` | The most chunks the area of a job may touch, so a long thin area can not make the server load many chunks for each check. | reload |
| `maxOpenPerOwner` | number | `5` | The most jobs a player may have waiting for a company at once. | reload |
| `maxDueHours` | number (hours) | `168` | The most hours an owner may give a company to finish a job. | reload |
| `openDays` | number (days) | `7` | How long a job waits for a company. Nobody took it by then: it expires and price and fee go back to its owner. | reload |
| `approveHours` | number (hours) | `72` | How long a job the company says is done waits for its owner to approve or reject it. Without an answer by then it is approved and paid. | reload |

The fee of a job is the order fee of [order.yml](./order-yml.md), lowered by the fee reductions of server events. A
company's order slots count its jobs; giving one back or running out of time costs the order penalties of
[company.yml](./company-yml.md).

## Schematics

Build jobs use the schematics in `plugins/Companies/schematics/` (any format WorldEdit reads, such as `.schem`).
Players pick one by its file name without the ending; nothing outside the folder can be picked. Reading them needs
WorldEdit or FastAsyncWorldEdit. A posted job keeps its own copy of the blueprint, so changing or removing a file never
changes running jobs.

## Land and access

Jobs need a land plugin to know whose land an area is, and give the working company access through it:

- **PlotSquared:** the area must lie on one plot of the owner, merged plots counting as one. The company's members are
  trusted on the plot while they work; only members who were not trusted before are added, and only those are removed.
  Trust reaches the whole plot, so Companies holds those members to the job's area: outside it they can not break or
  place blocks, use buckets, use blocks such as chests or doors, or hit entities on the plot.
- **WorldGuard:** the area must lie wholly in one region the owner owns. A region `companies-job-<id>`, above every
  region there, lists the company's members and the owner as members; other members of the owner's region can not
  build in it while it exists. On unowned land the job region marks who works there and keeps others out. A region owned
  through a group or a parent region counts as owned.
- **WorldEdit** (or FastAsyncWorldEdit) lets owners select the area of a job.

Switch each hook with `generalSettings.hooks.worldguard`, `plotsquared` and `worldedit` in
[config.yml](./config-yml.md#generalsettings). The server that holds a job's world gives and takes the access; it checks
every 30 seconds, so members who join or leave the company are added or removed, and access of jobs that ended while
the server was off is taken back when it starts. Access that can not be taken back yet, such as while the world is not
loaded, is tried again. The same check cancels a job whose land changed hands, such as a plot that was sold or a region
that lost its owner or was removed: a waiting job gives its price back; a job a company works on pays the company for
the share of the area it cleared and gives the rest back. While a company works on a job, only its members may place
blocks or empty buckets in the area, so nobody fills it up again. The primary server expires jobs and opens jobs whose
time ran out again, every minute.
