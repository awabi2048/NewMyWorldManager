package me.awabi2048.myworldmanager.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MigrationExecuteOptionsTest {
    @Test
    fun `force and confirmation are order independent`() {
        assertEquals(MigrationExecuteOptions(force = true, confirmed = true),
            MigrationExecuteOptions.parse(listOf("--force", "confirm")))
        assertEquals(MigrationExecuteOptions(force = true, confirmed = true),
            MigrationExecuteOptions.parse(listOf("CONFIRM", "--FORCE")))
    }

    @Test
    fun `normal execution remains available without force`() {
        assertEquals(MigrationExecuteOptions(force = false, confirmed = false),
            MigrationExecuteOptions.parse(emptyList()))
        assertEquals(MigrationExecuteOptions(force = false, confirmed = true),
            MigrationExecuteOptions.parse(listOf("confirm")))
    }

    @Test
    fun `unknown and duplicate options are rejected`() {
        assertNull(MigrationExecuteOptions.parse(listOf("--dimension", "NETHER")))
        assertNull(MigrationExecuteOptions.parse(listOf("--force", "--force")))
        assertNull(MigrationExecuteOptions.parse(listOf("confirm", "confirm")))
    }
}
