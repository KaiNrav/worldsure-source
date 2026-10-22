package io.github.kainrav.worldsure.mc;

/**
 * Which stand-ins hold blocks and items of removed mods. Normally Worldsure's own block and item;
 * in server-only mode (serverOnly=true in the config) vanilla bedrock and barriers, so players can join
 * without Worldsure. Set once when the mod loads, before anything is registered.
 */
public final class Placeholders {
    public static final String VANILLA_BLOCK = "minecraft:bedrock";
    public static final String VANILLA_ITEM = "minecraft:barrier";

    private static volatile boolean serverOnly;

    private Placeholders() {
    }

    public static boolean serverOnly() {
        return serverOnly;
    }

    /** Called when the mod loads; game tests also switch it to check both kinds of stand-ins. */
    public static void setServerOnly(boolean value) {
        serverOnly = value;
    }
}
