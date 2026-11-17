package com.tupinicraft.animation;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /tupinianim} (operadores): tocar, parar e listar animacoes de jogadores a partir do servidor. */
public final class PlayerAnimationCommands {
    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(PlayerAnimations.all().stream().map(PlayerAnimationDef::id), builder);

    private PlayerAnimationCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tupinianim")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    String ids = PlayerAnimations.all().stream().map(def -> def.id().toString())
                            .collect(Collectors.joining(", "));
                    ctx.getSource().sendSuccess(() -> Component.literal("Animações de jogador: " + ids), false);
                    return PlayerAnimations.all().size();
                }))
                .then(Commands.literal("play")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .then(Commands.argument("animacao", ResourceLocationArgument.id()).suggests(IDS)
                                        .executes(ctx -> play(ctx, false))
                                        .then(Commands.literal("loop").executes(ctx -> play(ctx, true))))))
                .then(Commands.literal("together")
                        .then(Commands.argument("primeiro", EntityArgument.player())
                                .then(Commands.argument("segundo", EntityArgument.player())
                                        .then(Commands.argument("animacao", ResourceLocationArgument.id()).suggests(IDS)
                                                .executes(PlayerAnimationCommands::together)))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("jogadores", EntityArgument.players()).executes(ctx -> {
                            int stopped = 0;
                            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "jogadores")) {
                                if (PlayerAnimationService.stop(player, PlayerAnimationEvent.EndReason.STOPPED)) stopped++;
                            }
                            int total = stopped;
                            ctx.getSource().sendSuccess(() -> Component.literal("Animações interrompidas: " + total), false);
                            return total;
                        }))));
    }

    private static PlayerAnimationDef definition(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "animacao");
        PlayerAnimationDef def = PlayerAnimations.get(id);
        if (def == null) ctx.getSource().sendFailure(Component.literal("Animação desconhecida: " + id));
        return def;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, boolean forceLoop) throws CommandSyntaxException {
        PlayerAnimationDef def = definition(ctx);
        if (def == null) return 0;
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "jogadores");
        int started = 0;
        for (ServerPlayer player : players) {
            if (PlayerAnimationService.play(player, def, player.serverLevel().getGameTime(), Optional.empty(),
                    new CompoundTag(), false, forceLoop || def.loop())) started++;
        }
        int total = started;
        if (total == 0) {
            ctx.getSource().sendFailure(Component.literal("Nenhum jogador pôde iniciar " + def.id()
                    + " (morto, espectador ou com animação de prioridade maior)."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Animação " + def.id() + " iniciada em "
                + total + " jogador(es)."), false);
        return total;
    }

    private static int together(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerAnimationDef def = definition(ctx);
        if (def == null) return 0;
        ServerPlayer first = EntityArgument.getPlayer(ctx, "primeiro");
        ServerPlayer second = EntityArgument.getPlayer(ctx, "segundo");
        if (!PlayerAnimationService.playTogether(first, def, second, def, new CompoundTag())) {
            ctx.getSource().sendFailure(Component.literal("Não foi possível iniciar a sequência sincronizada."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Sequência sincronizada " + def.id() + ": "
                + first.getGameProfile().getName() + " + " + second.getGameProfile().getName()), false);
        return 2;
    }
}
