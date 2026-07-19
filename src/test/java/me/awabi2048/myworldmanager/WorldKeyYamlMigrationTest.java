package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import me.awabi2048.myworldmanager.repository.WorldKeyYamlMigration;
import org.junit.jupiter.api.Test;

class WorldKeyYamlMigrationTest {

    private static final UUID UUID_VALUE =
        UUID.fromString("19e63979-93be-400a-a05f-d31a4645011d");

    @Test
    void addsWorldKeyFromCustomWorldName() {
        List<String> migrated = WorldKeyYamlMigration.INSTANCE.migrate(
            List.of(
                "world_data:",
                "  ==: me.awabi2048.myworldmanager.model.WorldData",
                "  uuid: " + UUID_VALUE,
                "  custom_world_name: my_world." + UUID_VALUE,
                "  gravity_value: 0.08"
            ),
            UUID_VALUE
        );

        assertTrue(migrated.contains("  world_key: minecraft:my_world." + UUID_VALUE));
        assertEquals(
            migrated.indexOf("  custom_world_name: my_world." + UUID_VALUE) + 1,
            migrated.indexOf("  world_key: minecraft:my_world." + UUID_VALUE)
        );
    }

    @Test
    void leavesAlreadyMigratedDataUntouched() {
        List<String> migrated = WorldKeyYamlMigration.INSTANCE.migrate(
            List.of(
                "world_data:",
                "  uuid: " + UUID_VALUE,
                "  world_key: minecraft:my_world." + UUID_VALUE
            ),
            UUID_VALUE
        );

        assertNull(migrated);
    }
}
