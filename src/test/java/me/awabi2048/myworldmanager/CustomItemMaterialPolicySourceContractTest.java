package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomItemMaterialPolicySourceContractTest {
    @Test
    void tourSignUsesCustomItemBaseInsteadOfPortalFrame() throws Exception {
        String source = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/myworldmanager/util/CustomItem.kt"),
            StandardCharsets.UTF_8
        );
        int start = source.indexOf("TOUR_SIGN(\"tour_sign\")");
        int end = source.indexOf("@Deprecated", start);
        String tourSign = source.substring(start, end);

        assertTrue(tourSign.contains("ItemStack(Material.POISONOUS_POTATO)"));
        assertTrue(tourSign.contains("setItemModel(NamespacedKey(\"minecraft\", \"pale_oak_sign\"))"));
        assertFalse(tourSign.contains("ItemStack(Material.END_PORTAL_FRAME)"));
    }
}
