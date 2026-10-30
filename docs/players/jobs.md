---
title: Jobs
sidebar_position: 10
---

# Jobs

Some work is not "deliver these items": dig out a cellar under your house, clear a quarry, empty a hill, or anything
you can describe in words. Post it as a **job** and a company does it for you. For work in an area, the company gets
access to just that area while it works, and is paid when the work checks out. For a **contract**, you decide whether
the work is done.

## Posting a job

A job lies on your land: wholly on one plot you own (PlotSquared), wholly in one region you own (WorldGuard), or on land
of nobody in worlds where the server allows it. Any other land, a road, or the area of another job that waits or is
worked on refuses it, and so does an area that reaches above or below the world or touches too many chunks. You may
have a few jobs waiting at once.

- **Select an area with WorldEdit** (`//pos1`, `//pos2`), then post it:
  `/order job dig <price> <hours> <title>`
- **Or use your whole plot:** stand on the ground of your plot and post
  `/order job dig plot <price> <hours> <depth> <title>`. The area is the plot, from the block below your feet down
  `depth` blocks.

`price` is what the company gets, `hours` how long it has to finish once it takes the job. You pay the price and the
[order fee](../companies/claiming-and-delivering.md) at once; the server holds the money until the job is done.

A **dig job** is done when every block of the area is air, water or lava. Blocks nobody can break, such as bedrock,
count as cleared. The company keeps what it digs.

A **terraform job** levels the area: every column must be solid up to a height you choose and empty above it. Select
the area with WorldEdit so it reaches from the lowest block that must be filled to the highest that must go, then post
`/order job terraform <price> <hours> <y> <title>`, where `y` is the height of the top solid layer. With
`/order job terraform surface <block> <price> <hours> <y> <title>` the top layer must also be made of one block, such
as `grass_block` (type the name without `minecraft:`). Blocks players can not place and keep in survival, such as
bedrock, spawners or farmland, are refused, and so are blocks that change by themselves, such as copper that is not
waxed or concrete powder. Above `y` the area must be air, water or lava; at or below it any solid
block counts. Blocks nobody
can break count as done. The company brings the blocks it fills with and keeps what it digs.

When your land changes hands while a job waits or is worked on, for example a plot you sold, the job is cancelled. A
waiting job gives its price back to you. A job a company works on pays the company for the share of the area it cleared,
and the rest of the price comes back to you. The fee is kept either way. While a company works on your job, only its
members may place blocks or pour buckets in the area.

## Build jobs

A **build job** asks a company to build a schematic. The server's schematics are listed by `/order job schematics`;
an admin puts them into the plugin's `schematics` folder. Stand where the schematic's origin should be and look at its
outline with `/order job blueprint <schematic> <rotation>`, where `rotation` turns it clockwise by 0, 90, 180 or 270
degrees. When it fits, post it from the same spot: `/order job build <schematic> <rotation> <price> <hours> <title>`.
The area is the box the schematic covers there, and it must lie on your land like any other job.

The job is done when enough of the schematic's blocks are in place: `buildMatchPercent` (95 % by default) in
[jobs.yml](../admin/configuration/jobs-yml.md). Only block types count, not which way a stair faces or how a fence
connects. Where the schematic has air, anything goes, so terrain and furniture never hold the job up.

Members of the company see what is missing with `/company job ghosts <id>`: small glowing copies of the missing and wrong
blocks of one layer, only for them, starting with the lowest layer that is not done. `/company job ghosts next <id>` and
`/company job ghosts previous <id>` move a layer up or down, `/company job ghosts off` hides them. Placing a block the
schematic asks for counts as work, once for each spot, and so does taking out a wrong block.

## Harvest jobs

A **harvest job** keeps a field harvested for a time. Select the field with WorldEdit and post
`/order job harvest <price per item> <budget> <hours> <title>`. You pay the budget and the fee at once. Once a company
takes the job it harvests for `hours`, and each item it harvests is paid from the budget at the price per item, taxed
and with the treasury cut like an order payout.

While the company works on it, its members can only harvest in the field:

- Wheat, carrots, potatoes, beetroots, nether wart and cocoa only when ripe. The crop is replanted at once; one seed of
  the yield is kept back for that.
- Sugar cane, bamboo and cactus only above the lowest block, which stays to grow again.
- Melons and pumpkins only the fruit, never the stem.
- Nothing else in the field can be broken, and nobody tramples the farmland. Members may plant field crops but not
  place other blocks or pour buckets there, and what they placed is no harvest.

Nothing drops: the harvest goes into the job's **stock**. List it with `/order job stock <id>` and take it with
`/order job collect <id>`; what does not fit into your inventory stays in the stock. The stock waits for you after the
job ended.

The job ends when its time or budget runs out, when the company ends it, or when you end it with `/order job end <id>`.
The rest of the budget comes back to you, the fee is kept, and the company keeps what it was paid. Running out of time
costs a harvest company nothing. A harvest job has no approval and no disputes.

## Contracts

A **contract** is work described in words, such as "build me a stable" or "bring 64 lanterns to my shop". Stand where
the work is and post it:
`/order job contract <price> <hours> <title>`, then describe it with `/order job describe <id> <text>` while nobody
has taken it yet. A contract gives the company no access to your land; agree on that with them in game.

There is no check. When the company says the work is done, your inbox asks you to approve or reject it:

- **Approve** (`/order job approve <id>`): the company is paid.
- **Reject** (`/order job reject <id> <reason>`): the company is back at work. The time it waited for your answer is
  added to its time, so your delay costs it nothing.
- **No answer:** after `approveHours` (72 by default, in [jobs.yml](../admin/configuration/jobs-yml.md)) the contract is
  approved and paid.

You may also approve a dig job early, before every block is gone, when the rest does not matter to you. It is paid as
it stands.

## Disputes

When you and the company can not agree, either side opens a **dispute** about a job a company works on or that waits
for your answer: `/order job dispute <id> <reason>`, or `/company job dispute <id> <reason>` for the company. The job
is frozen: the company's access ends, its time stops, and nobody, you included, may place blocks or pour into its area.
A dispute the owner opens frees the company's order slot meanwhile; one the company opens keeps it. An admin looks at it and decides how the price is split: anything
from all of it to the company to all of it back to you. The fee is kept.

Until then, the side that opened the dispute may withdraw it (`/order job withdraw <id>` or `/company job withdraw
<id>`): the job goes back to where it was, and the company gets the time the dispute lasted added. Work the company had
already said is done waits for your answer again, with the time to approve it running on as before. You may also still approve the
job, which pays the company in full and ends the dispute.

## Your jobs

| Command | What it does |
|---|---|
| `/order job` | Lists your jobs. |
| `/order job info <id>` | Shows a job: its area or place, money, state, and how far the work is (on the server of its area). |
| `/order job contract <price> <hours> <title>` | Posts a contract at the place you stand. |
| `/order job describe <id> <text>` | Describes a job nobody has taken yet. |
| `/order job approve <id>` | Approves a contract the company says is done, or a job a company works on as it stands, and pays the company. |
| `/order job reject <id> <reason>` | Sends a contract the company says is done back to work. |
| `/order job dispute <id> <reason>` | Opens a dispute an admin settles. |
| `/order job withdraw <id>` | Withdraws a dispute you opened. |
| `/order job schematics` | Lists the schematics build jobs may use. |
| `/order job blueprint <schematic> <rotation>` | Outlines a schematic placed where you stand for ten seconds. |
| `/order job build <schematic> <rotation> <price> <hours> <title>` | Posts a build job of a schematic placed where you stand. |
| `/order job harvest <price per item> <budget> <hours> <title>` | Posts a harvest job on the field you selected with WorldEdit. |
| `/order job stock <id>` | Lists the stock of a harvest job. |
| `/order job collect <id>` | Takes the stock of a harvest job into your inventory. |
| `/order job end <id>` | Ends a harvest job a company works on; the rest of the budget comes back. |
| `/order job cancel <id>` | Cancels a job nobody works on. The price comes back, the fee is kept. |

A job waits for a company for some days (`openDays` in [jobs.yml](../admin/configuration/jobs-yml.md)). When nobody
takes it, it expires and price and fee come back. The plugin never tells you which company works on your job; your inbox
tells you when it is taken, given back, done, waiting for your answer, disputed, settled or expired. The land plugins may still show who has access, for example
in the members of the job's WorldGuard region.

## Working on jobs

Members with the *manage orders* permission run the company's jobs:

| Command | What it does |
|---|---|
| `/company job` | Lists the open jobs. |
| `/company job mine` | Lists the jobs your company works on. |
| `/company job info <id>` | Shows a job's area, pay and time, and how far your work is. |
| `/company job take <id>` | Takes a job. It uses one of your company's order slots. |
| `/company job giveback <id>` | Gives a job back; it is open again. |
| `/company job done <id>` | Checks the area and, when the work is done, pays the job. Run it on the server of the job's area. For a contract it tells the owner the work is done; they approve or reject it. For a harvest job it ends the harvest. |
| `/company job dispute <id> <reason>` | Opens a dispute an admin settles. |
| `/company job ghosts <id>` | Shows the missing and wrong blocks of a build job, one layer at a time. Add `next` or `previous` before the id to move a layer; `/company job ghosts off` hides them. |
| `/company job withdraw <id>` | Withdraws a dispute your company opened. |

While your company has the job, its members may change the area: on a plot they are trusted, in a region a job region
lists them as members. Members who only have access through the job can not break or place blocks, use buckets, open
chests, press buttons or levers, or hit entities on the plot outside the areas of their jobs; they can still open doors,
gates and trapdoors and fight monsters. Nobody but staff may pour lava, place TNT or set fire in the area while a company
works on it.
The access ends within half a minute when the job is done, given back, or its time runs out; then the job is open
again for every company. Giving a job back or running out of time costs your company level progress, like an order.
When your company is disbanded, its jobs are open again.

When the job is done, the price is split among the members by the blocks each of them broke in the area. Blocks placed
during the job do not count when they are broken again; a block left in the area keeps the job from being done. In a
terraform job, breaking counts only above the target height and for top layer blocks that are not the surface block,
and placing a block counts when it fills its spot as the target asks, once for each spot. Putting back a block you
took out below the target earns nothing. Each
part is taxed and shares the [treasury cut](../companies/treasury.md) like an order payout; the tax and fee reductions
of [server events](./server-events.md) apply. Members who left the company keep the part they earned. When nobody's
work was counted, the member who finished it gets it all.
