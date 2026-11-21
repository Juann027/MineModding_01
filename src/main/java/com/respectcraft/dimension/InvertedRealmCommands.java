package com.respectcraft.dimension;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /respectcraft realm} (permissao 2): acesso de teste ao Inverted Realm enquanto nao ha portal.
 * {@code enter [players]}, {@code leave [players]} e {@code check} (gera a area de chegada e informa o chao; usado
 * pelo teste de fumaca no servidor dedicado).
 */
public final class InvertedRealmCommands {
    private InvertedRealmCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("realm")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enter")
                        .executes(c -> enter(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> enter(c, EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("leave")
                        .executes(c -> leave(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> leave(c, EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("check").executes(c -> check(c, 1))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                .executes(c -> check(c, IntegerArgumentType.getInteger(c, "radius")))));
    }

    private static int enter(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int moved = 0;
        for (ServerPlayer player : players) {
            InvertedRealm.Arrival arrival = InvertedRealm.enter(player);
            if (arrival == null) {
                c.getSource().sendFailure(Component.literal("The Inverted Realm is not loaded on this server."));
                return 0;
            }
            moved++;
            player.sendSystemMessage(Component.literal(arrival.platform()
                    ? "Welcome to the Inverted Realm. No dry ground nearby, so a small platform was built for you."
                    : "Welcome to the Inverted Realm. Use /respectcraft realm leave to go back."));
            String where = arrival.feet().toShortString();
            c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName()
                    + " entered the Inverted Realm at " + where + "."), true);
        }
        return moved;
    }

    private static int leave(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int moved = 0;
        for (ServerPlayer player : players) {
            if (!InvertedRealm.isIn(player)) {
                c.getSource().sendFailure(Component.literal(player.getGameProfile().getName()
                        + " is not in the Inverted Realm."));
                continue;
            }
            String where = InvertedRealm.leave(player).toShortString();
            moved++;
            c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName()
                    + " left the Inverted Realm (back at " + where + ")."), true);
        }
        return moved;
    }

    /**
     * Gera a coluna de chegada (0, 0) e uma amostra de (2r+1)^2 colunas a cada 16 blocos em volta dela; informa o chao
     * de chegada, a altura do terreno de baixo, quantas colunas tem ilha flutuante por cima e quantas tem agua.
     */
    private static int check(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel realm = InvertedRealm.level(c.getSource().getServer());
        if (realm == null) {
            c.getSource().sendFailure(Component.literal("The Inverted Realm is not loaded on this server."));
            return 0;
        }
        InvertedRealm.Arrival arrival = InvertedRealm.findArrival(realm, 0, 0);
        int columns = 0, islands = 0, water = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long sum = 0;
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                int x = i * 16 + 8, z = j * 16 + 8;
                realm.getChunk(x >> 4, z >> 4);
                int top = realm.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int ground = InvertedRealm.groundTop(realm, x, z);
                columns++;
                if (top > InvertedRealm.ISLAND_FLOOR) islands++;
                if (!realm.getBlockState(new BlockPos(x, ground, z)).getFluidState().isEmpty()) water++;
                min = Math.min(min, ground);
                max = Math.max(max, ground);
                sum += ground;
            }
        }
        String block = BuiltInRegistries.BLOCK.getKey(arrival.ground().getBlock()).toString();
        String biome = realm.getBiome(arrival.feet()).unwrapKey().map(k -> k.location().toString()).orElse("?");
        String time = realm.dimensionType().fixedTime().isPresent()
                ? String.valueOf(realm.dimensionType().fixedTime().getAsLong()) : "cycle";
        String summary = "Inverted Realm ready: arrival " + arrival.feet().toShortString() + " on " + block
                + (arrival.platform() ? " (platform)" : "") + ", biome " + biome + ", fixed time " + time
                + " | " + columns + " columns: ground y " + min + ".." + max + " (avg " + (sum / columns) + "), "
                + islands + " under floating islands, " + water + " with water";
        c.getSource().sendSuccess(() -> Component.literal(summary), false);
        return 1;
    }
}
