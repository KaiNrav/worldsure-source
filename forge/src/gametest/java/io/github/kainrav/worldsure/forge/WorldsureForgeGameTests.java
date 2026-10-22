package io.github.kainrav.worldsure.forge;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.EntityGameTests;
import io.github.kainrav.worldsure.mc.WorldsureGameTests;
import io.github.kainrav.worldsure.mc.ItemGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Hands the shared checks to Forge's game test server, one method per check in
 * {@link WorldsureGameTests#all()}; all of them use the empty structure worldsure:empty.
 */
@GameTestHolder(Worldsure.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldsureForgeGameTests {
    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void passportRecordsItsFormat(GameTestHelper helper) {
        WorldsureGameTests.passportRecordsItsFormat(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void placeholderCannotBeBroken(GameTestHelper helper) {
        WorldsureGameTests.placeholderCannotBeBroken(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void missingBlocksComeBack(GameTestHelper helper) {
        WorldsureGameTests.missingBlocksComeBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void missingEntitiesComeBack(GameTestHelper helper) {
        EntityGameTests.missingEntitiesComeBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void entityStorageKeepsMissingEntities(GameTestHelper helper) {
        EntityGameTests.entityStorageKeepsMissingEntities(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void placeholdersCarryTheirItems(GameTestHelper helper) {
        ItemGameTests.placeholdersCarryTheirItems(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void chestKeepsMissingItems(GameTestHelper helper) {
        ItemGameTests.chestKeepsMissingItems(helper);
    }

    @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
    public static void droppedItemKeepsMissingItem(GameTestHelper helper) {
        ItemGameTests.droppedItemKeepsMissingItem(helper);
    }
}
