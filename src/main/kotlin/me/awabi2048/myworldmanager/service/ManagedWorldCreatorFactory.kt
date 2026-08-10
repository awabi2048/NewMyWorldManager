package me.awabi2048.myworldmanager.service

import me.awabi2048.myworldmanager.model.ManagedDimension
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldCreator

/** 管理ワールドを誤った次元で開く経路を作らないための唯一のWorldCreator生成境界です。 */
class ManagedWorldCreatorFactory(
    private val endDragonPolicy: PaperEndDragonPolicyAdapter
) {
    fun create(key: NamespacedKey, dimension: ManagedDimension): WorldCreator {
        endDragonPolicy.prepareBeforeLoad(key, dimension)
        return WorldCreator(key).environment(dimension.bukkitEnvironment)
    }

    fun requireMatchingDimension(world: World, expected: ManagedDimension) {
        val actual = ManagedDimension.fromBukkit(world.environment)
        check(actual == expected) {
            "Managed world dimension mismatch: key=${world.key} expected=$expected actual=$actual"
        }
    }
}
