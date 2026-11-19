package com.respectcraft.client.playergeo;

import java.util.function.Function;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * As seis partes que o jogador vanilla e o rig GeckoLib tem em comum. E por elas que a pose passa de um
 * modelo para o outro (ver {@link PlayerPoseBridge}) e e nelas que as Body Layers se prendem.
 */
public enum PlayerPart {
    // BODY vem primeiro: cabeca e bracos sao filhos dele no rig.
    BODY("Body", 0F, 0F, 0F, false, model -> model.body),
    HEAD("Head", 0F, 0F, 0F, true, model -> model.head),
    RIGHT_ARM("RightArm", -5F, 2F, 0F, true, model -> model.rightArm),
    LEFT_ARM("LeftArm", 5F, 2F, 0F, true, model -> model.leftArm),
    RIGHT_LEG("RightLeg", -1.9F, 12F, 0F, false, model -> model.rightLeg),
    LEFT_LEG("LeftLeg", 1.9F, 12F, 0F, false, model -> model.leftLeg);

    private final String boneName;
    private final Vector3f rest;
    private final boolean childOfBody;
    private final Function<HumanoidModel<?>, ModelPart> vanilla;

    PlayerPart(String boneName, float restX, float restY, float restZ, boolean childOfBody,
               Function<HumanoidModel<?>, ModelPart> vanilla) {
        this.boneName = boneName;
        this.rest = new Vector3f(restX, restY, restZ);
        this.childOfBody = childOfBody;
        this.vanilla = vanilla;
    }

    /** Nome do bone no rig (e nos modelos de Body Layer exportados do Blockbench). */
    public String boneName() { return boneName; }

    /** Posicao de repouso do ModelPart vanilla, em pixels (Y para baixo). */
    public Vector3f rest() { return rest; }

    /** No rig, a parte e filha do tronco (cabeca e bracos). */
    public boolean childOfBody() { return childOfBody; }

    public ModelPart vanilla(HumanoidModel<?> model) { return vanilla.apply(model); }

    public static PlayerPart byBone(String boneName) {
        for (PlayerPart part : values()) if (part.boneName.equals(boneName)) return part;
        return null;
    }
}
