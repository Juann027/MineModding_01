package com.respectcraft.registry;

import com.respectcraft.RespectCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocos e block entities do mod (Etapa 4.9). Os itens dos blocos ficam em {@link ModItems#ITEMS}; o conjunto da
 * Madeira Retorcida se registra aqui por {@code block.wood.TwistedWoodBlocks}.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RespectCraft.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RespectCraft.MOD_ID);

    private ModBlocks() {}
}
