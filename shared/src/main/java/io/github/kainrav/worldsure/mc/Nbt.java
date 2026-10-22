package io.github.kainrav.worldsure.mc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * The few NBT reads Worldsure makes. Since 1.21.5 the getters return {@link Optional} or take a
 * default; before that they returned an empty value for a missing key.
 */
final class Nbt {
    private Nbt() {
    }

    static Optional<String> string(CompoundTag tag, String key) {
        //#if >=1.21.5
        return tag.getString(key);
        //#else
        //$ return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
        //#endif
    }

    static String stringOrEmpty(CompoundTag tag, String key) {
        return string(tag, key).orElse("");
    }

    static String value(StringTag tag) {
        //#if >=1.21.5
        return tag.value();
        //#else
        //$ return tag.getAsString();
        //#endif
    }

    static int intOr(CompoundTag tag, String key, int fallback) {
        //#if >=1.21.5
        return tag.getIntOr(key, fallback);
        //#else
        //$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
        //#endif
    }

    static byte byteOr(CompoundTag tag, String key, byte fallback) {
        //#if >=1.21.5
        return tag.getByteOr(key, fallback);
        //#else
        //$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getByte(key) : fallback;
        //#endif
    }

    /** The list under a key, empty when there is none. Before 26.3 every list read here holds compounds. */
    static ListTag list(CompoundTag tag, String key) {
        //#if >=1.21.5
        return tag.getListOrEmpty(key);
        //#else
        //$ return tag.getList(key, Tag.TAG_COMPOUND);
        //#endif
    }

    /** A list of strings under a key, empty when there is none. */
    static ListTag stringList(CompoundTag tag, String key) {
        //#if >=1.21.5
        return tag.getListOrEmpty(key);
        //#else
        //$ return tag.getList(key, Tag.TAG_STRING);
        //#endif
    }

    static Optional<CompoundTag> compound(CompoundTag tag, String key) {
        //#if >=1.21.5
        return tag.getCompound(key);
        //#else
        //$ return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(tag.getCompound(key)) : Optional.empty();
        //#endif
    }

    static CompoundTag compoundAt(ListTag list, int index) {
        //#if >=1.21.5
        return list.getCompoundOrEmpty(index);
        //#else
        //$ return list.getCompound(index);
        //#endif
    }

    static Stream<CompoundTag> compounds(ListTag list) {
        //#if >=1.21.5
        return list.compoundStream();
        //#else
        //$ return list.stream().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
        //#endif
    }

    static Optional<long[]> longArray(CompoundTag tag, String key) {
        //#if >=1.21.5
        return tag.getLongArray(key);
        //#else
        //$ return tag.contains(key, Tag.TAG_LONG_ARRAY) ? Optional.of(tag.getLongArray(key)) : Optional.empty();
        //#endif
    }
}
