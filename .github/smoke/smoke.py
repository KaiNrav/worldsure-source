"""
End-to-end check on a real dedicated server, for one target such as 26.3:fabric:
1. start a fresh world and stop it: the passport must be written;
2. pretend a mod "fakemod" was removed: list it in the passport, turn the stone of one
   chunk into fakemod:stone and an armor stand there into fakemod:mob, the way a world looks
   after its mod is uninstalled;
3. start again, edit that chunk and save: the world must be backed up, the change reported,
   and fakemod:stone and fakemod:mob must still be on disk, counted in the passport.
"""
import glob
import json
import os
import re
import socket
import struct
import subprocess
import sys
import threading
import time

import nbt

TARGET = sys.argv[1]
MINECRAFT, LOADER = TARGET.split(":")
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RUN = os.path.join(ROOT, "versions", MINECRAFT, LOADER, "run")
GRADLE = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
WORLD = os.path.join(RUN, "world")
RCON_PORT = 25575
PASSWORD = "smoke"


def log(msg):
    print(f"[smoke:{TARGET}] {msg}", flush=True)


def prepare():
    os.makedirs(RUN, exist_ok=True)
    with open(os.path.join(RUN, "eula.txt"), "w") as f:
        f.write("eula=true\n")
    with open(os.path.join(RUN, "server.properties"), "w") as f:
        f.write("\n".join([
            "online-mode=false", "enable-rcon=true", f"rcon.password={PASSWORD}", f"rcon.port={RCON_PORT}",
            "level-name=world", "level-seed=worldsure", "spawn-protection=0", "view-distance=4",
            "simulation-distance=4", "sync-chunk-writes=true", "",
        ]))


def rcon(*commands):
    s = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=60)

    def send(req_id, kind, body):
        payload = struct.pack("<ii", req_id, kind) + body.encode() + b"\x00\x00"
        s.sendall(struct.pack("<i", len(payload)) + payload)
        (length,) = struct.unpack("<i", s.recv(4))
        data = b""
        while len(data) < length:
            data += s.recv(length - len(data))
        return data[8:-2].decode(errors="replace")

    send(1, 3, PASSWORD)
    for i, command in enumerate(commands):
        log(f"> {command}: {send(10 + i, 2, command)}")
    s.close()


def run_server(*commands):
    """Starts the server, runs commands over RCON once it is up, stops it and returns its output."""
    task = f":{TARGET}:runServer"
    proc = subprocess.Popen([GRADLE, task, f"-Ptargets={MINECRAFT}", "--console=plain"], cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, text=True)
    lines = []
    ready = threading.Event()

    def pump():
        for line in proc.stdout:
            print(line, end="", flush=True)
            lines.append(line)
            if "RCON running on" in line:
                ready.set()

    reader = threading.Thread(target=pump, daemon=True)
    reader.start()
    if not ready.wait(900):
        proc.kill()
        raise SystemExit(f"{TARGET} server did not start")
    rcon(*commands, "save-all flush", "stop")
    proc.wait(300)
    reader.join(30)
    return "".join(lines)


def overworld_region(kind="region"):
    other_dimensions = ("DIM-1", "DIM1", "the_nether", "the_end")
    candidates = [p for p in glob.glob(os.path.join(WORLD, "**", kind, "r.0.0.mca"), recursive=True)
                  if not any(d in p for d in other_dimensions)]
    candidates.sort(key=len)
    if not candidates:
        raise SystemExit(f"No overworld {kind} file found")
    log(f"{kind} file: {os.path.relpath(candidates[0], WORLD)}")
    return candidates[0]


def entity_ids(chunk):
    return [entity.value["id"].value for entity in chunk.value["Entities"].value]


def block_id(entry):
    """A palette entry is a bare id string, or {id, properties} for blocks with several states.
    A list mixing both forms is stored as compounds, with each bare string wrapped as {"": id}."""
    if entry.type == nbt.STRING:
        return entry
    tag = entry.value.get("") or entry.value.get("id") or entry.value.get("Name")
    if tag is None:
        raise SystemExit(f"Unexpected palette entry: {sorted(entry.value)}")
    return tag


def palette_entries(chunk):
    for section in chunk.value["sections"].value:
        states = section.value.get("block_states")
        if states:
            yield from states.value["palette"].value


def palette_names(chunk):
    return {block_id(entry).value for entry in palette_entries(chunk)}


def check(condition, message):
    if not condition:
        raise SystemExit(f"FAILED: {message}")
    log(f"ok: {message}")


def main():
    prepare()

    run_server("forceload add 0 0", "summon minecraft:armor_stand 2 100 2 {NoGravity:1b,Invulnerable:1b}")
    manifest_path = os.path.join(WORLD, "worldsure", "manifest.json")
    check(os.path.isfile(manifest_path), "the passport is written on the first start")
    with open(manifest_path) as f:
        manifest = json.load(f)
    check("worldsure" in manifest["mods"], "the passport lists worldsure itself")

    manifest["mods"]["fakemod"] = "1.0.0"
    with open(manifest_path, "w") as f:
        json.dump(manifest, f)
    region = overworld_region()
    chunk = nbt.read_chunk(region, 0)
    renamed = 0
    for entry in palette_entries(chunk):
        name = block_id(entry)
        if name.value == "minecraft:stone":
            name.value = "fakemod:stone"
            renamed += 1
    check(renamed > 0, f"chunk 0,0 has stone to turn into fakemod:stone ({renamed} sections)")
    nbt.write_chunk(region, 0, chunk)
    entities_file = overworld_region("entities")
    entity_chunk = nbt.read_chunk(entities_file, 0)
    for entity in entity_chunk.value["Entities"].value:
        if entity.value["id"].value == "minecraft:armor_stand":
            entity.value["id"].value = "fakemod:mob"
    check("fakemod:mob" in entity_ids(entity_chunk), f"chunk 0,0 has an armor stand to turn into fakemod:mob")
    nbt.write_chunk(entities_file, 0, entity_chunk)

    output = run_server("forceload add 0 0", "setblock 1 200 1 minecraft:glass")
    check("Removed: fakemod 1.0.0" in output, "the removed mod is reported")
    check(re.search(r"Kept \d+ section\(s\)", output), "fakemod:stone was swapped for placeholders on load")
    check(glob.glob(os.path.join(RUN, "backups", "worldsure", "world_*.zip")), "the world was backed up before loading")

    chunk = nbt.read_chunk(region, 0)
    names = palette_names(chunk)
    check("fakemod:stone" in names, "fakemod:stone is still on disk after the chunk was saved")
    check("worldsure:missing_block" not in names, "no placeholder reached the disk")
    check("minecraft:glass" in names, "the edit made while the mod was missing was saved too")
    check(re.search(r"Kept \d+ entities of removed mods", output), "fakemod:mob was set aside on load")
    ids = entity_ids(nbt.read_chunk(entities_file, 0))
    check(ids.count("fakemod:mob") == 1, f"fakemod:mob is still on disk, once, after its chunk was saved ({ids})")
    with open(manifest_path) as f:
        manifest = json.load(f)
    blocks = manifest.get("counts", {}).get("fakemod", {}).get("blocks", 0)
    check(blocks > 0, f"the passport counts fakemod blocks ({blocks})")
    mobs = manifest.get("counts", {}).get("fakemod", {}).get("entities", 0)
    check(mobs == 1, f"the passport counts the fakemod mob ({mobs})")
    check("fakemod" not in manifest["mods"], "the passport now lists the mods actually installed")
    log("all checks passed")


if __name__ == "__main__":
    main()
