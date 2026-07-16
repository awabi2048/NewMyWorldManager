package me.awabi2048.myworldmanager.util

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.extension.WorldCreationDecision
import me.awabi2048.myworldmanager.api.extension.WorldCreationGuard
import me.awabi2048.myworldmanager.api.extension.WorldCreationOperation
import me.awabi2048.myworldmanager.api.extension.WorldCreationRequest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
