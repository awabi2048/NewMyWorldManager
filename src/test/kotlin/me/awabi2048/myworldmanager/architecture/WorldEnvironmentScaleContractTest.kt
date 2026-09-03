package me.awabi2048.myworldmanager.architecture

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 環境scaleがプレイヤー専用であり、他システムのbaseValueを壊さないことを検証します。 */
class WorldEnvironmentScaleContractTest {
    private val source = Path.of(
        "src/main/kotlin/me/awabi2048/myworldmanager/service/WorldEnvironmentService.kt",
    ).readText()

    @Test
    fun `エンティティ単位の環境適用はscaleだけPlayerへ適用する`() {
        val function = functionBody(source, "fun applyAttributes(entity: Entity, worldName: String)")

        assertTrue("if (entity is Player)" in function)
        assertTrue("applyScale(entity, worldData.fixedScale)" in function)
        assertTrue("clearScaleModifier(entity)" in function)
    }

    @Test
    fun `ワールド単位の環境適用は非プレイヤーのscaleを掃除だけに留める`() {
        val function = functionBody(source, "private fun applyAttributes(world: World, worldData: WorldData)")

        assertTrue("if (entity is Player)" in function)
        assertTrue("applyScale(entity, worldData.fixedScale)" in function)
        assertTrue("clearScaleModifier(entity)" in function)
    }

    @Test
    fun `環境modifierは属性のbaseValueを上書きしない`() {
        val gravity = functionBody(source, "private fun applyGravity(entity: LivingEntity, gravity: Double?)")
        val scale = functionBody(source, "private fun applyScale(entity: LivingEntity, scale: Double?)")

        assertFalse(Regex("baseValue\\s*=").containsMatchIn(gravity))
        assertFalse(Regex("baseValue\\s*=").containsMatchIn(scale))
        assertTrue("AttributeModifier.Operation.ADD_SCALAR" in scale)
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "$signature が見つかりません" }
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "$signature の本体が見つかりません" }

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
        error("$signature の本体が閉じられていません")
    }
}
