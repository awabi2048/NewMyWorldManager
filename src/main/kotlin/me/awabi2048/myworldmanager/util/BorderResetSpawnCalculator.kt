package me.awabi2048.myworldmanager.util

import kotlin.math.floor

/**
 * ボーダー後退後の保存スポーン補正を、Bukkitの状態から分離して判定する。
 * 実ワールドのブロック検査は呼び出し側で済ませ、ここでは保存値と補正候補だけを扱う。
 */
data class BorderResetSpawnPosition(
    val x: Double,
    val y: Double,
    val z: Double
)

data class BorderResetBounds(
    val minX: Double,
    val maxX: Double,
    val minZ: Double,
    val maxZ: Double
) {
    fun contains(x: Double, z: Double): Boolean {
        return x in minX..maxX && z in minZ..maxZ
    }

    fun clampInside(x: Double, z: Double, margin: Double = 1.0): Pair<Double, Double>? {
        if (minX > maxX || minZ > maxZ) return null
        require(margin >= 0.0) { "border margin must not be negative" }
        val safeMinX = minX + margin
        val safeMaxX = maxX - margin
        val safeMinZ = minZ + margin
        val safeMaxZ = maxZ - margin
        if (safeMinX > safeMaxX || safeMinZ > safeMaxZ) return null
        return x.coerceIn(safeMinX, safeMaxX) to z.coerceIn(safeMinZ, safeMaxZ)
    }

    companion object {
        fun centeredAt(centerX: Double, centerZ: Double, size: Double): BorderResetBounds {
            val radius = size / 2.0
            return BorderResetBounds(
                minX = centerX - radius,
                maxX = centerX + radius,
                minZ = centerZ - radius,
                maxZ = centerZ + radius
            )
        }
    }
}

data class BorderResetSpawnChange(
    val original: BorderResetSpawnPosition,
    val replacement: BorderResetSpawnPosition
)

data class BorderResetSpawnChanges(
    val guest: BorderResetSpawnChange?,
    val member: BorderResetSpawnChange?
) {
    val hasChanges: Boolean
        get() = guest != null || member != null
}

object BorderResetSpawnCalculator {
    fun calculate(
        guest: BorderResetSpawnPosition?,
        member: BorderResetSpawnPosition?,
        defaultPosition: BorderResetSpawnPosition,
        resultingBorder: BorderResetBounds,
        safeYCoordinates: List<Int>
    ): BorderResetSpawnChanges {
        val replacementY = safeYCoordinates.firstOrNull()
        // ボーダー境界線上はプレイヤーの当たり判定が外側へ出るため、内側へ余白を確保する。
        // 補正後のスポーンはブロック中央(x.5, z.5)に揃える。入力が整数（Bukkit標準スポーン）でも
        // 中央化済み（設定・読込値）でも結果を統一するため、ブロック座標へ丸めてから中央を付与する。
        val replacement = resultingBorder.clampInside(defaultPosition.x, defaultPosition.z)?.let { (x, z) ->
            replacementY?.let { y ->
                BorderResetSpawnPosition(floor(x) + 0.5, y.toDouble(), floor(z) + 0.5)
            }
        }

        return BorderResetSpawnChanges(
            guest = changeIfOutside(guest, resultingBorder, replacement),
            member = changeIfOutside(member, resultingBorder, replacement)
        )
    }

    private fun changeIfOutside(
        saved: BorderResetSpawnPosition?,
        resultingBorder: BorderResetBounds,
        replacement: BorderResetSpawnPosition?
    ): BorderResetSpawnChange? {
        if (saved == null || resultingBorder.contains(saved.x, saved.z) || replacement == null) {
            return null
        }
        return BorderResetSpawnChange(saved, replacement)
    }
}
