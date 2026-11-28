package com.respectcraft.dimension.timewarp;

import com.respectcraft.RespectCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Uma construcao do Time Warp (Etapa 4.15.2): casa de madeira, casa de pedregulho, abrigo de terra da primeira noite,
 * plantacao, doca, obra inacabada ou area comum. Tudo vem de um {@code seed} proprio gravado na peca (nunca do
 * {@code RandomSource} do chunk), entao a mesma peca sai identica em todos os chunks que ela cruza e em todo
 * mundo com a mesma seed. O chao ({@code heightPosition}) e decidido na criacao pela altura do gerador.
 *
 * <p>Coordenadas locais: x = largura, z = profundidade (z = 0 e a frente), y = 0 e o piso (o bloco do topo do
 * terreno). A rotacao/espelho do {@link ScatteredFeaturePiece} gira os estados (portas, camas, tochas).
 *
 * <p>Estilo: construcao de jogador das versoes antigas — blocos simples, imperfeicoes pequenas (um vidro faltando,
 * um bloco de terra na parede, a tocha torta), nada destruido. Ninguem mora aqui. Nenhuma entidade e criada.
 */
public final class TimeWarpPiece extends ScatteredFeaturePiece {
    public static final ResourceKey<LootTable> HOUSE_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, RespectCraft.id("chests/time_warp_house"));
    public static final ResourceKey<LootTable> SETTLEMENT_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, RespectCraft.id("chests/time_warp_settlement"));

    /** Tipos de construcao. O NBT grava o nome: novos tipos entram no fim, nunca renomeie. */
    public enum Kind {
        WOOD_HOUSE(7, 7, 7), COBBLE_HOUSE(8, 6, 6), DIRT_HUT(5, 4, 5), FARM(9, 2, 7), DOCK(3, 1, 10),
        UNFINISHED(7, 5, 7), COMMONS(9, 3, 9);

        final int width, height, depth;

        Kind(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        /** Extensao no mundo (x, z) para a orientacao dada. */
        public int extentX(Direction d) { return d.getAxis() == Direction.Axis.Z ? width : depth; }
        public int extentZ(Direction d) { return d.getAxis() == Direction.Axis.Z ? depth : width; }
    }

    /** Recados discretos deixados em placas. Nada explica nada. */
    static final String[][] SIGNS = {
            {"brb", "", "", ""}, {"dont touch", "my chest", "", ""}, {"back soon", "", "", ""},
            {"home", "", "", ""}, {"server restart", "at 9?", "", ""}, {"left at", "sunset", "", ""},
            {"farm", "", "", ""}, {"keep the", "torches on", "", ""}, {"", "", "", ""}};

    private final Kind kind;
    private final long seed;

    public TimeWarpPiece(Kind kind, int minX, int floorY, int minZ, Direction orientation, long seed) {
        super(TimeWarpStructures.PIECE.get(), minX, floorY, minZ, kind.width, kind.height, kind.depth, orientation);
        this.kind = kind;
        this.seed = seed;
        this.heightPosition = floorY;
    }

    public TimeWarpPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(TimeWarpStructures.PIECE.get(), tag);
        Kind k;
        try {
            k = Kind.valueOf(tag.getString("Kind"));
        } catch (IllegalArgumentException e) {
            k = Kind.WOOD_HOUSE;
        }
        this.kind = k;
        this.seed = tag.getLong("Seed");
    }

    /** Peca centrada em (cx, cz) com o piso em {@code floorY}. */
    public static TimeWarpPiece centered(Kind kind, int cx, int floorY, int cz, Direction d, long seed) {
        return new TimeWarpPiece(kind, cx - kind.extentX(d) / 2, floorY, cz - kind.extentZ(d) / 2, d, seed);
    }

    /** Peca cuja frente (meio da largura, z local = 0) fica no ponto (ax, az), crescendo para {@code d}. */
    public static TimeWarpPiece anchored(Kind kind, int ax, int floorY, int az, Direction d, long seed) {
        int half = kind.width / 2, back = kind.depth - 1;
        int minX, minZ;
        switch (d) {
            case NORTH -> { minX = ax - half; minZ = az - back; }
            case SOUTH -> { minX = ax - half; minZ = az; }
            case WEST -> { minX = ax - back; minZ = az - half; }
            default -> { minX = ax; minZ = az - half; }
        }
        return new TimeWarpPiece(kind, minX, floorY, minZ, d, seed);
    }

    public Kind kind() { return kind; }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Kind", kind.name());
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource chunkRandom,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        RandomSource r = RandomSource.create(seed);
        switch (kind) {
            case WOOD_HOUSE -> house(level, box, r, false);
            case COBBLE_HOUSE -> house(level, box, r, true);
            case DIRT_HUT -> hut(level, box, r);
            case FARM -> farm(level, box, r);
            case DOCK -> dock(level, box, r);
            case UNFINISHED -> unfinished(level, box, r);
            case COMMONS -> commons(level, box, r);
        }
    }

    // ============================================================================================= construcoes

    private static BlockState st(Block b) { return b.defaultBlockState(); }

    /** Piso nivelado: limpa o volume, poe o piso e completa a base para baixo (pedregulho/terra). */
    private void foundation(WorldGenLevel level, BoundingBox box, RandomSource r, BlockState floor, BlockState base, int clearHeight) {
        generateAirBox(level, box, 0, 1, 0, kind.width - 1, clearHeight, kind.depth - 1);
        for (int x = 0; x < kind.width; x++) {
            for (int z = 0; z < kind.depth; z++) {
                placeBlock(level, floor, x, 0, z, box);
                fillColumnDown(level, base, x, -1, z, box);
            }
        }
    }

    private void house(WorldGenLevel level, BoundingBox box, RandomSource r, boolean cobble) {
        int w = kind.width, d = kind.depth, wallTop = cobble ? 3 : 4;
        BlockState wall = cobble ? st(Blocks.COBBLESTONE) : st(Blocks.OAK_PLANKS);
        BlockState corner = cobble ? st(Blocks.COBBLESTONE) : st(Blocks.OAK_LOG);
        BlockState floor = cobble ? st(Blocks.OAK_PLANKS) : (r.nextBoolean() ? st(Blocks.OAK_PLANKS) : st(Blocks.COBBLESTONE));
        foundation(level, box, r, floor, st(Blocks.COBBLESTONE), kind.height);
        // Paredes (com cantos de tronco na casa de madeira) e uma imperfeicao: um bloco "emprestado".
        int oddX = 1 + r.nextInt(w - 2), oddY = 1 + r.nextInt(wallTop);
        for (int y = 1; y <= wallTop; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edgeX = x == 0 || x == w - 1, edgeZ = z == 0 || z == d - 1;
                    if (!edgeX && !edgeZ) continue;
                    BlockState s = edgeX && edgeZ ? corner : wall;
                    if (z == d - 1 && x == oddX && y == oddY) s = cobble ? st(Blocks.DIRT) : st(Blocks.COBBLESTONE);
                    placeBlock(level, s, x, y, z, box);
                }
            }
        }
        // Janelas de vidro inteiro (como nas versoes antigas); uma pode estar faltando.
        int missing = r.nextInt(5);
        window(level, box, 0, 2, d / 2, missing == 0);
        window(level, box, w - 1, 2, d / 2, missing == 1);
        window(level, box, w / 2, 2, d - 1, missing == 2);
        if (w >= 8) window(level, box, 1, 2, 0, false);
        // Porta na frente (z = 0); as vezes aberta, como se alguem tivesse acabado de sair.
        int doorX = w / 2;
        door(level, box, doorX, 1, 0, r.nextInt(4) == 0);
        // Telhado: lajes de tabua (ou pedregulho), com o beiral irregular de quem construiu no olho.
        BlockState roof = cobble ? st(Blocks.COBBLESTONE_SLAB) : st(Blocks.OAK_SLAB);
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) placeBlock(level, roof, x, wallTop + 1, z, box);
        if (!cobble && kind.height > wallTop + 1) {
            for (int x = 1; x < w - 1; x++) for (int z = 1; z < d - 1; z++) placeBlock(level, st(Blocks.OAK_PLANKS), x, wallTop + 1, z, box);
            for (int x = 2; x < w - 2; x++) for (int z = 2; z < d - 2; z++) placeBlock(level, roof, x, wallTop + 2, z, box);
        }
        // Interior: cama, bancada, fornalha, bau e tochas.
        bed(level, box, 1, 1, d - 3);
        placeBlock(level, st(Blocks.CRAFTING_TABLE), w - 2, 1, d - 2, box);
        furnace(level, box, r, w - 2, 1, d - 3);
        createChest(level, box, r, 1, 1, 1 + (d > 6 ? 1 : 0), HOUSE_LOOT);
        torch(level, box, 1, 3, d / 2, Direction.EAST);
        if (r.nextBoolean()) torch(level, box, w - 2, 3, 1, Direction.WEST);
        // Fora: tocha ao lado da porta e, as vezes, uma placa.
        torchOutside(level, box, doorX + 1, 2, 0);
        if (r.nextInt(3) == 0) sign(level, box, r, doorX - 2 < 0 ? 0 : doorX - 2, 1, -1);
    }

    private void hut(WorldGenLevel level, BoundingBox box, RandomSource r) {
        int w = kind.width, d = kind.depth;
        foundation(level, box, r, st(Blocks.DIRT), st(Blocks.DIRT), kind.height);
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 || x == w - 1 || z == 0 || z == d - 1) placeBlock(level, st(Blocks.DIRT), x, y, z, box);
                }
            }
        }
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) placeBlock(level, st(Blocks.DIRT), x, 3, z, box);
        // Abertura da porta (sem porta: a primeira noite foi corrida).
        placeBlock(level, Blocks.AIR.defaultBlockState(), w / 2, 1, 0, box);
        placeBlock(level, Blocks.AIR.defaultBlockState(), w / 2, 2, 0, box);
        placeBlock(level, st(Blocks.CRAFTING_TABLE), 1, 1, d - 2, box);
        torch(level, box, 1, 2, 2, Direction.EAST);
        if (r.nextBoolean()) createChest(level, box, r, w - 2, 1, d - 2, HOUSE_LOOT);
        if (r.nextInt(3) == 0) placeBlock(level, st(Blocks.GLASS), 0, 2, d / 2, box);
    }

    private void farm(WorldGenLevel level, BoundingBox box, RandomSource r) {
        int w = kind.width, d = kind.depth;
        foundation(level, box, r, st(Blocks.DIRT), st(Blocks.DIRT), kind.height);
        int cx = w / 2;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                if (edge) {
                    placeBlock(level, st(Blocks.GRASS_BLOCK), x, 0, z, box);
                    // Cerca com um buraco (o portao nunca foi feito).
                    if (!(z == 0 && Math.abs(x - cx) <= 0) && r.nextInt(9) != 0) placeBlock(level, st(Blocks.OAK_FENCE), x, 1, z, box);
                    continue;
                }
                if (x == cx) {
                    placeBlock(level, Blocks.WATER.defaultBlockState(), x, 0, z, box);
                    continue;
                }
                placeBlock(level, st(Blocks.FARMLAND).setValue(FarmBlock.MOISTURE, 7), x, 0, z, box);
                if (r.nextInt(6) != 0) {
                    int age = 2 + r.nextInt(CropBlock.MAX_AGE - 1);
                    placeBlock(level, st(Blocks.WHEAT).setValue(CropBlock.AGE, age), x, 1, z, box);
                }
            }
        }
        if (r.nextBoolean()) torchOnPost(level, box, 0, 0);
    }

    private void dock(WorldGenLevel level, BoundingBox box, RandomSource r) {
        int w = kind.width, d = kind.depth;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (z == d - 1 && x != 1 && r.nextBoolean()) continue; // ponta irregular
                placeBlock(level, st(Blocks.OAK_PLANKS), x, 0, z, box);
                if (z % 3 == 1 && x != 1) fillColumnDown(level, st(Blocks.OAK_FENCE), x, -1, z, box);
            }
        }
        if (r.nextInt(3) != 0) torchOnPostDeck(level, box, 0, d - 2);
    }

    private void unfinished(WorldGenLevel level, BoundingBox box, RandomSource r) {
        int w = kind.width, d = kind.depth;
        foundation(level, box, r, st(Blocks.COBBLESTONE), st(Blocks.COBBLESTONE), kind.height);
        // Paredes ate alturas diferentes: a obra parou no meio.
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (!(x == 0 || x == w - 1 || z == 0 || z == d - 1)) continue;
                int top = (z == d - 1) ? 3 : (x == 0 ? 2 : (z == 0 ? 1 : 0));
                if ((x + z) % 4 == 0 && r.nextBoolean()) top = Math.max(0, top - 1);
                for (int y = 1; y <= top; y++) placeBlock(level, st(Blocks.COBBLESTONE), x, y, z, box);
            }
        }
        // O pilar de terra de quem subiu para enxergar longe.
        for (int y = 1; y <= 4; y++) placeBlock(level, st(Blocks.DIRT), w - 2, y, 1, box);
        placeBlock(level, st(Blocks.CRAFTING_TABLE), 2, 1, d - 2, box);
        createChest(level, box, r, 3, 1, d - 2, HOUSE_LOOT);
        if (r.nextBoolean()) sign(level, box, r, 1, 1, 1);
    }

    private void commons(WorldGenLevel level, BoundingBox box, RandomSource r) {
        int w = kind.width, d = kind.depth;
        foundation(level, box, r, st(Blocks.COBBLESTONE), st(Blocks.COBBLESTONE), kind.height);
        for (int x = 1; x < w - 1; x++) for (int z = 1; z < d - 1; z++) if (r.nextInt(5) == 0) placeBlock(level, st(Blocks.GRAVEL), x, 0, z, box);
        // Poco no meio, fornalhas e bancada coletivas, bau e postes de cerca com tocha nos cantos.
        int cx = w / 2, cz = d / 2;
        placeBlock(level, Blocks.WATER.defaultBlockState(), cx, 0, cz, box);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            placeBlock(level, st(Blocks.COBBLESTONE_SLAB), cx + dir.getStepX(), 1, cz + dir.getStepZ(), box);
        }
        placeBlock(level, st(Blocks.CRAFTING_TABLE), 1, 1, d - 2, box);
        furnace(level, box, r, 2, 1, d - 2);
        furnace(level, box, r, 3, 1, d - 2);
        createChest(level, box, r, w - 2, 1, d - 2, SETTLEMENT_LOOT);
        torchOnPost(level, box, 0, 0);
        torchOnPost(level, box, w - 1, 0);
        torchOnPost(level, box, 0, d - 1);
        if (r.nextBoolean()) torchOnPost(level, box, w - 1, d - 1);
        sign(level, box, r, cx + 2, 1, 1);
    }

    // ============================================================================================= detalhes

    private void window(WorldGenLevel level, BoundingBox box, int x, int y, int z, boolean missing) {
        placeBlock(level, missing ? Blocks.AIR.defaultBlockState() : st(Blocks.GLASS), x, y, z, box);
    }

    private void door(WorldGenLevel level, BoundingBox box, int x, int y, int z, boolean open) {
        BlockState lower = st(Blocks.OAK_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, open);
        placeBlock(level, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), x, y, z, box);
        placeBlock(level, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x, y + 1, z, box);
    }

    private void bed(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
        BlockState bed = st(Blocks.RED_BED).setValue(BedBlock.FACING, Direction.NORTH);
        placeBlock(level, bed.setValue(BedBlock.PART, BedPart.FOOT), x, y, z, box);
        placeBlock(level, bed.setValue(BedBlock.PART, BedPart.HEAD), x, y, z + 1, box);
    }

    private void torch(WorldGenLevel level, BoundingBox box, int x, int y, int z, Direction facing) {
        placeBlock(level, st(Blocks.WALL_TORCH).setValue(WallTorchBlock.FACING, facing), x, y, z, box);
    }

    private void torchOutside(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
        // Na face externa da parede da frente (z = 0), virada para fora (-z local = SOUTH).
        placeBlock(level, st(Blocks.WALL_TORCH).setValue(WallTorchBlock.FACING, Direction.SOUTH), x, y, z - 1, box);
    }

    private void torchOnPost(WorldGenLevel level, BoundingBox box, int x, int z) {
        placeBlock(level, st(Blocks.OAK_FENCE), x, 1, z, box);
        placeBlock(level, st(Blocks.TORCH), x, 2, z, box);
    }

    private void torchOnPostDeck(WorldGenLevel level, BoundingBox box, int x, int z) {
        placeBlock(level, st(Blocks.OAK_FENCE), x, 1, z, box);
        placeBlock(level, st(Blocks.TORCH), x, 2, z, box);
    }

    private void furnace(WorldGenLevel level, BoundingBox box, RandomSource r, int x, int y, int z) {
        placeBlock(level, st(Blocks.FURNACE).setValue(FurnaceBlock.FACING, Direction.SOUTH), x, y, z, box);
        BlockPos pos = getWorldPos(x, y, z);
        if (!box.isInside(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FurnaceBlockEntity furnace && r.nextInt(3) == 0) {
            // Comida pronta esquecida na saida.
            furnace.setItem(2, new ItemStack(r.nextBoolean() ? Items.COOKED_PORKCHOP : Items.COOKED_COD, 1 + r.nextInt(3)));
        }
    }

    private void sign(WorldGenLevel level, BoundingBox box, RandomSource r, int x, int y, int z) {
        placeBlock(level, st(Blocks.OAK_SIGN).setValue(StandingSignBlock.ROTATION, r.nextInt(16)), x, y, z, box);
        BlockPos pos = getWorldPos(x, y, z);
        if (!box.isInside(pos)) return;
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String[] lines = SIGNS[r.nextInt(SIGNS.length)];
            SignText text = new SignText();
            for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.literal(lines[i]));
            // Durante a geracao nao ha Level: setText() chamaria sendBlockUpdated. Carrega o texto pelo codec.
            CompoundTag tag = new CompoundTag();
            SignText finalText = text;
            SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, finalText).result().ifPresent(t -> tag.put("front_text", t));
            sign.loadCustomOnly(tag, level.registryAccess());
        }
    }
}
