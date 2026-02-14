package me.awabi2048.myworldmanager.ui.bedrock

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.function.Consumer

class FloodgateFormBridge(private val plugin: MyWorldManager) {

    private var simpleFormLookupCompleted = false
    private var simpleFormClass: Class<*>? = null

    @Volatile
    private var floodgateLookupCompleted = false

    @Volatile
    private var floodgateApiClass: Class<*>? = null

    fun isAvailable(player: Player): Boolean {
        val api = resolveFloodgateApiInstance() ?: return false
        if (!isFloodgatePlayer(api, player.uniqueId)) {
            return false
        }

        val simpleForm = resolveSimpleFormClass() ?: return false
        val builderMethod = simpleForm.methods.firstOrNull {
            it.name == "builder" && it.parameterCount == 0
        }

        val sendFormMethod = api.javaClass.methods.firstOrNull {
            it.name == "sendForm" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == UUID::class.java
        }

        return builderMethod != null && sendFormMethod != null
    }

    fun sendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<String>,
        onSelect: (Int) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean {
        if (buttons.isEmpty()) {
            return false
        }

        val api = resolveFloodgateApiInstance() ?: return false
        if (!isFloodgatePlayer(api, player.uniqueId)) {
            return false
        }

        return runCatching {
            val form = buildSimpleForm(title, content, buttons, onSelect, onClosed) ?: return false

            val sendFormMethod = api.javaClass.methods.firstOrNull {
                it.name == "sendForm" &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0] == UUID::class.java
            } ?: return false

            sendFormMethod.invoke(api, player.uniqueId, form) as? Boolean ?: false
        }.getOrElse { throwable ->
            plugin.logger.warning("[BedrockUI] Failed to send form: ${throwable.message}")
            false
        }
    }

    private fun buildSimpleForm(
        title: String,
        content: String,
        buttons: List<String>,
        onSelect: (Int) -> Unit,
        onClosed: (() -> Unit)?
    ): Any? {
        val simpleForm = resolveSimpleFormClass() ?: return null
        val builderFactory = simpleForm.methods.firstOrNull {
            it.name == "builder" && it.parameterCount == 0
        } ?: return null
        val builder = builderFactory.invoke(null) ?: return null

        val titleMethod = builder.javaClass.methods.firstOrNull {
            it.name == "title" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        } ?: return null
        titleMethod.invoke(builder, title)

        val contentMethod = builder.javaClass.methods.firstOrNull {
            it.name == "content" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        } ?: return null
        contentMethod.invoke(builder, content)

        var callbackAttachedPerButton = false
        val buttonWithCallbackMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                Consumer::class.java.isAssignableFrom(it.parameterTypes[1])
        }

        if (buttonWithCallbackMethod != null) {
            callbackAttachedPerButton = true
            buttons.forEachIndexed { index, label ->
                val callback = Consumer<Any> {
                    Bukkit.getScheduler().runTask(plugin, Runnable { onSelect(index) })
                }
                buttonWithCallbackMethod.invoke(builder, label, callback)
            }
        } else {
            val buttonMethod = builder.javaClass.methods.firstOrNull {
                it.name == "button" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == String::class.java
            } ?: return null

            buttons.forEach { label ->
                buttonMethod.invoke(builder, label)
            }
        }

        if (!callbackAttachedPerButton) {
            val validResultMethod = builder.javaClass.methods.firstOrNull {
                it.name == "validResultHandler" &&
                    it.parameterCount == 1 &&
                    Consumer::class.java.isAssignableFrom(it.parameterTypes[0])
            }

            if (validResultMethod != null) {
                val callback = Consumer<Any> { response ->
                    val clickedIndex = runCatching {
                        val clickedMethod = response.javaClass.methods.firstOrNull {
                            it.name == "clickedButtonId" && it.parameterCount == 0
                        } ?: return@Consumer
                        val raw = clickedMethod.invoke(response) as? Number ?: return@Consumer
                        raw.toInt()
                    }.getOrNull() ?: return@Consumer

                    Bukkit.getScheduler().runTask(plugin, Runnable { onSelect(clickedIndex) })
                }
                validResultMethod.invoke(builder, callback)
            }
        }

        if (onClosed != null) {
            val closeMethod = builder.javaClass.methods.firstOrNull {
                it.name == "closedOrInvalidResultHandler" &&
                    it.parameterCount == 1 &&
                    Runnable::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            if (closeMethod != null) {
                closeMethod.invoke(builder, Runnable {
                    Bukkit.getScheduler().runTask(plugin, Runnable { onClosed() })
                })
            }
        }

        val buildMethod = builder.javaClass.methods.firstOrNull {
            it.name == "build" && it.parameterCount == 0
        } ?: return null

        return buildMethod.invoke(builder)
    }

    private fun isFloodgatePlayer(apiInstance: Any, playerUuid: UUID): Boolean {
        val method = apiInstance.javaClass.methods.firstOrNull {
            it.name == "isFloodgatePlayer" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == UUID::class.java
        } ?: return false

        return runCatching {
            method.invoke(apiInstance, playerUuid) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun resolveSimpleFormClass(): Class<*>? {
        if (simpleFormLookupCompleted) {
            return simpleFormClass
        }

        synchronized(this) {
            if (!simpleFormLookupCompleted) {
                simpleFormClass = runCatching {
                    Class.forName("org.geysermc.cumulus.form.SimpleForm")
                }.getOrNull()
                simpleFormLookupCompleted = true
            }
        }

        return simpleFormClass
    }

    private fun resolveFloodgateApiInstance(): Any? {
        val apiClass = resolveFloodgateApiClass() ?: return null
        val getInstanceMethod = apiClass.methods.firstOrNull {
            it.name == "getInstance" && it.parameterCount == 0
        } ?: return null

        return runCatching {
            getInstanceMethod.invoke(null)
        }.getOrNull()
    }

    private fun resolveFloodgateApiClass(): Class<*>? {
        if (floodgateLookupCompleted) {
            return floodgateApiClass
        }

        synchronized(this) {
            if (!floodgateLookupCompleted) {
                floodgateApiClass = runCatching {
                    Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                }.getOrNull()
                floodgateLookupCompleted = true
            }
        }

        return floodgateApiClass
    }
}
