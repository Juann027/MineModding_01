package com.respectcraft.block.wood;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.dispenser.BoatDispenseItemBehavior;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Madeira Retorcida, parte comum (Etapa 4.9): descascar com machado, inflamabilidade, barcos no dispensador e aba
 * criativa. Os renderizadores das placas ficam em {@code client.render.TwistedWoodRenderers}.
 */
public final class TwistedWood {
    private TwistedWood() {}

    public static void register(IEventBus modBus) {
        TwistedWoodBlocks.bootstrap();
        modBus.addListener(TwistedWood::onCommonSetup);
        modBus.addListener(TwistedWood::onBuildCreativeTab);
        NeoForge.EVENT_BUS.addListener(TwistedWood::onToolModification);
    }

    /** Machado: twisted_log -> stripped_twisted_log e twisted_wood -> stripped_twisted_wood, mantendo o eixo. */
    private static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP) return;
        BlockState state = event.getState();
        Block target = null;
        if (state.is(TwistedWoodBlocks.TWISTED_LOG.get())) target = TwistedWoodBlocks.STRIPPED_TWISTED_LOG.get();
        else if (state.is(TwistedWoodBlocks.TWISTED_WOOD.get())) target = TwistedWoodBlocks.STRIPPED_TWISTED_WOOD.get();
        if (target == null) return;
        BlockState stripped = target.defaultBlockState();
        if (state.hasProperty(RotatedPillarBlock.AXIS)) {
            stripped = stripped.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        event.setFinalState(stripped);
    }

    /** Valores do carvalho (troncos 5/5, tabuas e derivados 5/20, estante 30/20) e barcos pelo dispensador. */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            for (Supplier<? extends Block> log : List.<Supplier<? extends Block>>of(TwistedWoodBlocks.TWISTED_LOG,
                    TwistedWoodBlocks.TWISTED_WOOD, TwistedWoodBlocks.STRIPPED_TWISTED_LOG, TwistedWoodBlocks.STRIPPED_TWISTED_WOOD)) {
                fire.setFlammable(log.get(), 5, 5);
            }
            for (Supplier<? extends Block> b : List.<Supplier<? extends Block>>of(TwistedWoodBlocks.TWISTED_PLANKS,
                    TwistedWoodBlocks.TWISTED_STAIRS, TwistedWoodBlocks.TWISTED_SLAB, TwistedWoodBlocks.TWISTED_FENCE,
                    TwistedWoodBlocks.TWISTED_FENCE_GATE)) {
                fire.setFlammable(b.get(), 5, 20);
            }
            fire.setFlammable(TwistedWoodBlocks.TWISTED_BOOKSHELF.get(), 30, 20);
            DispenserBlock.registerBehavior(TwistedWoodBlocks.TWISTED_BOAT.get(),
                    new BoatDispenseItemBehavior(TwistedEnumParams.TWISTED_BOAT.getValue()));
            DispenserBlock.registerBehavior(TwistedWoodBlocks.TWISTED_CHEST_BOAT.get(),
                    new BoatDispenseItemBehavior(TwistedEnumParams.TWISTED_BOAT.getValue(), true));
        });
    }

    /** Tambem na aba vanilla "Building Blocks" (a aba do RespectCraft lista o conjunto em ModCreativeTabs). */
    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (Supplier<? extends ItemLike> item : TwistedWoodBlocks.CREATIVE_ORDER) event.accept(item.get());
        }
    }
}
