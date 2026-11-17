package com.tupinicraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tupinicraft.finisher.FinisherDef;
import com.tupinicraft.finisher.Finishers;
import com.tupinicraft.needs.Need;
import com.tupinicraft.needs.NeedsService;
import com.tupinicraft.profile.BirthRoulette;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.profile.ProfileService;
import com.tupinicraft.selftest.SelfTest;
import com.tupinicraft.skill.SkillDef;
import com.tupinicraft.skill.SkillService;
import com.tupinicraft.skill.Skills;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /tupini}: consulta da ficha e ferramentas de administracao/teste (permissao 2). */
public final class TupiniCommands {
    private TupiniCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tupini")
                .then(Commands.literal("perfil").executes(c -> profile(c, c.getSource().getPlayerOrException())))
                .then(Commands.literal("skills").then(Commands.literal("list").executes(TupiniCommands::listSkills)))
                .then(Commands.literal("finishers").executes(TupiniCommands::listFinishers))
                .then(Commands.literal("selftest").requires(s -> s.hasPermission(2)).executes(TupiniCommands::selfTest))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("perfil").then(Commands.argument("jogador", EntityArgument.player())
                                .executes(c -> profile(c, EntityArgument.getPlayer(c, "jogador")))))
                        .then(Commands.literal("pontos").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(0, 999))
                                        .executes(TupiniCommands::setPoints))))
                        .then(Commands.literal("roleta").then(Commands.argument("jogador", EntityArgument.player())
                                .executes(TupiniCommands::forceRoll)))
                        .then(Commands.literal("resetar").then(Commands.argument("jogador", EntityArgument.player())
                                .executes(TupiniCommands::reset)))
                        .then(Commands.literal("skill").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                Skills.all().stream().map(SkillDef::id), b))
                                        .executes(TupiniCommands::grantSkill))))
                        .then(Commands.literal("finisher").then(Commands.argument("executor", EntityArgument.entity())
                                .then(Commands.argument("alvo", EntityArgument.entity())
                                        .executes(c -> forceFinisher(c, null))
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                        Finishers.all().stream().map(FinisherDef::id), b))
                                                .executes(c -> forceFinisher(c, Finishers.get(ResourceLocationArgument.getId(c, "id"))))))))
                        .then(Commands.literal("efeito").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                com.tupinicraft.needs.NeedEffects.all().stream().map(com.tupinicraft.needs.NeedEffectDef::id), b))
                                        .executes(TupiniCommands::applyNeedEffect))))
                        .then(Commands.literal("necessidade").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("tipo", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Need.ACTIVE).map(Need::id), b))
                                        .then(Commands.argument("valor", IntegerArgumentType.integer(0, 100))
                                                .executes(TupiniCommands::setNeed)))))));
    }

    private static int profile(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        CharacterProfile p = ProfileService.get(player);
        String attrs = java.util.Arrays.stream(CharacterAttribute.values())
                .map(a -> a.id() + "=" + p.attribute(a)).collect(Collectors.joining(", "));
        var n = NeedsService.get(player);
        String needs = java.util.Arrays.stream(Need.ACTIVE)
                .map(need -> need.id() + "=" + n.percent(need) + "%").collect(Collectors.joining(", "));
        c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": nascido=" + p.born()
                + " origem=" + (p.origin().isEmpty() ? "-" : p.origin()) + " rerolls=" + p.rerolls()
                + " pontos=" + p.skillPoints() + " habilidades=" + p.skills().size()
                + " tracos=" + (p.traits().isEmpty() ? "-" : String.join(",", p.traits()))
                + "\n" + attrs + "\n" + needs + "\nefeitos=" + NeedsService.activeEffects(player).entries()), false);
        return 1;
    }

    private static int listSkills(CommandContext<CommandSourceStack> c) {
        String list = Skills.all().stream().map(s -> s.id().toString()).collect(Collectors.joining(", "));
        c.getSource().sendSuccess(() -> Component.literal("Habilidades (" + Skills.all().size() + "): " + list), false);
        return Skills.all().size();
    }

    private static int listFinishers(CommandContext<CommandSourceStack> c) {
        String list = Finishers.all().stream().map(FinisherDef::id).map(Object::toString).collect(Collectors.joining(", "));
        c.getSource().sendSuccess(() -> Component.literal("Finalizações: " + list), false);
        return Finishers.all().size();
    }

    private static int selfTest(CommandContext<CommandSourceStack> c) {
        List<String> failures = SelfTest.run();
        if (failures.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Autoteste RespectCraft: OK"), true);
            return 1;
        }
        for (String failure : failures) c.getSource().sendFailure(Component.literal("Autoteste falhou: " + failure));
        return 0;
    }

    private static int setPoints(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "jogador");
        int amount = IntegerArgumentType.getInteger(c, "quantidade");
        CharacterProfile p = ProfileService.get(player);
        ProfileService.set(player, p.withPoints(amount, p.levelRewarded()));
        c.getSource().sendSuccess(() -> Component.literal("Pontos de habilidade: " + amount), true);
        return 1;
    }

    private static int forceRoll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "jogador");
        // Giro forcado pelo admin: nao concede novo giro ao jogador (mantem o que ele tinha; antes do nascimento, 1).
        CharacterProfile before = ProfileService.get(player);
        BirthRoulette.Result result = ProfileService.roll(player, before.born() ? before.rerolls() : BirthRoulette.FREE_REROLLS);
        c.getSource().sendSuccess(() -> Component.literal("Roleta: " + result.origin().id() + " "
                + java.util.Arrays.toString(result.attributes())), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "jogador");
        ProfileService.set(player, CharacterProfile.DEFAULT);
        NeedsService.set(player, com.tupinicraft.needs.Needs.DEFAULT);
        NeedsService.clearEffects(player);
        NeedsService.sync(player);
        c.getSource().sendSuccess(() -> Component.literal("Ficha de " + player.getGameProfile().getName() + " zerada."), true);
        return 1;
    }

    private static int grantSkill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "jogador");
        var id = ResourceLocationArgument.getId(c, "id");
        SkillDef def = Skills.get(id);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("Habilidade desconhecida: " + id));
            return 0;
        }
        CharacterProfile p = ProfileService.get(player);
        ProfileService.set(player, p.withPoints(p.skillPoints() + def.cost(), p.levelRewarded()));
        if (!SkillService.purchase(player, id)) {
            c.getSource().sendFailure(Component.literal("Não foi possível liberar (nascimento, requisito ou já comprada)."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Habilidade liberada: " + id), true);
        return 1;
    }

    private static int forceFinisher(CommandContext<CommandSourceStack> c, FinisherDef def) throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(c, "executor") instanceof net.minecraft.world.entity.LivingEntity attacker)
                || !(EntityArgument.getEntity(c, "alvo") instanceof net.minecraft.world.entity.LivingEntity target)) {
            c.getSource().sendFailure(Component.literal("Executor e alvo precisam ser entidades vivas."));
            return 0;
        }
        if (!com.tupinicraft.testgame.FinisherTestMechanic.forceStart(attacker, target, def)) {
            c.getSource().sendFailure(Component.literal("Não foi possível iniciar (distância, sessão ativa ou montaria)."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Finalização iniciada."), true);
        return 1;
    }

    private static int setNeed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "jogador");
        Need need = Need.byId(StringArgumentType.getString(c, "tipo"));
        if (need == null || !need.active()) {
            c.getSource().sendFailure(Component.literal("Necessidade desconhecida ou inativa."));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(c, "valor");
        NeedsService.set(player, NeedsService.get(player).with(need, value * 100));
        NeedsService.sync(player);
        c.getSource().sendSuccess(() -> Component.literal(need.id() + " = " + value + "%"), true);
        return 1;
    }

    /** Aplica o efeito de um consumivel como se o jogador o tivesse consumido (teste). */
    private static int applyNeedEffect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "jogador");
        String id = StringArgumentType.getString(c, "id");
        var def = com.tupinicraft.needs.NeedEffects.get(id);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("Efeito desconhecido: " + id));
            return 0;
        }
        NeedsService.consume(player, def);
        c.getSource().sendSuccess(() -> Component.literal("Efeito aplicado: " + id + " (ativos: "
                + NeedsService.activeEffects(player).entries().size() + ")"), true);
        return 1;
    }
}
