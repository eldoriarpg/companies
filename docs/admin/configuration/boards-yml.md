---
title: boards.yml
sidebar_position: 9
---

# boards.yml

The NPCs bound to order boards on this server.

This file belongs to one server and is never shared, because NPCs exist on one server only. You usually do not edit it by hand. `/companyadmin board npc link` and `/companyadmin board npc unlink` write it for you. See [Boards](../boards.md) for a guide.

## Default file

```yaml
npcs: []
```

With two bound NPCs, it looks like this:

```yaml
npcs:
  - hook: Citizens
    npc: '12'
    board: iron
  - hook: FancyNpcs
    npc: miner_joe
    board: coal_block coal
```

## Keys

| Key | Type | Default | Meaning |
|---|---|---|---|
| `npcs` | list | `[]` | The bound NPCs. Each NPC has one board. |
| `npcs[].hook` | text | `''` | The NPC plugin: `Citizens` or `FancyNpcs`. Case does not matter. |
| `npcs[].npc` | text | `''` | The NPC: the id for Citizens, the name for FancyNpcs. Must match exactly. |
| `npcs[].board` | text | `''` | A preset name from `boards.presets` in [config.yml](./config-yml.md#boards), or materials separated by spaces. Empty shows all open orders. |

The hooks are switched with `generalSettings.hooks.citizens` and `generalSettings.hooks.fancynpcs` in [config.yml](./config-yml.md#generalsettings).

The link and unlink commands apply at once. Changes you make to this file by hand apply after `/companyadmin reload`.
