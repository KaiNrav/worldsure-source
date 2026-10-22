# Changelog

## 0.4.0

- The counts on the Worldsure screen now cover the whole world from the first start: in the
  background, Worldsure reads every chunk and entity file once and counts the blocks, chests or
  machines, mobs and items of each mod, also in places nobody visited since it was installed. Each
  dimension is read only once; after that every save keeps the counts current. The game is never
  held up: the count runs on its own thread, leaves the disk to the game between files, and a
  dimension it could not finish is counted again on the next start.
- Biomes of a removed mod are kept. While the mod is missing they load as plains; when the chunk is
  saved every spot still holding the stand-in gets its original biome back, and a spot changed in
  the meantime (with /fillbiome, for example) keeps the change.
- The object counts file (`index.dat`) moves to format 3, which remembers the dimensions already
  counted; formats 1 and 2 are read and rewritten, and such a world is counted once.

## 0.3.0

- Items of a removed mod are kept: in chests and machines, in player inventories and ender chests,
  in item frames, on the ground, carried by mobs, and inside shulker boxes and bundles. While the mod
  is missing each such stack is an *Item of a removed mod* that carries the original stack; its
  tooltip names the item and how many. It can be moved, stored and dropped like any item, and every
  save writes the original stack back wherever it now is, so the files never hold a placeholder.
  Putting the mod back turns them into the real items again.
- The passport and the Worldsure screen now count items per mod too. The object counts file
  (`index.dat`) moves to format 2; format 1 files are read and rewritten.
- A placeholder item does not stack, so two kept stacks can never merge and lose one of them.

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
