package me.awabi2048.myworldmanager.migration

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 移行状態の残骸昇格（reconcile）判定を固定します。
 * アーカイブ済み・削除済みなどで物理ディレクトリが存在しない未完エントリだけを COMPLETED へ昇格し、
 * 実在ディレクトリや手動修復が必要な状態・metadata隔離中を昇格で隠蔽しないことを契約とします。
 */
class MigrationStaleStateReconciliationTest {
    @Test
    fun `promotes missing directory with resolved metadata`() {
        assertTrue(
            isReconcilableStaleState(
                status = MigrationWorldStatus.FAILED,
                directoryState = WorldDirectoryState.MISSING,
                quarantined = false,
            )
        )
    }

    @Test
    fun `promotes waiting and retry like failed`() {
        assertTrue(
            isReconcilableStaleState(MigrationWorldStatus.WAITING, WorldDirectoryState.MISSING, quarantined = false)
        )
        assertTrue(
            isReconcilableStaleState(MigrationWorldStatus.RETRY, WorldDirectoryState.MISSING, quarantined = false)
        )
    }

    @Test
    fun `does not promote completed or running`() {
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.COMPLETED, WorldDirectoryState.MISSING, quarantined = false)
        )
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.RUNNING, WorldDirectoryState.MISSING, quarantined = false)
        )
    }

    @Test
    fun `does not promote legacy or current because migration target exists`() {
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.FAILED, WorldDirectoryState.LEGACY, quarantined = false)
        )
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.FAILED, WorldDirectoryState.CURRENT, quarantined = false)
        )
    }

    @Test
    fun `does not promote conflict or unsafe because manual repair is required`() {
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.FAILED, WorldDirectoryState.CONFLICT, quarantined = false)
        )
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.FAILED, WorldDirectoryState.UNSAFE, quarantined = false)
        )
    }

    @Test
    fun `does not promote while metadata is quarantined`() {
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.FAILED, WorldDirectoryState.MISSING, quarantined = true)
        )
    }

    @Test
    fun `does not promote when directory cannot be inspected`() {
        assertFalse(
            isReconcilableStaleState(MigrationWorldStatus.FAILED, directoryState = null, quarantined = false)
        )
    }
}
