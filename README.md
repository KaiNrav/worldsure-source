# Worldsure

A Minecraft mod that keeps a world safe when its mod list changes.

Every world remembers which mods it was played with (its *passport*, stored in
`<world>/worldsure/manifest.json`). When you open the world with a mod removed,
updated or downgraded, Worldsure:

1. backs the world up to `backups/worldsure/` **before any chunk loads**
   (the 5 newest copies per world are kept);
2. writes what changed to the log, and in single player shows it in chat once you join.

Adding a mod is reported but does not trigger a backup. The world format is never
touched, so removing Worldsure itself is always safe.

Settings live in `config/worldsure.properties` (`autoBackup`, `keepBackups`).

## Status

Prototype of stage 1 from the design. Not yet done: a confirmation screen before
loading, per-mod counts of blocks, items and mobs, and placeholders that keep the
data of removed mods (stage 2).

## Supported versions

| Loader | Minecraft |
|---|---|
| Fabric (also runs on Quilt) | 26.3 |
| NeoForge | 26.3 |

1.21.1 is planned next.

## Building

Requires JDK 25.

```
./gradlew build
```

Jars land in `fabric/build/libs` and `neoforge/build/libs`.
`./gradlew :common:test -PcommonOnly` runs the core tests without downloading Minecraft.

## Layout

- `common/` loader-independent logic (passport, diff, backup, report), plain Java with unit tests.
- `fabric/`, `neoforge/` thin entry points that hook server start and player join and
  compile the common sources into their jar.
