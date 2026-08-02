package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.WorldCreationDecision
import me.awabi2048.myworldmanager.api.extension.WorldCreationGuard
import me.awabi2048.myworldmanager.api.extension.WorldCreationOperation
import me.awabi2048.myworldmanager.api.extension.WorldCreationRequest
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readText

class WorldCreationGuardTest {
    @Test
    fun `creation checks use the registered typed guard`() {
        val guard = object : WorldCreationGuard {
            override fun getId(): String = "test.world_creation"

            override fun evaluate(request: WorldCreationRequest): WorldCreationDecision =
                if (request.operation == WorldCreationOperation.PRODUCTION) {
                    WorldCreationDecision.deny()
                } else {
                    WorldCreationDecision.allow()
                }
        }
        val normal = WorldCreationRequest(null, null, WorldCreationOperation.NORMAL, null)
        val production = WorldCreationRequest(null, null, WorldCreationOperation.PRODUCTION, null)

        MyWorldManagerApi.registerWorldCreationGuard(guard)
        try {
            assertTrue(MyWorldManagerApi.checkWorldCreation(normal).allowed)
            assertFalse(MyWorldManagerApi.checkWorldCreation(production).allowed)
        } finally {
            MyWorldManagerApi.unregisterWorldCreationGuard(guard)
        }
    }

    @Test
    fun `creation guard dispatch stays a read only evaluation boundary`() {
        val source = Path.of(
            "src/main/kotlin/me/awabi2048/myworldmanager/api/MyWorldManagerApi.kt",
        ).readText()
        val body = functionBody(source, "checkWorldCreation")

        assertTrue(".map { it.evaluate(request) }" in body)
        listOf(
            "sendMessage(",
            "runTask",
            "callEvent(",
            ".save(",
            "dispatchCommand",
        ).forEach { sideEffect ->
            assertFalse(sideEffect in body, "checkWorldCreation must stay read-only: $sideEffect")
        }
    }

    private fun functionBody(source: String, name: String): String {
        val start = source.indexOf("fun $name")
        require(start >= 0) { "function not found: $name" }
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "body not found: $name" }
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(bodyStart, index + 1)
                }
            }
        }
        error("body is not closed: $name")
    }
}
