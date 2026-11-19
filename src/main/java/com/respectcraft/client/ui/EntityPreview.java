package com.respectcraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Previa 3D de uma entidade viva (o proprio jogador, com rig, cabelo e busto das Body Layers), girada por
 * arrasto. Os angulos da entidade sao restaurados depois do desenho.
 */
public final class EntityPreview {
    private EntityPreview() {}

    public static void render(GuiGraphics g, int x1, int y1, int x2, int y2, float scale, float yaw, float pitch,
                              LivingEntity entity) {
        float cx = (x1 + x2) / 2F;
        float cy = (y1 + y2) / 2F;
        g.enableScissor(x1, y1, x2, y2);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180F));
        pose.mul(camera);
        float bodyRot = entity.yBodyRot, yRot = entity.getYRot(), xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO, headRot = entity.yHeadRot, bodyRotO = entity.yBodyRotO;
        entity.yBodyRot = 180F + yaw;
        entity.yBodyRotO = entity.yBodyRot;
        entity.setYRot(180F + yaw);
        entity.setXRot(0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        float entityScale = entity.getScale();
        Vector3f translate = new Vector3f(0F, entity.getBbHeight() / 2F + 0.0625F * entityScale, 0F);
        InventoryScreen.renderEntityInInventory(g, cx, cy, scale / entityScale, translate, pose, camera, entity);
        entity.yBodyRot = bodyRot;
        entity.yBodyRotO = bodyRotO;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
        g.disableScissor();
    }
}
