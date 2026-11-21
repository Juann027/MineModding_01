package com.respectcraft.dimension;

import com.respectcraft.RespectCraft;
import com.respectcraft.block.wood.TwistedWoodBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Inverted Realm (Etapa 4.9, MVP): dimensao do ceu roxo. Tudo que define o mundo e dado ({@code data/respectcraft/
 * dimension*}, {@code worldgen/...}); aqui ficam so a chave, a viagem de teste (servidor) e a busca de chao seguro.
 * O efeito de ceu/neblina do cliente e {@code client.dimension.InvertedRealmEffects}, registrado pelo mesmo id.
 */
public final class InvertedRealm {
    public static final ResourceLocation ID = RespectCraft.id("inverted_realm");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);
    /** persistentData do jogador: de onde ele veio (dimensao, posicao, olhar) para o {@code leave}. */
    public static final String RETURN_KEY = "respectcraft_realm_return";
    /** Raio (blocos) da busca por chao seco em volta da coluna de chegada. */
    static final int SEARCH_RADIUS = 48;
    static final int SEARCH_STEP = 4;
    /** Altura da plataforma de emergencia (entre o chao e as ilhas flutuantes). */
    static final int PLATFORM_Y = 120;
    /** Ilhas flutuantes so existem acima disto (noise_settings: faixa de y 148 a 236). */
    public static final int ISLAND_FLOOR = 146;

    private InvertedRealm() {}

    /** Onde o jogador chega: o pe fica em {@code feet}; {@code platform} = nenhum chao seguro, plataforma criada. */
    public record Arrival(BlockPos feet, boolean platform, BlockState ground) {}

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean isIn(ServerPlayer player) {
        return player.level().dimension() == LEVEL;
    }

    /** Leva o jogador ao Inverted Realm na mesma coluna x/z (escala 1:1). Devolve null se a dimensao nao existir. */
    public static Arrival enter(ServerPlayer player) {
        ServerLevel realm = level(player.server);
        if (realm == null) return null;
        if (!isIn(player)) saveReturn(player);
        Arrival arrival = findArrival(realm, player.getBlockX(), player.getBlockZ());
        BlockPos feet = arrival.feet();
        player.teleportTo(realm, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, player.getYRot(), 0.0F);
        player.fallDistance = 0.0F;
        return arrival;
    }

    /** Volta para onde o jogador estava ao entrar; sem registro, vai ao ponto de surgimento do Overworld. */
    public static BlockPos leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        CompoundTag back = player.getPersistentData().getCompound(RETURN_KEY);
        player.getPersistentData().remove(RETURN_KEY);
        ServerLevel target = null;
        if (back.contains("dim")) {
            ResourceLocation dim = ResourceLocation.tryParse(back.getString("dim"));
            if (dim != null) target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        }
        if (target != null && target.dimension() != LEVEL) {
            double x = back.getDouble("x"), y = back.getDouble("y"), z = back.getDouble("z");
            player.teleportTo(target, x, y, z, back.getFloat("yaw"), back.getFloat("pitch"));
            player.fallDistance = 0.0F;
            return BlockPos.containing(x, y, z);
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        player.teleportTo(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        player.fallDistance = 0.0F;
        return new BlockPos(spawn.getX(), y, spawn.getZ());
    }

    private static void saveReturn(ServerPlayer player) {
        CompoundTag back = new CompoundTag();
        back.putString("dim", player.level().dimension().location().toString());
        back.putDouble("x", player.getX());
        back.putDouble("y", player.getY());
        back.putDouble("z", player.getZ());
        back.putFloat("yaw", player.getYRot());
        back.putFloat("pitch", player.getXRot());
        player.getPersistentData().put(RETURN_KEY, back);
    }

    /** O registro de volta sobrevive a morte/respawn (o persistentData nao e copiado sozinho). */
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag back = event.getOriginal().getPersistentData().getCompound(RETURN_KEY);
        if (!back.isEmpty()) event.getEntity().getPersistentData().put(RETURN_KEY, back.copy());
    }

    /**
     * Procura chao seco e firme (sem folhas, sem fluido, dois blocos livres acima) em aneis a partir de (x, z), gerando
     * os chunks necessarios. Sem chao num raio de {@link #SEARCH_RADIUS}, cria uma plataforma 3x3 de tabuas retorcidas.
     */
    public static Arrival findArrival(ServerLevel level, int x, int z) {
        // Primeiro so chao natural (grama/micelio: fora de canions e cavernas abertas), depois qualquer chao firme.
        for (boolean natural : new boolean[] {true, false}) {
            for (int r = 0; r <= SEARCH_RADIUS; r += SEARCH_STEP) {
                for (int dx = -r; dx <= r; dx += SEARCH_STEP) {
                    for (int dz = -r; dz <= r; dz += SEARCH_STEP) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // so o perimetro do anel
                        BlockPos feet = safeFeet(level, x + dx, z + dz);
                        if (feet == null) continue;
                        BlockState ground = level.getBlockState(feet.below());
                        if (!natural || ground.is(BlockTags.DIRT)) return new Arrival(feet, false, ground);
                    }
                }
            }
        }
        BlockPos feet = new BlockPos(x, Math.min(PLATFORM_Y, level.getMaxBuildHeight() - 3), z);
        BlockState planks = TwistedWoodBlocks.TWISTED_PLANKS.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(feet.offset(dx, -1, dz), planks);
                level.setBlockAndUpdate(feet.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(feet.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
            }
        }
        return new Arrival(feet, true, planks);
    }

    /**
     * Primeiro bloco nao vazio descendo do topo da coluna, mas comecando abaixo da faixa das ilhas flutuantes: a
     * chegada fica no terreno de baixo, nunca no alto de uma ilha.
     */
    public static int groundTop(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // gera o chunk ate FULL antes de ler o heightmap
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(top, ISLAND_FLOOR), z);
        while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) pos.move(0, -1, 0);
        return pos.getY();
    }

    private static BlockPos safeFeet(ServerLevel level, int x, int z) {
        int y = groundTop(level, x, z) + 1;
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) return null;
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(BlockTags.LEAVES) || !ground.getFluidState().isEmpty()
                || !ground.isFaceSturdy(level, below, Direction.UP)) return null;
        for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return null;
        }
        return feet;
    }
}
