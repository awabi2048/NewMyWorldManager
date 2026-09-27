package me.awabi2048.myworldmanager.integration

import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.model.WorldData
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.data.DataMutateResult
import net.luckperms.api.model.data.NodeMap
import net.luckperms.api.model.group.Group
import net.luckperms.api.model.group.GroupManager
import net.luckperms.api.model.user.User
import net.luckperms.api.model.user.UserManager
import net.luckperms.api.node.Node
import net.luckperms.api.node.NodeBuilderRegistry
import net.luckperms.api.node.types.InheritanceNode
import net.luckperms.api.node.types.PermissionNode
import org.bukkit.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class WorldPermissionPolicyServiceTest {

    @TempDir
    lateinit var dataFolder: File

    @AfterEach
    fun tearDown() {
        unregisterLuckPerms()
    }

    private fun world(): WorldData {
        val owner = UUID.randomUUID()
        return WorldData(
            uuid = UUID.randomUUID(),
            dimension = ManagedDimension.OVERWORLD,
            name = "test",
            description = "",
            icon = Material.STONE,
            sourceWorld = "test",
            expireDate = "2099-01-01",
            owner = owner,
            members = mutableListOf(UUID.randomUUID())
        )
    }

    private class RecordedNodeCalls {
        val added = CopyOnWriteArrayList<Node>()
        val removed = CopyOnWriteArrayList<Node>()

        fun addedInheritanceGroupNames(): List<String> =
            added.filterIsInstance<InheritanceNode>().map { it.groupName }

        fun removedInheritanceGroupNames(): List<String> =
            removed.filterIsInstance<InheritanceNode>().map { it.groupName }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> ' '
        else -> null
    }

    private fun <T> stub(iface: Class<T>, handler: (Method, Array<out Any>?) -> Any?): T {
        val invocationHandler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Stub(${iface.simpleName})"
                else -> handler(method, args)
            }
        }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), invocationHandler) as T
    }

    /** チェイン呼び出し（withContext/value 等）で自身を返し、build でノードスタブを返す Builder スタブ。 */
    private fun <T> builderStub(iface: Class<T>, configure: (Method, Array<out Any>?) -> Unit, build: () -> Node): T {
        val invocationHandler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "StubBuilder(${iface.simpleName})"
                "build" -> build()
                else -> {
                    configure(method, args)
                    if (method.returnType.isInstance(proxy)) proxy else defaultValue(method.returnType)
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), invocationHandler) as T
    }

    private fun inheritanceNode(groupName: String): InheritanceNode =
        stub(InheritanceNode::class.java) { method, _ ->
            if (method.name == "getGroupName") groupName else defaultValue(method.returnType)
        }

    private fun permissionNode(permission: String): PermissionNode =
        stub(PermissionNode::class.java) { method, _ ->
            if (method.name == "getPermission") permission else defaultValue(method.returnType)
        }

    private fun nodeBuilderRegistry(): NodeBuilderRegistry =
        stub(NodeBuilderRegistry::class.java) { method, _ ->
            when (method.name) {
                "forInheritance" -> {
                    val groupName = AtomicReference<String>()
                    builderStub(InheritanceNode.Builder::class.java, { m, args ->
                        if (m.name == "group") {
                            groupName.set(
                                when (val arg = args?.get(0)) {
                                    is String -> arg
                                    is Group -> arg.name
                                    else -> null
                                }
                            )
                        }
                    }) { inheritanceNode(groupName.get() ?: "") }
                }
                "forPermission" -> {
                    val permission = AtomicReference<String>()
                    builderStub(PermissionNode.Builder::class.java, { m, args ->
                        if (m.name == "permission") permission.set(args?.get(0) as? String)
                    }) { permissionNode(permission.get() ?: "") }
                }
                else -> defaultValue(method.returnType)
            }
        }

    private class LuckPermsStub(val api: LuckPerms, val calls: RecordedNodeCalls)

    /** groupManager.getGroup が非nullを返すスタブ（作業グループ存在を模擬）。 */
    private fun luckPermsStub(): LuckPermsStub {
        val calls = RecordedNodeCalls()
        val nodeMap = stub(NodeMap::class.java) { method, args ->
            when (method.name) {
                "add" -> {
                    calls.added.add(args!![0] as Node)
                    DataMutateResult.SUCCESS
                }
                "remove" -> {
                    calls.removed.add(args!![0] as Node)
                    DataMutateResult.SUCCESS
                }
                else -> defaultValue(method.returnType)
            }
        }
        val user = stub(User::class.java) { method, _ ->
            if (method.name == "data") nodeMap else defaultValue(method.returnType)
        }
        val userManager = stub(UserManager::class.java) { method, _ ->
            when (method.name) {
                "loadUser" -> CompletableFuture.completedFuture(user)
                "saveUser" -> CompletableFuture.completedFuture<Void>(null)
                else -> defaultValue(method.returnType)
            }
        }
        val group = stub(Group::class.java) { method, _ -> defaultValue(method.returnType) }
        val groupManager = stub(GroupManager::class.java) { method, _ ->
            if (method.name == "getGroup") group else defaultValue(method.returnType)
        }
        val registry = nodeBuilderRegistry()
        val api = stub(LuckPerms::class.java) { method, _ ->
            when (method.name) {
                "getUserManager" -> userManager
                "getGroupManager" -> groupManager
                "getNodeBuilderRegistry" -> registry
                else -> defaultValue(method.returnType)
            }
        }
        return LuckPermsStub(api, calls)
    }

    /** LuckPermsProvider は静的シングルトンのため、ノード Builder の利用に登録が必要。 */
    private fun registerLuckPerms(api: LuckPerms) {
        val method = LuckPermsProvider::class.java.getDeclaredMethod("register", LuckPerms::class.java)
        method.isAccessible = true
        method.invoke(null, api)
    }

    private fun unregisterLuckPerms() {
        val method = LuckPermsProvider::class.java.getDeclaredMethod("unregister")
        method.isAccessible = true
        method.invoke(null)
    }

    private fun service(workGroupName: String, luckPerms: LuckPerms): WorldPermissionPolicyService =
        WorldPermissionPolicyService(
            luckPerms = luckPerms,
            worldGuardAvailable = false,
            workGroupName = workGroupName,
            logger = Logger.getLogger("WorldPermissionPolicyServiceTest"),
            dataFolder = dataFolder
        )

    @Test
    fun `同期では現行作業グループの継承ノードだけがremoveとaddの対象になる`() {
        // 旧仕様で蓄積されていた managed_work_groups が残っていても他グループに触れないこと
        val stateFile = File(dataFolder, "data/world_permission_policy.yml")
        stateFile.parentFile.mkdirs()
        stateFile.writeText("managed_work_groups:\n  - builder\n  - basic_tool\n")

        val stub = luckPermsStub()
        registerLuckPerms(stub.api)
        val worldData = world()
        service("basic_tool", stub.api).syncPersistentParticipantPermissions(worldData)

        val removedGroups = stub.calls.removedInheritanceGroupNames()
        val addedGroups = stub.calls.addedInheritanceGroupNames()
        assertTrue(removedGroups.isNotEmpty())
        assertTrue(addedGroups.isNotEmpty())
        assertTrue(removedGroups.all { it == "basic_tool" }, "removed: $removedGroups")
        assertTrue(addedGroups.all { it == "basic_tool" }, "added: $addedGroups")
        assertTrue("builder" !in removedGroups)
        assertTrue("builder" !in addedGroups)
        assertTrue(stub.calls.added.any { it is PermissionNode })
    }

    @Test
    fun `作業グループ名が空なら継承ノードを一切触れない`() {
        val stub = luckPermsStub()
        registerLuckPerms(stub.api)
        val worldData = world()
        service("", stub.api).syncPersistentParticipantPermissions(worldData)

        assertEquals(0, stub.calls.removedInheritanceGroupNames().size)
        assertEquals(0, stub.calls.addedInheritanceGroupNames().size)
        // worldguard.* のロール権限は従来どおり同期される
        assertTrue(stub.calls.removed.any { it is PermissionNode })
        assertTrue(stub.calls.added.any { it is PermissionNode })
    }

    @Test
    fun `clearWorldでは現行作業グループの継承ノードだけがremoveされる`() {
        val stateFile = File(dataFolder, "data/world_permission_policy.yml")
        stateFile.parentFile.mkdirs()
        stateFile.writeText("managed_work_groups:\n  - builder\n  - basic_tool\n")

        val stub = luckPermsStub()
        registerLuckPerms(stub.api)
        val worldData = world()
        val participants = listOf(worldData.owner, worldData.members.first())
        service("basic_tool", stub.api).clearWorld("my_world.${worldData.uuid}", participants)

        val removedGroups = stub.calls.removedInheritanceGroupNames()
        assertTrue(removedGroups.isNotEmpty())
        assertTrue(removedGroups.all { it == "basic_tool" }, "removed: $removedGroups")
        assertTrue("builder" !in removedGroups)
        assertTrue(stub.calls.addedInheritanceGroupNames().isEmpty())
    }
}
