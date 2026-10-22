# Changelog

## 0.2.0

- Mobs and other entities of a removed mod are kept. The game would skip them when their chunk
  loads and forget them at the next save; Worldsure sets them aside instead and writes them back
  every time the chunk is saved, so putting the mod back brings them back as they were.
- A mob of a removed mod riding a known one (or the other way round) is kept too. A removed mod's
  rider of a known mob comes back standing where it was rather than riding.
- Entity counts per mod now include the entities set aside.
- Optimize World and other tools reading the entity files see them untouched.

## 0.1.1

- The world passport and the object counts now check the format number they are stored with, so
  later versions can upgrade them. Older passports without a number are read as format 1.
- A world last played with a newer Worldsure keeps its passport and counts exactly as they are, so
  going back to that version loses nothing. Its mod list is still compared and the world is backed
  up before it loads.
- Issue templates for bugs, lost blocks, mobs or items, and ideas.
- Game tests on every Minecraft version and loader check the passport, the placeholder block and
  the round trip of a removed mod's blocks inside a real game test server.

## 0.1.0

First public version, for Minecraft 26.3, 26.2, 26.1.x, 1.21.11 and 1.21.1 on Fabric and NeoForge,
and 1.20.1 on Fabric and Forge.

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
