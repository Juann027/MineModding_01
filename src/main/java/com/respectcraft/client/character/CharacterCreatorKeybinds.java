package com.respectcraft.client.character;

import com.mojang.blaze3d.platform.InputConstants;
import com.respectcraft.client.holo.HoloCharacterScreen;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla K: criacao inicial, depois o menu holografico do personagem (Etapa 4.8.1); inventario vanilla intacto.
 * Com o menu aberto, a mesma tecla o fecha ({@link #isMenuKey}).
 */
public final class CharacterCreatorKeybinds {
    private static final KeyMapping OPEN_CREATOR = new KeyMapping(
            "key.respectcraft.creator", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.respectcraft");

    private CharacterCreatorKeybinds() {}

    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN_CREATOR); }

    /** A tecla configurada para o menu (para fechar com a mesma tecla dentro da tela). */
    public static boolean isMenuKey(int keyCode, int scanCode) { return OPEN_CREATOR.matches(keyCode, scanCode); }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_CREATOR.consumeClick()) {
            // O menu proprio do Rimulib (WhiteSpace) capturando a entrada tem prioridade: nao abre por cima dele.
            if (mc.player != null && mc.screen == null && !rimulibMenuOpen()) {
                boolean created = com.respectcraft.network.CharacterAppearanceClientCache
                        .get(mc.player.getUUID()).created();
                mc.setScreen(created ? new HoloCharacterScreen() : new CharacterCreationScreen());
            }
        }
    }

    private static boolean rimulibMenuOpen() {
        try {
            return MenuApi.isOpen() || MenuApi.capturesInput();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
