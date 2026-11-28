package com.respectcraft.dimension.timewarp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * Construcoes abandonadas do Time Warp (Etapa 4.15.2), geradas pelo sistema normal de estruturas do 1.21.1 (sem
 * nada por tick). {@code settlement = false}: uma casa (madeira, pedregulho ou abrigo de terra) e, as vezes, uma
 * plantacao e uma doca. {@code settlement = true}: area comum, varias casas, caminhos, plantacoes, uma obra
 * inacabada e docas — o "servidor antigo". So nasce em terra: a altura vem do gerador ({@code getBaseHeight}, sem
 * gerar chunks), entao a escolha e deterministica pela seed. Parte das ilhas fica vazia de proposito.
 */
public final class TimeWarpStructure extends Structure {
    public static final MapCodec<TimeWarpStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.BOOL.optionalFieldOf("settlement", false).forGetter(s -> s.settlement)
    ).apply(i, TimeWarpStructure::new));

    /** Chance de uma tentativa em terra de casa isolada virar casa (o resto das ilhas fica vazio). */
    static final float HOUSE_CHANCE = 0.55F;
    /** Fracao minima de terra na amostra 7x7 (passo 12) para um assentamento. */
    static final int SETTLEMENT_MIN_LAND = 30;

    private final boolean settlement;

    public TimeWarpStructure(StructureSettings settings, boolean settlement) {
        super(settings);
        this.settlement = settlement;
    }

    @Override
    public StructureType<?> type() {
        return TimeWarpStructures.TYPE.get();
    }

    /** Topo solido (ignorando agua) pela altura do gerador. */
    static int ground(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState()) - 1;
    }

    static boolean land(GenerationContext ctx, int x, int z, int margin) {
        return ground(ctx, x, z) >= TimeWarp.SEA_LEVEL + margin;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
        RandomSource r = ctx.random();
        if (!land(ctx, x, z, 1)) return Optional.empty();
        if (settlement) {
            int count = 0;
            for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) if (land(ctx, x + i * 12, z + j * 12, 0)) count++;
            if (count < SETTLEMENT_MIN_LAND) return Optional.empty();
        } else {
            for (int[] o : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) if (!land(ctx, x + o[0], z + o[1], 0)) return Optional.empty();
            if (r.nextFloat() >= HOUSE_CHANCE) return Optional.empty();
        }
        int y = ground(ctx, x, z);
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), b -> {
            if (settlement) buildSettlement(b, ctx, x, z, r);
            else buildHouse(b, ctx, x, z, r);
        }));
    }

    private static Direction randomDir(RandomSource r) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(r);
    }

    private static TimeWarpPiece.Kind randomHouse(RandomSource r) {
        int roll = r.nextInt(9);
        return roll < 4 ? TimeWarpPiece.Kind.WOOD_HOUSE : roll < 7 ? TimeWarpPiece.Kind.COBBLE_HOUSE : TimeWarpPiece.Kind.DIRT_HUT;
    }

    private static void buildHouse(StructurePiecesBuilder b, GenerationContext ctx, int x, int z, RandomSource r) {
        List<StructurePiece> placed = new ArrayList<>();
        Direction d = randomDir(r);
        add(b, placed, TimeWarpPiece.centered(randomHouse(r), x, ground(ctx, x, z), z, d, r.nextLong()));
        if (r.nextFloat() < 0.4F) tryNear(b, ctx, placed, TimeWarpPiece.Kind.FARM, x, z, 9, 12, r);
        if (r.nextFloat() < 0.45F) tryDock(b, ctx, placed, x, z, r);
    }

    private static void buildSettlement(StructurePiecesBuilder b, GenerationContext ctx, int x, int z, RandomSource r) {
        List<StructurePiece> placed = new ArrayList<>();
        add(b, placed, TimeWarpPiece.centered(TimeWarpPiece.Kind.COMMONS, x, ground(ctx, x, z), z, randomDir(r), r.nextLong()));
        int houses = 3 + r.nextInt(4);
        double start = r.nextDouble() * Math.PI * 2;
        List<int[]> fronts = new ArrayList<>();
        for (int i = 0; i < houses; i++) {
            double a = start + i * (Math.PI * 2 / houses) + (r.nextDouble() - 0.5) * 0.5;
            TimeWarpPiece.Kind kind = i == houses - 1 ? TimeWarpPiece.Kind.UNFINISHED : (r.nextInt(5) == 0 ? TimeWarpPiece.Kind.DIRT_HUT : randomHouse(r));
            for (int radius = 22 + r.nextInt(6); radius >= 13; radius -= 3) {
                int hx = x + (int) Math.round(Math.cos(a) * radius), hz = z + (int) Math.round(Math.sin(a) * radius);
                if (!land(ctx, hx, hz, 1)) continue;
                // Frente virada para o centro.
                Direction d = Math.abs(x - hx) > Math.abs(z - hz) ? (x > hx ? Direction.WEST : Direction.EAST) : (z > hz ? Direction.NORTH : Direction.SOUTH);
                TimeWarpPiece p = TimeWarpPiece.centered(kind, hx, ground(ctx, hx, hz), hz, d.getOpposite(), r.nextLong());
                if (add(b, placed, p)) {
                    fronts.add(new int[] {hx, hz});
                    break;
                }
            }
        }
        for (int[] f : fronts) b.addPiece(new TimeWarpPathPiece(x, z, f[0], f[1], r.nextLong()));
        int farms = 1 + r.nextInt(2);
        for (int i = 0; i < farms; i++) tryNear(b, ctx, placed, TimeWarpPiece.Kind.FARM, x, z, 12, 30, r);
        int docks = 1 + r.nextInt(2);
        for (int i = 0; i < docks; i++) tryDock(b, ctx, placed, x, z, r);
    }

    private static boolean add(StructurePiecesBuilder b, List<StructurePiece> placed, StructurePiece p) {
        BoundingBox inflated = p.getBoundingBox().inflatedBy(1);
        for (StructurePiece o : placed) if (o.getBoundingBox().intersects(inflated)) return false;
        placed.add(p);
        b.addPiece(p);
        return true;
    }

    private static void tryNear(StructurePiecesBuilder b, GenerationContext ctx, List<StructurePiece> placed, TimeWarpPiece.Kind kind,
                                int x, int z, int minR, int maxR, RandomSource r) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            int radius = minR + r.nextInt(Math.max(1, maxR - minR));
            int fx = x + (int) Math.round(Math.cos(a) * radius), fz = z + (int) Math.round(Math.sin(a) * radius);
            if (!land(ctx, fx, fz, 1) || !land(ctx, fx + 4, fz + 4, 0) || !land(ctx, fx - 4, fz - 4, 0)) continue;
            if (add(b, placed, TimeWarpPiece.centered(kind, fx, ground(ctx, fx, fz), fz, randomDir(r), r.nextLong()))) return;
        }
    }

    /** Doca na primeira margem encontrada numa direcao, apontando para o mar. */
    private static void tryDock(StructurePiecesBuilder b, GenerationContext ctx, List<StructurePiece> placed, int x, int z, RandomSource r) {
        int first = r.nextInt(4);
        for (int k = 0; k < 4; k++) {
            Direction d = Direction.from2DDataValue((first + k) & 3);
            int lx = x, lz = z;
            for (int step = 2; step <= 48; step += 2) {
                int px = x + d.getStepX() * step, pz = z + d.getStepZ() * step;
                if (ground(ctx, px, pz) < TimeWarp.SEA_LEVEL) {
                    // Agua funda o bastante logo adiante (doca, nao rampa).
                    int fx = px + d.getStepX() * 6, fz = pz + d.getStepZ() * 6;
                    if (ground(ctx, fx, fz) >= TimeWarp.SEA_LEVEL - 1) break;
                    if (add(b, placed, TimeWarpPiece.anchored(TimeWarpPiece.Kind.DOCK, lx, TimeWarp.SEA_LEVEL, lz, d, r.nextLong()))) return;
                    break;
                }
                lx = px;
                lz = pz;
            }
        }
    }
}
