package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PortalRepositoryPersistenceSourceContractTest {
    private static final Path SOURCE = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/repository/PortalRepository.kt"
    );

    @Test
    void loadingKeepsTemporarilyUnresolvedWorldReferencesWithoutRewritingTheFile() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertTrue(source.contains("worldUuid = worldUuid"));
        assertTrue(source.contains("読み込みだけで永続ファイルを書き換えない"));
        assertFalse(source.contains("行き先設定を解除しました"));
        assertFalse(source.contains("ポータルを削除しました"));

        int loadStart = source.indexOf("fun loadAll()");
        int saveStart = source.indexOf("fun saveAll()", loadStart);
        String loadBody = source.substring(loadStart, saveStart);
        assertFalse(loadBody.contains("saveAll()"));
    }
}
