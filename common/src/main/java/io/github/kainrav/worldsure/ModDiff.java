package io.github.kainrav.worldsure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What changed between the mods a world was saved with and the mods installed now. */
public record ModDiff(List<ModInfo> removed, List<VersionChange> changed, List<ModInfo> added) {

    public record VersionChange(String id, String oldVersion, String newVersion) {
        public boolean isDowngrade() {
            return VersionComparator.INSTANCE.compare(newVersion, oldVersion) < 0;
        }
    }

    public static ModDiff between(List<ModInfo> saved, List<ModInfo> current) {
        Map<String, String> before = toMap(saved);
        Map<String, String> now = toMap(current);
        List<ModInfo> removed = new ArrayList<>();
        List<VersionChange> changed = new ArrayList<>();
        List<ModInfo> added = new ArrayList<>();
        before.forEach((id, version) -> {
            String nowVersion = now.get(id);
            if (nowVersion == null) {
                removed.add(new ModInfo(id, version));
            } else if (!nowVersion.equals(version)) {
                changed.add(new VersionChange(id, version, nowVersion));
            }
        });
        now.forEach((id, version) -> {
            if (!before.containsKey(id)) {
                added.add(new ModInfo(id, version));
            }
        });
        removed.sort(Comparator.comparing(ModInfo::id));
        changed.sort(Comparator.comparing(VersionChange::id));
        added.sort(Comparator.comparing(ModInfo::id));
        return new ModDiff(List.copyOf(removed), List.copyOf(changed), List.copyOf(added));
    }

    private static Map<String, String> toMap(List<ModInfo> mods) {
        Map<String, String> map = new LinkedHashMap<>();
        mods.forEach(mod -> map.put(mod.id(), mod.version()));
        return map;
    }

    public boolean isEmpty() {
        return removed.isEmpty() && changed.isEmpty() && added.isEmpty();
    }

    /** Removing or re-versioning a mod can lose data; only adding one cannot. */
    public boolean isRisky() {
        return !removed.isEmpty() || !changed.isEmpty();
    }

    public boolean hasDowngrade() {
        return changed.stream().anyMatch(VersionChange::isDowngrade);
    }
}
