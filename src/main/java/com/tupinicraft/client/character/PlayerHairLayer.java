package com.tupinicraft.client.character;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tupinicraft.TupiniCraft;
import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartPose;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Protótipos geométricos de cabelo (sem arquivos Blockbench) anexados ao bone head.
 * Respeita as rotações/poses da cabeça, inclusive em animações vanilla.
 * A textura branca é tingida pela cor selecionada por jogador.
 */
public final class PlayerHairLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE_TEXTURE = TupiniCraft.id("textures/entity/character/hair_white.png");
    private final ModelPart[] styles = new ModelPart[CharacterAppearance.HAIR_IDS.length];

    public PlayerHairLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        for (int i = 1; i < styles.length; i++) styles[i] = build(i);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CharacterAppearance data = CharacterAppearanceClientCache.get(player.getUUID());
        if (!data.created() || data.hairStyle() == 0) return;
        ModelPart model = styles[data.hairStyle()];
        if (model == null) return;
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));
        model.render(poseStack, vertex, light, OverlayTexture.NO_OVERLAY,
                CharacterAppearance.COLOR_ARGB[data.hairColor()]);
        poseStack.popPose();
    }

    private static ModelPart build(int style) {
        CubeListBuilder boxes = CubeListBuilder.create().texOffs(0, 0);
        // A cabeça vanilla ocupa X[-4,+4], Y[-8,0], Z[-4,+4].
        // O cabelo começa do lado de fora para não atravessar o rosto.
        boxes.addBox(-4.25f, -8.8f, -4.25f, 8.5f, 1.15f, 8.5f);
        boxes.addBox(-4.45f, -8f, -4.25f, 0.7f, 3f, 8.5f);
        boxes.addBox(3.75f, -8f, -4.25f, 0.7f, 3f, 8.5f);
        switch (style) {
            case 2 -> { // franja
                boxes.addBox(-3.5f, -7.8f, -4.95f, 5.5f, 2.3f, 0.75f);
                boxes.addBox(1.9f, -7.8f, -4.8f, 1.9f, 1.1f, 0.7f);
            }
            case 3 -> { // cacheado, volumes pequenos
                for (int x = -4; x <= 2; x += 3) {
                    boxes.addBox(x, -10.2f, -4.2f, 2.8f, 1.5f, 2.8f);
                    boxes.addBox(x, -10.2f, 1.4f, 2.8f, 1.5f, 2.8f);
                }
                boxes.addBox(-5f, -8.3f, -3.8f, 1.1f, 4.5f, 7.6f);
                boxes.addBox(3.9f, -8.3f, -3.8f, 1.1f, 4.5f, 7.6f);
            }
            case 4 -> { // longo: mechas laterais e atrás
                boxes.addBox(-5f, -7.8f, -3.7f, 1.3f, 11f, 7.6f);
                boxes.addBox(3.7f, -7.8f, -3.7f, 1.3f, 11f, 7.6f);
                boxes.addBox(-3.7f, -7.9f, 3.8f, 7.4f, 11f, 1.25f);
            }
            case 5 -> { // rabo-de-cavalo: volume atrás da cabeça
                boxes.addBox(-3.8f, -7.9f, 3.9f, 7.6f, 3.2f, 1f);
                boxes.addBox(-1.9f, -5.3f, 4.5f, 3.8f, 4.5f, 3f);
                boxes.addBox(-1.4f, -1f, 5.4f, 2.8f, 6.5f, 2.1f);
            }
            case 6 -> { // moicano: crista central e laterais curtas
                boxes.addBox(-1.3f, -12.5f, -4.2f, 2.6f, 3.9f, 8.4f);
                boxes.addBox(-1.8f, -10.5f, -1.1f, 3.6f, 2.2f, 3f);
            }
            default -> { /* curto: só a cobertura base */ }
        }
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hair", boxes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("hair");
    }
}
