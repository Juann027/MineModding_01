package com.respectcraft.rig;

import com.respectcraft.RespectCraft;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.fx.GeoFxKeyframes;
import com.respectcraft.network.RigNetworking;
import com.respectcraft.registry.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Unico dono da visibilidade das partes de rigs customizadas (Etapa 4.12): "casca removida", "nucleo exposto",
 * "placa arrancada". So estado VISUAL e temporario: um attachment sem serializador (nunca vai para o save; some com a
 * entidade), sincronizado com quem ve a entidade quando muda e no {@code StartTracking}. O cliente so desenha
 * ({@code client.rig.RigGeoModel} esconde os bones). Nada aqui afeta dano, caixa de colisao ou gameplay.
 *
 * <p>Estado = so as partes cujo valor difere do padrao do contrato ({@code default_visible}). Cenas guardam um
 * {@link #snapshot} no comeco e chamam {@link #restore} em qualquer fim (concluida, cancelada, recusada no golpe), entao
 * uma cena nunca deixa uma entidade modificada por acidente.
 *
 * <p>Instrucoes de keyframe ({@code "timeline"} da animacao): {@code rig:hide:<parte>}, {@code rig:show:<parte>},
 * {@code rig:reset}. Disparadas pelo relogio do servidor durante a cena, nunca por handler de keyframe no cliente.
 */
public final class RigVisuals {
    public static final String PREFIX = "rig:";

    /** Attachment: parte → visivel, so o que difere do padrao. */
    public static final class State {
        final Map<String, Boolean> overrides = new LinkedHashMap<>();
    }

    /** Instrucao de parte ja decodificada. {@code part} = null em {@code reset}. */
    public record PartInstruction(String part, boolean visible, boolean reset) {}

    private RigVisuals() {}

    // ------------------------------------------------------------------ regras puras

    public static Optional<PartInstruction> parse(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (!t.startsWith(PREFIX)) return Optional.empty();
        String body = t.substring(PREFIX.length());
        if (body.equals("reset")) return Optional.of(new PartInstruction(null, true, true));
        int colon = body.indexOf(':');
        if (colon <= 0 || colon == body.length() - 1) return Optional.empty();
        String verb = body.substring(0, colon), part = body.substring(colon + 1);
        if (!part.matches("[a-z0-9_]+")) return Optional.empty();
        return switch (verb) {
            case "hide" -> Optional.of(new PartInstruction(part, false, false));
            case "show" -> Optional.of(new PartInstruction(part, true, false));
            default -> Optional.empty();
        };
    }

    /** Visibilidade efetiva de uma parte: override, senao o padrao do contrato. */
    public static boolean visible(RigDefinition.Part part, Map<String, Boolean> overrides) {
        return overrides.getOrDefault(part.name(), part.defaultVisible());
    }

    /** Instrucoes {@code rig:} de uma animacao, com o tick (para a cena agendar). */
    public static List<GeoFxKeyframes.Instruction> partEvents(ResourceLocation file, String animation) {
        List<GeoFxKeyframes.Instruction> out = new ArrayList<>();
        for (GeoFxKeyframes.Instruction i : GeoFxKeyframes.instructions(GeoFxKeyframes.load(file), animation)) {
            if (parse(i.text()).isPresent()) out.add(i);
        }
        return out;
    }

    // ------------------------------------------------------------------ servidor

    public static Optional<RigBinding> binding(Entity entity) {
        return entity instanceof LivingEntity living
                ? CustomRigRegistry.GLOBAL.binding(FinisherParticipants.typeId(living)) : Optional.empty();
    }

    public static Map<String, Boolean> overrides(Entity entity) {
        return entity.hasData(ModAttachments.RIG_VISUAL.get())
                ? Map.copyOf(entity.getData(ModAttachments.RIG_VISUAL.get()).overrides) : Map.of();
    }

    /** Mostra/esconde uma parte. Falso se a entidade nao tem rig ou a parte nao existe nela. */
    public static boolean set(Entity entity, String partName, boolean visible) {
        if (entity.level().isClientSide()) return false;
        Optional<RigBinding> binding = binding(entity);
        if (binding.isEmpty()) return false;
        Optional<RigDefinition.Part> part = CustomRigRegistry.GLOBAL.part(binding.get(), partName);
        if (part.isEmpty()) return false;
        Map<String, Boolean> overrides = entity.getData(ModAttachments.RIG_VISUAL.get()).overrides;
        Boolean before = overrides.get(partName);
        if (visible == part.get().defaultVisible()) overrides.remove(partName);
        else overrides.put(partName, visible);
        if (!java.util.Objects.equals(before, overrides.get(partName))) sync(entity);
        return true;
    }

    /** Aplica uma instrucao {@code rig:}; falso se nao era instrucao valida para esta entidade. */
    public static boolean apply(Entity entity, String instruction) {
        Optional<PartInstruction> parsed = parse(instruction);
        if (parsed.isEmpty()) return false;
        if (parsed.get().reset()) return reset(entity);
        boolean ok = set(entity, parsed.get().part(), parsed.get().visible());
        if (!ok) RespectCraft.LOGGER.debug("Rig instruction {} ignored on {}", instruction, entity.getType());
        return ok;
    }

    /** Volta todas as partes ao padrao do contrato. */
    public static boolean reset(Entity entity) {
        if (entity.level().isClientSide() || binding(entity).isEmpty()) return false;
        if (!entity.hasData(ModAttachments.RIG_VISUAL.get())) return true;
        Map<String, Boolean> overrides = entity.getData(ModAttachments.RIG_VISUAL.get()).overrides;
        if (overrides.isEmpty()) return true;
        overrides.clear();
        sync(entity);
        return true;
    }

    public static Map<String, Boolean> snapshot(Entity entity) { return overrides(entity); }

    /** Devolve o estado de antes da cena (seguro para entidade sem rig ou removida). */
    public static void restore(Entity entity, Map<String, Boolean> snapshot) {
        if (entity == null || entity.level().isClientSide() || binding(entity).isEmpty()) return;
        if (overrides(entity).equals(snapshot)) return;
        Map<String, Boolean> overrides = entity.getData(ModAttachments.RIG_VISUAL.get()).overrides;
        overrides.clear();
        overrides.putAll(snapshot);
        if (!entity.isRemoved()) sync(entity);
    }

    private static void sync(Entity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload(entity));
    }

    private static RigNetworking.RigPartsSync payload(Entity entity) {
        return new RigNetworking.RigPartsSync(entity.getId(), overrides(entity));
    }

    /** Quem comeca a ver uma entidade com rig recebe o estado das partes (vazio tambem: limpa cache de id reaproveitado). */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && binding(event.getTarget()).isPresent()) {
            PacketDistributor.sendToPlayer(player, payload(event.getTarget()));
        }
    }
}
