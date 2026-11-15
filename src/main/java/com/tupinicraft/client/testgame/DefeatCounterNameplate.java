package com.tupinicraft.client.testgame;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tupinicraft.network.DefeatCountClientCache;
import com.tupinicraft.network.FinisherVisualClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * Desenha o número em uma pequena linha ACIMA do nick, sem modificar equipes,
 * prefixos, scoreboard ou nomes customizados por outros mods.
 */
public final class DefeatCounterNameplate {
    private static final float SCALE = 0.025F;
    private DefeatCounterNameplate() {}

    public static void render(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isInvisible() || player.isSpectator()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Font font = mc.font;
        boolean activeTest = FinisherVisualClientCache.get(player.getUUID()) != null;
        Component number = Component.literal(String.valueOf(DefeatCountClientCache.get(player.getUUID()))
                + (activeTest ? " [TESTE]" : ""));
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Um pouco acima do nameplate padrão; os posicionamentos do modelo/GeckoLib
        // continuam independentes. A posição pode ser calibrada após o primeiro teste.
        pose.translate(0.0D, player.getBbHeight() + 0.88D, 0.0D);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-SCALE, -SCALE, SCALE);
        font.drawInBatch(number, -font.width(number) / 2f, -10f, 0xFF_F5C36B,
                false, pose.last().pose(), event.getMultiBufferSource(),
                Font.DisplayMode.NORMAL, 0x66000000, event.getPackedLight());
        pose.popPose();
    }
}
