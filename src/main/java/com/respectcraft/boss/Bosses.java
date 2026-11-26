package com.respectcraft.boss;

import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherTargeting;
import com.respectcraft.finisher.Finishers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Registro dos chefes personalizados (Etapa 4.11). Catalogo em codigo, como {@link Finishers}: nada de estado de
 * mundo aqui. <b>Vazio de proposito</b>: nenhum chefe definitivo existe ainda; o {@code SelfTest} usa fichas de teste
 * que nunca entram aqui ({@link #validate} e {@link #checkExecution} sao puras e recebem o catalogo).
 *
 * <p>Como um chefe novo entra (quando o dono aprovar): entidade em {@code ModEntities} + tag
 * {@code respectcraft:bosses}; finalizacao exclusiva em {@link Finishers} com {@code withTargeting(exclusiveTo(tipo))};
 * e {@code Bosses.register(BossProfile.of(tipo).withExclusiveFinisher(id)...)} depois dela.
 */
public final class Bosses {
    private static final Map<ResourceLocation, BossProfile> REGISTRY = new LinkedHashMap<>();

    private Bosses() {}

    /** Registra a ficha; recusa ficha invalida (finalizacao ausente ou nao exclusiva deste chefe) ou repetida. */
    public static synchronized BossProfile register(BossProfile profile) {
        List<String> problems = validate(profile, Finishers::get);
        if (!problems.isEmpty()) throw new IllegalStateException("Chefe invalido " + profile.entityType() + ": " + problems);
        if (REGISTRY.putIfAbsent(profile.entityType(), profile) != null) {
            throw new IllegalStateException("Chefe duplicado: " + profile.entityType());
        }
        return profile;
    }

    public static Optional<BossProfile> get(ResourceLocation entityType) {
        return entityType == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(entityType));
    }

    public static Collection<BossProfile> all() { return Collections.unmodifiableCollection(REGISTRY.values()); }

    /** Regra pura: problemas da ficha contra um catalogo de finalizacoes (vazio = valida). */
    public static List<String> validate(BossProfile profile, Function<ResourceLocation, FinisherDef> finishers) {
        List<String> problems = new ArrayList<>();
        for (ResourceLocation id : profile.exclusiveFinishers()) {
            FinisherDef def = finishers.apply(id);
            if (def == null) {
                problems.add("finisher " + id + " is not registered");
            } else if (!def.targeting().equals(FinisherTargeting.exclusiveTo(profile.entityType()))) {
                problems.add("finisher " + id + " is not exclusive to " + profile.entityType());
            }
        }
        if (profile.exclusiveFinishers().stream().distinct().count() != profile.exclusiveFinishers().size()) {
            problems.add("repeated exclusive finisher");
        }
        return problems;
    }

    /**
     * Regra pura: pode esta finalizacao (ja ELEGIVEL pela {@link FinisherTargeting}) comecar contra este chefe?
     * Vazio = sim; senao o motivo (ingles). Chefe sem ficha nunca e executado (exclusiva sem chefe registrado), a
     * finalizacao precisa estar na lista da ficha e todas as condicoes precisam passar.
     */
    public static Optional<String> checkExecution(Optional<BossProfile> profile, FinisherDef finisher, BossCondition.Context context) {
        if (profile.isEmpty()) return Optional.of("This boss has no registered executions.");
        if (!profile.get().exclusiveFinishers().contains(finisher.id())) {
            return Optional.of("The " + finisher.id().getPath() + " finisher is not registered for this boss.");
        }
        for (BossCondition condition : profile.get().executionConditions()) {
            Optional<String> denial = condition.denial(context);
            if (denial.isPresent()) return denial;
        }
        return Optional.empty();
    }
}
