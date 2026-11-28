package com.respectcraft.dimension.timewarp;

import com.respectcraft.RespectCraft;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registro do tipo de estrutura e das pecas do Time Warp (Etapa 4.15.2). As estruturas em si sao dados
 * ({@code worldgen/structure/time_warp_house.json} e {@code time_warp_settlement.json}) com os conjuntos em
 * {@code worldgen/structure_set}. Ids gravados nos chunks: nao renomeie.
 */
public final class TimeWarpStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, RespectCraft.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, RespectCraft.MOD_ID);

    public static final Supplier<StructureType<TimeWarpStructure>> TYPE =
            TYPES.register("time_warp_dwelling", () -> () -> TimeWarpStructure.CODEC);
    public static final Supplier<StructurePieceType> PIECE =
            PIECES.register("time_warp_building", () -> (StructurePieceType) TimeWarpPiece::new);
    public static final Supplier<StructurePieceType> PATH_PIECE =
            PIECES.register("time_warp_path", () -> (StructurePieceType) TimeWarpPathPiece::new);

    public static final ResourceKey<Structure> HOUSE = ResourceKey.create(Registries.STRUCTURE, RespectCraft.id("time_warp_house"));
    public static final ResourceKey<Structure> SETTLEMENT = ResourceKey.create(Registries.STRUCTURE, RespectCraft.id("time_warp_settlement"));

    private TimeWarpStructures() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }
}
