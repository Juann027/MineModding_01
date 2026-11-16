package com.tupinicraft.client.playergeo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Renderizador de depuracao, opt-in; nao substitui o player vanilla no jogo normal. */
public final class PreviewPlayerGeoRenderer extends GeoObjectRenderer<PreviewPlayerAnimatable> {
    private static final float VANILLA_PLAYER_SCALE = 0.9375F;

    public PreviewPlayerGeoRenderer() {
        super(new PreviewPlayerGeoModel());
        // O PlayerRenderer vanilla desenha o jogador em 15/16 (32 px = 1,875 bloco). Sem isto o rig fica
        // com 2 blocos de altura, maior que o jogador vanilla e que a hitbox (medido em teste real).
        withScale(VANILLA_PLAYER_SCALE);
    }

    /** Mesmo flash vermelho de dano do jogador vanilla (o padrao do GeoObjectRenderer e sem overlay). */
    @Override
    public int getPackedOverlay(PreviewPlayerAnimatable animatable, float u, float partialTick) {
        AbstractClientPlayer player = animatable.player();
        boolean hurt = player != null && (player.hurtTime > 0 || player.deathTime > 0);
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(hurt));
    }

    @Override
    public void preRender(PoseStack stack, PreviewPlayerAnimatable animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource buffers, @Nullable VertexConsumer vertex,
                          boolean reRender, float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(stack, animatable, model, buffers, vertex, reRender,
                partialTick, packedLight, packedOverlay, color);
        // GeoObjectRenderer foi pensado inicialmente para objetos e desloca-os em +0.5.
        // No evento do jogador ja estamos na origem da entidade: cancelar este deslocamento.
        stack.translate(-0.5F, -0.51F, -0.5F);
    }
}
