---
title: Jobs
sidebar_position: 10
---

# Jobs

Some work is not "deliver these items": dig out a cellar under your house, clear a quarry, empty a hill. Post it as a
**job** and a company does it for you. The company gets access to just that area while it works, and is paid when the
work checks out.

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

When your land changes hands while a job waits or is worked on, for example a plot you sold, the job is cancelled. A
waiting job gives its price back to you. A job a company works on pays the company for the share of the area it cleared,
and the rest of the price comes back to you. The fee is kept either way. While a company works on your job, only its
members may place blocks or pour buckets in the area.

## Your jobs

| Command | What it does |
|---|---|
| `/order job` | Lists your jobs. |
| `/order job info <id>` | Shows a job: its area, money, state, and how far the work is (on the server of its area). |
| `/order job cancel <id>` | Cancels a job nobody works on. The price comes back, the fee is kept. |

A job waits for a company for some days (`openDays` in [jobs.yml](../admin/configuration/jobs-yml.md)). When nobody
takes it, it expires and price and fee come back. The plugin never tells you which company works on your job; your inbox
tells you when it is taken, given back, done, or expired. The land plugins may still show who has access, for example
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
| `/company job done <id>` | Checks the area and, when the work is done, pays the job. Run it on the server of the job's area. |

While your company has the job, its members may change the area: on a plot they are trusted, in a region a job region
lists them as members. Members who only have access through the job can not break or place blocks, use buckets, open
chests, press buttons or levers, or hit entities on the plot outside the areas of their jobs; they can still open doors,
gates and trapdoors and fight monsters. Nobody but staff may pour lava, place TNT or set fire in the area while a company
works on it.
The access ends within half a minute when the job is done, given back, or its time runs out; then the job is open
again for every company. Giving a job back or running out of time costs your company level progress, like an order.
When your company is disbanded, its jobs are open again.

When the job is done, the price is split among the members by the blocks each of them broke in the area. Blocks placed
during the job do not count when they are broken again; a block left in the area keeps the job from being done. Each
part is taxed and shares the [treasury cut](../companies/treasury.md) like an order payout; the tax and fee reductions
of [server events](./server-events.md) apply. Members who left the company keep the part they earned. When nobody's
work was counted, the member who finished it gets it all.
