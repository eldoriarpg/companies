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
buildWhitelist:
  - scaffolding
maxChunks: 64
maxOpenPerOwner: 5
maxDueHours: 168
extensionsEnabled: true
maxTotalHours: 336
maxExtensionRequests: 3
openDays: 7
approveHours: 72
approveAreaJobs: false
schematicUploads: false
maxSchematics: 10
maxAreas: 10
maxDrafts: 2
draftDays: 14
buildGhostRadius: 8
dugToStock: true
suppliedFill: true
```

| Key | Values | Default | Description | Takes effect |
|-----|--------|---------|-------------|--------------|
| `enabled` | true/false | `true` | Players may post new jobs. Running jobs go on either way. | reload |
| `unownedWorlds` | list of world names | `[]` | Worlds where a job may lie on land of nobody (no plot, no region). Off everywhere unless a world is listed. Only WorldGuard and the area providers are asked whose land an area is; list only worlds no other claim plugin protects. | reload |
| `digMaxVolume` | number (blocks) | `32768` | The most blocks the area of a dig job may hold. The area is checked block by block on the main thread when a company finishes it. | reload |
| `terraformMaxVolume` | number (blocks) | `32768` | The most blocks the area of a terraform job may hold, checked like a dig job's. | reload |
| `harvestMaxVolume` | number (blocks) | `16384` | The most blocks the field of a harvest job may hold. | reload |
| `buildMaxVolume` | number (blocks) | `32768` | The most blocks the area of a build job may hold. | reload |
| `buildMatchPercent` | number (percent) | `95` | The share of a blueprint's blocks that must match for a build job to be done. A block matches by its type and how it is turned (facing, half, axis, hinge, a slab's half); connections and stair shapes never count. | reload |
| `buildWhitelist` | list of blocks | `[scaffolding]` | The blocks the working company may place anywhere in a build job's area to build up. Every other block may only go where the blueprint asks for it. | reload |
| `maxChunks` | number (chunks) | `64` | The most chunks the area of a job may touch, so a long thin area can not make the server load many chunks for each check. | reload |
| `maxOpenPerOwner` | number | `5` | The most jobs a player may have waiting for a company at once. | reload |
| `maxDueHours` | number (hours) | `168` | The most hours an owner may give a company to finish a job. | reload |
| `extensionsEnabled` | true/false | `true` | Owners may give a taken job [more time](../../players/jobs.md#more-time), and companies may ask for it. | reload |
| `maxTotalHours` | number (hours) | `336` | The most hours a job may have in all, extensions included, counted from when it was taken. Never less than `maxDueHours`. | reload |
| `maxExtensionRequests` | number | `3` | How often a company may ask for more time per job; refused requests count. | reload |
| `openDays` | number (days) | `7` | How long a job waits for a company. Nobody took it by then: it expires and price and fee go back to its owner. | reload |
| `approveHours` | number (hours) | `72` | How long a job the company says is done waits for its owner to approve or reject it. Without an answer by then it is approved and paid. | reload |
| `approveAreaJobs` | true/false | `false` | Dig, terraform and build jobs whose check passed wait for their owner to approve them, like contracts, instead of being paid at once. | reload |
| `schematicUploads` | true/false | `false` | Players may upload their own schematics for build jobs on the dashboard. Each upload is cleaned of the contents of containers, command blocks, creatures and other unwanted contents before it is kept. Uploads are at most 512 KB and must fit `buildMaxVolume`. Needs FastAsyncWorldEdit. Switched off, kept uploads can still be used for build jobs. Also on the integrations page of the setup. | reload |
| `maxSchematics` | number | `10` | The most schematics a player may keep uploaded. | reload |
| `maxAreas` | number | `10` | The most areas a player may keep stored for jobs (`/order job area`). | reload |
| `maxDrafts` | number | `2` | The most drafts of jobs a player may keep. Drafts are not counted as open jobs. | reload |
| `draftDays` | number (days) | `14` | How long a draft waits to be posted after it was saved; editing it does not move the date. Its owner is told a day before; then it is deleted and its stock goes to their item returns. A change applies to drafts saved afterwards. | reload |
| `dugToStock` | true/false | `true` | The owner of a dig, terraform or build job may have the blocks the company digs go into the job's stock, which comes back to them when the job ends. | reload |
| `suppliedFill` | true/false | `true` | The owner of a terraform job may supply the blocks it is filled with through the stock, placed with magic blocks. | reload |
| `buildGhostRadius` | number (blocks) | `8` | How far around a member the missing and wrong blocks of a build job show as ghost blocks, from 1 to 32. | reload |

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

- **PlotSquared** and every other [area provider](./areas-yml.md#area-plugins) that lets jobs on its land: the area
  must lie wholly in one plot or claim of the owner, merged plots counting as one; roads and unclaimed plots refuse it.
  The company's members are trusted there while they work; only members who were not trusted before are added, and
  only those are removed. Trust reaches the whole plot or claim, so Companies holds those members to the job's area:
  outside it they can not break or place blocks, use buckets, use blocks such as chests or doors, or hit entities.
- **WorldGuard:** the area must lie wholly in one region the owner owns. A region `companies-job-<id>`, above every
  region there, lists the company's members and the owner as members; other members of the owner's region can not
  build in it while it exists. On unowned land the job region marks who works there and keeps others out. A region owned
  through a group or a parent region counts as owned.
- **WorldEdit** (or FastAsyncWorldEdit) lets owners select the area of a job.

Switch each hook with `generalSettings.hooks.worldguard`, `plotsquared` and `worldedit` in
[config.yml](./config-yml.md#generalsettings); `providers` in [areas.yml](./areas-yml.md) switches an area provider off
for jobs as well. for jobs as well. The server that holds a job's world gives and takes the access; it checks every 30
seconds, so members who join or leave the company are added or removed, and access of jobs that ended while the server
was off is taken back when it starts. Access that can not be taken back yet, such as while the world is not loaded, is
tried again. The same check cancels a job whose land changed hands, such as a plot that was sold or a region that lost
its owner or was removed: a waiting job gives its price back; a job a company works on pays the company for the share of
the area it cleared and gives the rest back. Blocks of a dig job's area that were already clear when it was posted earn
nothing in that share; the server measures them while the job waits, and a job taken before that gives its company
access only once they are measured. Both sides are told the share done and how many blocks were left. Blocks placed and
positions filled while a job runs are stored, so after a restart a position never counts twice. While a company works on
a job, only its members may place blocks or empty buckets in the area, so nobody fills it up again. The primary server
expires jobs and opens jobs whose time ran out again, every minute.
