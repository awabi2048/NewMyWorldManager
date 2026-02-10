package me.awabi2048.myworldmanager.api.event

enum class MwmWarpReason {
    DIRECT,
    PORTAL
}

enum class MwmMemberAddSource {
    INVITE,
    REQUEST_APPROVE
}

enum class MwmMemberRemoveSource {
    MANUAL
}

enum class MwmOwnerTransferSource {
    MANUAL
}

enum class MwmFavoriteAddSource {
    FAVORITE_MENU,
    DISCOVERY_MENU
}

enum class MwmLikeSignLikeSource {
    SIGN_BLOCK,
    HOLOGRAM
}

enum class MwmVisitSource {
    WORLD_CHANGE,
    JOIN
}
