package com.tupinicraft.registry;
import com.tupinicraft.TupiniCraft;
import com.tupinicraft.tigrinho.TigrinhoBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.*;
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(TupiniCraft.MOD_ID);
    public static final DeferredBlock<TigrinhoBlock> TIGRINHO_MACHINE=BLOCKS.register("tigrinho_machine",
        () -> new TigrinhoBlock(BlockBehaviour.Properties.of().strength(2.5f).noOcclusion().sound(SoundType.METAL).lightLevel(s -> 5)));
    private ModBlocks() {}
}
