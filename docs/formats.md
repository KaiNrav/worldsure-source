# Worldsure file formats

Everything Worldsure writes, and how it changes. From 0.9.0 these formats are frozen for all of 1.x:
every version reads every format listed here, a change gets a new format number and an upgrade from
the old one, and nothing here is ever reinterpreted. The test resources in `common/src/test/resources/formats/`
hold one file of each format, and the build fails if a version stops reading them.

## World passport: `<world>/worldsure/manifest.json`

UTF-8 JSON, written through a temporary file and an atomic move.

| Key | Type | Meaning |
|---|---|---|
| `format` | integer | `1`. A passport without it is read as format 1. |
| `loader` | string | `fabric`, `neoforge` or `forge`. |
| `savedAt` | string | When it was written, ISO-8601 with offset, e.g. `2026-10-22T13:05:00+02:00`. |
| `mods` | object | Mod id → version of every mod the world last ran with, sorted by id. |
| `counts` | object | Namespace → `{blocks, blockEntities, entities, items}`, the totals of the count index. |

`format` and `mods` keep their meaning in every format, so any version can compare a newer passport with
the installed mods. Keys a reader does not know are ignored. A passport whose `format` is higher than the
reader knows is compared but never written over. `counts.*.items` came in 0.3.0 within format 1; a passport
without it reads as 0.

## Object counts: `<world>/worldsure/index.dat`

Gzip-compressed, big-endian (`DataOutputStream`): strings are modified UTF-8 with a 2-byte length.

```
int     format                      3
int     dimensions
  repeat dimensions:
  UTF     dimension id              e.g. minecraft:overworld
  int     chunks
    repeat chunks:
    long    chunk key               x in the low 32 bits, z in the high 32 bits
    int     block entries
      repeat: UTF namespace, long blocks, long block entities, long items
    int     entity entries
      repeat: UTF namespace, long entities, long items
int     scanned dimensions          (format 3)
  repeat: UTF dimension id          dimensions the background count has read completely
```

Format 1 (0.1.x–0.2.x) has no `items` longs and no scanned dimensions; format 2 (0.3.x) has the items
but no scanned dimensions. Both are read, counted as no items and nothing scanned, and written back as
format 3. Vanilla (`minecraft`) objects are never stored. A file of a higher format is left untouched and
the session's counts are not saved.

## Settings: `config/worldsure.properties`

Java properties, UTF-8. Keys a version adds are appended to an existing file with their explanation.

| Key | Default | Since |
|---|---|---|
| `autoBackup` | `true` | 0.1.0 |
| `keepBackups` | `5` | 0.1.0 |
| `serverOnly` | `false` | 0.6.0 |
| `strict` | `false` | 0.6.0 |

## Backups: `<game>/backups/worldsure/<world>_<yyyy-MM-dd_HH-mm-ss>.zip`

A zip of the world folder, every entry under `<world>/`, without `session.lock`. Only names of exactly this
shape are listed and pruned; anything else in the folder is left alone. A partial zip is written as
`.zip.part` and moved into place when complete.

## Restore asked for: `<game>/backups/worldsure/restore-pending.properties`

Java properties with `world` (the world folder name) and `backup` (a file name in the same folder). Used up
the next time that world is about to load; the replaced world becomes `<world>_before_restore_<time>`.

## Strict mode acceptance: `<world>/worldsure/accept-changes`

An empty file. Its presence lets a strict server start once with removed or downgraded mods; it is deleted
when used.

## Placeholders (never on disk)

Placeholders live only in the running game and in network traffic; saves write the original back.

- Blocks: `worldsure:missing_block`, or `minecraft:bedrock` in server-only mode, only where the chunk on
  disk held a block whose id the game does not know.
- Items: one `worldsure:missing_item`, or `minecraft:barrier` in server-only mode, whose custom data holds
  `worldsure_original` (the saved stack, its slot replaced by the placeholder's) and `worldsure_id` (a random
  id, so two placeholders never stack).
- Entities and biomes have no placeholder: entities wait in memory, biomes load as `minecraft:plains`.

If a placeholder item ever reaches a file by a path Worldsure does not cover, it still carries the original
and turns back into it when read while the item's mod is installed.
