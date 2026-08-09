package me.awabi2048.myworldmanager.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class WorldSeedItemModelContractTest {
    @Test
    fun `world seed uses the vanilla beetroot seeds item model without custom model data`() {
        val source = Path.of("src/main/kotlin/me/awabi2048/myworldmanager/util/CustomItem.kt").toFile().readText()
        val worldSeedBlock = source.substringAfter("WORLD_SEED(\"world_seed\") {").substringBefore("TOUR_SIGN(\"tour_sign\")")

        assertTrue(worldSeedBlock.contains("NamespacedKey.minecraft(\"beetroot_seeds\")"))
        assertFalse(worldSeedBlock.contains("NamespacedKey(\"kota_server\", \"mwm_misc\")"))
        assertFalse(worldSeedBlock.contains("DataComponentTypes.CUSTOM_MODEL_DATA"))
    }
}
