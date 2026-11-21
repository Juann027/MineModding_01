package com.respectcraft.client.render;

import com.respectcraft.block.wood.TwistedWoodBlocks;
import com.respectcraft.block.wood.TwistedWoodTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * So cliente: materiais e renderizadores das placas da Madeira Retorcida. Os barcos usam o BoatRenderer vanilla, que
 * ja cobre tipos estendidos (camadas e texturas {@code textures/entity/boat|chest_boat/twisted.png}).
 */
public final class TwistedWoodRenderers {
    private TwistedWoodRenderers() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Sheets.addWoodType(TwistedWoodTypes.WOOD));
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TwistedWoodBlocks.SIGN_BE.get(), SignRenderer::new);
        event.registerBlockEntityRenderer(TwistedWoodBlocks.HANGING_SIGN_BE.get(), HangingSignRenderer::new);
    }
}
