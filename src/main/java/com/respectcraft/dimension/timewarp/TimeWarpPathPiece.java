package com.respectcraft.dimension.timewarp;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Caminho simples de um assentamento (Etapa 4.15.2): de (x1, z1) a (x2, z2) em L (primeiro x, depois z), em cascalho
 * com falhas, seguindo o terreno. Onde passa por agua vira uma pontezinha de tabuas na altura do mar. Coordenadas do
 * mundo (sem orientacao); aleatoriedade do {@code seed} da peca, por coluna, igual em todos os chunks.
 */
public final class TimeWarpPathPiece extends StructurePiece {
    private final int x1, z1, x2, z2;
    private final long seed;

    public TimeWarpPathPiece(int x1, int z1, int x2, int z2, long seed) {
        super(TimeWarpStructures.PATH_PIECE.get(), 0, new BoundingBox(Math.min(x1, x2), TimeWarp.SEA_LEVEL - 24,
                Math.min(z1, z2), Math.max(x1, x2), TimeWarp.SEA_LEVEL + 40, Math.max(z1, z2)));
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.seed = seed;
        setOrientation(null);
    }

    public TimeWarpPathPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(TimeWarpStructures.PATH_PIECE.get(), tag);
        this.x1 = tag.getInt("X1");
        this.z1 = tag.getInt("Z1");
        this.x2 = tag.getInt("X2");
        this.z2 = tag.getInt("Z2");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X1", x1);
        tag.putInt("Z1", z1);
        tag.putInt("X2", x2);
        tag.putInt("Z2", z2);
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource chunkRandom,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) column(level, box, x, z1);
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) column(level, box, x2, z);
    }

    private void column(WorldGenLevel level, BoundingBox box, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        if (!box.isInside(pos.setY(TimeWarp.SEA_LEVEL))) return;
        RandomSource r = RandomSource.create(seed ^ (x * 341873128712L + z * 132897987541L));
        int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        pos.setY(top);
        BlockState ground = level.getBlockState(pos);
        if (top < TimeWarp.SEA_LEVEL - 1 || !level.getFluidState(pos.above()).isEmpty()) {
            // Agua: pontezinha de tabuas na superficie (cerca de apoio de vez em quando).
            pos.setY(TimeWarp.SEA_LEVEL);
            if (level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            return;
        }
        // Nao pisa em construcao: so troca grama/terra/areia do topo.
        if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.SAND))) return;
        int roll = r.nextInt(10);
        if (roll == 0) return; // trecho gasto
        level.setBlock(pos, roll <= 7 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 2);
        BlockPos above = pos.above();
        BlockState up = level.getBlockState(above);
        if (up.is(Blocks.SHORT_GRASS) || up.is(Blocks.DANDELION) || up.is(Blocks.POPPY)) level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        // Cascalho sobre agua/ar desaba; segura com terra embaixo.
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) level.setBlock(below, Blocks.DIRT.defaultBlockState(), 2);
    }
}
