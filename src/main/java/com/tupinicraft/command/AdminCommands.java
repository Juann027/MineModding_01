package com.tupinicraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.origin.OriginService;
import com.tupinicraft.origin.RegionalOrigin;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.politics.PoliticalOffice;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/** /brasil admin ... — exige permissão de operador nível 2. */
final class AdminCommands {
    private static final int PERMISSION_LEVEL = 2;
    private static final int MAX = PoliticalData.MAX_VALUE;

    private static final SuggestionProvider<CommandSourceStack> OFFICE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PoliticalOffice.values()).map(PoliticalOffice::id), builder);
    private static final SuggestionProvider<CommandSourceStack> ELECTIVE_OFFICE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PoliticalOffice.values())
                    .filter(PoliticalOffice::isElective).map(PoliticalOffice::id), builder);
    private static final SuggestionProvider<CommandSourceStack> UF_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(RegionalOrigin.values()).map(RegionalOrigin::code), builder);

    private AdminCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("eleicao")
                        .then(Commands.literal("iniciar")
                                .then(Commands.argument("cargo", StringArgumentType.word())
                                        .suggests(ELECTIVE_OFFICE_SUGGESTIONS)
                                        .executes(context -> startElection(context, false))
                                        .then(Commands.argument("cargo2", StringArgumentType.word())
                                                .suggests(ELECTIVE_OFFICE_SUGGESTIONS)
                                                .executes(context -> startElection(context, true)))))
                        .then(Commands.literal("cancelar").executes(context -> BrasilCommands.report(context.getSource(),
                                ElectionManager.cancel(context.getSource().getServer()), true)))
                        .then(Commands.literal("avancar").executes(context -> BrasilCommands.report(context.getSource(),
                                ElectionManager.advance(context.getSource().getServer()), true))))
                .then(numericStat("xp", "XP político", PoliticalData::politicalXp, PoliticalData::withPoliticalXp))
                .then(numericStat("popularidade", "Popularidade", PoliticalData::popularity, PoliticalData::withPopularity))
                .then(Commands.literal("cargo")
                        .then(Commands.literal("set")
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .then(Commands.argument("cargo", StringArgumentType.word())
                                                .suggests(OFFICE_SUGGESTIONS)
                                                .executes(AdminCommands::setOffice)))))
                .then(Commands.literal("origem")
                        .then(Commands.literal("set")
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .then(Commands.argument("uf", StringArgumentType.word())
                                                .suggests(UF_SUGGESTIONS)
                                                .executes(AdminCommands::setOrigin)))));
    }

    private static int startElection(CommandContext<CommandSourceStack> context, boolean hasSecond) {
        List<PoliticalOffice> offices = new ArrayList<>();
        for (String argument : hasSecond ? List.of("cargo", "cargo2") : List.of("cargo")) {
            String id = StringArgumentType.getString(context, argument);
            Optional<PoliticalOffice> office = PoliticalOffice.byId(id);
            if (office.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Cargo desconhecido: " + id + ". Use: " + officeIds()));
                return 0;
            }
            offices.add(office.get());
        }
        return BrasilCommands.report(context.getSource(), ElectionManager.start(context.getSource().getServer(), offices), true);
    }

    /** Gera "add" e "set" para um atributo numérico, impedindo valores negativos ou acima do máximo. */
    private static LiteralArgumentBuilder<CommandSourceStack> numericStat(
            String literal, String label, ToIntFunction<PoliticalData> getter,
            BiFunction<PoliticalData, Integer, PoliticalData> setter) {
        return Commands.literal(literal)
                .then(Commands.literal("add")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(-MAX, MAX))
                                        .executes(context -> changeStat(context, label, getter, setter, true)))))
                .then(Commands.literal("set")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(0, MAX))
                                        .executes(context -> changeStat(context, label, getter, setter, false)))));
    }

    private static int changeStat(CommandContext<CommandSourceStack> context, String label,
                                  ToIntFunction<PoliticalData> getter,
                                  BiFunction<PoliticalData, Integer, PoliticalData> setter,
                                  boolean add) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        int amount = IntegerArgumentType.getInteger(context, "quantidade");
        PoliticalData data = PoliticalDataService.get(target);
        int current = getter.applyAsInt(data);
        long result = add ? (long) current + amount : amount;
        if (result < 0) {
            context.getSource().sendFailure(Component.literal(label + " não pode ficar negativo(a): "
                    + target.getGameProfile().getName() + " tem " + current + "."));
            return 0;
        }
        if (result > MAX) {
            context.getSource().sendFailure(Component.literal(label + " não pode passar de " + MAX + "."));
            return 0;
        }
        int newValue = (int) result;
        PoliticalDataService.set(target, setter.apply(data, newValue));
        context.getSource().sendSuccess(() -> Component.literal(label + " de " + target.getGameProfile().getName()
                + ": " + current + " → " + newValue).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setOffice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        String id = StringArgumentType.getString(context, "cargo");
        Optional<PoliticalOffice> office = PoliticalOffice.byId(id);
        if (office.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Cargo desconhecido: " + id + ". Use: " + officeIds()));
            return 0;
        }
        PoliticalDataService.set(target, PoliticalDataService.get(target).withOffice(office.get()));
        context.getSource().sendSuccess(() -> Component.literal("Cargo de " + target.getGameProfile().getName()
                + " definido como " + office.get().displayName() + " (alteração administrativa).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setOrigin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        String code = StringArgumentType.getString(context, "uf");
        Optional<RegionalOrigin> origin = RegionalOrigin.byCode(code);
        if (origin.isEmpty()) {
            context.getSource().sendFailure(Component.literal("UF desconhecida: " + code
                    + ". Use uma das 27 siglas, ex.: MA, ES, RJ, SP, RS, DF."));
            return 0;
        }
        OriginService.setOrigin(target, origin.get());
        context.getSource().sendSuccess(() -> Component.literal("Origem de " + target.getGameProfile().getName()
                + " definida como ").withStyle(ChatFormatting.GREEN)
                .append(OriginService.describe(origin.get())), true);
        return 1;
    }

    private static String officeIds() {
        return String.join(", ", Arrays.stream(PoliticalOffice.values()).map(PoliticalOffice::id).toList());
    }
}
