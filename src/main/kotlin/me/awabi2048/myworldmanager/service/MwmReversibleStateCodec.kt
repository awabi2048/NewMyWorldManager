package me.awabi2048.myworldmanager.service

import com.awabi2048.ccsystem.api.gui.MenuReversibleOpaqueState
import com.google.gson.*
import me.awabi2048.myworldmanager.MyWorldManager
import org.bukkit.Color
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Type
import java.util.Base64
import java.util.UUID

internal interface MwmOpaqueProviderState : MenuReversibleOpaqueState {
    override fun snapshot(): Any = MwmReversibleStateCodec.encode(this)
}

internal class MwmStateDecodeException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

internal object MwmReversibleStateCodec {
    private const val SCHEMA_VERSION = 1
    private val rootKeys = setOf("schemaVersion", "providerId", "stateType", "payload")
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Color::class.java, object : JsonSerializer<Color>, JsonDeserializer<Color> {
            override fun serialize(src: Color, type: Type, context: JsonSerializationContext) = JsonPrimitive(src.asRGB())
            override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext) =
                Color.fromRGB(json.asInt)
        })
        .registerTypeHierarchyAdapter(ItemStack::class.java, object : JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
            override fun serialize(src: ItemStack, type: Type, context: JsonSerializationContext) =
                JsonPrimitive(Base64.getEncoder().encodeToString(src.serializeAsBytes()))
            override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext) =
                ItemStack.deserializeBytes(Base64.getDecoder().decode(json.asString))
        })
        .create()

    private data class Descriptor(val providerId: String, val stateType: String, val javaType: Class<out MwmOpaqueProviderState>)

    private val descriptors = listOf(
        descriptor(MwmReversibleContracts.PORTAL_STATE_PROVIDER, "portal", PortalState::class.java),
        descriptor(MwmReversibleContracts.SETTINGS_SESSION_PROVIDER, "settings", SettingsSessionState::class.java),
        descriptor(MwmReversibleContracts.DRAFT_PROVIDER, "tour", DraftSnapshot.Tour::class.java),
        descriptor(MwmReversibleContracts.DRAFT_PROVIDER, "template", DraftSnapshot.Template::class.java),
        descriptor(MwmReversibleContracts.WORLD_STATE_PROVIDER, "standard-publish", WorldStateSnapshot.StandardPublish::class.java),
        descriptor(MwmReversibleContracts.WORLD_STATE_PROVIDER, "policy-publish", WorldStateSnapshot.PolicyPublish::class.java),
        descriptor(MwmReversibleContracts.WORLD_STATE_PROVIDER, "notification", WorldStateSnapshot.Notification::class.java),
        descriptor(MwmReversibleContracts.WORLD_STATE_PROVIDER, "member-role", WorldStateSnapshot.MemberRole::class.java),
        descriptor(MwmReversibleContracts.PLAYER_STATE_PROVIDER, "favorite", PlayerStateSnapshot.Favorite::class.java),
        descriptor(MwmReversibleContracts.PLAYER_STATE_PROVIDER, "meet", PlayerStateSnapshot.Meet::class.java),
        descriptor(MwmReversibleContracts.MENU_SESSION_PROVIDER, "admin", MenuSessionSnapshot.Admin::class.java),
        descriptor(MwmReversibleContracts.MENU_SESSION_PROVIDER, "discovery", MenuSessionSnapshot.Discovery::class.java),
        descriptor(MwmReversibleContracts.MENU_SESSION_PROVIDER, "favorite", MenuSessionSnapshot.Favorite::class.java),
        descriptor(MwmReversibleContracts.USER_SETTINGS_PROVIDER, "user-setting", UserSettingState::class.java),
        descriptor(MwmReversibleContracts.DISPLAY_ORDER_PROVIDER, "display-order", DisplayOrderState::class.java),
        descriptor(MwmReversibleContracts.CREATION_SESSION_PROVIDER, "creation", CreationSessionState::class.java),
    )

    private fun descriptor(providerId: String, stateType: String, type: Class<out MwmOpaqueProviderState>) =
        Descriptor(providerId, stateType, type)

    fun encode(state: MwmOpaqueProviderState): Map<String, Any?> {
        val descriptor = descriptors.singleOrNull { it.javaType == state.javaClass }
            ?: error("未登録の可逆状態型です: ${state.javaClass.name}")
        val payload = when (state) {
            is WorldStateSnapshot.StandardPublish -> mapOf("worldUuid" to state.worldUuid, "planId" to state.plan.id)
            is WorldStateSnapshot.PolicyPublish -> mapOf("worldUuid" to state.worldUuid, "planId" to state.plan.id)
            is CreationSessionState -> jsonObjectToMap(gson.toJsonTree(state.copy(startPlan = null)).asJsonObject) +
                ("startPlanId" to state.startPlan?.id)
            else -> jsonObjectToMap(gson.toJsonTree(state).asJsonObject)
        }
        return linkedMapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "providerId" to descriptor.providerId,
            "stateType" to descriptor.stateType,
            "payload" to payload,
        )
    }

    fun decode(value: Any?, expectedProviderId: String, plugin: MyWorldManager? = null): MwmOpaqueProviderState {
        val root = value as? Map<*, *> ?: throw MwmStateDecodeException("snapshot root must be a map")
        if (root.keys.any { it !is String } || root.keys.toSet() != rootKeys) {
            throw MwmStateDecodeException("snapshot root fields are invalid")
        }
        if (root["schemaVersion"] != SCHEMA_VERSION) throw MwmStateDecodeException("unsupported schemaVersion")
        if (root["providerId"] != expectedProviderId) throw MwmStateDecodeException("providerId does not match")
        val stateType = root["stateType"] as? String ?: throw MwmStateDecodeException("stateType must be a string")
        val descriptor = descriptors.singleOrNull { it.providerId == expectedProviderId && it.stateType == stateType }
            ?: throw MwmStateDecodeException("unknown stateType")
        val payload = root["payload"] as? Map<*, *> ?: throw MwmStateDecodeException("payload must be a map")
        if (payload.keys.any { it !is String }) throw MwmStateDecodeException("payload keys must be strings")
        try {
            val decoded: MwmOpaqueProviderState = when (descriptor.javaType) {
                WorldStateSnapshot.StandardPublish::class.java -> {
                    requireExact(payload, setOf("worldUuid", "planId"))
                    val worldUuid = requireUuid(payload["worldUuid"], "worldUuid")
                    val plan = plugin?.worldPublishService?.consumeStandardPlan(requireUuid(payload["planId"], "planId"))
                        ?: throw MwmStateDecodeException("standard publish plan is unavailable")
                    WorldStateSnapshot.StandardPublish(worldUuid, plan)
                }
                WorldStateSnapshot.PolicyPublish::class.java -> {
                    requireExact(payload, setOf("worldUuid", "planId"))
                    val worldUuid = requireUuid(payload["worldUuid"], "worldUuid")
                    val plan = plugin?.worldPublishService?.consumePolicyPlan(requireUuid(payload["planId"], "planId"))
                        ?: throw MwmStateDecodeException("policy publish plan is unavailable")
                    WorldStateSnapshot.PolicyPublish(worldUuid, plan)
                }
                CreationSessionState::class.java -> {
                    requireExact(payload, setOf("before", "expectedAfter", "startPlan", "startPlanId"))
                    val json = mapToJsonObject(payload.filterKeys { it != "startPlanId" })
                    val base = gson.fromJson(json, CreationSessionState::class.java)
                    val id = payload["startPlanId"]?.let { requireUuid(it, "startPlanId") }
                    base.copy(startPlan = id?.let {
                        plugin?.creationSessionManager?.consumeBedrockStartPlan(it)
                            ?: throw MwmStateDecodeException("creation start plan is unavailable")
                    })
                }
                else -> gson.fromJson(mapToJsonObject(payload), descriptor.javaType)
            }
            if (encode(decoded)["payload"] != normalizeMap(payload)) {
                throw MwmStateDecodeException("payload fields or value types are invalid")
            }
            return decoded
        } catch (e: MwmStateDecodeException) {
            throw e
        } catch (e: Exception) {
            throw MwmStateDecodeException("payload could not be decoded", e)
        }
    }

    private fun requireExact(map: Map<*, *>, keys: Set<String>) {
        if (map.keys.toSet() != keys) throw MwmStateDecodeException("payload fields are invalid")
    }

    private fun requireUuid(value: Any?, field: String): UUID = value as? UUID
        ?: throw MwmStateDecodeException("$field must be a UUID")

    private fun jsonObjectToMap(value: JsonObject): Map<String, Any?> = value.entrySet().sortedBy { it.key }
        .associateTo(linkedMapOf()) { it.key to jsonToValue(it.value) }

    private fun jsonToValue(value: JsonElement): Any? = when {
        value.isJsonNull -> null
        value.isJsonObject -> jsonObjectToMap(value.asJsonObject)
        value.isJsonArray -> value.asJsonArray.map(::jsonToValue)
        value.asJsonPrimitive.isBoolean -> value.asBoolean
        value.asJsonPrimitive.isNumber -> value.asJsonPrimitive.asNumber
        else -> value.asString
    }

    private fun mapToJsonObject(value: Map<*, *>): JsonObject = JsonObject().also { result ->
        value.entries.sortedBy { it.key.toString() }.forEach { (key, item) -> result.add(key as String, valueToJson(item)) }
    }

    private fun valueToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is Map<*, *> -> mapToJsonObject(value)
        is Iterable<*> -> JsonArray().also { array -> value.forEach { array.add(valueToJson(it)) } }
        is UUID -> JsonPrimitive(value.toString())
        is Enum<*> -> JsonPrimitive(value.name)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> throw MwmStateDecodeException("unsupported payload value: ${value.javaClass.name}")
    }

    private fun normalizeMap(value: Map<*, *>): Map<String, Any?> = jsonObjectToMap(mapToJsonObject(value))
}
