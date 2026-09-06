# RampDoor 0.2.1

[한국어 README](README.md) · English documentation

RampDoor is a lightweight Minecraft Java Edition **26.2 / Fabric** mod that turns a player-built block structure into a moving cargo ramp. The closed ramp remains real Minecraft blocks. When it opens, the captured structure is rendered as one rigid group of `BlockDisplay` entities and a hidden collision surface keeps the ramp walkable.

## What is included

- Coordinate-defined size and direction for newly generated ramps.
- Coordinate capture for an existing closed structure.
- Hinge, angle, direction, duration, easing, and collision-width controls.
- Multiple ramps in one world. If an ID already exists, `create` and `demo` automatically choose `id_2`, `id_3`, and so on instead of failing with `Ramp already exists`.
- Ownership and overlap checks so one ramp cannot delete or overwrite another ramp's blocks or collision surface.
- Persistent state and crash recovery for open, opening, and closing ramps.

WorldEdit, Create, and Valkyrien Skies are optional; none is required at runtime.

## Requirements and installation

- Minecraft Java Edition **26.2**
- Fabric Loader **0.19.3 or newer**
- Fabric API for Minecraft 26.2
- Java **25**

Copy `build/libs/rampdoor-0.2.1.jar` to the `mods` folder on the server and client. Keep only one RampDoor JAR in that folder; `-sources.jar` is not an installable mod. Build from the project root with:

```sh
./gradlew build
```

On Windows, use `./gradlew.bat build`. The included `build-local.ps1` runs the same build and the GameTest suite with this project's local JDK setup.

## Quick start

Commands require OP level 2 or single-player cheats.

```mcfunction
/ramp demo cargo_ramp
/ramp open cargo_ramp
/ramp close cargo_ramp
/give @s rampdoor:ramp_controller
/ramp bind cargo_ramp
```

The controller toggles the bound ramp with a right click. A redstone signal is edge-triggered, so a constant signal does not repeatedly toggle the ramp.

## Choose size and direction with coordinates

Create a ramp from three points: the two blocks at the hinge-side edge and one block at the far end. The first two points define the width; the third defines the horizontal extension direction and length.

```mcfunction
/ramp demo cargo 100 70 100 115 70 100 100 70 121
```

This creates a 16-wide × 22-long ramp extending toward `+Z`. Relative coordinates such as `~ ~ ~` are supported and are resolved from the command executor.

Capture an existing closed structure by selecting two opposite corners:

```mcfunction
/ramp create custom
/ramp capture custom 100 69 100 115 70 121
/ramp hinge custom 108 71 100
/ramp direction custom 108 61 116
```

The `direction` target sets the horizontal facing and derives the opening angle from the height difference. The legacy `pos1`/`pos2`, `hinge`, `axis`, `extend`, `angle`, and `direction down|up` commands remain available. See [COORDINATES.md](COORDINATES.md) for coordinate rules, limits, console examples, and troubleshooting.

## Installing several ramps

The old `Ramp already exists` message occurred when the same ID was reused. In 0.2.1, these commands create three separate definitions:

```mcfunction
/ramp create cargo
/ramp create cargo
/ramp create cargo
```

The chat reports the actual IDs: `cargo`, `cargo_2`, and `cargo_3`. Use those IDs for capture, open, close, and controller binding. `/ramp demo cargo ...` follows the same rule, while `/ramp demo` generates `ramp`, `ramp_2`, and so on. Each ramp must occupy a different empty location, and the default limit of four simultaneous animations is unchanged.

## Safety and performance

RampDoor refuses to capture unsupported blocks, overwrite another ramp, close into an obstructing entity, or place a collision surface in a blocked location. A moving ramp is protected from ordinary block edits. Closed ramps have no display entities or tick work; open ramps keep only static displays and collision blocks. Default limits are 512 captured blocks, 2,048 collision blocks, and four simultaneous animations per world.

## Testing

```sh
./gradlew build
./gradlew runGameTest
```

The GameTests cover open/close recovery, coordinate sizing and direction, and multiple ramps with automatic ID suffixes. Optional WorldEdit compatibility can be checked with:

```sh
./gradlew runGameTest -PtestWorldEdit=/absolute/path/to/worldedit.jar --rerun-tasks
```

See [TESTING.md](TESTING.md) for the manual checklist and expected results. The project license is in [LICENSE](LICENSE).
