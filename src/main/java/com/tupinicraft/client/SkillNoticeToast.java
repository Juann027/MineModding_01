package com.tupinicraft.client;

import com.tupinicraft.network.SkillNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Notificação (toast) das árvores de habilidades; aparece por cima da tela da árvore. */
final class SkillNoticeToast {
    /** Um só "canal": uma notificação nova substitui a anterior em vez de empilhar. */
    private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId(5000L);

    private SkillNoticeToast() {
    }

    static void show(SkillNoticePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.forceHide(minecraft.getToasts(), ID);
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, ID, payload.title(), payload.message()));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                payload.success() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, 1.0F, 0.6F));
    }
}
