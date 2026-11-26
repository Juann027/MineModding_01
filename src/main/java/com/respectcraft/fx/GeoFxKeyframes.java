package com.respectcraft.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.respectcraft.RespectCraft;
import com.respectcraft.registry.ModAttachments;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Gancho GeckoLib → Calamidade_Awakening para mobs (Etapa 4.10, so infraestrutura; nenhum mob usa ainda).
 *
 * <p><b>Como usar.</b> No {@code .animation.json} do mob, um keyframe de instrucao (bloco {@code "timeline"} do
 * GeckoLib 4) com {@code calamidade:<efeito>} — por exemplo {@code "timeline": {"0.6": "calamidade:respectcraft:golpe;"}}
 * ({@code calamidade:golpe} = namespace {@code respectcraft}). No servidor, dispare a animacao por
 * {@link #triggerAnim}: ele chama {@code GeoEntity.triggerAnim} e agenda o efeito para o tick do keyframe
 * (segundos × 20), comecado preso ao mob por {@code EffectTriggers.entity(efeito).fire(mob)} (yaw seguindo o corpo,
 * fonte = o mob). O efeito precisa estar registrado no Calamidade ({@code Effects.register}) no construtor do mod.
 *
 * <p><b>Por que no servidor.</b> Os handlers de keyframe do GeckoLib rodam em cada cliente; o Calamidade comeca efeitos
 * no servidor (que sincroniza com quem ve). Por isso o servidor le as instrucoes do proprio arquivo de animacao (dentro
 * do jar do mod) e agenda pelo relogio dele — nada de pacote do cliente para o servidor. Animacoes que so o cliente
 * decide (idle/walk em laco) nao disparam efeitos: use {@code triggerAnim} para o que deve ter efeito.
 *
 * <p>A agenda fica num attachment sem serializador do proprio mob (nao e salva; some com ele). O cache dos arquivos
 * lidos e so dados imutaveis do jar.
 */
public final class GeoFxKeyframes {
    public static final String PREFIX = "calamidade:";

    /** Um efeito agendado num instante da animacao. */
    public record KeyframeFx(int tick, ResourceLocation effect) {}

    /** Agenda de um mob: efeitos ainda por disparar, em ticks do jogo. Attachment sem serializador. */
    public static final class Schedule {
        private record Pending(long at, ResourceLocation effect) {}

        private final List<Pending> pending = new ArrayList<>();

        boolean isEmpty() { return pending.isEmpty(); }
    }

    private static final Map<ResourceLocation, JsonObject> FILES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, EffectTriggers.EntityTrigger> TRIGGERS = new ConcurrentHashMap<>();

    private GeoFxKeyframes() {}

    // ------------------------------------------------------------------------------------------------ regras puras

    /** Uma instrucao {@code calamidade:<efeito>} → id do efeito (sem namespace = {@code respectcraft}). */
    public static Optional<ResourceLocation> parseInstruction(String instruction) {
        if (instruction == null) return Optional.empty();
        String text = instruction.trim();
        if (!text.startsWith(PREFIX)) return Optional.empty();
        String effect = text.substring(PREFIX.length()).trim();
        if (effect.isEmpty()) return Optional.empty();
        ResourceLocation id = effect.indexOf(':') >= 0 ? ResourceLocation.tryParse(effect)
                : ResourceLocation.tryBuild(RespectCraft.MOD_ID, effect);
        return Optional.ofNullable(id);
    }

    /** Texto de um keyframe de instrucao (varias separadas por {@code ;}) → efeitos do Calamidade, na ordem. */
    public static List<ResourceLocation> parseInstructions(String text) {
        List<ResourceLocation> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(";")) parseInstruction(part).ifPresent(out::add);
        return out;
    }

    /**
     * Efeitos de uma animacao de um arquivo GeckoLib 4: chaves do bloco {@code "timeline"} em segundos (× 20 = tick,
     * arredondado), valores texto ou lista de textos. Instrucoes que nao sao do Calamidade sao ignoradas.
     */
    public static List<KeyframeFx> scan(JsonObject file, String animation) {
        List<KeyframeFx> out = new ArrayList<>();
        for (Instruction i : instructions(file, animation)) {
            for (ResourceLocation id : parseInstructions(i.text())) out.add(new KeyframeFx(i.tick(), id));
        }
        out.sort(Comparator.comparingInt(KeyframeFx::tick));
        return out;
    }

    /** Uma instrucao de keyframe ({@code "timeline"}), ja no tick da animacao. */
    public record Instruction(int tick, String text) {}

    /**
     * Todas as instrucoes do bloco {@code "timeline"} de uma animacao GeckoLib 4, em ordem de tick (segundos × 20,
     * arredondado); valores texto ou lista de textos, cada um separado por {@code ;}. Usado por este gancho
     * ({@code calamidade:}) e pelas partes de rig ({@code rig:}, Etapa 4.12).
     */
    public static List<Instruction> instructions(JsonObject file, String animation) {
        List<Instruction> out = new ArrayList<>();
        if (file == null || !file.has("animations")) return out;
        JsonObject animations = file.getAsJsonObject("animations");
        if (!animations.has(animation)) return out;
        JsonObject anim = animations.getAsJsonObject(animation);
        if (!anim.has("timeline") || !anim.get("timeline").isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> entry : anim.getAsJsonObject("timeline").entrySet()) {
            double seconds;
            try {
                seconds = Double.parseDouble(entry.getKey().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!Double.isFinite(seconds) || seconds < 0) continue;
            int tick = (int) Math.round(seconds * 20.0);
            JsonElement value = entry.getValue();
            List<String> texts = new ArrayList<>();
            if (value.isJsonArray()) {
                for (JsonElement e : (JsonArray) value) if (e.isJsonPrimitive()) texts.add(e.getAsString());
            } else if (value.isJsonPrimitive()) {
                texts.add(value.getAsString());
            }
            for (String text : texts) {
                for (String part : text.split(";")) if (!part.isBlank()) out.add(new Instruction(tick, part.trim()));
            }
        }
        out.sort(Comparator.comparingInt(Instruction::tick));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ servidor

    /**
     * Dispara a animacao gatilho do mob e agenda os efeitos {@code calamidade:} dela (servidor).
     *
     * @param animationFile arquivo do mob, ex. {@code respectcraft:animations/entity/meu_mob.animation.json}
     * @param animation     nome da animacao dentro do arquivo (a que o gatilho toca)
     * @return false se a entidade nao e um {@code GeoEntity} ou esta no cliente (nada acontece)
     */
    public static boolean triggerAnim(LivingEntity entity, String controller, String trigger,
                                      ResourceLocation animationFile, String animation) {
        if (entity.level().isClientSide() || !(entity instanceof GeoEntity geo)) return false;
        geo.triggerAnim(controller, trigger);
        List<KeyframeFx> fx = scan(load(animationFile), animation);
        if (fx.isEmpty()) return true;
        Schedule schedule = entity.getData(ModAttachments.GEO_FX_SCHEDULE.get());
        long now = entity.level().getGameTime();
        for (KeyframeFx k : fx) schedule.pending.add(new Schedule.Pending(now + k.tick(), k.effect()));
        return true;
    }

    /** Servidor, todo tick de entidade: dispara o que chegou (so mobs com agenda pagam alguma coisa). */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()
                || !entity.hasData(ModAttachments.GEO_FX_SCHEDULE.get())) return;
        Schedule schedule = entity.getData(ModAttachments.GEO_FX_SCHEDULE.get());
        if (schedule.isEmpty()) return;
        long now = entity.level().getGameTime();
        for (int i = schedule.pending.size() - 1; i >= 0; i--) {
            if (schedule.pending.get(i).at() > now) continue;
            ResourceLocation effect = schedule.pending.remove(i).effect();
            try {
                TRIGGERS.computeIfAbsent(effect, EffectTriggers::entity).fire(entity);
            } catch (IllegalArgumentException unknown) {
                RespectCraft.LOGGER.warn("Keyframe calamidade:{} of {} is not a registered Calamidade effect", effect,
                        entity.getType());
            }
        }
    }

    /** Arquivo de animacao do jar (cache imutavel); objeto vazio se nao existir. */
    public static JsonObject load(ResourceLocation file) {
        return FILES.computeIfAbsent(file, id -> {
            String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
            try (InputStream in = GeoFxKeyframes.class.getResourceAsStream(path)) {
                if (in == null) {
                    RespectCraft.LOGGER.warn("Animation file {} not found for calamidade keyframes", id);
                    return new JsonObject();
                }
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                RespectCraft.LOGGER.warn("Could not read calamidade keyframes from {}", id, e);
                return new JsonObject();
            }
        });
    }
}
