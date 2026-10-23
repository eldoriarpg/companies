---
title: Boards
sidebar_position: 3
---

# Boards

An order board is the order search with a filter already set. Put one on a sign at the market, or let an NPC open it.
Players right-click and see the matching open orders.

This page shows you how to set boards up. For how players use them, see [Order boards](../players/order-boards.md).

## What a board shows

A board is one of these:

- **A preset.** A named filter from `boards.presets` in `config.yml`, such as `iron`.
- **Materials.** One or more materials, separated by spaces, such as `oak_log birch_log`. Each one matches every
  material whose name contains it, so `iron` matches `iron_ingot`, `iron_block`, `raw_iron` and more.
- **Nothing.** An empty board shows every open order.

A board opens the first page of results. Players can press **[Change]** to adjust the search from there. Members with
the `MANAGE_ORDERS` company permission can accept orders right from the board. Everyone else sees the list without
accept buttons.

Using a board needs `companies.boards.use`, which every player has by default. A player opens at most one board per
second, so a double click shows one menu.

You can open any board yourself with `/company board [preset or materials]`.

## Presets

Presets live in `config.yml`:

```yaml
boards:
  enabled: true
  refreshSeconds: 60
  signHeader: '[Companies]'
  presets:
    iron:
      label: Iron
      materials: [iron_ingot, iron_block, raw_iron]
      exactMatch: true
    cheap-wood:
      label: Cheap wood
      materials: [log]
      maxPrice: 500
```

| Key | Default | Meaning |
|---|---|---|
| `label` | the preset name | Text shown on the sign |
| `name` | empty | Part of the order name. Empty means any name |
| `materials` | empty | Materials to look for |
| `anyMaterial` | `true` | `true`: an order needs one of the materials. `false`: it needs all of them |
| `exactMatch` | `false` | `true`: whole material names only. `false`: a part of the name is enough |
| `minPrice` | `0` | Lowest order price |
| `maxPrice` | `0` | Highest order price. `0` means no limit |
| `minSize` | `0` | Fewest items in the order |
| `maxSize` | `0` | Most items in the order. `0` means no limit |

Preset names ignore case. If two presets have the same name, the first one counts.

`/companyadmin board presets` lists every preset with the number of open orders it shows right now.

:::tip Changing presets
After you change presets, run `/companyadmin reload` and then `/companyadmin board reload`. The second command
writes the loaded board signs again, so they show the new labels.
:::

All board keys are on the [config.yml](./configuration/config-yml.md) page.

## Sign boards

To make a sign board:

1. Place a sign.
2. Write `[Companies]` on the first line. Case does not matter. You can change this text with `boards.signHeader`.
3. Write a preset name or materials on the second line. Leave it empty for every open order.
4. Finish editing.

You need `companies.admin.boards` for this. The sign turns into a board right away: it is waxed, and both sides show
the header, the label, the number of open orders and **Right-click**. The board is stored on the sign itself.

If something is wrong, the sign stays a plain sign and the header is cleared:

- A player without `companies.admin.boards` gets "You are not allowed to create order boards."
- An unknown preset or material gets "... is neither a board preset nor a material."

If you remove a preset later, its signs stay, but clicking them shows the same "unknown" error.

### Counts

The open order count on each sign is refreshed every `boards.refreshSeconds` (default `60`, at least `10`). It is
also refreshed a few seconds after an order on this server is created, accepted, released or removed. Only signs in
loaded chunks are counted, and each different board is counted once, however many signs show it.

### Protection

Board signs are protected:

- Nobody can edit them.
- Only players with `companies.admin.boards` can break them.
- Explosions and fire leave them alone.

A sign still falls off when the block it hangs on is removed, and pistons move it like any block. Place board signs
on blocks that a region plugin protects.

### Turning sign boards off

`boards.enabled: false` turns sign boards off. `/company board` and NPC boards keep working. This setting needs a
server restart.

## NPC boards

Right-clicking a bound NPC opens its board, with the same permission and the same one-per-second limit as signs.
Companies has built-in support for Citizens and FancyNpcs.

| Command | What it does |
|---|---|
| `/companyadmin board npc link <hook> <npc> [board]` | Binds an NPC to a preset, materials, or (without a board) every open order |
| `/companyadmin board npc unlink <hook> <npc>` | Removes the board from an NPC |
| `/companyadmin board npc list` | Lists the bound NPCs |

`<hook>` is `Citizens` or `FancyNpcs`. How you name the NPC depends on the plugin:

- **Citizens:** the NPC id, or `selected` for the NPC you selected with `/npc select`.
- **FancyNpcs:** the NPC name.

```bash
/npc select
/companyadmin board npc link Citizens selected iron
/companyadmin board npc link FancyNpcs trader oak_log birch_log
/companyadmin board npc link Citizens 12
```

An NPC has one board. Linking it again replaces the board.

The bindings are saved in `boards.yml` on this server. They are never copied to other servers, so bind the NPCs on
each server that has them.

```yaml
npcs:
  - hook: Citizens
    npc: '12'
    board: iron
  - hook: FancyNpcs
    npc: trader
    board: oak_log birch_log
```

### When the hooks load

A hook loads when the server starts, if its plugin is enabled and its switch in `config.yml` is on:

```yaml
generalSettings:
  hooks:
    citizens: true
    fancynpcs: true
```

The plugin checks this once, shortly after start. If you install or enable an NPC plugin later, restart the server.
If a hook fails to load, for example because the NPC plugin changed, Companies logs a warning and the other hook
still loads.

### Other NPC and menu plugins

Any plugin that can run a command as the player can open a board. Let it run `company board [board]` as the clicking
player. No hook is needed.

```bash
/npc command add -p company board iron
```

For FancyNpcs, the same works with the action `player_command company board iron`.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `companies.boards.use` | everyone | Opening boards from signs, NPCs and `/company board` |
| `companies.admin.boards` | op | Creating and breaking board signs, `/companyadmin board ...` |

See [Permissions](../reference/permissions.md) for the full list.
