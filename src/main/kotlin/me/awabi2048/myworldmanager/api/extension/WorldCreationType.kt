package me.awabi2048.myworldmanager.api.extension

/** 公開APIで扱うワールド作成方式。内部のセッション実装型を公開しない。 */
enum class WorldCreationType {
    TEMPLATE,
    SEED,
    RANDOM
}
