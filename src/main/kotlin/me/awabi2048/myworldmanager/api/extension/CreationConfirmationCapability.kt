package me.awabi2048.myworldmanager.api.extension

interface WorldCreationDraft {
    val worldName: String?

    fun getBoolean(key: String): Boolean?

    fun setBoolean(key: String, value: Boolean)
}

object CreationConfirmationCapabilityContract {
    const val PLACEMENT = "myworldmanager.creation.confirmation"
    const val DRAFT_ATTRIBUTE = "myworldmanager.creation.draft"
}
