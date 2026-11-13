package com.tupinicraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;



import com.tupinicraft.skilltree.SkillCredits;
import com.tupinicraft.skilltree.SkillCreditsService;
import com.tupinicraft.skilltree.SkillCreditFormatter;
import com.tupinicraft.party.PartyAffiliation;
import com.tupinicraft.player.OfficeProgress;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.player.SkillProgression;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.registry.ModAttachments;
import com.tupinicraft.skilltree.SkillCatalog;
import com.tupinicraft.skilltree.SkillDef;
import com.tupinicraft.skilltree.SkillGate;
import com.tupinicraft.skilltree.SkillPlayerContext;
import com.tupinicraft.skilltree.SkillTreeState;
import com.tupinicraft.skilltree.SkillTreeKind;
import com.tupinicraft.skilltree.SkillTreeService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * /skills [comprar &lt;skill&gt;] e os comandos administrativos de TESTE
 * (/skilladmin dinheiro|partido|pontos). Economia e partidos gerais não existem: esses comandos
 * só servem para validar as árvores de habilidades.
 */
public final class SkillCommands {
    private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SkillCatalog.get().allSkills().stream().map(SkillDef::id), builder);

    private SkillCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> player() {
        return Commands.literal("skills")
                .executes(SkillCommands::list)
                .then(Commands.literal("comprar")
                        .then(Commands.argument("skill", StringArgumentType.greedyString())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(SkillCommands::purchase)));
    }

    /** Sob /skilladmin (já exige OP). */
    public static LiteralArgumentBuilder<CommandSourceStack> adminTest() {
        return Commands.literal("skilladmin")
                .then(Commands.literal("dinheiro")
                        .then(Commands.literal("set").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("valor", LongArgumentType.longArg(0, SkillCredits.MAX_BALANCE))
                                        .executes(context -> money(context, false)))))
                        .then(Commands.literal("add").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("valor", LongArgumentType.longArg(-SkillCredits.MAX_BALANCE, SkillCredits.MAX_BALANCE))
                                        .executes(context -> money(context, true))))))
                .then(Commands.literal("partido")
                        .then(Commands.literal("set").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("partido", StringArgumentType.word())
                                        .executes(SkillCommands::setParty))))
                        .then(Commands.literal("sair").then(Commands.argument("jogador", EntityArgument.player())
                                .executes(SkillCommands::leaveParty))))
                .then(Commands.literal("pontos")
                        .then(Commands.literal("set").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("cargo", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(PoliticalOffice.values())
                                                        .filter(PoliticalOffice::isElective).map(PoliticalOffice::id), builder))
                                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(0, PoliticalData.MAX_VALUE))
                                                .executes(SkillCommands::setPoints))))));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        SkillPlayerContext ctx = SkillTreeService.context(player);
        SkillTreeState state = SkillTreeService.state(player);
        source.sendSuccess(() -> Component.literal("[SKILLS] Saldo: " + SkillCreditFormatter.format(ctx.money()) + " | Partido: "
                + ctx.party().orElse("nenhum") + " | Mandato: " + ctx.activeOffice().map(PoliticalOffice::displayName).orElse("nenhum")
                + " | Árvores: tecla K").withStyle(ChatFormatting.AQUA), false);
        for (SkillTreeKind kind : SkillTreeKind.values()) {
            source.sendSuccess(() -> Component.literal(kind.displayName() + ":").withStyle(ChatFormatting.GOLD), false);
            SkillCatalog.get().allSkills().stream().filter(skill -> skill.rule().kind() == kind)
                    .sorted(java.util.Comparator.comparing(SkillDef::id)).forEach(skill -> {
                boolean unlocked = state.isUnlocked(skill.id());
                boolean allowed = SkillGate.effectAllowed(skill.rule(), ctx);
                String status = !unlocked ? "à venda" : allowed ? "ativa" : "suspensa";
                source.sendSuccess(() -> Component.literal("  " + skill.id() + " — " + status + " — custo: "
                        + SkillTreeService.cost(skill.rule()) + " — " + SkillTreeService.requirementText(skill.rule()))
                        .withStyle(unlocked ? ChatFormatting.WHITE : ChatFormatting.GRAY), false);
            });
        }
        return 1;
    }

    private static int purchase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SkillTreeService.Outcome outcome = SkillTreeService.purchase(player, StringArgumentType.getString(context, "skill").trim());
        if (outcome.success()) {
            context.getSource().sendSuccess(outcome::message, false);
            return 1;
        }
        context.getSource().sendFailure(outcome.message());
        return 0;
    }

    private static int money(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        long value = LongArgumentType.getLong(context, "valor");
        long before = SkillCreditsService.balance(target);
        long after = add ? before + value : value;
        if (after < 0 || after > SkillCredits.MAX_BALANCE) {
            context.getSource().sendFailure(Component.literal("Saldo inválido: " + after + " (de 0 a " + SkillCredits.MAX_BALANCE + ")."));
            return 0;
        }
        SkillCreditsService.set(target, after);
        context.getSource().sendSuccess(() -> Component.literal("[TESTE] Saldo de " + target.getGameProfile().getName() + ": "
                + SkillCreditFormatter.format(before) + " → " + SkillCreditFormatter.format(after)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setParty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        String party = StringArgumentType.getString(context, "partido");
        if (!PartyAffiliation.isValidId(party)) {
            context.getSource().sendFailure(Component.literal("Id de partido inválido: use 2 a 32 letras minúsculas, números ou _."));
            return 0;
        }
        target.setData(ModAttachments.PARTY, new PartyAffiliation(Optional.of(party)));
        context.getSource().sendSuccess(() -> Component.literal("[TESTE] " + target.getGameProfile().getName()
                + " agora é filiado(a) ao partido de teste \"" + party + "\".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int leaveParty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        target.setData(ModAttachments.PARTY, PartyAffiliation.NONE);
        context.getSource().sendSuccess(() -> Component.literal("[TESTE] " + target.getGameProfile().getName()
                + " saiu do partido (as skills partidárias ficam salvas e inativas).").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setPoints(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        String officeId = StringArgumentType.getString(context, "cargo");
        Optional<PoliticalOffice> office = PoliticalOffice.byId(officeId).filter(PoliticalOffice::isElective);
        if (office.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Cargo desconhecido: " + officeId));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "quantidade");
        PoliticalData data = PoliticalDataService.get(target);
        Map<PoliticalOffice, OfficeProgress> progress = SkillProgression.copy(data.officeProgress());
        OfficeProgress current = progress.getOrDefault(office.get(), OfficeProgress.EMPTY);
        progress.put(office.get(), new OfficeProgress(amount, current.onlineSeconds()));
        PoliticalDataService.set(target, data.withOfficeProgress(progress));
        context.getSource().sendSuccess(() -> Component.literal("[TESTE] Pontos de " + office.get().displayName() + " de "
                + target.getGameProfile().getName() + ": " + current.skillPoints() + " → " + amount).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
