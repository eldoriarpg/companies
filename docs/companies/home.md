---
title: Company home
sidebar_position: 8
---

# Company home

A company has one home its members teleport to.

| Command | Who | What it does |
|---|---|---|
| `/company sethome` | members with `manage_areas` | Sets the home where you stand, looking the way you look. Setting it again moves it. |
| `/company home` | every member | Teleports you to the home. |

While your company has [areas](../admin/configuration/areas-yml.md), the home has to lie inside one of them (unless the
server switched `homeInsideArea` off).

The teleport waits a few seconds first (`homeWarmupSeconds` in [areas.yml](../admin/configuration/areas-yml.md)):
stand still, since moving to another block or taking damage cancels it. Afterwards you wait a while before the next
teleport home (`homeCooldownSeconds`). A teleport can cost money, paid by you or by the company's
[treasury](./treasury.md), as the server sets it. When the warm-up ends, the teleport goes to where the home is then;
if you left the company meanwhile, it is called off. A teleport that fails gives its cost and cooldown back. The
cooldown is kept per server.

## Homes on other servers

On a network the home stays on the server it was set on. From another server, `/company home` sends you there through
the proxy after the warm-up, and that server teleports you to the home when you arrive. This needs the server's name in
the proxy in `proxyServer` of its [node.yml](../admin/configuration/node-yml.md), and a proxy that passes plugin
messages (BungeeCord and Velocity do by default). A home set on a server without that name can only be used there.
When the proxy refuses to send you, for example because the name is wrong, the teleport has still been paid.

You need the server permission `companies.company.home`, which everyone has by default.
