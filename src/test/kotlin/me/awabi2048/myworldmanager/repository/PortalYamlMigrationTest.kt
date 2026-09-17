package me.awabi2048.myworldmanager.repository

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * 旧ポータル形式（location.world / target_world_name）の変換契約を固定します。
 * 添付の移行失敗データ（旧形式＋config_version: 1 の混在）が再発しないことを目的とします。
 */
class PortalYamlMigrationTest {
    private val noLookupUuid: (UUID) -> String? = { null }
    private val noLookupFolder: (String) -> String? = { null }

    private fun sectionWithLocation(world: String?): org.bukkit.configuration.ConfigurationSection {
        val config = YamlConfiguration()
        val section = config.createSection("portal")
        val location = section.createSection("location")
        if (world != null) location.set("world", world)
        location.set("x", 1)
        location.set("y", 2)
        location.set("z", 3)
        return section
    }

    @Test
    fun `my_world folder falls back to minecraft key when WorldData is missing`() {
        val uuid = UUID.randomUUID()
        val resolved = PortalYamlMigration.resolveLocationWorldKey(
            "my_world.$uuid",
            noLookupUuid,
            noLookupFolder,
        )
        assertEquals("minecraft:my_world.$uuid", resolved)
    }

    @Test
    fun `my_world folder prefers WorldData worldKey for customWorldName`() {
        val uuid = UUID.randomUUID()
        val resolved = PortalYamlMigration.resolveLocationWorldKey(
            "my_world.$uuid",
            findWorldKeyByUuid = { "minecraft:custom_home" },
            findWorldKeyByFolder = { null },
        )
        assertEquals("minecraft:custom_home", resolved)
    }

    @Test
    fun `vanilla folder world maps to overworld key`() {
        val resolved = PortalYamlMigration.resolveLocationWorldKey("world", noLookupUuid, noLookupFolder)
        assertEquals("minecraft:overworld", resolved)
    }

    @Test
    fun `vanilla nether and end folders map to dimension keys`() {
        assertEquals(
            "minecraft:the_nether",
            PortalYamlMigration.resolveLocationWorldKey("world_nether", noLookupUuid, noLookupFolder),
        )
        assertEquals(
            "minecraft:the_end",
            PortalYamlMigration.resolveLocationWorldKey("world_the_end", noLookupUuid, noLookupFolder),
        )
    }

    @Test
    fun `external folders fold into minecraft namespace`() {
        assertEquals("minecraft:spawn", PortalYamlMigration.resolveLocationWorldKey("spawn", noLookupUuid, noLookupFolder))
        assertEquals("minecraft:end_b_2", PortalYamlMigration.resolveLocationWorldKey("end_b_2", noLookupUuid, noLookupFolder))
        assertEquals(
            "minecraft:resource_normal.a.20260410",
            PortalYamlMigration.resolveLocationWorldKey("resource_normal.a.20260410", noLookupUuid, noLookupFolder),
        )
    }

    @Test
    fun `legacy section is migrated and old key is removed`() {
        val section = sectionWithLocation("my_world.${UUID.randomUUID()}")
        val migrated = PortalYamlMigration.migrateSection(section, noLookupUuid, noLookupFolder)
        assertTrue(migrated)
        val location = section.getConfigurationSection("location")!!
        assertTrue(location.contains("world_key"))
        assertFalse(location.contains("world"))
    }

    @Test
    fun `new section is idempotent`() {
        val config = YamlConfiguration()
        val section = config.createSection("portal")
        val location = section.createSection("location")
        location.set("world_key", "minecraft:overworld")
        location.set("x", 0)
        location.set("y", 0)
        location.set("z", 0)
        assertTrue(PortalYamlMigration.migrateSection(section, noLookupUuid, noLookupFolder))
        assertEquals("minecraft:overworld", location.getString("world_key"))
    }

    @Test
    fun `target_world_name is converted to target_world_key`() {
        val config = YamlConfiguration()
        val section = config.createSection("portal")
        val location = section.createSection("location")
        location.set("world_key", "minecraft:overworld")
        section.set("target_world_name", "spawn")
        assertTrue(PortalYamlMigration.migrateSection(section, noLookupUuid, noLookupFolder))
        assertEquals("minecraft:spawn", section.getString("target_world_key"))
        assertFalse(section.contains("target_world_name"))
    }

    @Test
    fun `needsMigration detects legacy keys even at current version`() {
        val config = YamlConfiguration()
        val portals = config.createSection("portals")
        val legacy = portals.createSection(UUID.randomUUID().toString())
        val location = legacy.createSection("location")
        location.set("world", "world")
        location.set("x", -44)
        location.set("y", -48)
        location.set("z", 37)
        legacy.set("world_uuid", UUID.randomUUID().toString())
        legacy.set("owner_uuid", UUID.randomUUID().toString())

        val current = portals.createSection(UUID.randomUUID().toString())
        val currentLocation = current.createSection("location")
        currentLocation.set("world_key", "minecraft:overworld")
        currentLocation.set("x", 0)
        currentLocation.set("y", 0)
        currentLocation.set("z", 0)
        current.set("owner_uuid", UUID.randomUUID().toString())

        // 添付データと同様に新旧混在でも旧形式を検出できる必要があります。
        assertTrue(PortalYamlMigration.needsMigration(config))
        assertTrue(PortalYamlMigration.isLegacySection(legacy))
        assertFalse(PortalYamlMigration.isLegacySection(current))
    }
}
