package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldCreationPermissionSourceContractTest {

    @Test
    void selfCreationUsesDedicatedPermissionAcrossJavaAndBedrock() throws Exception {
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String permissionManager = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/util/PermissionManager.kt"
        ));
        String creationGui = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/gui/CreationGui.kt"
        ));
        String bedrock = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/ui/bedrock/BedrockMenuService.kt"
        ));

        assertTrue(pluginYml.contains("myworldmanager.world.create:"));
        assertTrue(permissionManager.contains("WORLD_CREATE = \"myworldmanager.world.create\""));
        assertTrue(creationGui.contains("checkSelfCreatePermission(player"));
        assertTrue(bedrock.contains("PermissionManager.WORLD_CREATE"));
    }

    @Test
    void enteringMyWorldDoesNotDeleteShulkers() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/integration/WorldPermissionPolicyService.kt"
        ));

        assertFalse(source.contains("getEntitiesByClass(org.bukkit.entity.Shulker::class.java)"));
    }
}
