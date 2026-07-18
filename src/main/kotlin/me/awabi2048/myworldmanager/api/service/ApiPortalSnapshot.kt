package me.awabi2048.myworldmanager.api.service

import java.util.UUID

data class ApiPortalSnapshot(
    val id: UUID,
    val worldKey: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val worldUuid: UUID?,
    val targetWorldKey: String?,
    val showText: Boolean,
    val particleColorRgb: Int,
    val ownerUuid: UUID,
    val createdAt: String,
    val textDisplayUuid: UUID?,
    val type: String,
    val minX: Int?,
    val minY: Int?,
    val minZ: Int?,
    val maxX: Int?,
    val maxY: Int?,
    val maxZ: Int?
)
