# Changelog

## 1.0.0

The first stable version. On all twelve builds (Minecraft 26.3, 26.2, 26.1.x, 1.21.11 and 1.21.1 on
Fabric and NeoForge, 1.20.1 on Fabric and Forge), removing or downgrading a mod no longer costs the
world anything:

- its blocks, chests and machines, mobs, items and biomes are kept while it is missing and come back
  with it, and the files on disk never hold a placeholder, so removing Worldsure itself stays safe;
- the world is backed up before it loads with the change, and any backup can be restored from the
  Worldsure screen or with `/worldsure restore`, keeping the world it replaces;
- the Worldsure screen counts what each mod has in the world, from the first start on.

Servers get server-only mode, strict mode and `/worldsure status`, `backups`, `backup`, `restore` and
`scan`. The file formats are frozen for 1.x (`docs/formats.md`). Every build is checked by game tests in
a real game test server and by a real dedicated server, also next to C2ME, Distant Horizons and Create.

Nothing changed in the code since 0.9.0; this version moves Worldsure from beta to release.

## 0.9.0

- The file formats are frozen for 1.x and written down in `docs/formats.md`: the passport (format 1),
  the object counts (format 3, reading 1 and 2), the settings, backups, restore requests and the strict
  mode acceptance. One file of each format, made by a separate script, is part of the tests, so a
  version that stops reading any of them cannot be released.
- A world whose passport was written by a newer Worldsure no longer shows as a first visit in the
  pause menu.

## 0.8.0

- Load tests. A game test sends 256 chunks holding blocks, chests and biomes of a removed mod through
  Worldsure from 8 threads at once, the way chunk saves run, three saves each: nothing is lost and no
  kept data lingers afterwards (about 0.2 to 0.4 s in all). The server test now also turns the stone
  of all 256 chunks at the origin into a removed mod's block, loads them, changes every one and checks
  that each was saved with its blocks.
- Biomes of a removed mod are let go from memory once no chunk spot holds their stand-in any more,
  instead of being kept until the world closes.
- `/worldsure status` and the log, when a world closes, show how much time Worldsure spent on chunk,
  entity and item data in the session.

## 0.7.0

- The world list marks worlds whose mods changed since they were last played: their info line
  gets "⛨ Mods changed: N".
- A settings screen, opened with the ⚙ button on the Worldsure screen and, on NeoForge and Forge,
  with Config in the mod list: automatic backups, how many to keep, server-only placeholders and
  strict mode, saved at once.
- Translations: German, Spanish, French, Brazilian Portuguese, Ukrainian, Polish, Simplified
  Chinese and Japanese, next to English and Russian.
- Checked next to C2ME, Distant Horizons and Create (see Compatibility in the README): a real
  server with them keeps a removed mod's blocks, mobs, items and biomes, restores backups and
  honours strict mode; Worldsure's game tests pass with C2ME and Create loaded.

## 0.6.0

- **Server-only mode** (`serverOnly=true` in `config/worldsure.properties`): Worldsure registers no
  block or item of its own, so players can join without it. Blocks of removed mods are then kept as
  bedrock and items as barriers named after the item; on save only the spots that held a missing
  block get it back, so real bedrock and bedrock players place stay bedrock.
- **Strict mode** (`strict=true`) for dedicated servers: when a mod was removed or downgraded, the
  server refuses to start and says which mods and how to go on: put them back, or create
  `<world>/worldsure/accept-changes` to start once anyway (after the usual backup).
- New commands for game masters: `/worldsure status` (changes at start, what the world holds per
  mod, settings, backups), `/worldsure backup` (save and back up now) and `/worldsure scan` (count
  the whole world again).
- Settings added by a new version are now written into an existing `worldsure.properties`, with
  their explanation.
- Kept items no longer stack even when two kept stacks are identical.

## 0.5.0

- Backups can be restored from the game. The Worldsure screen has a **Backups** button, both
  before a changed world opens and from the pause menu, listing the world's automatic backups with
  their time and size. Before the world opens, the picked backup is restored at once; while the
  world is open, it is restored the next time the world is opened.
- On a server, `/worldsure backups` lists the backups (game masters) and `/worldsure restore <file>`
  (owners) stops the server and restores the backup when it starts again.
- A restore never deletes anything: the world it replaces is renamed to
  `<world>_before_restore_<time>` next to it, and shows up in the world list in single player. A
  backup that cannot be read leaves the world as it was.

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
