package me.awabi2048.myworldmanager.api.internal

import me.awabi2048.myworldmanager.MyWorldManager
import me.awabi2048.myworldmanager.api.service.ApiTemplateRepository
import me.awabi2048.myworldmanager.api.service.ApiTemplateValidationIssue
import me.awabi2048.myworldmanager.model.TemplateData
import me.awabi2048.myworldmanager.repository.TemplateRepository

internal class TemplateRepositoryAdapter(private val plugin: MyWorldManager) : ApiTemplateRepository {

    override fun findAll(): List<TemplateData> {
        return plugin.templateRepository.findAll()
    }

    override fun findById(id: String): TemplateData? {
        return plugin.templateRepository.findById(id)
    }

    override fun validationIssue(id: String): ApiTemplateValidationIssue? {
        val template = plugin.templateRepository.findById(id)
            ?: return ApiTemplateValidationIssue.MISSING_DIRECTORY
        return when (plugin.templateRepository.validationIssue(template)) {
            TemplateRepository.ValidationIssue.MISSING_DIRECTORY ->
                ApiTemplateValidationIssue.MISSING_DIRECTORY
            TemplateRepository.ValidationIssue.MISSING_ORIGIN ->
                ApiTemplateValidationIssue.MISSING_ORIGIN
            null -> null
        }
    }
}
