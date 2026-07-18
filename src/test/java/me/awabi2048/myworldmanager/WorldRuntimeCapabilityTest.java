package me.awabi2048.myworldmanager;

import me.awabi2048.myworldmanager.api.extension.DefaultWorldRuntimePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRuntimeCapabilityTest {
    @Test
    void defaultRuntimeEnablesPointAndSlotSystems() {
        assertTrue(DefaultWorldRuntimePolicy.INSTANCE.isWorldPointEconomyEnabled());
        assertTrue(DefaultWorldRuntimePolicy.INSTANCE.isWorldSlotSystemEnabled());
    }
}
