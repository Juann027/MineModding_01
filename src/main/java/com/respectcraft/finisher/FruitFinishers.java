package com.respectcraft.finisher;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.fx.FinisherFxDef;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Pools proprios de NPCs (Etapa 4.13.3). Hoje: as cinco finalizacoes do morango, feitas no Blockbench Web pelo dono
 * (fonte {@code model_sources/rig/strawberry/finishers/}) e separadas pelo Finisher Scene Authoring Kit
 * ({@code tools/scenekit/strawberry_finishers.py}): clip do jogador em {@link #AUTHORED_SCENES_FILE}, clip do morango
 * no arquivo proprio dele ({@code animation_file} + {@code clip_overrides} do binding). Eventos e cameras copiados das
 * linhas Java do relatorio da separacao ({@code .events.txt}); o tick 0 e o ultimo levam o inicio/fim de sequencia.
 *
 * <p>Regras: exclusivas de {@code respectcraft:strawberry} ({@link FinisherTargeting#npcExclusive}: nunca contra
 * jogador, chefe, manequim ou outro mob), executor sempre jogador, sem desbloqueio pessoal, pesos iguais, sorteadas
 * pelo servidor ({@link FinisherSelection}). Sem clip de jogador vitima (cena so PvE, nada de PvP falso): as quedas e o
 * corte da musica ({@link Finishers#EVENT_SILENCE}, no estouro da polpa de cada cena) ficam em
 * {@code entityVictimEvents}.
 */
public final class FruitFinishers {
    public static final ResourceLocation AUTHORED_SCENES_FILE =
            RespectCraft.id("animations/player/respectcraft_authored_scenes.animation.json");
    public static final ResourceLocation STRAWBERRY = RespectCraft.id("strawberry");

    public static final FinisherDef STRAWBERRY_CRUSHER = strawberry("strawberry_crusher", 120,
            FinisherFxDef.Look.COMBO_PRESSURE, FinisherFxDef.Tier.INTENSE, b -> b
                    .event(9, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(12, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.5,5")
                    .event(28, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.25,4")
                    .event(38, Finishers.EVENT_HIT, "1")
                    .event(38, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.4,5")
                    .event(48, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,6")
                    .event(63, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.7,6")
                    .event(68, PlayerAnimations.EVENT_CAMERA_FOV, "1.1,14")
                    .event(84, Finishers.EVENT_HIT, "3")
                    .event(84, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.4,14")
                    .event(84, PlayerAnimations.EVENT_CAMERA_FOV, "0.8,8")
                    .event(105, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.5,5"),
            List.of(fall(63, "2,0.1"), silence(84), fall(105, "3,0.2"), fall(111, "1,0.1")),
            CameraTrack.of(
                    new CameraTrack.Key(4, 140, 4.4, 1.6, 1.4, 1.0, 0),
                    new CameraTrack.Key(12, 70, 3.2, 1.9, 1.9, 0.95, -3),
                    new CameraTrack.Key(48, 30, 2.8, 1.7, 1.8, 0.92, 5),
                    new CameraTrack.Key(68, 160, 3.4, 1.6, 1.7, 1.05, 0),
                    new CameraTrack.Key(84, 60, 2.3, 1.6, 1.7, 0.82, -7),
                    new CameraTrack.Key(120, 20, 4.2, 2.3, 0.6, 1.0, 0)));

    public static final FinisherDef FRUIT_PUNCH = strawberry("fruit_punch", 140,
            FinisherFxDef.Look.COMBO_PRESSURE, FinisherFxDef.Tier.INTENSE, b -> b
                    .event(8, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(13, Finishers.EVENT_HIT, "2")
                    .event(13, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.7,6")
                    .event(26, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(27, Finishers.EVENT_HIT, "1")
                    .event(27, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,4")
                    .event(34, Finishers.EVENT_HIT, "1")
                    .event(34, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,4")
                    .event(41, Finishers.EVENT_HIT, "1")
                    .event(41, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,4")
                    .event(49, Finishers.EVENT_HIT, "1")
                    .event(49, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,4")
                    .event(57, Finishers.EVENT_HIT, "1")
                    .event(57, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.45,4")
                    .event(72, PlayerAnimations.EVENT_CAMERA_FOV, "1.1,10")
                    .event(86, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(89, Finishers.EVENT_HIT, "3")
                    .event(89, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.5,14")
                    .event(89, PlayerAnimations.EVENT_CAMERA_FOV, "0.8,8")
                    .event(101, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.7,6"),
            List.of(silence(89), fall(101, "3,0.4"), fall(110, "1,0.1")),
            CameraTrack.of(
                    new CameraTrack.Key(8, 120, 4.4, 1.5, 1.3, 1.0, 0),
                    new CameraTrack.Key(13, 75, 3.0, 1.1, 1.0, 0.92, -3),
                    new CameraTrack.Key(41, 40, 2.8, 0.9, 1.4, 0.95, 5),
                    new CameraTrack.Key(72, 160, 3.6, 1.4, 1.2, 1.05, 0),
                    new CameraTrack.Key(89, 70, 2.3, 1.6, 1.6, 0.82, -8),
                    new CameraTrack.Key(140, 25, 4.4, 2.2, 0.5, 1.0, 0)));

    public static final FinisherDef GROUND_SMASHER = strawberry("ground_smasher", 120,
            FinisherFxDef.Look.COMBO_PRESSURE, FinisherFxDef.Tier.INTENSE, b -> b
                    .event(4, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(11, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.4,4")
                    .event(31, PlayerAnimations.EVENT_CAMERA_FOV, "1.08,6")
                    .event(37, Finishers.EVENT_HIT, "2")
                    .event(37, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.1,10")
                    .event(37, PlayerAnimations.EVENT_CAMERA_FOV, "0.88,6")
                    .event(52, Finishers.EVENT_HIT, "2")
                    .event(52, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.2,10")
                    .event(66, Finishers.EVENT_HIT, "2")
                    .event(66, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.2,10")
                    .event(74, PlayerAnimations.EVENT_CAMERA_FOV, "1.1,8")
                    .event(88, Finishers.EVENT_HIT, "3")
                    .event(88, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.5,13")
                    .event(88, PlayerAnimations.EVENT_CAMERA_FOV, "0.82,8"),
            List.of(fall(37, "3,0.3"), silence(88), fall(97, "1,0.1")),
            CameraTrack.of(
                    new CameraTrack.Key(4, 110, 4.6, 1.4, 1.2, 1.0, 0),
                    new CameraTrack.Key(31, 150, 3.6, 2.0, 2.0, 1.0, 4),
                    new CameraTrack.Key(37, 60, 3.0, 1.0, 0.5, 0.9, -5),
                    new CameraTrack.Key(74, 20, 3.2, 0.6, 0.4, 1.05, 6),
                    new CameraTrack.Key(88, 75, 2.6, 1.2, 0.4, 0.85, -6),
                    new CameraTrack.Key(120, 30, 4.2, 2.4, 0.4, 1.0, 0)));

    public static final FinisherDef SHUT_UP = strawberry("shut_up", 80,
            FinisherFxDef.Look.TAKEDOWN_FOCUS, FinisherFxDef.Tier.MEDIUM, b -> b
                    .event(14, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.35,3")
                    .event(14, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(22, Finishers.EVENT_HIT, "2")
                    .event(22, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.8,8")
                    .event(36, PlayerAnimations.EVENT_CAMERA_FOV, "1.1,8")
                    .event(44, Finishers.EVENT_HIT, "3")
                    .event(44, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.8,14")
                    .event(44, PlayerAnimations.EVENT_CAMERA_FOV, "0.8,8")
                    .event(57, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.5,5"),
            List.of(silence(44), fall(57, "2,0.2")),
            CameraTrack.of(
                    new CameraTrack.Key(0, 200, 4.0, 1.5, 1.5, 1.0, 0),
                    new CameraTrack.Key(22, 60, 2.8, 1.6, 1.6, 0.92, -4),
                    new CameraTrack.Key(36, 160, 3.4, 0.8, 2.2, 1.05, 3),
                    new CameraTrack.Key(44, 80, 2.4, 1.4, 1.2, 0.84, -7),
                    new CameraTrack.Key(80, 30, 4.2, 2.0, 1.0, 1.0, 0)));

    /** O estouro decisivo do Blender (tick 121) e uma QUEDA com a polpa, nao um EVENT_HIT (o ultimo golpe e o 87). */
    public static final FinisherDef STRAWBERRY_BLENDER = strawberry("strawberry_blender", 160,
            FinisherFxDef.Look.COMBO_PRESSURE, FinisherFxDef.Tier.INTENSE, b -> b
                    .event(9, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.35,3")
                    .event(9, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(28, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(44, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(44, PlayerAnimations.EVENT_CAMERA_FOV, "1.15,26")
                    .event(60, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(70, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.4,4")
                    .event(87, Finishers.EVENT_HIT, "2")
                    .event(87, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.2,10")
                    .event(87, PlayerAnimations.EVENT_CAMERA_FOV, "0.86,6")
                    .event(100, PlayerAnimations.EVENT_CAMERA_FOV, "1.1,8")
                    .event(107, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                    .event(113, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.5,4")
                    .event(121, PlayerAnimations.EVENT_CAMERA_SHAKE, "1.7,14")
                    .event(121, PlayerAnimations.EVENT_CAMERA_FOV, "0.8,8"),
            List.of(fall(87, "3,0.3"), fall(121, "3,0.5"), silence(121), fall(129, "1,0.1")),
            CameraTrack.of(
                    new CameraTrack.Key(2, 130, 4.6, 1.6, 1.3, 1.0, 0),
                    new CameraTrack.Key(28, 90, 5.2, 1.8, 1.2, 1.05, 0),
                    new CameraTrack.Key(60, 0, 3.2, 2.6, 1.0, 1.12, 10),
                    new CameraTrack.Key(78, 160, 4.2, 1.0, 3.2, 1.05, 4),
                    new CameraTrack.Key(87, 45, 3.0, 1.0, 0.5, 0.9, -5),
                    new CameraTrack.Key(121, 30, 3.4, 1.2, 0.3, 0.84, -6),
                    new CameraTrack.Key(160, 10, 4.6, 2.6, 0.4, 1.0, 0)));

    /** As cinco do morango, na ordem do registro. */
    public static final List<FinisherDef> STRAWBERRY_POOL =
            List.of(STRAWBERRY_CRUSHER, FRUIT_PUNCH, GROUND_SMASHER, SHUT_UP, STRAWBERRY_BLENDER);

    private FruitFinishers() {}

    /** Forca o carregamento da classe (registro no catalogo). */
    static void bootstrap() {}

    private static FinisherDef strawberry(String name, int ticks, FinisherFxDef.Look look, FinisherFxDef.Tier tier,
                                          java.util.function.UnaryOperator<PlayerAnimationDef.Builder> events,
                                          List<PlayerAnimationDef.TimedEvent> victimEvents, CameraTrack camera) {
        PlayerAnimationDef.Builder b = PlayerAnimationDef
                .builder(RespectCraft.id("finisher/" + name + "_attacker"), AUTHORED_SCENES_FILE,
                        "animation.respectcraft.player.scene." + name + "_attacker", ticks)
                .priority(50).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY)
                .event(0, PlayerAnimations.EVENT_SEQUENCE_START, name);
        PlayerAnimationDef attacker = PlayerAnimations.register(events.apply(b)
                .event(ticks - 1, PlayerAnimations.EVENT_SEQUENCE_END, name).build());
        return Finishers.register(new FinisherDef(RespectCraft.id(name), attacker, null, camera, null, 1, false, List.of(),
                new FinisherFxDef(RespectCraft.id("finisher_fx/" + name + "_scene"), look, tier,
                        FinisherFxDef.Orientation.ARRIVING_FROM_ATTACKER),
                FinisherTargeting.npcExclusive(STRAWBERRY),
                new FinisherRigs(List.of(), List.of(
                        new FinisherRigs.Requirement(FinisherRole.ATTACKER, Set.of(FinisherRigs.Requirement.PLAYER_TYPE),
                                Set.of(), null, Set.of(), true),
                        new FinisherRigs.Requirement(FinisherRole.VICTIM, Set.of(STRAWBERRY), Set.of(),
                                RespectCraft.id("fruit_humanoid_v1"), Set.of(), true))),
                victimEvents));
    }

    private static PlayerAnimationDef.TimedEvent fall(int tick, String data) {
        return new PlayerAnimationDef.TimedEvent(tick, Finishers.EVENT_FALL, data);
    }

    private static PlayerAnimationDef.TimedEvent silence(int tick) {
        return new PlayerAnimationDef.TimedEvent(tick, Finishers.EVENT_SILENCE, "");
    }
}
