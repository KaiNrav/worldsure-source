# Worldsure

**Insurance for your world when the mod list changes.**

Removing a mod, or loading an older version of one, makes Minecraft quietly delete everything that
mod added: blocks, chests and machines, mobs. Worldsure notices before that happens and keeps them.

## What it does

- **Remembers the mods of every world.** Each world gets a small passport listing the mods and
  versions it was last played with.
- **Asks before opening a changed world.** In single player a clear screen lists every mod that was
  removed, updated, downgraded or added, with how many blocks, chests or machines and mobs of each
  are in the world. Choose *Back up & play*, *Play without backup* or *Back*, and open the same
  screen again any time from the pause menu.
- **Backs up servers automatically.** On a dedicated server the world is zipped to
  `backups/worldsure/` before a single chunk loads. The 5 newest backups per world are kept.
- **Restores a backup in two clicks.** Pick one from the *Backups* list on the Worldsure screen, or
  use `/worldsure restore` on a server. The world it replaces is kept, never deleted.
- **Keeps the blocks of removed mods.** While a mod is missing its blocks appear as a
  *Block of a removed mod* that cannot be broken. When the chunk is saved, the original blocks and
  their contents are written back, so putting the mod back restores everything.
- **Keeps the mobs of removed mods.** Mobs and other entities of a missing mod are set aside when
  their chunk loads and written back every time it is saved, so they return with the mod.
- **Keeps the items of removed mods.** In chests, inventories, item frames and on the ground, each
  becomes an *Item of a removed mod* that carries the original and turns back into it on every save.
  Biomes of a removed mod are kept too.
- **Counts the whole world.** On the first start Worldsure counts every mod's blocks, machines, mobs
  and items in the background, so the numbers on the screen are right from the start.

Removing Worldsure itself is always safe: its placeholder blocks are never saved into the world.

## Versions

Minecraft 26.3, 26.2, 26.1.x, 1.21.11 and 1.21.1 on Fabric and NeoForge; 1.20.1 on Fabric and Forge.
On Fabric, Fabric API is required.

## Good to know

- Install it on both the server and the clients, since it adds a block.
- Settings: `config/worldsure.properties` (`autoBackup`, `keepBackups`).

Source code and issues: https://github.com/KaiNrav/worldsure-source
