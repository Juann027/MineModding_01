package com.tupinicraft.animation;

import com.tupinicraft.TupiniCraft;
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
            TupiniCraft.id("animations/player/tupinicraft_animated_player.animation.json");

    /** Tipos de evento reservados para os sistemas futuros (som, VFX, camera, sequencias). */
    public static final ResourceLocation EVENT_SOUND = TupiniCraft.id("sound");
    public static final ResourceLocation EVENT_PARTICLE = TupiniCraft.id("particle");
    public static final ResourceLocation EVENT_VFX = TupiniCraft.id("vfx");
    public static final ResourceLocation EVENT_CAMERA_MOVE = TupiniCraft.id("camera_move");
    public static final ResourceLocation EVENT_CAMERA_SHAKE = TupiniCraft.id("camera_shake");
    public static final ResourceLocation EVENT_CAMERA_FOV = TupiniCraft.id("camera_fov");
    public static final ResourceLocation EVENT_SEQUENCE_START = TupiniCraft.id("sequence_start");
    public static final ResourceLocation EVENT_SEQUENCE_END = TupiniCraft.id("sequence_end");
    /** Keyframes de som/particula/instrucao gravados no proprio clip (Blockbench) chegam com estes tipos. */
    public static final ResourceLocation EVENT_CLIP_SOUND = TupiniCraft.id("clip_sound");
    public static final ResourceLocation EVENT_CLIP_PARTICLE = TupiniCraft.id("clip_particle");
    public static final ResourceLocation EVENT_CLIP_INSTRUCTION = TupiniCraft.id("clip_instruction");

    /** Respiracao em loop, somada a pose vanilla. */
    public static final PlayerAnimationDef IDLE = register(PlayerAnimationDef
            .builder(TupiniCraft.id("idle"), PLAYER_FILE, "animation.tupinicraft.player.idle", 40)
            .loop().blend(PlayerAnimationDef.Blend.ADDITIVE).build());

    /**
     * Aceno de 2 s; o jogador pode pedir pela tecla F9. So o braco direito segue o clip; tronco, cabeca e
     * braco esquerdo somam os pequenos movimentos do clip a pose vanilla (da para acenar andando e olhando).
     */
    public static final PlayerAnimationDef WAVE = register(PlayerAnimationDef
            .builder(TupiniCraft.id("wave"), PLAYER_FILE, "animation.tupinicraft.player.wave", 40)
            .priority(10).overrideBones("RightArm").playerTriggerable().build());

    private PlayerAnimations() {}

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
