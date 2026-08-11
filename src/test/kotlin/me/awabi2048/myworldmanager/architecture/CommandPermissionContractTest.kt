package me.awabi2048.myworldmanager.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * コマンド権限の既定値と代理閲覧権限の境界を、plugin.yml と実行コードの両方で固定します。
 * citizen の既定付与に戻ると全基本コマンドが意図せず公開されるため、契約テストで検知します。
 */
class CommandPermissionContractTest {
    private val sourceRoot = Path.of("src/main")

    @Test
    fun `citizen is not granted by default and myworld other is an admin child`() {
        val pluginYaml = sourceRoot.resolve("resources/plugin.yml").readText()
        val admin = permissionBlock(pluginYaml, "myworldmanager.admin")
        val citizen = permissionBlock(pluginYaml, "myworldmanager.citizen")
        val other = permissionBlock(pluginYaml, "myworldmanager.command.myworld.other")

        assertTrue(citizen.contains("    default: false"))
        assertTrue(admin.contains("      myworldmanager.command.myworld.other: true"))
        assertTrue(other.contains("    default: false"))
    }

    @Test
    fun `other player lookup and tab completion use the dedicated permission`() {
        val command = sourceRoot.resolve(
            "kotlin/me/awabi2048/myworldmanager/command/PlayerWorldCommand.kt"
        ).readText()

        assertTrue(command.contains("PermissionManager.COMMAND_MYWORLD_OTHER"))
        assertTrue(command.contains("if (args.size == 1 && PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MYWORLD_OTHER))"))
        assertTrue(command.contains("if (PermissionManager.checkPermission(sender, PermissionManager.COMMAND_MYWORLD_OTHER))"))
        assertFalse(command.contains("sender.hasPermission(\"myworldmanager.admin\")"))
    }

    @Test
    fun `permission manager exposes dedicated other player permission`() {
        val permissions = sourceRoot.resolve(
            "kotlin/me/awabi2048/myworldmanager/util/PermissionManager.kt"
        ).readText()

        assertTrue(
            permissions.contains(
                "const val COMMAND_MYWORLD_OTHER = \"myworldmanager.command.myworld.other\""
            )
        )
    }

    /** plugin.yml の permissions 直下にある1権限のブロックだけを取り出します。 */
    private fun permissionBlock(yaml: String, permission: String): String {
        val lines = yaml.replace("\r\n", "\n").lines()
        val header = "  $permission:"
        val start = lines.indexOf(header)
        assertTrue(start >= 0, "permission block not found: $permission")

        val block = buildList {
            add(lines[start])
            for (line in lines.drop(start + 1)) {
                if (line.startsWith("  ") && !line.startsWith("    ") && line.trim().endsWith(":")) {
                    break
                }
                add(line)
            }
        }
        return block.joinToString("\n")
    }
}
