package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TemplateCreationSafetySourceContractTest {

    @Test
    void templateCopyRemovesPaperIdentityAndUsesRegisteredSpawn() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldService.kt"
        ));

        assertTrue(source.contains("data/paper/metadata.dat"));
        assertTrue(source.contains("findById(templateId)"));
        assertTrue(source.contains("template.originLocation!!"));
        assertTrue(source.contains("cleanupFailedTemplateWorld"));
    }

    @Test
    void templateWizardUsesRepositorySerializationOnly() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/listener/TemplateWizardListener.kt"
        ));

        assertTrue(source.contains("templateRepository.saveTemplate"));
        assertTrue(source.contains("session.sourceWorldName"));
        assertFalse(source.contains("origin.x"));
        assertFalse(source.contains("origin.y"));
        assertFalse(source.contains("origin.z"));
    }

    @Test
    void legacyWorldKeyIsMigratedBeforeDeserialization() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/repository/WorldConfigRepository.kt"
        ));

        assertTrue(source.contains("migrateMissingWorldKey(file, uuid)"));
        assertTrue(source.contains("pre-world-key-migration.bak"));
        assertTrue(source.indexOf("migrateMissingWorldKey(file, uuid)")
            < source.indexOf("val worldData = loadWorldData(file)"));
    }
}
