package com.tupinicraft.client.playergeo;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Conversao de pose entre o modelo vanilla do jogador e o rig GeckoLib.
 *
 * <p>Espaco "geo": o do GeckoLib (blocos, Y para cima, pes na origem). Espaco "vanilla": o do
 * {@code PlayerModel} (o LivingEntityRenderer aplica {@code scale(-1,-1,1)} e {@code translate(0,-1.501,0)}).
 * Uma pose e descrita pela matriz que leva um ponto do repouso ate a posicao atual, no espaco geo.
 */
public final class PlayerPoseBridge {
    private static final float MODEL_Y = 1.501F;
    private static final float PX = 1F / 16F;

    private PlayerPoseBridge() {}

    /** Coloca a pilha (que esta no espaco vanilla, dentro de uma RenderLayer) no espaco geo. */
    public static void vanillaToGeoSpace(PoseStack stack) {
        stack.translate(0F, MODEL_Y, 0F);
        stack.scale(-1F, -1F, 1F);
    }

    private static Matrix4f geoToVanilla() {
        return new Matrix4f().translate(0F, MODEL_Y, 0F).scale(-1F, -1F, 1F);
    }

    private static Matrix4f vanillaToGeo() {
        return new Matrix4f().scale(-1F, -1F, 1F).translate(0F, -MODEL_Y, 0F);
    }

    /** Pose atual de um ModelPart vanilla como deslocamento repouso -> atual no espaco geo. */
    public static Matrix4f readVanilla(ModelPart part, Vector3f rest, Matrix4f dest) {
        return dest.set(vanillaToGeo())
                .translate(part.x * PX, part.y * PX, part.z * PX)
                .rotateZYX(part.zRot, part.yRot, part.xRot)
                .translate(-rest.x * PX, -rest.y * PX, -rest.z * PX)
                .mul(geoToVanilla());
    }

    /** Aplica a um ModelPart vanilla o deslocamento repouso -> atual dado no espaco geo. */
    public static void writeVanilla(ModelPart part, Vector3f rest, Matrix4f geoDelta) {
        Matrix4f local = geoToVanilla().mul(geoDelta).mul(vanillaToGeo())
                .translate(rest.x * PX, rest.y * PX, rest.z * PX);
        Vector3f angles = local.getEulerAnglesZYX(new Vector3f());
        part.x = local.m30() * 16F;
        part.y = local.m31() * 16F;
        part.z = local.m32() * 16F;
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
    }

    /** Deslocamento repouso -> atual de um bone no espaco geo, somando toda a cadeia de pais. */
    public static Matrix4f boneWorld(GeoBone bone) {
        Deque<GeoBone> chain = new ArrayDeque<>();
        for (GeoBone current = bone; current != null; current = current.getParent()) chain.push(current);
        PoseStack stack = new PoseStack();
        for (GeoBone current : chain) RenderUtil.prepMatrixForBone(stack, current);
        return new Matrix4f(stack.last().pose());
    }

    /**
     * Soma ao bone a rotacao e a posicao que reproduzem {@code localDelta} (deslocamento em relacao ao pai),
     * nas convencoes do GeckoLib (rotacao ZYX em torno do pivo, X da posicao invertido).
     *
     * @param rotationWeight quanto da rotacao vanilla entra (0 = o bone fica so com a rotacao do clip)
     */
    public static void addToBone(GeoBone bone, Matrix4f localDelta, float rotationWeight) {
        Vector3f pivot = new Vector3f(bone.getPivotX() * PX, bone.getPivotY() * PX, bone.getPivotZ() * PX);
        Vector3f moved = localDelta.transformPosition(new Vector3f(pivot)).sub(pivot);
        Vector3f angles = localDelta.getEulerAnglesZYX(new Vector3f());
        bone.setRotX(bone.getRotX() + angles.x * rotationWeight);
        bone.setRotY(bone.getRotY() + angles.y * rotationWeight);
        bone.setRotZ(bone.getRotZ() + angles.z * rotationWeight);
        bone.setPosX(bone.getPosX() - moved.x * 16F);
        bone.setPosY(bone.getPosY() + moved.y * 16F);
        bone.setPosZ(bone.getPosZ() + moved.z * 16F);
    }
}
