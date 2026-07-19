package me.awabi2048.myworldmanager.api.service

import me.awabi2048.myworldmanager.model.TemplateData

interface ApiTemplateRepository {

    fun findAll(): List<TemplateData>
    fun findById(id: String): TemplateData?
    fun isUsable(id: String): Boolean
}
