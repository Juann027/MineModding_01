package com.tupinicraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tupinicraft.election.ElectionCalendar;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.mandate.Mandate;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.mandate.SeatKey;
import com.tupinicraft.origin.OriginService;
import com.tupinicraft.origin.RegionalOrigin;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.territory.Territories;
import com.tupinicraft.territory.Territory;
import com.tupinicraft.territory.TerritoryType;
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
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/** /brasil admin ... — exige permissão de operador nível 2. */
final class AdminCommands {
    private static final int PERMISSION_LEVEL = 2;
    private static final int MAX = PoliticalData.MAX_VALUE;

    private static final SuggestionProvider<CommandSourceStack> OFFICE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PoliticalOffice.values()).map(PoliticalOffice::id), builder);
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
                                        .suggests(BrasilCommands.ELECTIVE_OFFICE_SUGGESTIONS)
                                        .executes(context -> startElection(context, 1, false))
                                        .then(Commands.argument("territorio", StringArgumentType.word())
                                                .suggests(BrasilCommands.TERRITORY_SUGGESTIONS)
                                                .executes(context -> startElection(context, 1, true))
                                                .then(Commands.argument("cargo2", StringArgumentType.word())
                                                        .suggests(BrasilCommands.ELECTIVE_OFFICE_SUGGESTIONS)
                                                        .then(Commands.argument("territorio2", StringArgumentType.word())
                                                                .suggests(BrasilCommands.TERRITORY_SUGGESTIONS)
                                                                .executes(context -> startElection(context, 2, true)))))))
                        .then(Commands.literal("cancelar").executes(context -> BrasilCommands.report(context.getSource(),
                                ElectionManager.cancel(context.getSource().getServer()), true)))
                        .then(Commands.literal("avancar").executes(context -> BrasilCommands.report(context.getSource(),
                                ElectionManager.advance(context.getSource().getServer()), true))))
                .then(ConfigCommands.build())
                .then(SkillCommands.adminTest())
                .then(Commands.literal("mandato")
                        .then(Commands.literal("encerrar")
                                .then(Commands.argument("territorio", StringArgumentType.word())
                                        .suggests(BrasilCommands.TERRITORY_SUGGESTIONS)
                                        .then(Commands.argument("cargo", StringArgumentType.word())
                                                .suggests(BrasilCommands.ELECTIVE_OFFICE_SUGGESTIONS)
                                                .executes(AdminCommands::endMandate)))))
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

    /**
     * /brasil admin eleicao iniciar &lt;cargo&gt; [territorio] [cargo2 territorio2]. Sem território, só Presidente
     * (Brasil) é aceito; os demais cargos exigem o território (estado ou município).
     */
    private static int startElection(CommandContext<CommandSourceStack> context, int races, boolean withTerritory) {
        List<SeatKey> seats = new ArrayList<>();
        for (int i = 1; i <= races; i++) {
            String officeArg = i == 1 ? "cargo" : "cargo2";
            String territoryArg = i == 1 ? "territorio" : "territorio2";
            String officeId = StringArgumentType.getString(context, officeArg);
            Optional<PoliticalOffice> office = PoliticalOffice.byId(officeId).filter(PoliticalOffice::isElective);
            if (office.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Cargo desconhecido: " + officeId + ". Use: " + electiveIds()));
                return 0;
            }
            Optional<Territory> territory;
            if (withTerritory) {
                String territoryId = StringArgumentType.getString(context, territoryArg);
                territory = Territories.get(territoryId);
                if (territory.isEmpty()) {
                    context.getSource().sendFailure(Component.literal("Território desconhecido: " + territoryId
                            + ". Ex.: brasil, es, sp, vitoria, sao_paulo."));
                    return 0;
                }
            } else if (office.get().jurisdiction() == TerritoryType.COUNTRY) {
                territory = Optional.of(Territories.brasil());
            } else {
                context.getSource().sendFailure(Component.literal("Informe o território: /brasil admin eleicao iniciar "
                        + office.get().id() + " <" + office.get().jurisdiction().displayName().toLowerCase(Locale.ROOT) + ">"));
                return 0;
            }
            if (territory.get().type() != office.get().jurisdiction()) {
                context.getSource().sendFailure(Component.literal(office.get().displayName() + " é disputado em território do tipo "
                        + office.get().jurisdiction().displayName() + "; " + territory.get().name() + " é "
                        + territory.get().type().displayName() + "."));
                return 0;
            }
            seats.add(new SeatKey(territory.get().id(), office.get(), 1));
        }
        return BrasilCommands.report(context.getSource(),
                ElectionManager.start(context.getSource().getServer(), seats, ElectionCalendar.MANUAL), true);
    }

    private static int endMandate(CommandContext<CommandSourceStack> context) {
        String territoryId = StringArgumentType.getString(context, "territorio");
        String officeId = StringArgumentType.getString(context, "cargo");
        Optional<Territory> territory = Territories.get(territoryId);
        Optional<PoliticalOffice> office = PoliticalOffice.byId(officeId).filter(PoliticalOffice::isElective);
        if (territory.isEmpty() || office.isEmpty() || territory.get().type() != office.get().jurisdiction()) {
            context.getSource().sendFailure(Component.literal("Cadeira inválida: " + territoryId + " / " + officeId + "."));
            return 0;
        }
        SeatKey seat = new SeatKey(territory.get().id(), office.get(), 1);
        Optional<Mandate> removed = MandateService.vacate(context.getSource().getServer(), seat);
        if (removed.isEmpty()) {
            context.getSource().sendFailure(Component.literal("A cadeira de " + MandateService.seatLabel(seat) + " já está vaga."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Mandato de " + removed.get().occupant().displayName()
                + " como " + MandateService.seatLabel(seat) + " encerrado (ação administrativa).").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String electiveIds() {
        return String.join(", ", Arrays.stream(PoliticalOffice.values()).filter(PoliticalOffice::isElective)
                .map(PoliticalOffice::id).toList());
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
