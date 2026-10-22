"""
Downloads mods Worldsure is checked against, for one target such as 1.21.1:neoforge, into a folder:
    python3 .github/compat/fetch.py 1.21.1:neoforge build/compat-mods
Each mod's newest release for that Minecraft version and loader comes from Modrinth, with its required
dependencies. The jars are only used by test runs (gradlew ... -PcompatMods=<folder>); they are never
committed or shipped.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

# Modrinth projects per loader; a project without a build for the target is skipped.
MODS = {
    "fabric": ["c2me-fabric", "distanthorizons", "create-fabric"],
    "neoforge": ["c2me-neoforge", "distanthorizons", "create"],
    "forge": ["distanthorizons", "create"],
}
# Loader libraries the game run already provides.
PROVIDED = {"P7dR8mSH", "fabric-api"}
API = "https://api.modrinth.com/v2"
HEADERS = {"User-Agent": "KaiNrav/worldsure-source compat check (github.com/KaiNrav/worldsure-source)"}


def get(path, **params):
    query = urllib.parse.urlencode({k: json.dumps(v) for k, v in params.items()})
    request = urllib.request.Request(f"{API}{path}" + (f"?{query}" if query else ""), headers=HEADERS)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def newest(project, minecraft, loader):
    versions = get(f"/project/{project}/version", loaders=[loader], game_versions=[minecraft])
    releases = [v for v in versions if v["version_type"] == "release"] or versions
    return releases[0] if releases else None


def fetch(project, minecraft, loader, folder, seen):
    if project in seen or project in PROVIDED:
        return
    seen.add(project)
    version = newest(project, minecraft, loader)
    if version is None:
        print(f"  {project}: no build for {minecraft} {loader}, skipped")
        return
    file = next((f for f in version["files"] if f["primary"]), version["files"][0])
    target = os.path.join(folder, file["filename"])
    if not os.path.exists(target):
        request = urllib.request.Request(file["url"], headers=HEADERS)
        with urllib.request.urlopen(request, timeout=300) as response, open(target, "wb") as out:
            out.write(response.read())
    print(f"  {project} {version['version_number']}: {file['filename']}")
    for dependency in version.get("dependencies", []):
        if dependency["dependency_type"] == "required" and dependency.get("project_id"):
            fetch(dependency["project_id"], minecraft, loader, folder, seen)


def main():
    minecraft, loader = sys.argv[1].split(":")
    folder = sys.argv[2]
    os.makedirs(folder, exist_ok=True)
    print(f"Compat mods for {minecraft} {loader} into {folder}:")
    seen = set()
    for project in MODS[loader]:
        fetch(project, minecraft, loader, folder, seen)


if __name__ == "__main__":
    main()
