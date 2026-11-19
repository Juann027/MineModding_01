package com.respectcraft.client.playergeo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Desenha o rig do jogador. A escala 15/16 e a rotacao do corpo ja vem do PlayerRenderer vanilla. */
public final class PlayerRigRenderer extends GeoObjectRenderer<PlayerRigAnimatable> {
    public PlayerRigRenderer() {
        super(new PlayerRigModel());
    }

    @Override
    public long getInstanceId(PlayerRigAnimatable animatable) {
        return animatable.uuid().getLeastSignificantBits();
    }

    /** Mesmo flash vermelho de dano do jogador vanilla (o padrao do GeoObjectRenderer e sem overlay). */
    @Override
    public int getPackedOverlay(PlayerRigAnimatable animatable, float u, float partialTick) {
        AbstractClientPlayer player = animatable.player();
        boolean hurt = player != null && (player.hurtTime > 0 || player.deathTime > 0);
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(hurt));
    }

    @Override
    public void preRender(PoseStack stack, PlayerRigAnimatable animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource buffers, @Nullable VertexConsumer vertex,
                          boolean reRender, float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(stack, animatable, model, buffers, vertex, reRender,
                partialTick, packedLight, packedOverlay, color);
        // GeoObjectRenderer desloca objetos em (+0.5, +0.51, +0.5); aqui a origem ja e os pes do jogador.
        stack.translate(-0.5F, -0.51F, -0.5F);
    }
}
