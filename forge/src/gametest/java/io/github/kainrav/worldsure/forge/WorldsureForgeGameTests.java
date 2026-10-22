package io.github.kainrav.worldsure.forge;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.WorldsureGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Hands the shared checks to Forge's game test server; all of them use the empty structure worldsure:empty. */
@GameTestHolder(Worldsure.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldsureForgeGameTests {
    @GameTest(template = "empty")
    public static void passportRecordsItsFormat(GameTestHelper helper) {
        WorldsureGameTests.passportRecordsItsFormat(helper);
    }

    @GameTest(template = "empty")
    public static void placeholderCannotBeBroken(GameTestHelper helper) {
        WorldsureGameTests.placeholderCannotBeBroken(helper);
    }

    @GameTest(template = "empty")
    public static void missingBlocksComeBack(GameTestHelper helper) {
        WorldsureGameTests.missingBlocksComeBack(helper);
    }
}
