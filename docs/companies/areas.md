---
title: Company areas
sidebar_position: 9
---

# Company areas

A company can use plots and regions as its areas. Members with the `build` permission build there, members with
`containers` open the chests, barrels, furnaces and other containers there. How many areas a company may have depends
on its level (`maxAreas` in [company.yml](../admin/configuration/company-yml.md), 1 by default).

| Command | Who | What it does |
|---|---|---|
| `/company area` | every member | Lists the areas of the company. |
| `/company area link` | members with `manage_areas` | Links the plot or region you stand on and own. |
| `/company area unlink [id]` | members with `manage_areas`, or the member who linked it | Unlinks the area you stand on, or the one with the id. Run it on the server of the area. |
| `/company area market` | every member | Lists the areas the server offers to buy or rent. |
| `/company area buy <offer>` | members with `manage_areas` | Buys or rents an offered area from the treasury. Run it on the server of the area. |
| `/company area cancel <id>` | members with `manage_areas` | Stops renewing a rent, or sells a bought area back. |

Areas come from [PlotSquared](https://www.spigotmc.org/resources/77506/) plots and
[WorldGuard](https://enginehub.org/worldguard) regions, as the server set up in [areas.yml](../admin/configuration/areas-yml.md).
A company links a plot or region a member owns, or buys or rents one the server offers.

## Linking your own plot or region

Stand on a plot or in a region you own and run `/company area link`. It stays yours: the company only uses it. When you
leave the company, or no longer own the plot or region, it is unlinked again within a minute. A plot or region
belongs to one company at a time.

## Buying and renting

Admins offer plots and regions with `/companyadmin area offer`. `/company area market` lists them with their price; a
rent is paid per period, for example per 7 days. `/company area buy <offer>` pays the price, or the first period, from the
[treasury](./treasury.md). The area then belongs to the owner of the company and follows a new owner. Only members
with `build` build there: everybody else who could build there before is taken off.

The treasury pays every next period of a rent when the last one is over. The members who manage areas are told a day
before (`rentWarnHours`) and when it was paid. When the treasury does not hold enough, the rent ends and the area goes
back to the server.

`/company area cancel <id>` on a rented area stops renewing it; the rent runs until the end of the period paid. On a
bought area it sells it back: it goes back to the server at once and the treasury gets `sellBackPercent` of the price
(half by default). When a company is disbanded, its rents end and its bought areas go back to the server; the owner
gets `sellBackPercent` of their price with the treasury at once, while the areas go back when their server next
checks them, about a minute later.

## Building

Members with `build` are made builders of the area: trusted on a plot, members of a region. When a member loses
`build` or leaves the company, that is taken back within half a minute. Players you trusted on your plot yourself stay
trusted; Companies only takes back what it gave.

## Containers

Inside a company's area, its members open containers, take books out of chiseled bookshelves and items out of decorated
pots only with `containers`. The owner of the company and the owner of the area always may. Players outside the company
are kept out by the plot or region itself, as before.

`containers` only narrows what members may. The plot or region still decides first: on PlotSquared and WorldGuard a
member opens containers only as a builder, so `containers` without `build` lets nobody in. Permission changes made on
another server reach this rule within half a minute. Containers in minecarts, boats and on animals are not covered.
