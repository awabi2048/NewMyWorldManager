package me.awabi2048.myworldmanager.model

import org.bukkit.Material
import org.bukkit.Location

/**
 * ワールドテンプレートのデータ
 */
data class TemplateData(
    val id: String,
    val path: String,
    val name: String,
    val description: List<String>,
    val icon: Material,
    val originLocation: Location?,
    val previewTime: Long? = null,
    val previewWeather: String? = null
)
