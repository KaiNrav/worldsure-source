# Changelog

## 0.1.0

First public version, for Minecraft 26.3 on Fabric and NeoForge.

- Every world keeps a passport of the mods it was played with (`<world>/worldsure/manifest.json`).
- In single player, opening a world whose mods changed first shows the Worldsure screen: every
  removed, downgraded, updated and added mod as a card with how many blocks, chests or machines and
  mobs of it the world holds, an "All mods" tab, and "Back up & play", "Play without backup" and
  "Back". The same screen opens from a button in the pause menu, showing the backup that was made.
- On a dedicated server the world is backed up to `backups/worldsure/` before any chunk loads.
  The 5 newest backups per world are kept (`config/worldsure.properties`).
- Blocks of a removed mod are kept: while the mod is missing they show as an unbreakable
  "Block of a removed mod", and the original blocks and block entities are written back when the
  chunk saves. Reinstalling the mod restores them.
- Counts of blocks, block entities and entities per mod are kept in `<world>/worldsure/index.dat`.

Known limits: entities and items of a removed mod are still dropped by the game, and counts only
cover chunks saved while Worldsure was installed. Worldsure adds a block, so it must be installed on
both the server and the clients.
