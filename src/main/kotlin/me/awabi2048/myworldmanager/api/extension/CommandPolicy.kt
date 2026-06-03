package me.awabi2048.myworldmanager.api.extension

import org.bukkit.command.CommandSender

interface CommandPolicy {
    fun getId(): String

    fun canExecuteMwmSubCommand(
        sender: CommandSender,
        subCommand: String,
        args: List<String>
    ): Boolean = true

    fun canSuggestMwmSubCommand(
        sender: CommandSender,
        subCommand: String,
        args: List<String>
    ): Boolean = true

    fun canSuggestMwmGiveItem(
        sender: CommandSender,
        itemId: String
    ): Boolean = true
}

interface CreateCommandHandler {
    fun getId(): String

    fun handleCreateCommand(
        sender: CommandSender,
        args: List<String>
    ): Boolean

    fun tabCompleteCreateCommand(
        sender: CommandSender,
        args: List<String>
    ): List<String>? = null
}
