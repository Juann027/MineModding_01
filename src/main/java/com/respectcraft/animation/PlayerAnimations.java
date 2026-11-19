package com.respectcraft.animation;

import com.respectcraft.RespectCraft;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Catalogo de animacoes de jogador, igual no cliente e no servidor (e codigo, nao estado de mundo).
 * Registre novas animacoes na inicializacao do mod com {@link #register}; nao e preciso classe nova.
 */
public final class PlayerAnimations {
    private static final Map<ResourceLocation, PlayerAnimationDef> REGISTRY = new LinkedHashMap<>();

    /** Arquivo de animacoes exportado do Blockbench para o rig do jogador. */
    public static final ResourceLocation PLAYER_FILE =
            RespectCraft.id("animations/player/respectcraft_animated_player.animation.json");

    /** Tipos de evento reservados para os sistemas futuros (som, VFX, camera, sequencias). */
    public static final ResourceLocation EVENT_SOUND = RespectCraft.id("sound");
    public static final ResourceLocation EVENT_PARTICLE = RespectCraft.id("particle");
    public static final ResourceLocation EVENT_VFX = RespectCraft.id("vfx");
    public static final ResourceLocation EVENT_CAMERA_MOVE = RespectCraft.id("camera_move");
    public static final ResourceLocation EVENT_CAMERA_SHAKE = RespectCraft.id("camera_shake");
    public static final ResourceLocation EVENT_CAMERA_FOV = RespectCraft.id("camera_fov");
    public static final ResourceLocation EVENT_SEQUENCE_START = RespectCraft.id("sequence_start");
    public static final ResourceLocation EVENT_SEQUENCE_END = RespectCraft.id("sequence_end");
    /** Keyframes de som/particula/instrucao gravados no proprio clip (Blockbench) chegam com estes tipos. */
    public static final ResourceLocation EVENT_CLIP_SOUND = RespectCraft.id("clip_sound");
    public static final ResourceLocation EVENT_CLIP_PARTICLE = RespectCraft.id("clip_particle");
    public static final ResourceLocation EVENT_CLIP_INSTRUCTION = RespectCraft.id("clip_instruction");

    /** Respiracao em loop, somada a pose vanilla. */
    public static final PlayerAnimationDef IDLE = register(PlayerAnimationDef
            .builder(RespectCraft.id("idle"), PLAYER_FILE, "animation.respectcraft.player.idle", 40)
            .loop().blend(PlayerAnimationDef.Blend.ADDITIVE).build());

    /**
     * Aceno de 2 s; o jogador pode pedir pela tecla F9. So o braco direito segue o clip; tronco, cabeca e
     * braco esquerdo somam os pequenos movimentos do clip a pose vanilla (da para acenar andando e olhando).
     */
    public static final PlayerAnimationDef WAVE = register(PlayerAnimationDef
            .builder(RespectCraft.id("wave"), PLAYER_FILE, "animation.respectcraft.player.wave", 40)
            .priority(10).overrideBones("RightArm").playerTriggerable().build());

    // ------------------------------------------------------------------ biblioteca de combate e chao (Etapa 4)
    // Clips em COMBAT_FILE, gerados por tools/player_anim (fonte editavel: model_sources/player/*.bbmodel).
    // Todos comecam e terminam numa pose conhecida: em pe (neutra), guarda ou quatro apoios.

    /** Arquivo da biblioteca de combate/chao e das coreografias da Etapa 4. */
    public static final ResourceLocation COMBAT_FILE =
            RespectCraft.id("animations/player/respectcraft_player_combat.animation.json");

    /** Guarda de combate em loop (pernas em base, punhos no queixo). */
    public static final PlayerAnimationDef COMBAT_IDLE = register(library("combat_idle", 40, 6, 5).loop().build());
    /** Direto de direita com avanco: antecipacao, golpe (tick 13), retorno a guarda e a pose neutra. */
    public static final PlayerAnimationDef PUNCH = register(library("punch", 26, 3, 20)
            .event(10, EVENT_SOUND, "respectcraft:finisher.whoosh").build());
    /** Chute frontal com a perna de tras: recolhe, estende (tick 14), recolhe e pousa. */
    public static final PlayerAnimationDef KICK = register(library("kick", 34, 3, 20)
            .event(11, EVENT_SOUND, "respectcraft:finisher.whoosh").build());
    /** Reacao a um golpe no tronco: dobra para a frente e se recompoe. */
    public static final PlayerAnimationDef HIT_REACTION = register(library("hit_reaction", 20, 1, 30).build());
    /** Desequilibrio: cambaleia para os lados e se firma. */
    public static final PlayerAnimationDef STAGGER = register(library("stagger", 34, 2, 30).build());
    /** Queda controlada: de pe ate quatro apoios (termina exatamente na pose de HANDS_AND_KNEES). */
    public static final PlayerAnimationDef KNOCKDOWN = register(library("knockdown", 34, 1, 40)
            .event(20, EVENT_SOUND, "minecraft:entity.player.small_fall")
            .event(23, EVENT_SOUND, "minecraft:entity.player.small_fall").build());
    /** Ajoelha nos dois joelhos, fica e levanta. */
    public static final PlayerAnimationDef KNEEL = register(library("kneel", 60, 3, 10).build());
    /** Quatro apoios (pose neutra reutilizavel), respirando em loop. */
    public static final PlayerAnimationDef HANDS_AND_KNEES = register(library("hands_and_knees", 40, 6, 40).loop().build());
    /** Engatinhar no lugar (ciclo de diagonais); o deslocamento no mundo e de quem chama. */
    public static final PlayerAnimationDef CRAWL = register(library("crawl", 24, 6, 40).loop().build());
    /** Recuperacao: de quatro apoios ate em pe, passando por um joelho. */
    public static final PlayerAnimationDef GET_UP = register(library("get_up", 40, 2, 40).build());

    // ------------------------------------------------------------------ sequencias (Etapa 4.1)

    /**
     * Queda, dois segundos em quatro apoios e recuperacao, sem voltar a pose em pe entre os clips
     * (o fim de KNOCKDOWN e o inicio de GET_UP sao a pose de HANDS_AND_KNEES).
     */
    public static final AnimationSequence FALL_AND_RECOVER =
            AnimationSequence.of(KNOCKDOWN).thenFor(HANDS_AND_KNEES, 40).then(GET_UP);

    private PlayerAnimations() {}

    /** Clip da biblioteca: id respectcraft:<nome>, clip animation.respectcraft.player.<nome> em COMBAT_FILE. */
    private static PlayerAnimationDef.Builder library(String name, int ticks, int transition, int priority) {
        return PlayerAnimationDef.builder(RespectCraft.id(name), COMBAT_FILE, "animation.respectcraft.player." + name, ticks)
                .transitionTicks(transition).priority(priority).blend(PlayerAnimationDef.Blend.FULL_BODY);
    }

    public static synchronized PlayerAnimationDef register(PlayerAnimationDef def) {
        if (REGISTRY.putIfAbsent(def.id(), def) != null) {
            throw new IllegalStateException("Animacao de jogador duplicada: " + def.id());
        }
        return def;
    }

    public static PlayerAnimationDef get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<PlayerAnimationDef> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Garante o carregamento da classe (e das animacoes padrao) na inicializacao do mod. */
    public static void bootstrap() {}
}
