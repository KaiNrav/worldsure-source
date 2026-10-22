package io.github.kainrav.worldsure.neoforge;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.WorldsureGameTests;
//#if >=1.21.5
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
//#else
//$ import io.github.kainrav.worldsure.mc.EntityGameTests;
//$ import io.github.kainrav.worldsure.mc.ItemGameTests;
//$ import net.minecraft.gametest.framework.GameTest;
//$ import net.minecraft.gametest.framework.GameTestHelper;
//$ import net.neoforged.neoforge.gametest.GameTestHolder;
//$ import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
//#endif

/**
 * Hands the shared checks to NeoForge's game test server. Since 1.21.5 tests are registry entries,
 * registered from {@link WorldsureGameTests#all()}; before that they are annotated methods, one per
 * check. All of them use the empty structure worldsure:empty.
 */
//#if >=1.21.5
@EventBusSubscriber(modid = Worldsure.MOD_ID)
public final class WorldsureNeoForgeGameTests {
    private static final Identifier EMPTY = id("empty");

    private WorldsureNeoForgeGameTests() {
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Worldsure.MOD_ID, name);
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper ->
                WorldsureGameTests.all().forEach((name, test) -> helper.register(id(name), test)));
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"));
        for (String name : WorldsureGameTests.all().keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                    new TestData<>(environment, EMPTY, WorldsureGameTests.MAX_TICKS, 0, true)));
        }
    }
}
//#else
//$ @GameTestHolder(Worldsure.MOD_ID)
//$ @PrefixGameTestTemplate(false)
//$ public final class WorldsureNeoForgeGameTests {
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void passportRecordsItsFormat(GameTestHelper helper) {
//$         WorldsureGameTests.passportRecordsItsFormat(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void placeholderCannotBeBroken(GameTestHelper helper) {
//$         WorldsureGameTests.placeholderCannotBeBroken(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void missingBlocksComeBack(GameTestHelper helper) {
//$         WorldsureGameTests.missingBlocksComeBack(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void missingEntitiesComeBack(GameTestHelper helper) {
//$         EntityGameTests.missingEntitiesComeBack(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void entityStorageKeepsMissingEntities(GameTestHelper helper) {
//$         EntityGameTests.entityStorageKeepsMissingEntities(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void placeholdersCarryTheirItems(GameTestHelper helper) {
//$         ItemGameTests.placeholdersCarryTheirItems(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void chestKeepsMissingItems(GameTestHelper helper) {
//$         ItemGameTests.chestKeepsMissingItems(helper);
//$     }
//$
//$     @GameTest(template = "empty", timeoutTicks = WorldsureGameTests.MAX_TICKS)
//$     public static void droppedItemKeepsMissingItem(GameTestHelper helper) {
//$         ItemGameTests.droppedItemKeepsMissingItem(helper);
//$     }
//$ }
//#endif
