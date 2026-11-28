package com.respectcraft.dimension.timewarp;

import com.respectcraft.RespectCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Time Warp (Etapa 4.15): dimensao oceanica com cara de mundo antigo, independente do Inverted Realm. Mundo, biomas,
 * ruido e estruturas sao dados ({@code data/respectcraft/dimension*}, {@code worldgen/...}); aqui ficam a chave, o
 * estado persistente do jogador, a viagem segura com escala 3,33:1 e as regras da dimensao (sem dormir/sem ponto de
 * renascimento, sem portal do Nether).
 *
 * <p><b>Escala:</b> o fator vem dos {@code coordinate_scale} ({@code DimensionType.getTeleportationScale}, igual ao
 * vanilla) e e aplicado uma vez, aqui; {@code teleportTo} nao escala nada.
 *
 * <p><b>Persistencia</b> ({@code persistentData["respectcraft_time_warp"]}, copiado na morte): exposicao (ticks), ultimo
 * estagio avisado, ultima viagem (relogio do Overworld) e o ponto de entrada no Overworld (so enquanto o jogador esta
 * no Time Warp; serve a recuperacao sem relogio).
 */
public final class TimeWarp {
    public static final ResourceLocation ID = RespectCraft.id("time_warp");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);
    public static final ResourceKey<Biome> OCEAN = ResourceKey.create(Registries.BIOME, RespectCraft.id("time_warp_ocean"));
    public static final ResourceKey<Biome> ISLAND = ResourceKey.create(Registries.BIOME, RespectCraft.id("time_warp_island"));
    /** {@code sea_level} do {@code noise_settings/time_warp.json}: agua ate y = 63. */
    public static final int SEA_LEVEL = 64;
    public static final String DATA_KEY = "respectcraft_time_warp";
    /** Folga da borda do mundo na chegada. */
    static final double BORDER_MARGIN = 16.0;
    /** Busca de chao seguro no Overworld na saida. */
    static final int EXIT_SEARCH_RADIUS = 32;

    private TimeWarp() {}

    public record Arrival(ServerLevel level, double x, double y, double z, boolean water, String note) {
        public BlockPos block() { return BlockPos.containing(x, y, z); }
    }

    public static ServerLevel level(MinecraftServer server) { return server.getLevel(LEVEL); }

    public static boolean isIn(Player player) { return player.level().dimension() == LEVEL; }

    // ================================================================================================ estado salvo

    public static CompoundTag data(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA_KEY)) root.put(DATA_KEY, new CompoundTag());
        return root.getCompound(DATA_KEY);
    }

    public static int exposure(ServerPlayer p) { return data(p).getInt("exposure"); }
    public static void setExposure(ServerPlayer p, int ticks) { data(p).putInt("exposure", Math.max(0, ticks)); }
    public static long lastTravel(ServerPlayer p) { CompoundTag d = data(p); return d.contains("last_travel") ? d.getLong("last_travel") : -1; }
    static void setLastTravel(ServerPlayer p, long tick) { data(p).putLong("last_travel", tick); }

    static void saveAnchor(ServerPlayer p) {
        CompoundTag a = new CompoundTag();
        a.putString("dim", p.level().dimension().location().toString());
        a.putDouble("x", p.getX());
        a.putDouble("y", p.getY());
        a.putDouble("z", p.getZ());
        a.putFloat("yaw", p.getYRot());
        data(p).put("anchor", a);
    }

    static CompoundTag anchor(ServerPlayer p) { return data(p).getCompound("anchor"); }
    static void clearAnchor(ServerPlayer p) { data(p).remove("anchor"); }

    /** A exposicao e a ultima viagem sobrevivem a morte (o persistentData nao e copiado sozinho). */
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag d = event.getOriginal().getPersistentData().getCompound(DATA_KEY);
        if (!d.isEmpty()) event.getEntity().getPersistentData().put(DATA_KEY, d.copy());
    }

    // ================================================================================================ viagem

    /** Fator vanilla de uma dimensao para outra (le os dois {@code coordinate_scale}). */
    public static double factor(ServerLevel from, ServerLevel to) {
        return DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
    }

    /** Coordenada escalada e dentro da borda do destino. */
    static double scaled(double v, double factor, WorldBorder border, boolean xAxis) {
        double t = TimeWarpRules.toWarp(v, factor);
        return xAxis ? TimeWarpRules.clampToBorder(t, border.getMinX(), border.getMaxX(), BORDER_MARGIN)
                : TimeWarpRules.clampToBorder(t, border.getMinZ(), border.getMaxZ(), BORDER_MARGIN);
    }

    /** Overworld -> Time Warp na coordenada escalada. null = dimensao ausente. */
    public static Arrival enter(ServerPlayer player) {
        ServerLevel warp = level(player.server);
        if (warp == null) return null;
        ServerLevel from = player.serverLevel();
        double f = factor(from, warp);
        double tx = scaled(player.getX(), f, warp.getWorldBorder(), true);
        double tz = scaled(player.getZ(), f, warp.getWorldBorder(), false);
        Arrival a = findWarpArrival(warp, tx, tz, TimeWarpConfig.landSearchRadius());
        if (!isIn(player)) saveAnchor(player);
        move(player, a);
        if (a.water()) {
            // Chegada no mar: cabeca fora d'agua; folga contra afogar enquanto o jogador se orienta.
            player.setAirSupply(player.getMaxAirSupply());
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60 * 20, 0, false, false, true));
        }
        return a;
    }

    /** Time Warp -> Overworld na posicao atual escalada (nunca de volta ao ponto de entrada). */
    public static Arrival exit(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        ServerLevel from = player.serverLevel();
        double f = factor(from, overworld);
        double tx = scaled(player.getX(), f, overworld.getWorldBorder(), true);
        double tz = scaled(player.getZ(), f, overworld.getWorldBorder(), false);
        Arrival a = findOverworldArrival(overworld, tx, tz);
        if (a == null) a = anchorOrSpawn(player, "no safe ground near the scaled point");
        move(player, a);
        clearAnchor(player);
        return a;
    }

    /** Recuperacao sem relogio: volta ao ponto de entrada salvo (sem escala), senao ao surgimento do Overworld. */
    public static Arrival driftBack(ServerPlayer player) {
        Arrival a = anchorOrSpawn(player, "drifted back to the entry point");
        move(player, a);
        clearAnchor(player);
        return a;
    }

    private static Arrival anchorOrSpawn(ServerPlayer player, String note) {
        MinecraftServer server = player.server;
        CompoundTag a = anchor(player);
        if (a.contains("dim")) {
            ResourceLocation dim = ResourceLocation.tryParse(a.getString("dim"));
            ServerLevel target = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (target != null && target.dimension() != LEVEL) {
                BlockPos p = BlockPos.containing(a.getDouble("x"), a.getDouble("y"), a.getDouble("z"));
                target.getChunk(p.getX() >> 4, p.getZ() >> 4);
                if (safeFeet(target, p) || safeWater(target, p)) {
                    return new Arrival(target, a.getDouble("x"), a.getDouble("y"), a.getDouble("z"), false, note);
                }
                Arrival near = findOverworldArrival(target, p.getX() + 0.5, p.getZ() + 0.5);
                if (near != null) return near;
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        return new Arrival(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, false, "world spawn");
    }

    private static void move(ServerPlayer player, Arrival a) {
        player.stopRiding();
        player.teleportTo(a.level(), a.x(), a.y(), a.z(), player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;
    }

    /**
     * Chegada no Time Warp: chao seco num raio pequeno (aneis, mais perto primeiro); sem chao, a superficie do mar na
     * coluna exata (pes dentro do bloco do topo da agua, cabeca fora). Nada e construido.
     */
    public static Arrival findWarpArrival(ServerLevel warp, double x, double z, int radius) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int r = 0; r <= radius; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos feet = topFeet(warp, bx + dx, bz + dz);
                    if (feet != null && feet.getY() > SEA_LEVEL - 1 && safeFeet(warp, feet)) {
                        return new Arrival(warp, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, false, "land");
                    }
                }
            }
        }
        // Mar aberto: superficie na coluna exata (ou a primeira coluna com agua aberta por perto).
        for (int r = 0; r <= 8; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos surface = new BlockPos(bx + dx, SEA_LEVEL - 1, bz + dz);
                    warp.getChunk(surface.getX() >> 4, surface.getZ() >> 4);
                    if (safeWater(warp, surface)) return new Arrival(warp, surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5, true, "ocean");
                }
            }
        }
        BlockPos top = topFeet(warp, bx, bz);
        return new Arrival(warp, bx + 0.5, top == null ? SEA_LEVEL + 1 : top.getY(), bz + 0.5, false, "fallback column");
    }

    /** Chegada no Overworld: chao firme sem fluido; senao superficie de agua; null = nada seguro no raio. */
    public static Arrival findOverworldArrival(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        Arrival water = null;
        for (int r = 0; r <= EXIT_SEARCH_RADIUS; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (!level.getWorldBorder().isWithinBounds(bx + dx, bz + dz)) continue;
                    BlockPos feet = topFeet(level, bx + dx, bz + dz);
                    if (feet == null) continue;
                    if (safeFeet(level, feet)) return new Arrival(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, false, "land");
                    if (water == null && safeWater(level, feet.below())) {
                        water = new Arrival(level, feet.getX() + 0.5, feet.getY() - 1, feet.getZ() + 0.5, true, "water surface");
                    }
                }
            }
            if (water != null && r >= 8) return water;
        }
        return water;
    }

    /** Primeiro bloco livre acima do topo (heightmap sem folhas), gerando o chunk ate FULL. */
    static BlockPos topFeet(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) return null;
        return new BlockPos(x, y, z);
    }

    /** Pes em ar sobre chao firme, sem fluido nem lava, dois blocos livres. */
    static boolean safeFeet(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(BlockTags.LEAVES) || !ground.getFluidState().isEmpty() || ground.is(BlockTags.FIRE)
                || ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || ground.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || !ground.isFaceSturdy(level, below, Direction.UP)) return false;
        for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /** {@code surface} e o bloco do topo de agua (fonte, nao lava) com dois blocos de ar acima. */
    static boolean safeWater(ServerLevel level, BlockPos surface) {
        if (!level.getFluidState(surface).is(FluidTags.WATER)) return false;
        for (BlockPos p : new BlockPos[] {surface.above(), surface.above(2)}) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir()) return false;
        }
        return true;
    }

    // ================================================================================================ regras da dimensao

    /** Ninguem descansa no Time Warp (as camas existem, so nao funcionam). */
    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (event.getLevel().dimension() != LEVEL || event.getProblem() != null) return;
        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        event.getEntity().displayClientMessage(Component.translatable("message.respectcraft.time_warp.no_sleep"), true);
    }

    /** O ponto de renascimento nunca fica no Time Warp (so por comando de admin, {@code forced}). */
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (LEVEL.equals(event.getSpawnLevel()) && !event.isForced() && event.getNewSpawn() != null) event.setCanceled(true);
    }

    /** Portal do Nether nao acende no Time Warp (o vanilla levaria ao Nether e quebraria a viagem do relogio). */
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == LEVEL) event.setCanceled(true);
    }
}
