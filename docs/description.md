# Worldsure

**Insurance for your world when the mod list changes.**

Removing a mod, or loading an older version of one, makes Minecraft quietly delete everything that
mod added: blocks, chests and machines, mobs. Worldsure notices before that happens.

## What it does

- **Remembers the mods of every world.** Each world gets a small passport listing the mods and
  versions it was last played with.
- **Asks before opening a changed world.** In single player you see what was removed, updated or
  downgraded, and how many blocks, chests or machines and mobs of each removed mod are in the world.
  Choose *Back up and play*, *Play without backup* or *Cancel*.
- **Backs up servers automatically.** On a dedicated server the world is zipped to
  `backups/worldsure/` before a single chunk loads. The 5 newest backups per world are kept.
- **Keeps the blocks of removed mods.** While a mod is missing its blocks appear as a
  *Block of a removed mod* that cannot be broken. When the chunk is saved, the original blocks and
  their contents are written back, so putting the mod back restores everything.

Removing Worldsure itself is always safe: its placeholder blocks are never saved into the world.

## Good to know

- Install it on both the server and the clients, since it adds a block.
- Mobs and items of a removed mod are not kept yet. Make a backup before removing mods that add them.
- Counts only cover chunks saved while Worldsure was installed.
- Settings: `config/worldsure.properties` (`autoBackup`, `keepBackups`).

Source code and issues: https://github.com/KaiNrav/worldsure
