package com.respectcraft.rig;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.finisher.FinisherRole;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.registry.ModEntities;
import com.respectcraft.testgame.FinisherTestMechanic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /respectcraft rig} (Etapa 4.12, permissao 2): diagnostico do Custom Rig Framework. So leitura, exceto
 * {@code part} (estado visual temporario), {@code fixtures} (invoca os dois manequins de teste) e {@code demo} (inicia
 * uma cena de teste — {@code peel} (partes, padrao) ou {@code authoring} (clips do Finisher Scene Authoring Kit) — pelas
 * mesmas regras do {@code /respectcraft admin finisher}).
 */
public final class RigCommands {
    private RigCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("rig").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(RigCommands::list))
                .then(Commands.literal("inspect").then(Commands.argument("entity", EntityArgument.entity()).executes(RigCommands::inspect)))
                .then(Commands.literal("clips").then(Commands.argument("entity", EntityArgument.entity()).executes(RigCommands::clips)))
                .then(Commands.literal("check").then(Commands.argument("attacker", EntityArgument.entity())
                        .then(Commands.argument("target", EntityArgument.entity()).executes(RigCommands::check))))
                .then(Commands.literal("part").then(Commands.argument("entity", EntityArgument.entity())
                        .then(Commands.literal("reset").executes(c -> part(c, null, true)))
                        .then(Commands.argument("part", StringArgumentType.word())
                                .then(Commands.literal("hide").executes(c -> part(c, StringArgumentType.getString(c, "part"), false)))
                                .then(Commands.literal("show").executes(c -> part(c, StringArgumentType.getString(c, "part"), true))))))
                .then(Commands.literal("fixtures").executes(RigCommands::fixtures))
                .then(Commands.literal("demo").then(Commands.argument("target", EntityArgument.entity())
                        .executes(c -> demo(c, Rigs.PEEL_DEMO))
                        .then(Commands.literal("peel").executes(c -> demo(c, Rigs.PEEL_DEMO)))
                        .then(Commands.literal("authoring").executes(c -> demo(c, Rigs.AUTHORING_DEMO)))));
    }

    private static void reply(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    /** Catalogo + finalizacoes de teste (fora do catalogo) que os diagnosticos avaliam. */
    private static List<FinisherDef> finishers() {
        List<FinisherDef> out = new ArrayList<>(Finishers.all());
        out.addAll(Rigs.devFinishers());
        return out;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("Rigs (" + CustomRigRegistry.GLOBAL.rigs().size() + "):");
        for (RigDefinition rig : CustomRigRegistry.GLOBAL.rigs()) {
            sb.append("\n ").append(rig.id()).append(" v").append(rig.contractVersion()).append(": ").append(rig.bones().size())
                    .append(" bones (").append(rig.bones().stream().filter(RigDefinition.Bone::required).count()).append(" required), ")
                    .append(rig.sockets().size()).append(" sockets, ").append(rig.parts().size()).append(" parts, ")
                    .append(rig.animations().size()).append(" shared animations; bound: ")
                    .append(CustomRigRegistry.GLOBAL.bindingsOf(rig.id()).stream().map(b -> b.entityType().toString()).toList());
        }
        reply(c, sb.toString());
        return CustomRigRegistry.GLOBAL.rigs().size();
    }

    private static Optional<LivingEntity> living(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        Entity e = FinisherParticipants.owner(EntityArgument.getEntity(c, arg));
        if (e instanceof LivingEntity living) return Optional.of(living);
        c.getSource().sendFailure(Component.literal("Target must be a living entity."));
        return Optional.empty();
    }

    private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Optional<LivingEntity> entity = living(c, "entity");
        if (entity.isEmpty()) return 0;
        var type = FinisherParticipants.typeId(entity.get());
        Optional<RigBinding> binding = CustomRigRegistry.GLOBAL.binding(type);
        if (binding.isEmpty()) {
            reply(c, "Rig of " + type + ": none (vanilla or independent GeckoLib entity; finishers use the existing rules)");
            return 0;
        }
        RigBinding b = binding.get();
        StringBuilder sb = new StringBuilder("Rig of " + type + ": " + b.rig() + " v" + b.contractVersion()
                + " | proportion " + vec(b.proportion()) + " scale " + b.renderScale()
                + " | capabilities " + CustomRigRegistry.GLOBAL.capabilities(b)
                + " | extra bones " + b.extraBones().stream().map(RigDefinition.Bone::name).toList()
                + " | alignment " + (b.alignment() == null ? "family" : b.alignment()));
        Map<String, Boolean> overrides = RigVisuals.overrides(entity.get());
        sb.append("\n Parts:");
        for (RigDefinition.Part p : CustomRigRegistry.GLOBAL.parts(b)) {
            sb.append(' ').append(p.name()).append('=').append(RigVisuals.visible(p, overrides) ? "visible" : "hidden")
                    .append(p.detachable() ? "(detachable)" : "");
        }
        sb.append("\n Sockets (rest pose, world):");
        var rig = CustomRigRegistry.GLOBAL.rig(b.rig()).orElseThrow();
        List<String> names = new ArrayList<>();
        rig.sockets().forEach(s -> names.add(s.name()));
        b.sockets().forEach(s -> { if (!names.contains(s.name())) names.add(s.name()); });
        for (String name : names) RigSockets.restWorld(entity.get(), name).ifPresent(p -> sb.append(' ').append(name).append('@').append(vec(p)));
        reply(c, sb.toString());
        return 1;
    }

    private static int clips(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Optional<LivingEntity> entity = living(c, "entity");
        if (entity.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder("Clip resolution for " + FinisherParticipants.typeId(entity.get()) + ":");
        for (FinisherDef def : finishers()) {
            for (FinisherRole role : FinisherRole.values()) {
                var res = FinisherParticipants.resolve(def, role, entity.get());
                sb.append("\n ").append(def.id()).append(' ').append(role).append(": ");
                if (!res.valid()) sb.append("REFUSED (").append(res.problem()).append(')');
                else sb.append(res.source()).append(' ').append(describe(res.clip().get()));
            }
        }
        reply(c, sb.toString());
        return 1;
    }

    private static String describe(FinisherParticipants.Clip clip) {
        return switch (clip) {
            case FinisherParticipants.PlayerClip p -> p.def().id().toString();
            case FinisherParticipants.EntityClip e -> e.clip().controller() + "/" + e.clip().animation();
            case FinisherParticipants.Hold h -> "hold " + h.durationTicks() + "t";
        };
    }

    private static int check(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Optional<LivingEntity> attacker = living(c, "attacker"), target = living(c, "target");
        if (attacker.isEmpty() || target.isEmpty()) return 0;
        int ok = 0;
        StringBuilder sb = new StringBuilder("Finisher check " + FinisherParticipants.typeId(attacker.get()) + " -> "
                + FinisherParticipants.typeId(target.get()) + ":");
        for (FinisherDef def : finishers()) {
            var result = FinisherTestMechanic.check(attacker.get(), target.get(), def);
            if (result.started()) ok++;
            sb.append("\n ").append(def.id()).append(": ").append(result.started() ? "ELIGIBLE" : result.message());
        }
        reply(c, sb.toString());
        return ok;
    }

    private static int part(CommandContext<CommandSourceStack> c, String part, boolean visible) throws CommandSyntaxException {
        Optional<LivingEntity> entity = living(c, "entity");
        if (entity.isEmpty()) return 0;
        boolean ok = part == null ? RigVisuals.reset(entity.get()) : RigVisuals.set(entity.get(), part, visible);
        if (!ok) {
            c.getSource().sendFailure(Component.literal("No rig or no such part on " + FinisherParticipants.typeId(entity.get()) + "."));
            return 0;
        }
        reply(c, "Rig parts of " + FinisherParticipants.typeId(entity.get()) + ": " + RigVisuals.overrides(entity.get()));
        return 1;
    }

    private static int fixtures(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot() + 90F);
        spawn(player, ModEntities.DEV_RIG_MANNEQUIN_A.get(), player.position().add(forward.scale(3)).add(right.scale(-1.2)));
        spawn(player, ModEntities.DEV_RIG_MANNEQUIN_B.get(), player.position().add(forward.scale(3)).add(right.scale(1.2)));
        reply(c, "Rig fixtures summoned: " + Rigs.MANNEQUIN_A_ID + ", " + Rigs.MANNEQUIN_B_ID);
        return 2;
    }

    private static void spawn(ServerPlayer player, EntityType<?> type, Vec3 at) {
        Entity e = type.create(player.serverLevel());
        if (e == null) return;
        e.moveTo(at.x, at.y, at.z, player.getYRot() + 180F, 0F);
        if (e instanceof net.minecraft.world.entity.Mob mob) {
            mob.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);
        }
        player.serverLevel().addFreshEntity(e);
    }

    private static int demo(CommandContext<CommandSourceStack> c, FinisherDef scene) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Optional<LivingEntity> target = living(c, "target");
        if (target.isEmpty()) return 0;
        var result = FinisherTestMechanic.forceStart(player, target.get(), scene);
        if (!result.started()) {
            c.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        reply(c, "Rig demo " + scene.id() + " started on " + FinisherParticipants.typeId(target.get()) + ".");
        return 1;
    }

    private static String vec(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }
}
