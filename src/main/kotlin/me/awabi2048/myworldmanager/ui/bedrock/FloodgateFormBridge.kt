package me.awabi2048.myworldmanager.ui.bedrock

import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.function.Consumer

class FloodgateFormBridge(private val plugin: MyWorldManager) {

    data class SimpleFormButton(
        val label: String,
        val imagePath: String? = null
    )

    data class CustomFormInput(
        val label: String,
        val placeholder: String = "",
        val defaultValue: String = ""
    )

    private val simpleFormClassCandidates =
        listOf(
            "org.geysermc.cumulus.form.SimpleForm",
            "org.geysermc.cumulus.SimpleForm"
        )

    private val customFormClassCandidates =
        listOf(
            "org.geysermc.cumulus.form.CustomForm",
            "org.geysermc.cumulus.CustomForm"
        )

    private val formImageClassCandidates =
        listOf(
            "org.geysermc.cumulus.util.FormImage",
            "org.geysermc.cumulus.FormImage"
        )

    private var simpleFormLookupCompleted = false
    private var simpleFormClass: Class<*>? = null

    private var customFormLookupCompleted = false
    private var customFormClass: Class<*>? = null

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
        return sendSimpleFormWithImages(
            player = player,
            title = title,
            content = content,
            buttons = buttons.map { SimpleFormButton(label = it) },
            onSelect = onSelect,
            onClosed = onClosed
        )
    }

    fun sendSimpleFormWithImages(
        player: Player,
        title: String,
        content: String,
        buttons: List<SimpleFormButton>,
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
            invokeSendForm(api, player.uniqueId, form)
        }.getOrElse { throwable ->
            plugin.logger.warning("[BedrockUI] Failed to send form: ${throwable.message}")
            false
        }
    }

    fun sendCustomInputForm(
        player: Player,
        title: String,
        label: String,
        placeholder: String,
        defaultValue: String,
        onSubmit: (String) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean {
        return sendCustomForm(
            player = player,
            title = title,
            inputs = listOf(CustomFormInput(label, placeholder, defaultValue)),
            onSubmit = { values ->
                onSubmit(values.firstOrNull().orEmpty())
            },
            onClosed = onClosed
        )
    }

    fun sendCustomForm(
        player: Player,
        title: String,
        inputs: List<CustomFormInput>,
        onSubmit: (List<String>) -> Unit,
        onClosed: (() -> Unit)? = null
    ): Boolean {
        if (inputs.isEmpty()) {
            return false
        }

        val api = resolveFloodgateApiInstance() ?: return false
        if (!isFloodgatePlayer(api, player.uniqueId)) {
            return false
        }

        return runCatching {
            val form =
                buildCustomForm(
                    title = title,
                    inputs = inputs,
                    onSubmit = onSubmit,
                    onClosed = onClosed
                ) ?: return false
            invokeSendForm(api, player.uniqueId, form)
        }.getOrElse { throwable ->
            plugin.logger.warning("[BedrockUI] Failed to send custom form: ${throwable.message}")
            false
        }
    }

    private fun buildSimpleForm(
        title: String,
        content: String,
        buttons: List<SimpleFormButton>,
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

        val textButtonMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        }
        val textWithCallbackMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                Consumer::class.java.isAssignableFrom(it.parameterTypes[1])
        }

        val imagePathButtonMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 3 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1].isEnum &&
                it.parameterTypes[2] == String::class.java
        }
        val imagePathWithCallbackMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 4 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1].isEnum &&
                it.parameterTypes[2] == String::class.java &&
                Consumer::class.java.isAssignableFrom(it.parameterTypes[3])
        }

        val imageButtonMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                !Consumer::class.java.isAssignableFrom(it.parameterTypes[1])
        }
        val imageWithCallbackMethod = builder.javaClass.methods.firstOrNull {
            it.name == "button" &&
                it.parameterCount == 3 &&
                it.parameterTypes[0] == String::class.java &&
                !it.parameterTypes[1].isEnum &&
                Consumer::class.java.isAssignableFrom(it.parameterTypes[2])
        }

        val validResultMethod = builder.javaClass.methods.firstOrNull {
            it.name == "validResultHandler" &&
                it.parameterCount == 1 &&
                Consumer::class.java.isAssignableFrom(it.parameterTypes[0])
        }

        val useValidResultHandler = validResultMethod != null

        buttons.forEachIndexed { index, button ->
            val added =
                if (useValidResultHandler) {
                    addSimpleFormButtonWithoutCallback(
                        builder = builder,
                        button = button,
                        textButtonMethod = textButtonMethod,
                        imagePathButtonMethod = imagePathButtonMethod,
                        imageButtonMethod = imageButtonMethod
                    )
                } else {
                    val callback = Consumer<Any> {
                        Bukkit.getScheduler().runTask(plugin, Runnable { onSelect(index) })
                    }
                    addSimpleFormButtonWithCallback(
                        builder = builder,
                        button = button,
                        callback = callback,
                        textWithCallbackMethod = textWithCallbackMethod,
                        imagePathWithCallbackMethod = imagePathWithCallbackMethod,
                        imageWithCallbackMethod = imageWithCallbackMethod
                    )
                }

            if (!added) {
                return null
            }
        }

        if (useValidResultHandler) {
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

    private fun buildCustomForm(
        title: String,
        inputs: List<CustomFormInput>,
        onSubmit: (List<String>) -> Unit,
        onClosed: (() -> Unit)?
    ): Any? {
        val customForm = resolveCustomFormClass() ?: return null
        val builderFactory = customForm.methods.firstOrNull {
            it.name == "builder" && it.parameterCount == 0
        } ?: return null
        val builder = builderFactory.invoke(null) ?: return null

        val titleMethod = builder.javaClass.methods.firstOrNull {
            it.name == "title" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        } ?: return null
        titleMethod.invoke(builder, title)

        inputs.forEach { input ->
            val appended = appendCustomInput(builder, input)
            if (!appended) {
                return null
            }
        }

        val validResultMethod = builder.javaClass.methods.firstOrNull {
            it.name == "validResultHandler" &&
                it.parameterCount == 1 &&
                Consumer::class.java.isAssignableFrom(it.parameterTypes[0])
        } ?: return null

        val callback = Consumer<Any> { response ->
            val values =
                inputs.indices.map { index ->
                    extractCustomInput(response, index)
                }
            Bukkit.getScheduler().runTask(plugin, Runnable { onSubmit(values) })
        }
        validResultMethod.invoke(builder, callback)

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

    private fun appendCustomInput(builder: Any, input: CustomFormInput): Boolean {
        val inputMethod = builder.javaClass.methods.firstOrNull {
            it.name == "input" &&
                it.parameterCount == 3 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == String::class.java &&
                it.parameterTypes[2] == String::class.java
        }

        val inputMethod2Args = builder.javaClass.methods.firstOrNull {
            it.name == "input" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == String::class.java
        }

        val inputMethod1Arg = builder.javaClass.methods.firstOrNull {
            it.name == "input" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        }

        return when {
            inputMethod != null -> {
                inputMethod.invoke(builder, input.label, input.placeholder, input.defaultValue)
                true
            }
            inputMethod2Args != null -> {
                inputMethod2Args.invoke(builder, input.label, input.placeholder)
                true
            }
            inputMethod1Arg != null -> {
                inputMethod1Arg.invoke(builder, input.label)
                true
            }
            else -> false
        }
    }

    private fun extractCustomInput(response: Any, index: Int): String {
        return runCatching {
            val asInputByIndex = response.javaClass.methods.firstOrNull {
                it.name == "asInput" &&
                    it.parameterCount == 1 &&
                    (it.parameterTypes[0] == Int::class.javaPrimitiveType ||
                        it.parameterTypes[0] == Int::class.java)
            }
            if (asInputByIndex != null) {
                (asInputByIndex.invoke(response, index) as? String).orEmpty()
            } else {
                val getInputByIndex = response.javaClass.methods.firstOrNull {
                    it.name == "getInput" &&
                        it.parameterCount == 1 &&
                        (it.parameterTypes[0] == Int::class.javaPrimitiveType ||
                            it.parameterTypes[0] == Int::class.java)
                }
                if (getInputByIndex != null) {
                    (getInputByIndex.invoke(response, index) as? String).orEmpty()
                } else {
                    val asInput = response.javaClass.methods.firstOrNull {
                        it.name == "asInput" && it.parameterCount == 0
                    }
                    if (index == 0 && asInput != null) {
                        (asInput.invoke(response) as? String).orEmpty()
                    } else {
                        val getInput = response.javaClass.methods.firstOrNull {
                            it.name == "getInput" && it.parameterCount == 0
                        }
                        if (index == 0 && getInput != null) {
                            (getInput.invoke(response) as? String).orEmpty()
                        } else {
                            ""
                        }
                    }
                }
            }
        }.getOrDefault("")
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
                simpleFormClass = resolveFirstExistingClass(simpleFormClassCandidates)
                simpleFormLookupCompleted = true
            }
        }

        return simpleFormClass
    }

    private fun resolveCustomFormClass(): Class<*>? {
        if (customFormLookupCompleted) {
            return customFormClass
        }

        synchronized(this) {
            if (!customFormLookupCompleted) {
                customFormClass = resolveFirstExistingClass(customFormClassCandidates)
                customFormLookupCompleted = true
            }
        }

        return customFormClass
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

    private fun resolveFormImageClass(): Class<*>? {
        return resolveFirstExistingClass(formImageClassCandidates)
    }

    private fun resolveFirstExistingClass(candidates: List<String>): Class<*>? {
        return candidates.firstNotNullOfOrNull { className ->
            runCatching { Class.forName(className) }.getOrNull()
        }
    }

    private fun invokeSendForm(api: Any, playerUuid: UUID, form: Any): Boolean {
        val sendFormMethod =
            api.javaClass.methods.firstOrNull {
                it.name == "sendForm" &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0] == UUID::class.java &&
                    it.parameterTypes[1].isAssignableFrom(form.javaClass)
            } ?: return false

        return sendFormMethod.invoke(api, playerUuid, form) as? Boolean ?: false
    }

    private fun addSimpleFormButtonWithoutCallback(
        builder: Any,
        button: SimpleFormButton,
        textButtonMethod: java.lang.reflect.Method?,
        imagePathButtonMethod: java.lang.reflect.Method?,
        imageButtonMethod: java.lang.reflect.Method?
    ): Boolean {
        val imagePath = button.imagePath?.takeIf { it.isNotBlank() }
        if (imagePath != null) {
            if (imagePathButtonMethod != null) {
                val pathType = resolvePathImageType(imagePathButtonMethod.parameterTypes[1])
                if (pathType != null) {
                    imagePathButtonMethod.invoke(builder, button.label, pathType, imagePath)
                    return true
                }
            }

            if (imageButtonMethod != null) {
                val formImage = createPathFormImage(imageButtonMethod.parameterTypes[1], imagePath)
                if (formImage != null) {
                    imageButtonMethod.invoke(builder, button.label, formImage)
                    return true
                }
            }
        }

        if (textButtonMethod != null) {
            textButtonMethod.invoke(builder, button.label)
            return true
        }

        return false
    }

    private fun addSimpleFormButtonWithCallback(
        builder: Any,
        button: SimpleFormButton,
        callback: Consumer<Any>,
        textWithCallbackMethod: java.lang.reflect.Method?,
        imagePathWithCallbackMethod: java.lang.reflect.Method?,
        imageWithCallbackMethod: java.lang.reflect.Method?
    ): Boolean {
        val imagePath = button.imagePath?.takeIf { it.isNotBlank() }
        if (imagePath != null) {
            if (imagePathWithCallbackMethod != null) {
                val pathType = resolvePathImageType(imagePathWithCallbackMethod.parameterTypes[1])
                if (pathType != null) {
                    imagePathWithCallbackMethod.invoke(builder, button.label, pathType, imagePath, callback)
                    return true
                }
            }

            if (imageWithCallbackMethod != null) {
                val formImage = createPathFormImage(imageWithCallbackMethod.parameterTypes[1], imagePath)
                if (formImage != null) {
                    imageWithCallbackMethod.invoke(builder, button.label, formImage, callback)
                    return true
                }
            }
        }

        if (textWithCallbackMethod != null) {
            textWithCallbackMethod.invoke(builder, button.label, callback)
            return true
        }

        return false
    }

    private fun resolvePathImageType(typeClass: Class<*>): Any? {
        if (!typeClass.isEnum) {
            return null
        }

        return typeClass.enumConstants?.firstOrNull { enumConstant ->
            (enumConstant as? Enum<*>)?.name.equals("PATH", ignoreCase = true)
        }
    }

    private fun createPathFormImage(formImageClass: Class<*>, imagePath: String): Any? {
        val classForFactory =
            if (formImageClass == Any::class.java) {
                resolveFormImageClass() ?: return null
            } else {
                formImageClass
            }

        val ofWithTypeMethod = classForFactory.methods.firstOrNull {
            it.name == "of" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0].isEnum &&
                it.parameterTypes[1] == String::class.java
        }

        if (ofWithTypeMethod != null) {
            val pathType = resolvePathImageType(ofWithTypeMethod.parameterTypes[0])
            if (pathType != null) {
                return runCatching {
                    ofWithTypeMethod.invoke(null, pathType, imagePath)
                }.getOrNull()
            }
        }

        val ofWithStringMethod = classForFactory.methods.firstOrNull {
            it.name == "of" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == String::class.java
        }

        return if (ofWithStringMethod == null) {
            null
        } else {
            runCatching {
                ofWithStringMethod.invoke(null, "path", imagePath)
            }.getOrNull()
        }
    }
}
