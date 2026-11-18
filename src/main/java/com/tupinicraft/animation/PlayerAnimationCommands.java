package com.tupinicraft.animation;

import com.mojang.brigadier.arguments.StringArgumentType;
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
                    ctx.getSource().sendSuccess(() -> Component.literal("Player animations: " + ids), false);
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
                .then(Commands.literal("sequence")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .then(Commands.literal("queda").executes(ctx ->
                                        sequence(ctx, PlayerAnimations.FALL_AND_RECOVER)))
                                .then(Commands.argument("passos", StringArgumentType.greedyString())
                                        .executes(ctx -> sequence(ctx, null)))))
                .then(Commands.literal("next")
                        .then(Commands.argument("jogadores", EntityArgument.players()).executes(ctx -> {
                            int advanced = 0;
                            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "jogadores")) {
                                if (PlayerAnimationService.advance(player)) advanced++;
                            }
                            int total = advanced;
                            ctx.getSource().sendSuccess(() -> Component.literal("Sequences advanced: " + total), false);
                            return total;
                        })))
                .then(Commands.literal("stop")
                        .then(Commands.argument("jogadores", EntityArgument.players()).executes(ctx -> {
                            int stopped = 0;
                            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "jogadores")) {
                                if (PlayerAnimationService.stop(player, PlayerAnimationEvent.EndReason.STOPPED)) stopped++;
                            }
                            int total = stopped;
                            ctx.getSource().sendSuccess(() -> Component.literal("Animations stopped: " + total), false);
                            return total;
                        }))));
    }

    private static PlayerAnimationDef definition(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "animacao");
        PlayerAnimationDef def = PlayerAnimations.get(id);
        if (def == null) ctx.getSource().sendFailure(Component.literal("Unknown animation: " + id));
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
            ctx.getSource().sendFailure(Component.literal("No player could start " + def.id()
                    + " (dead, spectator or playing a higher-priority animation)."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Animation " + def.id() + " started for "
                + total + " player(s)."), false);
        return total;
    }

    /**
     * Passos separados por espaco: {@code id} ou {@code id@ticks} (obrigatorio para loops); sem namespace vale
     * {@code tupinicraft}. Ex.: {@code knockdown hands_and_knees@40 get_up}.
     */
    public static Optional<AnimationSequence> parseSequence(String text, StringBuilder error) {
        AnimationSequence sequence = null;
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            int at = token.lastIndexOf('@');
            String name = at < 0 ? token : token.substring(0, at);
            int ticks = 0;
            if (at >= 0) {
                try {
                    ticks = Integer.parseInt(token.substring(at + 1));
                } catch (NumberFormatException e) {
                    error.append("invalid duration in '").append(token).append("'");
                    return Optional.empty();
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : "tupinicraft:" + name);
            PlayerAnimationDef def = id == null ? null : PlayerAnimations.get(id);
            if (def == null) {
                error.append("unknown animation: ").append(name);
                return Optional.empty();
            }
            sequence = sequence == null ? AnimationSequence.of(def, ticks) : sequence.thenFor(def, ticks);
        }
        if (sequence == null) {
            error.append("provide at least one animation");
            return Optional.empty();
        }
        Optional<String> problem = sequence.problem();
        problem.ifPresent(error::append);
        return problem.isPresent() ? Optional.empty() : Optional.of(sequence);
    }

    private static int sequence(CommandContext<CommandSourceStack> ctx, AnimationSequence preset) throws CommandSyntaxException {
        AnimationSequence sequence = preset;
        if (sequence == null) {
            StringBuilder error = new StringBuilder();
            Optional<AnimationSequence> parsed = parseSequence(StringArgumentType.getString(ctx, "passos"), error);
            if (parsed.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("Invalid sequence: " + error));
                return 0;
            }
            sequence = parsed.get();
        }
        int started = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "jogadores")) {
            if (PlayerAnimationService.playSequence(player, sequence, new CompoundTag(), false)) started++;
        }
        int total = started;
        int ticks = sequence.totalTicks();
        if (total == 0) {
            ctx.getSource().sendFailure(Component.literal("No player could start the sequence "
                    + "(dead, spectator or playing a higher-priority animation)."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Sequence of " + ticks + " ticks started for "
                + total + " player(s)."), false);
        return total;
    }

    private static int together(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerAnimationDef def = definition(ctx);
        if (def == null) return 0;
        ServerPlayer first = EntityArgument.getPlayer(ctx, "primeiro");
        ServerPlayer second = EntityArgument.getPlayer(ctx, "segundo");
        if (!PlayerAnimationService.playTogether(first, def, second, def, new CompoundTag())) {
            ctx.getSource().sendFailure(Component.literal("Could not start the synchronized sequence."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Synchronized sequence " + def.id() + ": "
                + first.getGameProfile().getName() + " + " + second.getGameProfile().getName()), false);
        return 2;
    }
}
