# Worldsure

A Minecraft mod that keeps a world safe when its mod list changes.

Every world remembers which mods it was played with (its *passport*, stored in
`<world>/worldsure/manifest.json`). When you open the world with a mod removed,
updated or downgraded, Worldsure:

1. in single player, stops before the world opens and shows the Worldsure screen: every
   changed mod as a card with how much of it is in the world. You choose: back up and play,
   play without a backup, or go back. The same screen opens from the pause menu;
2. on a server, backs the world up to `backups/worldsure/` **before any chunk loads**
   (the 5 newest copies per world are kept);
3. keeps the blocks of a removed mod. While the mod is missing they show as a
   "Block of a removed mod" that cannot be broken, and when the chunk is saved the
   original blocks and their chests or machines are written back. Put the mod back and
   everything is where it was;
4. keeps the mobs and other entities of a removed mod the same way: they are set aside when
   their chunk loads and written back whenever it is saved;
5. keeps the items of a removed mod, wherever they are (chests, inventories, item frames, the
   ground, shulker boxes): each becomes an "Item of a removed mod" that carries the original stack
   and turns back into it whenever the game saves;
6. writes what changed to the log, and in chat tells single player players how the backup went.

Counts of blocks, block entities, entities and items per mod are kept in
`<world>/worldsure/index.dat` and summed into the passport. Adding a mod is reported but
does not trigger a backup. The world on disk never contains placeholders, so removing
Worldsure itself is always safe.

Settings live in `config/worldsure.properties` (`autoBackup`, `keepBackups`).

## Files and formats

`manifest.json` has a `"format"` number and `index.dat` starts with one. A file in an older format
is upgraded when it is read (`Upgrades` in `common/`, one step per format). A file written by a
newer Worldsure is never overwritten: the mods listed in a newer passport are still compared and the
world is backed up, but both files stay as they are until that newer version opens the world again.
`format` and `mods` keep their meaning in every passport format, so this comparison always works.

## Not done yet

Entities of a removed mod are kept but not shown while the mod is missing. Counts cover chunks
saved while Worldsure was installed; items in player inventories are kept but not counted. Items
stored by other mods in their own files (storage networks, global backpacks) are outside Worldsure's
reach.

## Supported versions

| Minecraft | Loaders |
|---|---|
| 26.3 | Fabric, NeoForge |
| 26.2 | Fabric, NeoForge |
| 26.1 – 26.1.2 | Fabric, NeoForge |
| 1.21.11 | Fabric, NeoForge |
| 1.21 – 1.21.1 | Fabric, NeoForge |
| 1.20.1 | Fabric, Forge |

## Installing

Put the jar for your loader and Minecraft version, e.g. `worldsure-fabric-0.1.1+1.21.1.jar` (with
Fabric API), `worldsure-neoforge-0.1.1+26.3.jar` or `worldsure-forge-0.1.1+1.20.1.jar`, into `mods/`. Worldsure adds a block, so a server and the clients joining it all need it.

## Building

Gradle runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`); the JDKs 17 and 21 that the
1.20.1 and 1.21.x targets compile with are found locally or downloaded.

```
./gradlew build
```

builds all twelve jars into `versions/<minecraft>/<loader>/build/libs`, named like
`worldsure-fabric-0.1.1+26.3.jar`. `-Ptargets=26.3,1.21.1` builds only those Minecraft versions;
`./gradlew :common:test -PcommonOnly` runs the core tests without downloading Minecraft.
`./gradlew :1.21.1:fabric:runClient` (or `runServer`) starts the game with the mod; add
`-PquickPlay=<world>` to open a single player world right away.

## Layout

- `common/` loader-independent logic (passport, diff, backup, report), plain Java with unit tests.
- `shared/` Minecraft code used by every loader: the Worldsure screen and pause menu button, block
  and item placeholders, kept entities and counting mixins. All targets use the official Mojang names, so it is
  compiled into each jar.
- `fabric/`, `neoforge/`, `forge/` thin entry points that register the placeholder block and hook
  server start and player join.
- `versions/<minecraft>/gradle.properties` the Minecraft, loader and Java versions of a target;
  `gradle/` the build each loader shares (`settings.gradle` lists the targets).

The sources are written for the newest Minecraft. Where an older version needs other code, the
build's preprocessor picks it from blocks like

```java
//#if >=1.21.2
newer code
//#else
//$ older code, commented out
//#endif
```

so an IDE reads the files as plain Java for 26.3. Conditions compare the Minecraft version
(`>=`, `<`, `==`, ...; several are combined with spaces); `//#elif` and nesting work.

## Tests

`./gradlew :common:test -PcommonOnly` runs the unit tests of the core.

Game tests run inside a real game test server, with no client: `./gradlew :26.3:fabric:gametest
-Ptargets=26.3` (any target works the same way). They check the passport the server writes, the
placeholder block and the round trip of a removed mod's blocks with the game's own registries. The
checks are in `shared/src/gametest`; each loader registers them from `<loader>/src/gametest`. They are
a separate source set, so they never go into the jars. The run starts from a fresh world in
`versions/<minecraft>/<loader>/build/gametest`.

CI builds every jar and then, for each target, runs the game tests and a real dedicated server twice
(`.github/smoke/smoke.py`): the second run opens the world with a fake mod's blocks
written into a chunk and checks the backup, the report, that the blocks survive a save
and that the passport counts them.

## Releasing

Versions go out one a day from a queue:

1. Set `mod_version` (and `release_type`: `release`, `beta` or `alpha`) in `gradle.properties` and
   add a `## <version>` section to `CHANGELOG.md`.
2. Push a tag `v<version>` on that commit, e.g. `git tag v0.1.1 && git push origin v0.1.1`. The tag
   only starts the Build workflow (jars, game tests, server smoke test).
3. Every day at 12:00 UTC the Daily release workflow takes the lowest tag that has no GitHub release
   yet and, if its Build run passed, runs the Release workflow for it. A tag whose Build is still
   running waits for the next day; one whose Build failed stops the queue and fails the workflow, so
   GitHub sends a notice. Fix it and move the tag (`git tag -f`, `git push -f origin <tag>`).

Release (also runnable by hand from the Actions tab with a tag) checks the tag against `mod_version`,
builds, and creates a GitHub release with all twelve jars and the changelog section. To also publish
to Modrinth and CurseForge, add repository secrets `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` and
variables `MODRINTH_ID` / `CURSEFORGE_ID` (Settings → Secrets and variables → Actions). The store
page text and icon are in `docs/`.
