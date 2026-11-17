package com.tupinicraft.client.bodylayer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Malhas do rosto e das maos (Etapa 3.1), nas coordenadas dos ModelParts vanilla. O rosto e uma placa 8x8 sem
 * profundidade logo a frente da cabeca (cabeca: X[-4,4], Y[-8,0], frente em Z=-4), abaixo da camada do chapeu
 * da skin; usa o canto (0,0)-(8,8) de uma textura 16x16. As maos cobrem os 3 px de baixo do braco.
 */
final class FaceMeshes {
    private FaceMeshes() {}

    static ModelPart plate(float z) {
        return bake(CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, z, 8F, 8F, 0F), 16, 16);
    }

    /** Braco direito classic: X[-3,1]; slim: X[-2,1]. Esquerdo classic: X[-1,3]; slim: X[-1,2]. Y[-2,10], Z[-2,2]. */
    static ModelPart hand(boolean right, boolean slim) {
        float width = slim ? 3F : 4F;
        float x = right ? (slim ? -2F : -3F) : -1F;
        return bake(CubeListBuilder.create().texOffs(0, 0).addBox(x, 7F, -2F, width, 3F, 4F, new CubeDeformation(0.06F)), 16, 16);
    }

    private static ModelPart bake(CubeListBuilder boxes, int texWidth, int texHeight) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", boxes, PartPose.ZERO);
        return LayerDefinition.create(mesh, texWidth, texHeight).bakeRoot().getChild("part");
    }
}
