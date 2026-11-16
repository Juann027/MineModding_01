package com.tupinicraft.client.playergeo;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.lwjgl.glfw.GLFW;

/**
 * TESTE VOLUNTARIO, so neste cliente: F8 liga/desliga o render Geo, F9 executa wave.
 * O player vanilla permanece intacto quando F8 esta desligado.
 * O preview nao representa um renderer de producao (itens, armaduras e capas nao sao re-renderizados).
 */
public final class PlayerGeoTestKeys {
    private static final KeyMapping TOGGLE = new KeyMapping("key.tupinicraft.geo_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.tupinicraft");
    private static final KeyMapping WAVE = new KeyMapping("key.tupinicraft.geo_wave",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.tupinicraft");
    private static final PreviewPlayerAnimatable PREVIEW = new PreviewPlayerAnimatable();
    private static final PreviewPlayerGeoRenderer RENDERER = new PreviewPlayerGeoRenderer();
    private static boolean enabled;

    private PlayerGeoTestKeys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(WAVE);
    }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            enabled = !enabled;
            mc.player.displayClientMessage(Component.literal("TupiniCraft Gecko player preview: "
                    + (enabled ? "ON (F5 para terceira pessoa)" : "OFF")), true);
        }
        while (WAVE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            PREVIEW.setPlayer(mc.player);
            if (!enabled) enabled = true;
            PREVIEW.wave();
            mc.player.displayClientMessage(Component.literal("TupiniCraft: wave (GeckoLib)"), true);
        }
    }

    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        // Ativa somente a representacao local no F5; nao afeta o servidor nem outros jogadores.
        if (mc.player == null || event.getEntity() != mc.player
                || !(event.getEntity() instanceof AbstractClientPlayer player)
                || player.isInvisible() || player.isSpectator() || !player.isAlive()
                || mc.options.getCameraType().isFirstPerson()) return;

        PREVIEW.setPlayer(player);
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
            stack.mulPose(Axis.YP.rotationDegrees(180F - yaw));
            ResourceLocation texture = player.getSkin().texture();
            RenderType renderType = RenderType.entityCutoutNoCull(texture);
            RENDERER.render(stack, PREVIEW, event.getMultiBufferSource(), renderType,
                    event.getMultiBufferSource().getBuffer(renderType), event.getPackedLight(),
                    event.getPartialTick());
            event.setCanceled(true);
        } finally {
            stack.popPose();
        }
    }

    public static void clear() {
        enabled = false;
        PREVIEW.setPlayer(null);
    }
}
