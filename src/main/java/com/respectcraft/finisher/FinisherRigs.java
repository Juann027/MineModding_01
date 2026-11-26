package com.respectcraft.finisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Parte "rigs customizadas" de uma {@link FinisherDef} (Etapa 4.12). So dados.
 *
 * <ul>
 *   <li>{@link Clip}: clip da FAMILIA de rig para um papel — vale para toda entidade associada a ela
 *       ({@code rig.RigBinding}), pela chave de uma animacao compartilhada do contrato.</li>
 *   <li>{@link Requirement}: o que um papel precisa ter para a finalizacao comecar (tipo, tag, familia, capacidades,
 *       clip animado). Sem requisito = qualquer participante, como antes desta etapa.</li>
 * </ul>
 * Ordem de resolucao do clip de uma entidade: {@code entityClips} da finalizacao (por tipo) → override do
 * {@code RigBinding} da entidade → clip da familia → parado ({@code Hold}), que um requisito
 * {@code requiresClip} recusa antes da cena.
 */
public record FinisherRigs(List<Clip> clips, List<Requirement> requirements) {
    public static final FinisherRigs NONE = new FinisherRigs(List.of(), List.of());

    /** Clip da familia {@code rig} no papel {@code role}: a animacao compartilhada {@code key} do contrato. */
    public record Clip(ResourceLocation rig, FinisherRole role, String key) {}

    /**
     * Requisito de um papel. Conjuntos vazios nao restringem.
     *
     * @param entityTypes  tipos aceitos ({@code minecraft:player} inclui jogadores)
     * @param entityTags   tags de tipo de entidade aceitas (basta uma)
     * @param rig          familia exigida (null = qualquer, inclusive sem rig)
     * @param capabilities capacidades exigidas (todas), da familia + do binding
     * @param requiresClip o participante precisa ter clip animado (jogador ou rig); parado = recusa
     */
    public record Requirement(FinisherRole role, Set<ResourceLocation> entityTypes, Set<ResourceLocation> entityTags,
                              ResourceLocation rig, Set<ResourceLocation> capabilities, boolean requiresClip) {
        public static final ResourceLocation PLAYER_TYPE = ResourceLocation.withDefaultNamespace("player");

        public Requirement {
            entityTypes = Set.copyOf(entityTypes);
            entityTags = Set.copyOf(entityTags);
            capabilities = Set.copyOf(capabilities);
        }

        /** So familia + capacidades + clip animado (o caso comum das cenas de rig). */
        public static Requirement rig(FinisherRole role, ResourceLocation rig, Set<ResourceLocation> capabilities) {
            return new Requirement(role, Set.of(), Set.of(), rig, capabilities, true);
        }

        /**
         * Regra pura. {@code type} = tipo da entidade (jogador = {@link #PLAYER_TYPE}); {@code rigId}/{@code caps} =
         * familia e capacidades do participante (null/vazio sem rig); {@code animated} = clip que nao e parado.
         */
        public Optional<String> check(ResourceLocation type, Predicate<ResourceLocation> hasTag, ResourceLocation rigId,
                                      Set<ResourceLocation> caps, boolean animated) {
            if (!entityTypes.isEmpty() && !entityTypes.contains(type)) return Optional.of(type + " is not an accepted entity type");
            if (!entityTags.isEmpty() && entityTags.stream().noneMatch(hasTag)) return Optional.of(type + " has none of the required tags " + entityTags);
            if (rig != null && !rig.equals(rigId)) {
                return Optional.of(type + (rigId == null ? " has no custom rig" : " uses rig " + rigId) + "; requires " + rig);
            }
            List<ResourceLocation> missing = new ArrayList<>();
            for (ResourceLocation c : capabilities) if (caps == null || !caps.contains(c)) missing.add(c);
            if (!missing.isEmpty()) return Optional.of(type + " lacks rig capabilities " + missing);
            if (requiresClip && !animated) return Optional.of(type + " has no animation for this finisher (would only hold still)");
            return Optional.empty();
        }
    }

    public FinisherRigs {
        clips = List.copyOf(clips);
        requirements = List.copyOf(requirements);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Clip c : clips) {
            if (!seen.add(c.rig() + "/" + c.role())) throw new IllegalArgumentException("Repeated rig clip " + c.rig() + " (" + c.role() + ")");
        }
    }

    public Optional<Clip> clip(ResourceLocation rig, FinisherRole role) {
        for (Clip c : clips) if (c.rig().equals(rig) && c.role() == role) return Optional.of(c);
        return Optional.empty();
    }

    public List<Requirement> requirements(FinisherRole role) {
        return requirements.stream().filter(r -> r.role() == role).toList();
    }

    public FinisherRigs withClip(Clip clip) {
        List<Clip> list = new ArrayList<>(clips);
        list.add(clip);
        return new FinisherRigs(list, requirements);
    }

    public FinisherRigs withRequirement(Requirement requirement) {
        List<Requirement> list = new ArrayList<>(requirements);
        list.add(requirement);
        return new FinisherRigs(clips, list);
    }
}
