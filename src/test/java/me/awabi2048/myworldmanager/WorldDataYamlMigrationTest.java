package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import me.awabi2048.myworldmanager.repository.WorldDataYamlMigration;
import org.junit.jupiter.api.Test;

class WorldDataYamlMigrationTest {

    private static final UUID UUID_VALUE =
        UUID.fromString("19e63979-93be-400a-a05f-d31a4645011d");

    @Test
    void addsWorldKeyFromCustomWorldName() {
        List<String> migrated = WorldDataYamlMigration.INSTANCE.migrate(
            List.of(
                "world_data:",
                "  ==: me.awabi2048.myworldmanager.model.WorldData",
                "  uuid: " + UUID_VALUE,
                "  dimension: OVERWORLD",
                "  custom_world_name: my_world." + UUID_VALUE,
                "  gravity_value: 0.08"
            ),
            UUID_VALUE,
            null
        );

        assertTrue(migrated.contains("  world_key: minecraft:my_world." + UUID_VALUE));
        assertEquals(
            migrated.indexOf("  custom_world_name: my_world." + UUID_VALUE) + 1,
            migrated.indexOf("  world_key: minecraft:my_world." + UUID_VALUE)
        );
    }

    @Test
    void leavesAlreadyMigratedDataUntouched() {
        List<String> migrated = WorldDataYamlMigration.INSTANCE.migrate(
            List.of(
                "world_data:",
                "  uuid: " + UUID_VALUE,
                "  world_key: minecraft:my_world." + UUID_VALUE
            ),
            UUID_VALUE,
            null
        );

        assertNull(migrated);
    }

    @Test
    void addsDimensionOnlyWhenExplicitlyProvided() {
        List<String> migrated = WorldDataYamlMigration.INSTANCE.migrate(
            List.of(
                "world_data:",
                "  uuid: " + UUID_VALUE,
                "  world_key: minecraft:my_world." + UUID_VALUE
            ),
            UUID_VALUE,
            "NETHER"
        );

        assertTrue(migrated.contains("  dimension: NETHER"));
    }
}
