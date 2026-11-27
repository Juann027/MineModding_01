package com.respectcraft.finisher;

import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimations;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import com.respectcraft.rig.CustomRigRegistry;
import com.respectcraft.rig.RigBinding;
import com.respectcraft.rig.RigDefinition;
import com.respectcraft.RespectCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.entity.PartEntity;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Participantes de uma finalizacao com rigs diferentes (Etapa 4.8). Decide, so com dados, o que cada lado toca:
 * o clip do rig do jogador ({@link PlayerAnimationDef}), o clip do rig GeckoLib proprio da entidade
 * ({@link EntityFinisherClip}) ou nada (a entidade fica parada, como antes desta etapa). Nao ha controlador
 * universal: jogador e mob nunca compartilham ossos nem controladores.
 *
 * <p>As regras puras ({@link #resolve}, {@link #interaction}, {@link #stageDistance}, {@link #stage}) nao tocam no
 * mundo e sao cobertas pelo {@code SelfTest}. {@code FinisherTestMechanic} aplica o resultado no servidor.
 */
public final class FinisherParticipants {
    /** Distancia padrao entre os participantes (a mesma de sempre nas cenas jogador x jogador). */
    public static final double STAGE_DISTANCE = 1.15;
    /** Folga entre as caixas de colisao quando um rig e mais largo que o do jogador. */
    public static final double STAGE_GAP = 0.25;

    /**
     * Palco por tipo de entidade, para rigs cujo modelo passa da caixa de colisao (ex.: cabeca a frente). Vale quando
     * a entidade nao tem clip proprio com alinhamento. So dados de catalogo (registrados em {@link Finishers}).
     */
    private static final Map<ResourceLocation, EntityFinisherClip.Alignment> STAGING = new LinkedHashMap<>();

    private FinisherParticipants() {}

    public static synchronized EntityFinisherClip.Alignment registerStaging(ResourceLocation entityType,
                                                                         EntityFinisherClip.Alignment alignment) {
        if (STAGING.putIfAbsent(entityType, alignment) != null) {
            throw new IllegalStateException("Palco duplicado: " + entityType);
        }
        return alignment;
    }

    public static Optional<EntityFinisherClip.Alignment> staging(ResourceLocation entityType) {
        return Optional.ofNullable(STAGING.get(entityType));
    }

    /**
     * Chefes do RespectCraft e chefes de outros mods que nao usam a convencao {@code c:bosses} (Etapa 4.10.1). Tag de
     * dados ({@code data/respectcraft/tags/entity_type/bosses.json}, vazia hoje): um chefe futuro do mod entra aqui
     * (ou em {@code c:bosses}); um pacote de dados do servidor pode acrescentar chefes de outros mods sem codigo.
     */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, RespectCraft.id("bosses"));

    /**
     * Tipo de participante. Chefe = tag comum {@code c:bosses} (NeoForge; inclui Wither e Dragao do Fim) ou
     * {@link #BOSSES}. Nunca por vida, tamanho, nome ou barra de chefe (nao ha API comum para a barra).
     */
    public enum Kind { PLAYER, MOB, BOSS }

    /** Combinacoes suportadas. Precisam de um jogador: a sessao avanca no tick dele. */
    public enum Interaction {
        PLAYER_VS_PLAYER, PLAYER_VS_MOB, PLAYER_VS_BOSS, MOB_VS_PLAYER, BOSS_VS_PLAYER;

        public boolean involvesBoss() { return this == PLAYER_VS_BOSS || this == BOSS_VS_PLAYER; }
    }

    /** O que um participante toca durante a cena. */
    public sealed interface Clip permits PlayerClip, EntityClip, Hold {
        int durationTicks();
    }

    /** Jogador: clip do rig do jogador, iniciado por {@code PlayerAnimationService}. */
    public record PlayerClip(PlayerAnimationDef def) implements Clip {
        public int durationTicks() { return def.durationTicks(); }
    }

    /** Entidade GeckoLib com clip proprio, disparado no controlador dela. */
    public record EntityClip(EntityFinisherClip clip) implements Clip {
        public int durationTicks() { return clip.durationTicks(); }
    }

    /** Entidade sem clip para esta finalizacao: fica parada no palco com as animacoes normais dela. */
    public record Hold(int durationTicks) implements Clip {}

    /**
     * De onde veio o clip (Etapa 4.12, diagnostico): rig do jogador, clip explicito da finalizacao para o tipo,
     * override do {@code RigBinding} da entidade, clip da familia de rig, ou parado.
     */
    public enum Source { PLAYER, ENTITY, BINDING, FAMILY, HOLD }

    /** Resultado da resolucao: um clip ou o motivo da recusa (nunca os dois). */
    public record Resolution(Optional<Clip> clip, String problem, Source source) {
        static Resolution ok(Clip clip, Source source) { return new Resolution(Optional.of(clip), null, source); }
        static Resolution fail(String problem) { return new Resolution(Optional.empty(), problem, null); }
        public boolean valid() { return clip.isPresent(); }
    }

    // ------------------------------------------------------------- regras puras

    public static Optional<Interaction> interaction(Kind attacker, Kind victim) {
        if (attacker == Kind.PLAYER) {
            return Optional.of(switch (victim) {
                case PLAYER -> Interaction.PLAYER_VS_PLAYER;
                case MOB -> Interaction.PLAYER_VS_MOB;
                case BOSS -> Interaction.PLAYER_VS_BOSS;
            });
        }
        if (victim != Kind.PLAYER) return Optional.empty(); // mob x mob: sem jogador, a sessao nunca avancaria
        return Optional.of(attacker == Kind.BOSS ? Interaction.BOSS_VS_PLAYER : Interaction.MOB_VS_PLAYER);
    }

    /**
     * Clip de um participante (catalogo global de rigs, sem tags).
     *
     * @param entityType tipo da entidade (ignorado para jogador)
     * @param geckoRig   a entidade implementa {@link GeoEntity} (pode receber {@code triggerAnim})
     */
    public static Resolution resolve(FinisherDef def, FinisherRole role, Kind kind, ResourceLocation entityType, boolean geckoRig) {
        return resolve(CustomRigRegistry.GLOBAL, def, role, kind, entityType, geckoRig, tag -> false);
    }

    /**
     * Clip de um participante (Etapa 4.12), regra pura. Ordem: jogador → rig do jogador; entidade → clip explicito da
     * finalizacao para o tipo ({@code entityClips}) → override do {@code RigBinding} → clip da familia de rig →
     * parado. Depois, os requisitos do papel ({@link FinisherRigs.Requirement}); um requisito {@code requiresClip}
     * recusa o parado. Nada aqui toca no mundo: a recusa acontece antes de palco, animacao ou efeito.
     */
    public static Resolution resolve(CustomRigRegistry registry, FinisherDef def, FinisherRole role, Kind kind,
                                     ResourceLocation entityType, boolean geckoRig, Predicate<ResourceLocation> hasTag) {
        if (kind == Kind.PLAYER) {
            PlayerAnimationDef clip = def.playerClip(role);
            if (clip == null) return Resolution.fail(def.id() + " has no player clip for the " + role.name().toLowerCase(java.util.Locale.ROOT));
            if (PlayerAnimations.get(clip.id()) != clip) {
                return Resolution.fail("player clip " + clip.id() + " of " + def.id() + " is not registered");
            }
            Optional<String> denied = requirements(def, role, FinisherRigs.Requirement.PLAYER_TYPE, hasTag, null, Set.of(), true);
            return denied.isPresent() ? Resolution.fail(denied.get()) : Resolution.ok(new PlayerClip(clip), Source.PLAYER);
        }
        Optional<RigBinding> binding = registry.binding(entityType);
        Resolution res = entityClip(registry, def, role, entityType, binding);
        if (res.valid() && res.clip().get() instanceof EntityClip && !geckoRig) {
            return Resolution.fail(entityType + " has a clip in " + def.id() + " but no GeckoLib rig (GeoEntity)");
        }
        if (!res.valid()) return res;
        Optional<String> denied = requirements(def, role, entityType, hasTag, binding.map(RigBinding::rig).orElse(null),
                binding.map(registry::capabilities).orElse(Set.of()), !(res.clip().get() instanceof Hold));
        return denied.isPresent() ? Resolution.fail(denied.get()) : res;
    }

    private static Resolution entityClip(CustomRigRegistry registry, FinisherDef def, FinisherRole role,
                                         ResourceLocation entityType, Optional<RigBinding> binding) {
        Optional<EntityFinisherClip> explicit = def.entityClip(entityType, role);
        if (explicit.isPresent()) return Resolution.ok(new EntityClip(explicit.get()), Source.ENTITY);
        if (binding.isEmpty()) return Resolution.ok(new Hold(def.durationTicks()), Source.HOLD);
        Optional<RigDefinition> rig = registry.rig(binding.get().rig());
        if (rig.isEmpty()) return Resolution.fail(entityType + " is bound to unknown rig " + binding.get().rig());
        EntityFinisherClip.Alignment alignment = binding.get().alignment() != null ? binding.get().alignment() : rig.get().alignment();
        Optional<RigBinding.ClipOverride> override = binding.get().clipOverride(def.id(), role);
        if (override.isPresent()) {
            if (override.get().ticks() != def.durationTicks()) {
                return Resolution.fail("override " + override.get().animation() + " of " + entityType + " has " + override.get().ticks()
                        + " ticks; " + def.id() + " has " + def.durationTicks());
            }
            return Resolution.ok(new EntityClip(new EntityFinisherClip(entityType, role, rig.get().controller(),
                    override.get().animation(), override.get().ticks(), alignment)), Source.BINDING);
        }
        Optional<FinisherRigs.Clip> family = def.rigs().clip(rig.get().id(), role);
        if (family.isPresent()) {
            RigDefinition.SharedAnimation shared = rig.get().animations().get(family.get().key());
            if (shared == null) return Resolution.fail("rig " + rig.get().id() + " has no shared animation " + family.get().key());
            if (shared.ticks() != def.durationTicks()) {
                return Resolution.fail("shared animation " + family.get().key() + " of " + rig.get().id() + " has " + shared.ticks()
                        + " ticks; " + def.id() + " has " + def.durationTicks());
            }
            return Resolution.ok(new EntityClip(new EntityFinisherClip(entityType, role, rig.get().controller(),
                    rig.get().animationName(shared.key()), shared.ticks(), alignment)), Source.FAMILY);
        }
        return Resolution.ok(new Hold(def.durationTicks()), Source.HOLD);
    }

    private static Optional<String> requirements(FinisherDef def, FinisherRole role, ResourceLocation type,
                                                 Predicate<ResourceLocation> hasTag, ResourceLocation rig,
                                                 Set<ResourceLocation> caps, boolean animated) {
        for (FinisherRigs.Requirement r : def.rigs().requirements(role)) {
            Optional<String> problem = r.check(type, hasTag, rig, caps, animated);
            if (problem.isPresent()) return Optional.of(role.name().toLowerCase(java.util.Locale.ROOT) + ": " + problem.get());
        }
        return Optional.empty();
    }

    /**
     * Alinhamento que vale para a cena: o do clip de entidade (executor primeiro), senao o palco do tipo de entidade
     * nao-jogador (executor primeiro), senao o automatico.
     */
    public static EntityFinisherClip.Alignment alignment(Clip attacker, ResourceLocation attackerType,
                                                         Clip victim, ResourceLocation victimType) {
        if (attacker instanceof EntityClip e) return e.clip().alignment();
        if (victim instanceof EntityClip e) return e.clip().alignment();
        if (!(attacker instanceof PlayerClip) && STAGING.containsKey(attackerType)) return STAGING.get(attackerType);
        if (!(victim instanceof PlayerClip) && STAGING.containsKey(victimType)) return STAGING.get(victimType);
        // Etapa 4.12: entidade parada associada a uma familia de rig usa o palco do binding (ou da familia).
        if (!(attacker instanceof PlayerClip)) {
            Optional<EntityFinisherClip.Alignment> a = rigAlignment(attackerType);
            if (a.isPresent()) return a.get();
        }
        if (!(victim instanceof PlayerClip)) {
            Optional<EntityFinisherClip.Alignment> a = rigAlignment(victimType);
            if (a.isPresent()) return a.get();
        }
        return EntityFinisherClip.Alignment.AUTO;
    }

    private static Optional<EntityFinisherClip.Alignment> rigAlignment(ResourceLocation type) {
        return CustomRigRegistry.GLOBAL.binding(type).map(b -> b.alignment() != null ? b.alignment()
                : CustomRigRegistry.GLOBAL.rig(b.rig()).map(RigDefinition::alignment).orElse(EntityFinisherClip.Alignment.AUTO))
                .filter(a -> a != EntityFinisherClip.Alignment.AUTO);
    }

    /**
     * Distancia executor-vitima. Automatica: {@link #STAGE_DISTANCE}, aumentada so quando as caixas de colisao se
     * sobreporiam (rigs mais largos que o do jogador). Jogador x jogador continua em 1,15.
     */
    public static double stageDistance(EntityFinisherClip.Alignment alignment, double attackerWidth, double victimWidth) {
        if (!alignment.autoDistance()) return alignment.distance();
        return Math.max(STAGE_DISTANCE, (attackerWidth + victimWidth) / 2.0 + STAGE_GAP);
    }

    /** Posicao da vitima: a {@code distance} blocos a frente do executor e {@code lateral} para a direita dele. */
    public static Vec3 stage(Vec3 attackerPos, float attackerYaw, double distance, double lateral) {
        Vec3 forward = Vec3.directionFromRotation(0, attackerYaw);
        Vec3 right = Vec3.directionFromRotation(0, attackerYaw + 90F);
        return attackerPos.add(forward.scale(distance)).add(right.scale(lateral));
    }

    /** Giro extra do modelo da entidade (0 para jogador e para quem fica parado). */
    public static float yawOffset(Clip clip) {
        return clip instanceof EntityClip e ? e.clip().alignment().yawOffset() : 0F;
    }

    // ------------------------------------------------------------- mundo (servidor)

    public static Kind kindOf(LivingEntity entity) {
        if (entity instanceof Player) return Kind.PLAYER;
        return isBossType(entity.getType()) ? Kind.BOSS : Kind.MOB;
    }

    public static boolean isBossType(EntityType<?> type) {
        return type.is(Tags.EntityTypes.BOSSES) || type.is(BOSSES);
    }

    /**
     * Entidade que o jogador realmente mirou: partes de entidades de varias partes (ex.: o Dragao do Fim, via
     * {@link PartEntity} do NeoForge) viram a entidade dona, para a regra de chefe valer nelas tambem.
     */
    public static Entity owner(Entity entity) {
        return entity instanceof PartEntity<?> part && part.getParent() != null ? part.getParent() : entity;
    }

    public static ResourceLocation typeId(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    public static Resolution resolve(FinisherDef def, FinisherRole role, LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return resolve(CustomRigRegistry.GLOBAL, def, role, kindOf(entity), typeId(entity), entity instanceof GeoEntity,
                tag -> type.is(TagKey.create(Registries.ENTITY_TYPE, tag)));
    }
}
