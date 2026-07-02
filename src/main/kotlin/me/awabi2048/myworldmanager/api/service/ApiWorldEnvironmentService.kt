package me.awabi2048.myworldmanager.api.service

import java.util.UUID

interface ApiWorldEnvironmentService {
    fun applyAll(worldUuid: UUID): Boolean
    fun applyWeather(worldUuid: UUID): Boolean
    fun applyTime(worldUuid: UUID): Boolean
    fun applyAttributes(worldUuid: UUID): Boolean
    fun applyFlight(worldUuid: UUID): Boolean
}
