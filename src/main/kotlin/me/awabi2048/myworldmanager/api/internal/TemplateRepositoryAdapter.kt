package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiTemplateRepository
import me.awabi2048.myworldmanager.model.TemplateData

internal class TemplateRepositoryAdapter(private val plugin: MyWorldManager) : ApiTemplateRepository {

    override fun findAll(): List<TemplateData> {
        return plugin.templateRepository.findAll()
    }

    override fun findById(id: String): TemplateData? {
        return plugin.templateRepository.findById(id)
    }

    override fun isUsable(id: String): Boolean {
        val template = plugin.templateRepository.findById(id) ?: return false
        return plugin.templateRepository.isUsable(template)
    }
}
