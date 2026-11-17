package com.tupinicraft.client.bodylayer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Penteados provisorios montados em codigo (sem arquivo Blockbench), nas coordenadas da cabeca vanilla:
 * X[-4,+4], Y[-8,0], Z[-4,+4]. Um penteado modelado no Blockbench pode substituir qualquer um deles
 * registrando uma Body Layer com {@code GeoGeometry} e um bone "Head".
 */
final class HairMeshes {
    private HairMeshes() {}

    static ModelPart build(int style) {
        CubeListBuilder boxes = CubeListBuilder.create().texOffs(0, 0);
        // O cabelo começa do lado de fora da cabeça para não atravessar o rosto.
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
        mesh.getRoot().addOrReplaceChild("hair", boxes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("hair");
    }
}
