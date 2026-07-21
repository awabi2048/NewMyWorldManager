package me.awabi2048.myworldmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.awabi2048.myworldmanager.util.TourSignItemPolicy;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TourSignItemPolicyTest {
    @Test
    void usesCustomItemBaseAndSignModel() {
        assertEquals(Material.POISONOUS_POTATO, TourSignItemPolicy.INSTANCE.getBaseMaterial());
        assertEquals("minecraft", TourSignItemPolicy.INSTANCE.getItemModel().getNamespace());
        assertEquals("pale_oak_sign", TourSignItemPolicy.INSTANCE.getItemModel().getKey());
    }
}
