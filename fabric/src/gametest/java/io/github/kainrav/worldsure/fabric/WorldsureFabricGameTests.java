package io.github.kainrav.worldsure.fabric;

import io.github.kainrav.worldsure.mc.EntityGameTests;
import io.github.kainrav.worldsure.mc.WorldsureGameTests;
import io.github.kainrav.worldsure.mc.ServerModeGameTests;
import io.github.kainrav.worldsure.mc.BackupGameTests;
import io.github.kainrav.worldsure.mc.ScanGameTests;
import io.github.kainrav.worldsure.mc.ItemGameTests;
import net.minecraft.gametest.framework.GameTestHelper;
//#if >=1.21.5
import net.fabricmc.fabric.api.gametest.v1.GameTest;
//#else
//$ import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
//$ import net.minecraft.gametest.framework.GameTest;
//#endif

/**
 * Hands the shared checks to Fabric's game test runner, through the fabric-gametest entrypoint.
 * Every check in {@link WorldsureGameTests#all()} needs a method here.
 */
//#if >=1.21.5
public final class WorldsureFabricGameTests {
//#else
//$ public final class WorldsureFabricGameTests implements FabricGameTest {
//#endif
    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void passportRecordsItsFormat(GameTestHelper helper) {
        WorldsureGameTests.passportRecordsItsFormat(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void placeholderCannotBeBroken(GameTestHelper helper) {
        WorldsureGameTests.placeholderCannotBeBroken(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void missingBlocksComeBack(GameTestHelper helper) {
        WorldsureGameTests.missingBlocksComeBack(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void missingEntitiesComeBack(GameTestHelper helper) {
        EntityGameTests.missingEntitiesComeBack(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void entityStorageKeepsMissingEntities(GameTestHelper helper) {
        EntityGameTests.entityStorageKeepsMissingEntities(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void placeholdersCarryTheirItems(GameTestHelper helper) {
        ItemGameTests.placeholdersCarryTheirItems(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void chestKeepsMissingItems(GameTestHelper helper) {
        ItemGameTests.chestKeepsMissingItems(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void droppedItemKeepsMissingItem(GameTestHelper helper) {
        ItemGameTests.droppedItemKeepsMissingItem(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void missingBiomesComeBack(GameTestHelper helper) {
        ScanGameTests.missingBiomesComeBack(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void scanCountsStoredChunks(GameTestHelper helper) {
        ScanGameTests.scanCountsStoredChunks(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void commandsListAndAskForRestore(GameTestHelper helper) {
        BackupGameTests.commandsListAndAskForRestore(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void serverOnlyBlocksUseBedrock(GameTestHelper helper) {
        ServerModeGameTests.serverOnlyBlocksUseBedrock(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void serverOnlyItemsUseBarriers(GameTestHelper helper) {
        ServerModeGameTests.serverOnlyItemsUseBarriers(helper);
    }

    //#if >=1.21.5
    @GameTest(maxTicks = WorldsureGameTests.MAX_TICKS)
    //#else
    //$ @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = WorldsureGameTests.MAX_TICKS)
    //#endif
    public void statusBackupAndScanCommands(GameTestHelper helper) {
        ServerModeGameTests.statusBackupAndScanCommands(helper);
    }
}
