package me.awabi2048.myworldmanager.migration

import com.awabi2048.ccsystem.core.world.WorldDirectoryServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class WorldDirectoryResolverTest {
    @Test
    fun `classifies legacy only as legacy`() = withTempRoot { root ->
        val folder = "my_world.${UUID.randomUUID()}"
        Files.createDirectories(root.resolve(folder))

        val result = resolver(root).inspect(folder)

        assertEquals(WorldDirectoryState.LEGACY, result?.state)
        assertEquals(root.resolve(folder), result?.existingPath)
    }

    @Test
    fun `classifies current only as current`() = withTempRoot { root ->
        val folder = "my_world.${UUID.randomUUID()}"
        val current = root.resolve("world/dimensions/minecraft/$folder")
        Files.createDirectories(current)

        val result = resolver(root).inspect(folder)

        assertEquals(WorldDirectoryState.CURRENT, result?.state)
        assertEquals(current, result?.existingPath)
    }

    @Test
    fun `classifies absent directory as missing`() = withTempRoot { root ->
        val result = resolver(root).inspect("my_world.${UUID.randomUUID()}")

        assertEquals(WorldDirectoryState.MISSING, result?.state)
        assertNull(result?.existingPath)
    }

    @Test
    fun `classifies both directories as conflict without selecting either`() = withTempRoot { root ->
        val folder = "my_world.${UUID.randomUUID()}"
        Files.createDirectories(root.resolve(folder))
        Files.createDirectories(root.resolve("world/dimensions/minecraft/$folder"))

        val result = resolver(root).inspect(folder)

        assertEquals(WorldDirectoryState.CONFLICT, result?.state)
        assertEquals(root.resolve(folder), result?.legacyPath)
        assertEquals(root.resolve("world/dimensions/minecraft/$folder"), result?.currentPath)
        assertNull(result?.existingPath)
        assertTrue(resolver(root).findLegacyWorlds().isEmpty())
    }

    @Test
    fun `rejects traversal and malformed uuid folders`() = withTempRoot { root ->
        val resolver = resolver(root)

        assertNull(resolver.inspect("../outside"))
        assertNull(resolver.inspect("nested/my_world.${UUID.randomUUID()}"))
        assertNull(resolver.inspect("my_world.not-a-uuid"))
        assertFalse(WorldDirectoryResolver.parseWorldUuid("my_world.${UUID.randomUUID()}-extra") != null)
    }

    @Test
    fun `does not accept symbolic links`() = withTempRoot { root ->
        val uuid = UUID.randomUUID()
        val target = root.resolve("target").also { Files.createDirectories(it) }
        val link = root.resolve("my_world.$uuid")
        runCatching { Files.createSymbolicLink(link, target) }.getOrElse { return@withTempRoot }

        val result = resolver(root).inspect(link.fileName.toString())

        assertNotNull(result)
        assertEquals(WorldDirectoryState.MISSING, result?.state)
        assertNull(result?.existingPath)
        assertTrue(resolver(root).findLegacyWorlds().isEmpty())
    }

    private fun withTempRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("mwm-world-migration-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun resolver(root: Path): WorldDirectoryResolver =
        WorldDirectoryResolver(WorldDirectoryServiceImpl(root, "world"))
}
