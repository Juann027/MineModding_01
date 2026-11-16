package com.tupinicraft.client.playergeo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Renderizador de depuracao, opt-in; nao substitui o player vanilla no jogo normal. */
public final class PreviewPlayerGeoRenderer extends GeoObjectRenderer<PreviewPlayerAnimatable> {
    public PreviewPlayerGeoRenderer() {
        super(new PreviewPlayerGeoModel());
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
