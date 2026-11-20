package com.respectcraft.client.character;

import com.mojang.blaze3d.platform.InputConstants;
import com.respectcraft.client.holo.HoloCharacterScreen;
import com.respectcraft.client.holo.HoloConfig;
import com.respectcraft.client.holo.HoloWorld;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla K: criacao inicial, depois o menu holografico do personagem; inventario vanilla intacto.
 *
 * <p>Etapa 4.8.4-A: no modo mundo (padrao) K invoca e dispensa as placas paradas no mundo ({@link HoloWorld}) e a
 * tecla Recall (R por padrao, configuravel em Controles) as leva para a frente da mira atual. No modo tela (reserva
 * de desenvolvimento) K abre a tela da 4.8.1, e a mesma tecla a fecha ({@link #isMenuKey}).
 */
public final class CharacterCreatorKeybinds {
    private static final KeyMapping OPEN_CREATOR = new KeyMapping(
            "key.respectcraft.creator", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.respectcraft");
    /** R: sem uso no vanilla 1.21.1; so age com os paineis abertos no mundo. */
    private static final KeyMapping RECALL = new KeyMapping(
            "key.respectcraft.holo_recall", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, "key.categories.respectcraft");

    private CharacterCreatorKeybinds() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CREATOR);
        event.register(RECALL);
    }

    /** Nome da tecla do menu e da tecla Recall como o jogador as configurou (dicas na barra de acao). */
    public static Component menuKeyName() { return OPEN_CREATOR.getTranslatedKeyMessage(); }
    public static Component recallKeyName() { return RECALL.getTranslatedKeyMessage(); }

    /** A tecla configurada para o menu (para fechar com a mesma tecla dentro da tela). */
    public static boolean isMenuKey(int keyCode, int scanCode) { return OPEN_CREATOR.matches(keyCode, scanCode); }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_CREATOR.consumeClick()) {
            // O menu proprio do Rimulib (WhiteSpace) capturando a entrada tem prioridade: nao abre por cima dele.
            if (mc.player != null && mc.screen == null && !rimulibMenuOpen()) {
                boolean created = com.respectcraft.network.CharacterAppearanceClientCache
                        .get(mc.player.getUUID()).created();
                if (!created) {
                    HoloWorld.closeAll();
                    mc.setScreen(new CharacterCreationScreen());
                } else if (HoloConfig.worldMode()) {
                    HoloWorld.toggle();
                } else {
                    mc.setScreen(new HoloCharacterScreen());
                }
            }
        }
        while (RECALL.consumeClick()) {
            if (mc.screen == null && HoloWorld.isOpen()) HoloWorld.recall();
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
