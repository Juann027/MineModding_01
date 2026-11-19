package com.respectcraft.client.character;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Tecla K: criacao inicial, depois menu principal do mod; inventario vanilla intacto. */
public final class CharacterCreatorKeybinds {
    private static final KeyMapping OPEN_CREATOR = new KeyMapping(
            "key.respectcraft.creator", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.respectcraft");

    private CharacterCreatorKeybinds() {}

    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN_CREATOR); }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_CREATOR.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                boolean created = com.respectcraft.network.CharacterAppearanceClientCache
                        .get(mc.player.getUUID()).created();
                mc.setScreen(created ? new CharacterHubScreen() : new CharacterCreationScreen());
            }
        }
    }
}
