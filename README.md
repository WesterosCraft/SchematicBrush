# SchematicBrush

A [WorldEdit](https://enginehub.org/worldedit) add-on for Fabric that turns schematics into brushes. Bind a single schematic — or a weighted *set* of schematics with randomized rotation, flipping, and placement — to a tool, then paint structures into the world with the WorldEdit brush.

This is the **Fabric 1.21.1** port of the original [SchematicBrush](https://github.com/mikeprimm/SchematicBrushReborn) Bukkit plugin, maintained by [WesterosCraft](https://www.westeroscraft.com/).

- **Mod ID:** `schematicbrush`
- **Version:** 3.0.0
- **License:** Apache-2.0

## Features

- Bind any held item as a **schematic brush** that pastes `.schem` files on click.
- Define reusable, named **schematic sets** that randomly pick a schematic on each placement.
- Per-schematic **rotation** (0/90/180/270 or random), **flip** (north–south, east–west, or random), **weight**, and **vertical offset**.
- **Wildcard / regex** schematic names — `castle_*` picks a random matching file each time.
- Configurable **placement modes**: center, bottom, or drop-to-ground.
- Control over how air and existing blocks are handled (`-incair`, `-replaceall`).
- Sets are persisted to disk as JSON and reloaded on server start.

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.1 |
| Java | 21+ |
| Fabric Loader | ≥ 0.18.2 |
| Fabric API | latest for 1.21.1 |
| WorldEdit (Fabric) | 7.3.8+ |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1.
2. Drop the following into your `mods/` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [WorldEdit](https://modrinth.com/plugin/worldedit) (Fabric build)
   - `SchematicBrush-3.0.0.jar` (see [Building](#building-from-source))
3. Start the server (or client). On startup the mod creates its config directory and confirms it found WorldEdit in the log.

Schematic brushes operate on the WorldEdit schematics directory (WorldEdit's configured `saveDir`, normally `config/worldedit/schematics`). Place your `.schem` files there.

## Usage

### Binding a brush — `/schbr`

Hold the item you want to use as a brush, then run:

```
/schbr [flags] <schematic-spec> [schematic-spec ...]
/schbr [flags] &<set-id>
```

You can supply either a list of individual schematic specs **or** a single schematic set (prefixed with `&`) — the two cannot be mixed. Right-clicking with the bound tool then pastes a schematic at the targeted block.

**Flags** (must appear in this order, all optional):

| Flag | Effect |
|------|--------|
| `-incair` | Include the air blocks from the schematic when pasting (by default schematic air is skipped). |
| `-replaceall` | Replace existing blocks. By default the brush only pastes into air, leaving existing terrain untouched. |
| `-yoff <n>` | Shift the paste vertically by `n` blocks. |
| `-place <center\|bottom\|drop>` | Placement anchor — see below. Defaults to `center`. |

**Placement modes:**

- `center` — paste centered on the target block, like WorldEdit's clipboard brush.
- `bottom` — anchor the bottom of the schematic at the target.
- `drop` — like `bottom`, but anchored to the lowest non-air layer of the schematic so it sits flush on the ground.

**Examples:**

```
/schbr house                 # bind "house.schem" to the held item
/schbr -place drop tree@*     # random-rotation tree, dropped to ground
/schbr &village               # bind the "village" schematic set
```

### Schematic specification syntax

Each schematic spec follows the form:

```
name[@<rotation><flip>][:<weight>][^<offset>]
```

| Part | Meaning |
|------|---------|
| `name` | Schematic file name (no extension). Supports `*`/`?` wildcards, or a `^`-prefixed regex. A random match is chosen per placement. |
| `@<rotation>` | Rotation in degrees: `0`, `90`, `180`, `270`, or `*` for random. |
| `<flip>` | Flip direction appended to the rotation: `N`/`S` (north–south), `E`/`W` (east–west), or `*` for random. |
| `:<weight>` | Relative selection weight within a set (see below). |
| `^<offset>` | Vertical offset applied to this schematic. |

Examples: `tower@90`, `tree@*N`, `rock@180:25`, `pillar^-1`, `wall_*@*:`.

### Schematic sets — `/schset`

Sets are named collections of schematic specs. When a set is used as a brush, one schematic is randomly chosen on each placement.

| Command | Description |
|---------|-------------|
| `/schset list [contains]` | List all sets, optionally filtered by a substring of the set ID. |
| `/schset create <set-id> [specs...]` | Create a new set, optionally seeding it with schematic specs. |
| `/schset delete <set-id>` | Delete a set. |
| `/schset append <set-id> <specs...>` | Add schematics to a set. |
| `/schset remove [-exact] <set-id> <specs...>` | Remove schematics. By default matches by name; `-exact` matches the full spec (rotation/flip/weight/offset). |
| `/schset setdesc <set-id> "<description>"` | Set the set's description. |
| `/schset get <set-id>` | Show a set's description and the schematics it contains. |

**Weighting:** A schematic with no `:weight` shares equal probability with other unweighted entries. A schematic with an explicit weight (e.g. `:25`) is chosen with that weight out of 100, leaving the remainder split among the unweighted entries. If the total of fixed weights exceeds 100, unweighted entries are never selected (the `get` command warns about this).

### Listing schematics — `/schlist`

```
/schlist [page]
```

Lists the `.schem` files available in the WorldEdit schematics directory, 10 per page.

## Permissions

The mod checks WorldEdit permission nodes. Operators have them by default; grant them via your permissions system as needed.

| Node | Grants |
|------|--------|
| `schematicbrush.brush.use` | Use `/schbr` and the resulting brush. |
| `schematicbrush.set.list` | `/schset list` |
| `schematicbrush.set.create` | `/schset create` |
| `schematicbrush.set.delete` | `/schset delete` |
| `schematicbrush.set.append` | `/schset append` |
| `schematicbrush.set.remove` | `/schset remove` |
| `schematicbrush.set.setdesc` | `/schset setdesc` |
| `schematicbrush.set.get` | `/schset get` |
| `schematicbrush.list` | `/schlist` |

Commands are usable by in-game players only (not the server console).

## Configuration

Schematic sets are stored as JSON at:

```
config/schematicbrush/schembrush.json
```

The file is written automatically whenever sets change and is reloaded when the server starts. If it is missing or unreadable, the mod recreates it with an empty set list.

## Building from source

This project uses Gradle with [Fabric Loom](https://fabricmc.net/develop/).

```bash
./gradlew build
```

The built jar is produced in `build/libs/`. WorldEdit is pulled from the [EngineHub Maven repository](https://maven.enginehub.org/repo/), which is already configured in `build.gradle`.

Dependency and version properties (Minecraft, Fabric, WorldEdit, mod version) live in `gradle.properties`.

## Credits

- Original SchematicBrush plugin by **mikeprimm** and **Emoticone11**.
- Fabric port maintained by [WesterosCraft](https://www.westeroscraft.com/).

## License

Licensed under the [Apache License 2.0](LICENSE).
