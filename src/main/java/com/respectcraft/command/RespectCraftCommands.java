package com.respectcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.respectcraft.animation.PlayerAnimationCommands;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.needs.Need;
import com.respectcraft.needs.NeedsService;
import com.respectcraft.profile.BirthRoulette;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.ProfileService;
import com.respectcraft.profile.Specialities;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityConfig;
import com.respectcraft.profile.SpecialityOdds;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.profile.SpecialityRoll;
import com.respectcraft.selftest.SelfTest;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.skill.SkillService;
import com.respectcraft.skill.Skills;
import com.respectcraft.testgame.TestGameCommands;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /respectcraft}: arvore unica de comandos do mod. Consulta da ficha, catalogos, ferramentas de
 * administracao/teste (permissao 2), animacoes ({@code anim}, permissao 2) e o teste de finalizacao ({@code test}).
 */
public final class RespectCraftCommands {
    private RespectCraftCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("respectcraft")
                .then(Commands.literal("profile").executes(c -> profile(c, c.getSource().getPlayerOrException())))
                .then(Commands.literal("skills").then(Commands.literal("list").executes(RespectCraftCommands::listSkills)))
                .then(Commands.literal("finishers").executes(RespectCraftCommands::listFinishers)
                        .then(Commands.argument("target", EntityArgument.entity())
                                .requires(source -> source.hasPermission(2))
                                .executes(RespectCraftCommands::finisherEligibility)))
                .then(Commands.literal("selftest").requires(s -> s.hasPermission(2)).executes(RespectCraftCommands::selfTest))
                .then(Commands.literal("speciality").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(RespectCraftCommands::listSpecialities))
                        .then(Commands.literal("odds").executes(RespectCraftCommands::specialityOdds))
                        .then(Commands.literal("simulate").then(Commands.argument("rolls", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(RespectCraftCommands::specialitySimulate))))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("profile").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> profile(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("points").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 999))
                                        .executes(RespectCraftCommands::setPoints))))
                        .then(Commands.literal("roulette").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> forceRoll(c, null))
                                // Teste administrativo: forca so a raridade (a Speciality sai dentro dela).
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(
                                                SpecialityRarity.values()).map(SpecialityRarity::id), b))
                                        .executes(c -> {
                                            String id = StringArgumentType.getString(c, "rarity");
                                            SpecialityRarity rarity = SpecialityRarity.byId(id);
                                            if (rarity == null) {
                                                c.getSource().sendFailure(Component.literal("Unknown rarity: " + id));
                                                return 0;
                                            }
                                            return forceRoll(c, rarity);
                                        }))))
                        .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                                .executes(RespectCraftCommands::reset)))
                        // Etapa 4.8.2: diagnostico; o proximo pedido de giro do jogador falha no servidor (nada muda).
                        .then(Commands.literal("roulette_fail").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> {
                                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                                    ProfileService.armInjectedFailure(target);
                                    c.getSource().sendSuccess(() -> Component.literal("The next Birth Roulette request of "
                                            + target.getGameProfile().getName() + " will fail on the server (test)."), true);
                                    return 1;
                                })))
                        .then(Commands.literal("skill").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                Skills.all().stream().map(SkillDef::id), b))
                                        .executes(RespectCraftCommands::grantSkill))))
                        .then(Commands.literal("finisher").then(Commands.argument("attacker", EntityArgument.entity())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> forceFinisher(c, null))
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                        Finishers.all().stream().map(FinisherDef::id), b))
                                                .executes(c -> forceFinisher(c, Finishers.get(ResourceLocationArgument.getId(c, "id"))))))))
                        .then(Commands.literal("effect").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                com.respectcraft.needs.NeedEffects.all().stream().map(com.respectcraft.needs.NeedEffectDef::id), b))
                                        .executes(RespectCraftCommands::applyNeedEffect))))
                        .then(Commands.literal("need").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Need.ACTIVE).map(Need::id), b))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(RespectCraftCommands::setNeed))))))
                .then(PlayerAnimationCommands.node())
                .then(TestGameCommands.node())
                // Etapa 4.9: acesso de teste ao Inverted Realm (sem portal ainda).
                .then(com.respectcraft.dimension.InvertedRealmCommands.node())
                .then(com.respectcraft.dimension.timewarp.TimeWarpCommands.node())
                // Etapa 4.11: avisos holograficos (teste) e caca a recompensa.
                .then(com.respectcraft.notice.NoticeCommands.node())
                .then(com.respectcraft.bounty.BountyCommands.node())
                .then(com.respectcraft.quest.QuestCommands.node())
                // Etapa 4.12: diagnostico do Custom Rig Framework.
                .then(com.respectcraft.rig.RigCommands.node())
                .then(com.respectcraft.entity.fruit.FruitCommands.node()));
    }

    private static int profile(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        CharacterProfile p = ProfileService.get(player);
        String attrs = java.util.Arrays.stream(CharacterAttribute.values())
                .map(a -> a.id() + "=" + p.attribute(a)).collect(Collectors.joining(", "));
        var n = NeedsService.get(player);
        String needs = java.util.Arrays.stream(Need.ACTIVE)
                .map(need -> need.id() + "=" + n.percent(need) + "%").collect(Collectors.joining(", "));
        c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": born=" + p.born()
                + " speciality=" + (p.birthSpeciality().isEmpty() ? "-" : p.birthSpeciality())
                + " current=" + (p.currentSpeciality().isEmpty() ? "-" : p.currentSpeciality())
                + " legacy_origin=" + (p.origin().isEmpty() ? "-" : p.origin()) + " rerolls=" + p.rerolls()
                + " points=" + p.skillPoints() + " skills=" + p.skills().size()
                + " traits=" + (p.traits().isEmpty() ? "-" : String.join(",", p.traits()))
                + "\n" + attrs + "\n" + needs + "\neffects=" + NeedsService.activeEffects(player).entries()), false);
        return 1;
    }

    private static int listSkills(CommandContext<CommandSourceStack> c) {
        String list = Skills.all().stream().map(s -> s.id().toString()).collect(Collectors.joining(", "));
        c.getSource().sendSuccess(() -> Component.literal("Skills (" + Skills.all().size() + "): " + list), false);
        return Skills.all().size();
    }

    private static int listFinishers(CommandContext<CommandSourceStack> c) {
        String list = Finishers.all().stream().map(FinisherDef::id).map(Object::toString).collect(Collectors.joining(", "));
        c.getSource().sendSuccess(() -> Component.literal("Finishers: " + list), false);
        return Finishers.all().size();
    }

    /** Catalogo de Specialities por raridade (ids). */
    private static int listSpecialities(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("Specialities (" + Speciality.values().length + "):");
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            sb.append("\n ").append(rarity.id()).append(": ").append(Specialities.CATALOG.inRarity(rarity).stream()
                    .map(Speciality::id).collect(Collectors.joining(", ")));
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return Speciality.values().length;
    }

    /** Chances de raridade ativas (config do servidor) e quantas Specialities ha em cada raridade. */
    private static int specialityOdds(CommandContext<CommandSourceStack> c) {
        SpecialityOdds odds = SpecialityConfig.odds();
        String error = SpecialityConfig.error();
        StringBuilder sb = new StringBuilder("Speciality rarity odds"
                + (error == null ? "" : " (config invalid: " + error + "; using defaults)") + ":");
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            sb.append("\n ").append(rarity.id()).append(" = ").append(SpecialityOdds.formatPercent(odds.permille(rarity)))
                    .append(" (specialities: ").append(Specialities.CATALOG.inRarity(rarity).size()).append(")");
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /** Simula sorteios de Speciality no servidor (nao grava nada na ficha). */
    private static int specialitySimulate(CommandContext<CommandSourceStack> c) {
        int rolls = IntegerArgumentType.getInteger(c, "rolls");
        SpecialityOdds odds = SpecialityConfig.odds();
        java.util.random.RandomGenerator random = com.respectcraft.profile.BirthRoulette.newRandom();
        int[] counts = new int[SpecialityRarity.values().length];
        int empty = 0;
        for (int i = 0; i < rolls; i++) {
            SpecialityRoll.Result result = SpecialityRoll.roll(random, odds, Specialities.CATALOG,
                    com.respectcraft.profile.BirthTrait.Body.MALE);
            counts[result.rarity().ordinal()]++;
            if (!result.hasSpeciality()) empty++;
        }
        StringBuilder sb = new StringBuilder("Speciality simulation (" + rolls + " rolls):");
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            sb.append("\n ").append(rarity.id()).append(" = ").append(counts[rarity.ordinal()])
                    .append(String.format(java.util.Locale.ROOT, " (%.2f%%, expected %s)",
                            100.0 * counts[rarity.ordinal()] / rolls, SpecialityOdds.formatPercent(odds.permille(rarity))));
        }
        sb.append("\nRolls with no Speciality in the rolled rarity: ").append(empty);
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return rolls;
    }

    private static int selfTest(CommandContext<CommandSourceStack> c) {
        List<String> failures = SelfTest.run();
        if (failures.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("RespectCraft self-test: OK"), true);
            return 1;
        }
        for (String failure : failures) c.getSource().sendFailure(Component.literal("Self-test failed: " + failure));
        return 0;
    }

    private static int setPoints(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        CharacterProfile p = ProfileService.get(player);
        ProfileService.set(player, p.withPoints(amount, p.levelRewarded()));
        c.getSource().sendSuccess(() -> Component.literal("Skill points: " + amount), true);
        return 1;
    }

    private static int forceRoll(CommandContext<CommandSourceStack> c, SpecialityRarity forcedRarity) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        // Giro forcado pelo admin: nao concede novo giro ao jogador (mantem o que ele tinha; antes do nascimento, 1).
        CharacterProfile before = ProfileService.get(player);
        BirthRoulette.Result result = ProfileService.roll(player,
                before.born() ? before.rerolls() : BirthRoulette.FREE_REROLLS, forcedRarity);
        c.getSource().sendSuccess(() -> Component.literal("Roulette: " + result.speciality().id() + " ("
                + result.rarity().id() + (forcedRarity == null ? "" : ", forced by admin") + ") "
                + java.util.Arrays.toString(result.attributes())), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        ProfileService.set(player, CharacterProfile.DEFAULT);
        NeedsService.set(player, com.respectcraft.needs.Needs.DEFAULT);
        NeedsService.clearEffects(player);
        NeedsService.sync(player);
        c.getSource().sendSuccess(() -> Component.literal("Character sheet of " + player.getGameProfile().getName() + " reset."), true);
        return 1;
    }

    private static int grantSkill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        var id = ResourceLocationArgument.getId(c, "id");
        SkillDef def = Skills.get(id);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("Unknown skill: " + id));
            return 0;
        }
        CharacterProfile p = ProfileService.get(player);
        ProfileService.set(player, p.withPoints(p.skillPoints() + def.cost(), p.levelRewarded()));
        if (!SkillService.purchase(player, id)) {
            c.getSource().sendFailure(Component.literal("Could not unlock (not born, missing requirement or already owned)."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Skill unlocked: " + id), true);
        return 1;
    }

    private static int forceFinisher(CommandContext<CommandSourceStack> c, FinisherDef def) throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(c, "attacker") instanceof net.minecraft.world.entity.LivingEntity attacker)
                || !(EntityArgument.getEntity(c, "target") instanceof net.minecraft.world.entity.LivingEntity target)) {
            c.getSource().sendFailure(Component.literal("Attacker and target must be living entities."));
            return 0;
        }
        var result = com.respectcraft.testgame.FinisherTestMechanic.forceStart(attacker, target, def);
        if (!result.started()) {
            c.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    /**
     * Etapa 4.10.1: quais finalizacoes podem executar este alvo (so leitura, para admins e o teste de fumaca). Mostra
     * o tipo do alvo (jogador, mob, chefe) e o veredito de cada finalizacao do catalogo.
     */
    private static int finisherEligibility(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity raw = com.respectcraft.finisher.FinisherParticipants.owner(EntityArgument.getEntity(c, "target"));
        if (!(raw instanceof net.minecraft.world.entity.LivingEntity target)) {
            c.getSource().sendFailure(Component.literal("Target must be a living entity."));
            return 0;
        }
        var kind = com.respectcraft.finisher.FinisherParticipants.kindOf(target);
        var type = com.respectcraft.finisher.FinisherParticipants.typeId(target);
        int eligible = 0;
        StringBuilder sb = new StringBuilder("Finisher eligibility for " + type + " (" + kind + "):");
        for (FinisherDef def : Finishers.all()) {
            var verdict = def.targeting().check(kind, type);
            if (verdict.eligible()) eligible++;
            sb.append("\n ").append(def.id()).append(": ").append(verdict);
        }
        // Etapa 4.13.3: o que o servidor sortearia para um jogador sem desbloqueios (politica por categoria de vitima).
        var pool = com.respectcraft.finisher.FinisherSelection.candidates(Finishers.all(), kind, type, s -> false, d -> true);
        sb.append("\n Selection pool (no unlocks): ").append(pool.stream().map(d -> d.id().toString()).toList());
        if (kind == com.respectcraft.finisher.FinisherParticipants.Kind.BOSS) {
            // Etapa 4.11: ficha do chefe personalizado (Bosses). Sem ficha = so derrota comum.
            var profile = com.respectcraft.boss.Bosses.get(type);
            sb.append("\n Boss profile: ").append(profile.map(p -> p.exclusiveFinishers().size() + " exclusive finisher(s), "
                    + p.executionConditions().size() + " condition(s)").orElse("none (normal defeat only)"));
        }
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return eligible;
    }

    private static int setNeed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Need need = Need.byId(StringArgumentType.getString(c, "type"));
        if (need == null || !need.active()) {
            c.getSource().sendFailure(Component.literal("Unknown or inactive need."));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(c, "value");
        NeedsService.set(player, NeedsService.get(player).with(need, value * 100));
        NeedsService.sync(player);
        c.getSource().sendSuccess(() -> Component.literal(need.id() + " = " + value + "%"), true);
        return 1;
    }

    /** Aplica o efeito de um consumivel como se o jogador o tivesse consumido (teste). */
    private static int applyNeedEffect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        String id = StringArgumentType.getString(c, "id");
        var def = com.respectcraft.needs.NeedEffects.get(id);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("Unknown effect: " + id));
            return 0;
        }
        NeedsService.consume(player, def);
        c.getSource().sendSuccess(() -> Component.literal("Effect applied: " + id + " (active: "
                + NeedsService.activeEffects(player).entries().size() + ")"), true);
        return 1;
    }
}
