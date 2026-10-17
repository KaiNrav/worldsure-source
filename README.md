# Worldsure

A Minecraft mod that keeps a world safe when its mod list changes.

Every world remembers which mods it was played with (its *passport*, stored in
`<world>/worldsure/manifest.json`). When you open the world with a mod removed,
updated or downgraded, Worldsure:

1. in single player, stops before the world opens and shows what changed, with how much
   of each removed mod is in the world. You choose: back up and play, play without a
   backup, or go back;
2. on a server, backs the world up to `backups/worldsure/` **before any chunk loads**
   (the 5 newest copies per world are kept);
3. keeps the blocks of a removed mod. While the mod is missing they show as a
   "Block of a removed mod" that cannot be broken, and when the chunk is saved the
   original blocks and their chests or machines are written back. Put the mod back and
   everything is where it was;
4. writes what changed to the log, and in single player shows it in chat once you join.

Counts of blocks, block entities and entities per mod are kept in
`<world>/worldsure/index.dat` and summed into the passport. Adding a mod is reported but
does not trigger a backup. The world on disk never contains placeholders, so removing
Worldsure itself is always safe.

Settings live in `config/worldsure.properties` (`autoBackup`, `keepBackups`).

## Status

Prototype of stages 1 and 2 from the design. Not yet done: placeholders for entities
and items of removed mods (those are still dropped by the game), item counts, and
1.21.1. Counts cover chunks saved while Worldsure was installed.

## Supported versions

| Loader | Minecraft |
|---|---|
| Fabric | 26.3 |
| NeoForge | 26.3 |

1.21.1 is planned next.

## Installing

Put `worldsure-fabric-<version>.jar` (with Fabric API) or `worldsure-neoforge-<version>.jar` into
`mods/`. Worldsure adds a block, so a server and the clients joining it all need it.

## Building

Requires JDK 25.

```
./gradlew build
```

Jars land in `fabric/build/libs` and `neoforge/build/libs`, named like `worldsure-fabric-0.1.0+26.3.jar`.
`./gradlew :common:test -PcommonOnly` runs the core tests without downloading Minecraft.

## Layout

- `common/` loader-independent logic (passport, diff, backup, report), plain Java with unit tests.
- `shared/` Minecraft code used by both loaders: the confirmation screen, block
  placeholders and counting mixins. 26.3 uses the same names on both loaders, so it is
  compiled into each jar.
- `fabric/`, `neoforge/` thin entry points that register the placeholder block, hook
  server start and player join, and compile the common and shared sources into their jar.

## Tests

CI builds both jars and then, for each loader, runs a real dedicated server twice
(`.github/smoke/smoke.py`): the second run opens the world with a fake mod's blocks
written into a chunk and checks the backup, the report, that the blocks survive a save
and that the passport counts them.

## Releasing

1. Set `mod_version` (and `release_type`: `release`, `beta` or `alpha`) in `gradle.properties`.
2. Add a `## <version>` section to `CHANGELOG.md`.
3. Push a tag `v<version>`, e.g. `git tag v0.1.0 && git push origin v0.1.0`.

The Release workflow checks the tag against `mod_version`, builds, and creates a GitHub release with
both jars and the changelog section. To also publish to Modrinth and CurseForge, add repository
secrets `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` and variables `MODRINTH_ID` / `CURSEFORGE_ID`
(Settings → Secrets and variables → Actions). The store page text and icon are in `docs/`.
