package com.respectcraft.dimension.timewarp;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.respectcraft.dimension.timewarp.TimeWarpExposure.Happening;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * {@code /respectcraft warp} (permissao 2): diagnostico e acesso administrativo do Time Warp (Etapa 4.15).
 * <ul>
 *   <li>{@code enter|leave [players]}: a mesma viagem do relogio (escala 3,33 e chegada segura), sem relogio/cooldown;</li>
 *   <li>{@code status [player]}: exposicao, estagio, ponto de entrada, cooldown;</li>
 *   <li>{@code exposure <players> <seconds>}: define a exposicao (teste dos estagios);</li>
 *   <li>{@code happen <player> <acontecimento>}: dispara um acontecimento agora;</li>
 *   <li>{@code survey [radius] [seed]}: mede agua/terra e conta ilhas pela altura do gerador (sem gerar chunks),
 *   com a seed do mundo ou outra qualquer;</li>
 *   <li>{@code census [radius] [seed]}: conta casas, assentamentos e construcoes pelas tentativas dos conjuntos de
 *   estruturas (mesmo calculo da geracao, sem gerar chunks);</li>
 *   <li>{@code pieces}: as construcoes da estrutura onde o jogador esta (tipo e centro);</li>
 *   <li>{@code check}: gera a chegada em (0, 0) e confere blocos do Inverted Realm (teste de fumaca).</li>
 * </ul>
 */
public final class TimeWarpCommands {
    private TimeWarpCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("warp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enter")
                        .executes(c -> travel(c, List.of(c.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> travel(c, EntityArgument.getPlayers(c, "players"), true))))
                .then(Commands.literal("leave")
                        .executes(c -> travel(c, List.of(c.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> travel(c, EntityArgument.getPlayers(c, "players"), false))))
                .then(Commands.literal("status")
                        .executes(c -> status(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> status(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("exposure")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(c -> exposure(c, EntityArgument.getPlayers(c, "players"),
                                                IntegerArgumentType.getInteger(c, "seconds"))))))
                .then(Commands.literal("happen")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("happening", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Happening.values()).map(Happening::id), b))
                                        .executes(c -> happen(c, EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "happening"))))))
                .then(Commands.literal("survey")
                        .executes(c -> survey(c, 2048, null))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(256, 16384))
                                .executes(c -> survey(c, IntegerArgumentType.getInteger(c, "radius"), null))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(c -> survey(c, IntegerArgumentType.getInteger(c, "radius"), LongArgumentType.getLong(c, "seed"))))))
                .then(Commands.literal("census")
                        .executes(c -> census(c, 4096, null))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(512, 16384))
                                .executes(c -> census(c, IntegerArgumentType.getInteger(c, "radius"), null))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(c -> census(c, IntegerArgumentType.getInteger(c, "radius"), LongArgumentType.getLong(c, "seed"))))))
                .then(Commands.literal("pieces").executes(c -> pieces(c, c.getSource().getPlayerOrException())))
                .then(Commands.literal("check").executes(TimeWarpCommands::check));
    }

    private static int travel(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, boolean enter) {
        int moved = 0;
        for (ServerPlayer p : players) {
            String name = p.getGameProfile().getName();
            if (enter == TimeWarp.isIn(p)) {
                c.getSource().sendFailure(Component.literal(name + (enter ? " is already in the Time Warp." : " is not in the Time Warp.")));
                continue;
            }
            if (enter && p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                c.getSource().sendFailure(Component.literal(name + " must be in the Overworld to enter the Time Warp."));
                continue;
            }
            double fx = p.getX(), fz = p.getZ();
            TimeWarp.Arrival a = enter ? TimeWarp.enter(p) : TimeWarp.exit(p);
            if (a == null) {
                c.getSource().sendFailure(Component.literal("The Time Warp is not loaded on this server."));
                return moved;
            }
            moved++;
            String msg = String.format("%s %s the Time Warp: %.1f, %.1f -> %s %.1f, %.1f, %.1f (%s)", name, enter ? "entered" : "left",
                    fx, fz, a.level().dimension().location(), a.x(), a.y(), a.z(), a.note());
            c.getSource().sendSuccess(() -> Component.literal(msg), true);
        }
        return moved;
    }

    private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        int exposure = TimeWarp.exposure(p);
        var stage = TimeWarpRules.stage(exposure, TimeWarpConfig.stageSeconds());
        long now = TimeWarpTravel.now(p);
        var anchor = TimeWarp.anchor(p);
        String a = anchor.contains("dim") ? String.format("%s %.0f %.0f %.0f", anchor.getString("dim"), anchor.getDouble("x"),
                anchor.getDouble("y"), anchor.getDouble("z")) : "none";
        String msg = String.format("%s: in Time Warp %s | exposure %ds, stage %s | entry anchor %s | cooldown %ds",
                p.getGameProfile().getName(), TimeWarp.isIn(p), exposure / 20, stage.id(), a,
                TimeWarpRules.cooldownLeftSeconds(TimeWarp.lastTravel(p), now, TimeWarpConfig.cooldownTicks()));
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return exposure / 20;
    }

    private static int exposure(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int seconds) {
        for (ServerPlayer p : players) {
            TimeWarp.setExposure(p, seconds * 20);
            TimeWarpExposure.sync(p, false);
        }
        c.getSource().sendSuccess(() -> Component.literal("Time Warp exposure set to " + seconds + "s for " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int happen(CommandContext<CommandSourceStack> c, ServerPlayer p, String id) {
        Happening h = Happening.byId(id);
        if (h == null) {
            c.getSource().sendFailure(Component.literal("Unknown happening: " + id));
            return 0;
        }
        var stage = TimeWarpRules.stage(TimeWarp.exposure(p), TimeWarpConfig.stageSeconds());
        boolean ok = TimeWarpExposure.happen(p, h, stage);
        c.getSource().sendSuccess(() -> Component.literal("Happening " + id + (ok ? " played for " : " had nothing to play for ")
                + p.getGameProfile().getName() + "."), false);
        return ok ? 1 : 0;
    }

    /**
     * Amostra a cada max(16, radius/64) blocos num quadrado de lado 2*radius pela altura do gerador (nao gera chunks). Terra = topo
     * solido acima do mar. Ilhas = componentes conexos (vizinhanca 4) de colunas de terra na grade.
     */
    private static int survey(CommandContext<CommandSourceStack> c, int radius, Long seedArg) {
        ServerLevel warp = TimeWarp.level(c.getSource().getServer());
        if (warp == null) {
            c.getSource().sendFailure(Component.literal("The Time Warp is not loaded on this server."));
            return 0;
        }
        ChunkGenerator gen = warp.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise)) {
            c.getSource().sendFailure(Component.literal("The Time Warp generator is not a noise generator."));
            return 0;
        }
        long seed = seedArg != null ? seedArg : warp.getSeed();
        RandomState state = seedArg == null ? warp.getChunkSource().randomState()
                : RandomState.create(noise.generatorSettings().value(), warp.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        int step = Math.max(16, radius / 64), n = radius * 2 / step + 1; // <= ~129x129 colunas (alguns segundos)
        boolean[][] land = new boolean[n][n];
        int landCount = 0, maxY = Integer.MIN_VALUE, minY = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = -radius + i * step, z = -radius + j * step;
                int top = noise.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, warp, state) - 1;
                maxY = Math.max(maxY, top);
                minY = Math.min(minY, top);
                if (top >= TimeWarp.SEA_LEVEL) {
                    land[i][j] = true;
                    landCount++;
                }
            }
        }
        int islands = 0, biggest = 0, small = 0;
        boolean[][] seen = new boolean[n][n];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!land[i][j] || seen[i][j]) continue;
                islands++;
                int size = 0;
                queue.add(new int[] {i, j});
                seen[i][j] = true;
                while (!queue.isEmpty()) {
                    int[] q = queue.poll();
                    size++;
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int a = q[0] + d[0], b = q[1] + d[1];
                        if (a < 0 || b < 0 || a >= n || b >= n || seen[a][b] || !land[a][b]) continue;
                        seen[a][b] = true;
                        queue.add(new int[] {a, b});
                    }
                }
                biggest = Math.max(biggest, size);
                if (size <= 4) small++;
            }
        }
        int total = n * n;
        double waterPct = 100.0 * (total - landCount) / total;
        double areaKm2 = (2.0 * radius) * (2.0 * radius) / 1_000_000.0;
        String msg = String.format("Time Warp survey seed %d, %dx%d blocks (%d columns, step %d): water %.2f%%, land %.2f%%, %d islands (%.1f per km2, %d small <=4 cells, biggest %d cells ~%d blocks2), top y %d..%d",
                seed, radius * 2, radius * 2, total, step, waterPct, 100 - waterPct, islands, islands / areaKm2, small, biggest,
                biggest * step * step, minY, maxY);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        com.respectcraft.RespectCraft.LOGGER.info(msg);
        return (int) Math.round(waterPct);
    }

    /** Conta as estruturas que nasceriam no quadrado (tentativas dos conjuntos + {@code findGenerationPoint}). */
    private static int census(CommandContext<CommandSourceStack> c, int radius, Long seedArg) {
        ServerLevel warp = TimeWarp.level(c.getSource().getServer());
        if (warp == null || !(warp.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen)) {
            c.getSource().sendFailure(Component.literal("The Time Warp is not loaded on this server."));
            return 0;
        }
        long seed = seedArg != null ? seedArg : warp.getSeed();
        RandomState state = seedArg == null ? warp.getChunkSource().randomState()
                : RandomState.create(gen.generatorSettings().value(), warp.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        var sets = warp.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        StringBuilder sb = new StringBuilder(String.format("Time Warp census seed %d, %dx%d blocks:", seed, radius * 2, radius * 2));
        int total = 0;
        for (String id : new String[] {"time_warp_houses", "time_warp_settlements"}) {
            var set = sets.get(com.respectcraft.RespectCraft.id(id));
            if (set == null || !(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread)) continue;
            int attempts = 0, built = 0, pieces = 0;
            int minChunk = -radius >> 4, maxChunk = radius >> 4, sp = spread.spacing();
            for (int rx = Math.floorDiv(minChunk, sp); rx <= Math.floorDiv(maxChunk, sp); rx++) {
                for (int rz = Math.floorDiv(minChunk, sp); rz <= Math.floorDiv(maxChunk, sp); rz++) {
                    net.minecraft.world.level.ChunkPos cp = spread.getPotentialStructureChunk(seed, rx * sp, rz * sp);
                    if (cp.x < minChunk || cp.x > maxChunk || cp.z < minChunk || cp.z > maxChunk) continue;
                    attempts++;
                    for (var entry : set.structures()) {
                        var structure = entry.structure().value();
                        var start = structure.generate(warp.registryAccess(), gen, gen.getBiomeSource(), state,
                                warp.getStructureManager(), seed, cp, 0, warp, b -> structure.biomes().contains(b));
                        if (start.isValid()) {
                            built++;
                            pieces += start.getPieces().size();
                        }
                    }
                }
            }
            total += built;
            double km2 = (2.0 * radius) * (2.0 * radius) / 1_000_000.0;
            sb.append(String.format(" %s %d built / %d attempts (%.2f per km2, %d pieces);", id, built, attempts, built / km2, pieces));
        }
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        com.respectcraft.RespectCraft.LOGGER.info(msg);
        return total;
    }

    private static int pieces(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        StringBuilder sb = new StringBuilder();
        for (var key : List.of(TimeWarpStructures.HOUSE, TimeWarpStructures.SETTLEMENT)) {
            var structure = registry.get(key);
            if (structure == null) continue;
            var start = level.structureManager().getStructureAt(p.blockPosition(), structure);
            if (!start.isValid()) continue;
            sb.append(key.location().getPath()).append(':');
            for (var piece : start.getPieces()) {
                String kind = piece instanceof TimeWarpPiece tp ? tp.kind().name().toLowerCase(java.util.Locale.ROOT) : "path";
                sb.append(' ').append(kind).append('@').append(piece.getBoundingBox().getCenter().toShortString());
            }
            sb.append(' ');
        }
        String msg = sb.isEmpty() ? "No Time Warp building around " + p.getGameProfile().getName() + "." : sb.toString().trim();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return sb.isEmpty() ? 0 : 1;
    }

    /** Gera a chegada em (0, 0) e confere uma amostra de colunas: nada do Inverted Realm, terreno estavel. */
    private static int check(CommandContext<CommandSourceStack> c) {
        ServerLevel warp = TimeWarp.level(c.getSource().getServer());
        if (warp == null) {
            c.getSource().sendFailure(Component.literal("The Time Warp is not loaded on this server."));
            return 0;
        }
        TimeWarp.Arrival a = TimeWarp.findWarpArrival(warp, 0.5, 0.5, 16);
        int foreign = 0, water = 0, columns = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int x = i * 16 + 8, z = j * 16 + 8;
                warp.getChunk(x >> 4, z >> 4);
                columns++;
                int top = warp.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = top; y >= Math.max(warp.getMinBuildHeight(), top - 12); y--) {
                    BlockState s = warp.getBlockState(pos.set(x, y, z));
                    var key = BuiltInRegistries.BLOCK.getKey(s.getBlock());
                    if ("respectcraft".equals(key.getNamespace()) || s.is(Blocks.PURPLE_TERRACOTTA) || s.is(Blocks.MYCELIUM)
                            || s.is(Blocks.AMETHYST_BLOCK)) foreign++;
                }
                if (!warp.getFluidState(pos.set(x, top, z)).isEmpty()) water++;
            }
        }
        String biome = warp.getBiome(a.block()).unwrapKey().map(k -> k.location().toString()).orElse("?");
        String msg = String.format("Time Warp ready: arrival %s (%s), biome %s, scale %.2f | %d columns, %d with water on top, %d foreign blocks",
                a.block().toShortString(), a.note(), biome, warp.dimensionType().coordinateScale(), columns, water, foreign);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return foreign == 0 ? 1 : 0;
    }
}
