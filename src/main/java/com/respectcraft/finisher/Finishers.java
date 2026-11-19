package com.respectcraft.finisher;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import net.minecraft.resources.ResourceLocation;

/**
 * Catalogo das finalizacoes. Cada uma e um par de animacoes FULL_BODY com eventos temporizados (impacto, tremor,
 * FOV, som) e uma trilha de camera. Nada de classe por finalizacao.
 */
public final class Finishers {
    /** Evento de impacto no executor: o servidor toca som, particulas e o "tranco" da vitima. data = forca (1..3). */
    public static final ResourceLocation EVENT_HIT = RespectCraft.id("finisher_hit");

    public static final ResourceLocation TAKEDOWN_ID = RespectCraft.id("takedown");
    public static final ResourceLocation RASTEIRA_ID = RespectCraft.id("rasteira");

    /** Parametros enviados junto com o estado da animacao (NBT livre de PlayerAnimationState). */
    public static final String PARAM_FINISHER = "finisher";
    public static final String PARAM_ROLE = "role";
    public static final String PARAM_X = "ax";
    public static final String PARAM_Y = "ay";
    public static final String PARAM_Z = "az";
    public static final String PARAM_YAW = "yaw";

    private static final Map<ResourceLocation, FinisherDef> REGISTRY = new LinkedHashMap<>();

    /** Vitima caida durante a recuperacao (loop; o servidor para quando ela se recupera). */
    public static final PlayerAnimationDef KNOCKED_DOWN = PlayerAnimations.register(PlayerAnimationDef
            .builder(RespectCraft.id("knocked_down"), PlayerAnimations.PLAYER_FILE, "animation.respectcraft.player.knocked_down", 40)
            .loop().priority(40).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY).build());

    public static final FinisherDef TAKEDOWN = register(new FinisherDef(TAKEDOWN_ID,
            PlayerAnimations.register(PlayerAnimationDef
                    .builder(RespectCraft.id("finisher/takedown_attacker"), PlayerAnimations.PLAYER_FILE,
                            "animation.respectcraft.player.finisher.takedown_attacker", 60)
                    .priority(50).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY)
                    .event(0, PlayerAnimations.EVENT_SEQUENCE_START, "takedown")
                    .event(8, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(14, EVENT_HIT, "1").event(14, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.35,6")
                    .event(26, EVENT_HIT, "1").event(26, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,6")
                    .event(36, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(44, EVENT_HIT, "3").event(44, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.0,14")
                    .event(44, PlayerAnimations.EVENT_CAMERA_FOV, "0.82,12")
                    .event(59, PlayerAnimations.EVENT_SEQUENCE_END, "takedown").build()),
            PlayerAnimations.register(PlayerAnimationDef
                    .builder(RespectCraft.id("finisher/takedown_victim"), PlayerAnimations.PLAYER_FILE,
                            "animation.respectcraft.player.finisher.takedown_victim", 60)
                    .priority(50).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY)
                    .event(14, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.6,8")
                    .event(26, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.7,8")
                    .event(44, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.3,16")
                    .event(44, PlayerAnimations.EVENT_CAMERA_FOV, "1.15,10").build()),
            CameraTrack.of(
                    new CameraTrack.Key(0, 150, 4.6, 1.7, 1.1, 1.0, 0),
                    new CameraTrack.Key(14, 115, 3.3, 1.4, 1.1, 0.95, 2),
                    new CameraTrack.Key(30, 75, 3.0, 1.2, 1.0, 0.95, -2),
                    new CameraTrack.Key(44, 40, 2.6, 0.9, 0.6, 0.85, -5),
                    new CameraTrack.Key(60, 20, 4.2, 2.3, 0.4, 1.0, 0)),
            null, 3));

    public static final FinisherDef RASTEIRA = register(new FinisherDef(RASTEIRA_ID,
            PlayerAnimations.register(PlayerAnimationDef
                    .builder(RespectCraft.id("finisher/rasteira_attacker"), PlayerAnimations.PLAYER_FILE,
                            "animation.respectcraft.player.finisher.rasteira_attacker", 40)
                    .priority(50).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY)
                    .event(0, PlayerAnimations.EVENT_SEQUENCE_START, "rasteira")
                    .event(6, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(14, EVENT_HIT, "2").event(14, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.8,10")
                    .event(14, PlayerAnimations.EVENT_CAMERA_FOV, "0.85,8")
                    .event(39, PlayerAnimations.EVENT_SEQUENCE_END, "rasteira").build()),
            PlayerAnimations.register(PlayerAnimationDef
                    .builder(RespectCraft.id("finisher/rasteira_victim"), PlayerAnimations.PLAYER_FILE,
                            "animation.respectcraft.player.finisher.rasteira_victim", 40)
                    .priority(50).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY)
                    .event(14, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.1,12").build()),
            CameraTrack.of(
                    new CameraTrack.Key(0, 95, 4.0, 1.0, 0.9, 1.0, 0),
                    new CameraTrack.Key(14, 60, 2.8, 0.5, 0.5, 0.85, 4),
                    new CameraTrack.Key(40, 25, 4.3, 2.1, 0.3, 1.0, 0)),
            com.respectcraft.RespectCraft.id("combate/rasteira"), 2));

    /**
     * Queda da vitima (evento no clip da vitima): o servidor solta poeira do chao e o baque, vistos por todos.
     * data = "forca,recuo" (forca 1..3; recuo em blocos para tras da vitima, onde o corpo realmente bate no chao).
     */
    public static final ResourceLocation EVENT_FALL = RespectCraft.id("finisher_fall");

    public static final ResourceLocation COMBO_ID = RespectCraft.id("combo");

    /**
     * "Combo de rua" (Etapa 4, 7 s): guarda, direto no estomago, alvo dobra e cambaleia para a frente, chute frontal,
     * alvo e empurrado, cai de joelhos e termina em quatro apoios; o executor recua para a guarda e acompanha com o
     * olhar; o alvo levanta passando por um joelho e da um passo de volta; os dois relaxam. Cada participante tem o
     * seu clip (arquivo de combate), iniciados juntos pelo servidor.
     * Linha do tempo (ticks): 0 preparacao, 19 e 37 golpe (whoosh), 25 e 43 impacto, 62/65 queda,
     * 98 recuperacao, 139 encerramento.
     */
    public static final FinisherDef COMBO = register(new FinisherDef(COMBO_ID,
            PlayerAnimations.register(PlayerAnimationDef
                    .builder(RespectCraft.id("finisher/combo_attacker"), PlayerAnimations.COMBAT_FILE,
                            "animation.respectcraft.player.finisher.combo_attacker", 140)
                    .priority(50).transitionTicks(3).blend(PlayerAnimationDef.Blend.FULL_BODY)
                    .event(0, PlayerAnimations.EVENT_SEQUENCE_START, "combo")
                    .event(2, PlayerAnimations.EVENT_SOUND, "minecraft:item.armor.equip_leather")
                    .event(19, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(25, EVENT_HIT, "1").event(25, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.4,6")
                    .event(37, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(43, EVENT_HIT, "2").event(43, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.8,10")
                    .event(43, PlayerAnimations.EVENT_CAMERA_FOV, "0.86,8")
                    .event(124, PlayerAnimations.EVENT_SOUND, "minecraft:item.armor.equip_leather")
                    .event(139, PlayerAnimations.EVENT_SEQUENCE_END, "combo").build()),
            PlayerAnimations.register(PlayerAnimationDef
                    .builder(RespectCraft.id("finisher/combo_victim"), PlayerAnimations.COMBAT_FILE,
                            "animation.respectcraft.player.finisher.combo_victim", 140)
                    .priority(50).transitionTicks(3).blend(PlayerAnimationDef.Blend.FULL_BODY)
                    .event(25, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.6,8")
                    .event(43, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.1,12")
                    .event(43, PlayerAnimations.EVENT_CAMERA_FOV, "1.12,10")
                    .event(62, EVENT_FALL, "2,0.4").event(62, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.7,8")
                    .event(65, EVENT_FALL, "1,0.1")
                    .event(98, PlayerAnimations.EVENT_SOUND, "minecraft:item.armor.equip_generic").build()),
            CameraTrack.of(
                    new CameraTrack.Key(0, 150, 4.8, 1.8, 1.1, 1.0, 0),      // estabelecimento por tras do executor
                    new CameraTrack.Key(20, 108, 3.6, 1.4, 1.1, 0.95, 2),    // lateral: leitura do soco
                    new CameraTrack.Key(26, 100, 3.3, 1.3, 1.0, 0.92, 3),
                    new CameraTrack.Key(43, 82, 3.2, 0.9, 0.8, 0.88, -3),    // baixa: chute
                    new CameraTrack.Key(62, 58, 3.7, 2.0, 0.4, 0.95, -2),    // alta: queda
                    new CameraTrack.Key(92, 32, 4.0, 1.6, 0.5, 1.0, 0),      // orbita lenta com o alvo no chao
                    new CameraTrack.Key(122, 42, 4.4, 1.5, 1.0, 1.0, 0),     // alvo levantando
                    new CameraTrack.Key(140, 62, 4.8, 1.8, 1.1, 1.0, 0)),
            null, 3, true));

    /**
     * Etapa 4.8, palco do Leao do Fim (sem clip de finalizacao): no .geo.json a cabeca chega a ~1,41 bloco a frente do
     * centro, alem da caixa de 1,4 de largura; a 1,9 o jogador fica diante da cabeca em vez de dentro da juba.
     */
    public static final EntityFinisherClip.Alignment LEAO_DO_FIM_STAGING = FinisherParticipants.registerStaging(
            RespectCraft.id("leao_do_fim"), new EntityFinisherClip.Alignment(1.9, 0.0, 0F));

    private Finishers() {}

    public static synchronized FinisherDef register(FinisherDef def) {
        if (REGISTRY.putIfAbsent(def.id(), def) != null) throw new IllegalStateException("Finalizacao duplicada: " + def.id());
        return def;
    }

    public static FinisherDef get(ResourceLocation id) { return REGISTRY.get(id); }

    public static Collection<FinisherDef> all() { return Collections.unmodifiableCollection(REGISTRY.values()); }

    /** Finalizacao da qual esta animacao faz parte (executor ou vitima), ou null. */
    public static FinisherDef byAnimation(ResourceLocation animation) {
        for (FinisherDef def : REGISTRY.values()) {
            if (def.attacker().id().equals(animation) || def.victim().id().equals(animation)) return def;
        }
        return null;
    }

    /** Sorteio ponderado entre as finalizacoes liberadas. */
    public static FinisherDef pick(Predicate<ResourceLocation> hasSkill, RandomGenerator random) {
        List<FinisherDef> options = new ArrayList<>();
        int total = 0;
        for (FinisherDef def : REGISTRY.values()) {
            if (def.requiresSkill() == null || hasSkill.test(def.requiresSkill())) {
                options.add(def);
                total += def.weight();
            }
        }
        if (options.isEmpty()) return TAKEDOWN;
        int roll = random.nextInt(Math.max(1, total));
        for (FinisherDef def : options) {
            roll -= def.weight();
            if (roll < 0) return def;
        }
        return options.get(0);
    }

    public static void bootstrap() {}
}
