package com.tupinicraft.client.bodylayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

/** Desenha um bone (e seus filhos) de um modelo GeckoLib ja carregado, sem animatable nem controller. */
final class GeoMeshRenderer {
    private GeoMeshRenderer() {}

    static void renderBone(PoseStack stack, GeoBone bone, VertexConsumer buffer, int light, int overlay, int color) {
        if (bone.isHidden()) return;
        stack.pushPose();
        RenderUtil.prepMatrixForBone(stack, bone);
        for (GeoCube cube : bone.getCubes()) {
            stack.pushPose();
            RenderUtil.translateToPivotPoint(stack, cube);
            RenderUtil.rotateMatrixAroundCube(stack, cube);
            RenderUtil.translateAwayFromPivotPoint(stack, cube);
            Matrix3f normalMatrix = stack.last().normal();
            Matrix4f pose = stack.last().pose();
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
                RenderUtil.fixInvertedFlatCube(cube, normal);
                for (GeoVertex vertex : quad.vertices()) {
                    Vector4f position = pose.transform(new Vector4f(vertex.position().x(), vertex.position().y(),
                            vertex.position().z(), 1F));
                    buffer.addVertex(position.x(), position.y(), position.z(), color, vertex.texU(), vertex.texV(),
                            overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
            stack.popPose();
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) renderBone(stack, child, buffer, light, overlay, color);
        }
        stack.popPose();
    }
}
