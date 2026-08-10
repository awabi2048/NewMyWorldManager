package me.awabi2048.myworldmanager.model

import org.bukkit.World

/**
 * 管理ワールドの生成・再ロードを通して変化しない次元です。
 *
 * BukkitのEnvironmentを直接永続化すると、将来追加される値まで保存形式へ漏れるため、
 * MyWorldが正式に扱う3種類だけをドメイン値として固定します。
 */
enum class ManagedDimension(val bukkitEnvironment: World.Environment) {
    OVERWORLD(World.Environment.NORMAL),
    NETHER(World.Environment.NETHER),
    END(World.Environment.THE_END);

    companion object {
        fun fromBukkit(environment: World.Environment): ManagedDimension = when (environment) {
            World.Environment.NORMAL -> OVERWORLD
            World.Environment.NETHER -> NETHER
            World.Environment.THE_END -> END
            else -> throw IllegalArgumentException("Unsupported managed world environment: $environment")
        }

        fun parse(raw: String): ManagedDimension = entries.firstOrNull { it.name == raw }
            ?: throw IllegalArgumentException("Unsupported managed dimension: $raw")
    }
}
