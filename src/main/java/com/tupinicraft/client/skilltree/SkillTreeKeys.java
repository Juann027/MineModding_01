package com.tupinicraft.client.skilltree;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Tecla K (configurável em Opções > Controles > TupiniCraft) abre as árvores de habilidades. */
public final class SkillTreeKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.tupinicraft.skill_tree", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.tupinicraft");

    private SkillTreeKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().screen == null) {
                SkillTreeScreen.open();
            }
        }
    }
}
