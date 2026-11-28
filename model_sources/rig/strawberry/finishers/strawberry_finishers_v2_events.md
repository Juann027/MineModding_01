# Strawberry Finishers V2 — Crusher + Blender: eventos e verificação

Arquivos: `respectcraft_strawberry_brutal_finishers_v2.bbmodel` e `respectcraft_strawberry_brutal_finishers_v2.animation.json` (as 5 animações; Crusher e Blender revisadas, as outras três idênticas à V1).

Verificação tick a tick (todos os 121/161 ticks, não só keyframes):

| Checagem | Crusher V2 | Blender V2 |
|---|---|---|
| Mãos do jogador → alvo (cabeça / pulsos) | ≤ 1,9 px | ≤ 1 px, exceto tick 87 (impacto, 3,4 px) |
| Deslize de pé plantado | ≤ 1,6 px (borda do cubo inclinado) | ≤ 0,3 px (giro sobre o pé-pivô) |
| Vítima abaixo do chão | 0 | 0 |
| Sobreposição de corpos (AABB aproximado) | ≤ 3,5 px, só cabeça × casca já oculta após a polpa | ≤ 2,5 px |
| `split_scene.py` | 0 ERROR | 0 ERROR |

Correção de visibilidade: na polpa a linha é `rig:hide:shell;rig:hide:damage_head;rig:hide:damage_torso;rig:show:core` — as placas de rachadura somem junto com a casca.

## `scene.strawberry_crusher`

```
tick  owner     event         data -> runtime
   4  @scene    camera        140,4.4,1.6,1.4,1.0,0 -> CameraTrack.Key
   9  @attacker sound         respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND
   9  @victim   instruction   rig:hide:face_normal -> keyframe timeline (rig)
   9  @victim   instruction   rig:show:eyes_wide -> keyframe timeline (rig)
   9  @victim   instruction   rig:show:brows_panic -> keyframe timeline (rig)
   9  @victim   instruction   rig:show:mouth_open -> keyframe timeline (rig)
   9  @victim   note          expression smug idle -> shock as the attacker lunges -> documentation only
  12  @attacker shake         0.5,5 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  12  @attacker note          grab both hands clamp the head sides (socket_head), victim lifted off the ground -> documentation only
  12  @scene    camera        70,3.2,1.9,1.9,0.95,-3 -> CameraTrack.Key
  12  @victim   instruction   rig:hide:mouth_open -> keyframe timeline (rig)
  12  @victim   instruction   rig:show:mouth_scream -> keyframe timeline (rig)
  16  @victim   note          reaction legs kick, hands claw at the attacker's forearms -> documentation only
  28  @attacker note          contact squeeze pulse 1 -> documentation only
  28  @attacker shake         0.25,4 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  28  @victim   note          audio rind creak (no sound id provided) -> documentation only
  38  @attacker hit           1 -> Finishers.EVENT_HIT
  38  @attacker shake         0.4,5 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  38  @victim   instruction   rig:show:damage_head -> keyframe timeline (rig)
  38  @victim   note          crack pulse 2 cracks the head shell -> documentation only
  38  @victim   note          vfx juice spurts from the head cracks (no effect id provided) -> documentation only
  48  @attacker note          contact squeeze pulse 3 -> documentation only
  48  @attacker shake         0.45,6 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  48  @scene    camera        30,2.8,1.7,1.8,0.92,5 -> CameraTrack.Key
  48  @victim   note          vfx seeds pop out of the cracks (no effect id provided) -> documentation only
  63  @attacker shake         0.7,6 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  63  @victim   fall          2,0.1 -> Finishers.EVENT_FALL
  63  @victim   note          reaction slammed down onto its knees -> documentation only
  63  @victim   note          vfx dust puff at socket_ground_contact -> documentation only
  68  @attacker fov           1.1,14 -> PlayerAnimations.EVENT_CAMERA_FOV
  68  @attacker note          other dramatic pause: inhale, shoulders up, grip re-set (68-83) -> documentation only
  68  @scene    camera        160,3.4,1.6,1.7,1.05,0 -> CameraTrack.Key
  84  @attacker hit           3 -> Finishers.EVENT_HIT
  84  @attacker shake         1.4,14 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  84  @attacker fov           0.8,8 -> PlayerAnimations.EVENT_CAMERA_FOV
  84  @attacker note          audio wet crunch on the crush (no sound id provided) -> documentation only
  84  @scene    camera        60,2.3,1.6,1.7,0.82,-7 -> CameraTrack.Key
  85  @victim   instruction   rig:hide:shell -> keyframe timeline (rig)
  85  @victim   instruction   rig:hide:damage_head -> keyframe timeline (rig)
  85  @victim   instruction   rig:hide:damage_torso -> keyframe timeline (rig)
  85  @victim   instruction   rig:show:core -> keyframe timeline (rig)
  85  @victim   instruction   rig:hide:eyes_wide -> keyframe timeline (rig)
  85  @victim   instruction   rig:show:eyes_closed -> keyframe timeline (rig)
  85  @victim   note          pulp shell bursts: shell + crack plates hidden together, pulp core exposed -> documentation only
  85  @victim   note          vfx juice burst + seeds + rind shards from socket_head (no effect id provided) -> documentation only
  85  @victim   note          other the leaf crown/stem should detach as separate props here; runtime has no detachable parts, the extra_* bones only pop and fall back while still parented -> documentation only
  92  @victim   instruction   rig:hide:mouth_scream -> keyframe timeline (rig)
  92  @victim   instruction   rig:show:mouth_open -> keyframe timeline (rig)
  95  @attacker note          release hands spring open, victim drops -> documentation only
 102  @attacker note          vfx juice flicked off the right hand (no effect id provided) -> documentation only
 105  @attacker shake         0.5,5 -> PlayerAnimations.EVENT_CAMERA_SHAKE
 105  @victim   fall          3,0.2 -> Finishers.EVENT_FALL
 105  @victim   note          vfx juice splat at socket_ground_contact -> documentation only
 111  @victim   fall          1,0.1 -> Finishers.EVENT_FALL
 120  @scene    camera        20,4.2,2.3,0.6,1.0,0 -> CameraTrack.Key
 120  @scene    execute        -> FinisherExecution (scene end)

attacker (Java):
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
    .event(105, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.5,5")

victim (Java):
    .event(63, Finishers.EVENT_FALL, "2,0.1")
    .event(105, Finishers.EVENT_FALL, "3,0.2")
    .event(111, Finishers.EVENT_FALL, "1,0.1")

camera (Java):
    new CameraTrack.Key(4, 140, 4.4, 1.6, 1.4, 1.0, 0)
    new CameraTrack.Key(12, 70, 3.2, 1.9, 1.9, 0.95, -3)
    new CameraTrack.Key(48, 30, 2.8, 1.7, 1.8, 0.92, 5)
    new CameraTrack.Key(68, 160, 3.4, 1.6, 1.7, 1.05, 0)
    new CameraTrack.Key(84, 60, 2.3, 1.6, 1.7, 0.82, -7)
    new CameraTrack.Key(120, 20, 4.2, 2.3, 0.6, 1.0, 0)
```

## `scene.strawberry_blender`

```
tick  owner     event         data -> runtime
   2  @scene    camera        130,4.6,1.6,1.3,1.0,0 -> CameraTrack.Key
   2  @victim   note          reaction shoves the attacker's chest with both palms -> documentation only
   9  @attacker shake         0.35,3 -> PlayerAnimations.EVENT_CAMERA_SHAKE
   9  @attacker note          grab both hands snatch the victim's wrists (socket_right_hand / socket_left_hand) -> documentation only
   9  @attacker sound         respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND
  10  @victim   instruction   rig:hide:face_normal -> keyframe timeline (rig)
  10  @victim   instruction   rig:show:eyes_wide -> keyframe timeline (rig)
  10  @victim   instruction   rig:show:brows_panic -> keyframe timeline (rig)
  10  @victim   instruction   rig:show:mouth_scream -> keyframe timeline (rig)
  10  @victim   note          expression panic as it is yanked forward -> documentation only
  16  @attacker note          movement pivots on the right foot (no foot travel) -> documentation only
  16  @victim   note          reaction feet scramble and drag as the spin starts -> documentation only
  28  @attacker sound         respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND
  28  @scene    camera        90,5.2,1.8,1.2,1.05,0 -> CameraTrack.Key
  36  @victim   note          reaction lifted off the ground by the spin -> documentation only
  44  @attacker sound         respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND
  44  @attacker fov           1.15,26 -> PlayerAnimations.EVENT_CAMERA_FOV
  44  @victim   note          other centrifugal stretch (torso/head scale) -> documentation only
  52  @victim   instruction   rig:show:damage_torso -> keyframe timeline (rig)
  52  @victim   note          crack torso splits under the strain -> documentation only
  52  @victim   note          vfx juice droplets flung outward every turn 52-70 (no effect id provided) -> documentation only
  60  @attacker sound         respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND
  60  @scene    camera        0,3.2,2.6,1.0,1.12,10 -> CameraTrack.Key
  70  @attacker note          other hard brake: left foot slams down, counter-twist whip (70-76) -> documentation only
  70  @attacker shake         0.4,4 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  78  @attacker note          other dramatic hang: victim upside down overhead (78-84) -> documentation only
  78  @scene    camera        160,4.2,1.0,3.2,1.05,4 -> CameraTrack.Key
  87  @attacker hit           2 -> Finishers.EVENT_HIT
  87  @attacker shake         1.2,10 -> PlayerAnimations.EVENT_CAMERA_SHAKE
  87  @attacker fov           0.86,6 -> PlayerAnimations.EVENT_CAMERA_FOV
  87  @attacker note          audio heavy thud (no sound id provided) -> documentation only
  87  @scene    camera        45,3.0,1.0,0.5,0.9,-5 -> CameraTrack.Key
  87  @victim   fall          3,0.3 -> Finishers.EVENT_FALL
  87  @victim   instruction   rig:hide:mouth_scream -> keyframe timeline (rig)
  87  @victim   instruction   rig:show:mouth_open -> keyframe timeline (rig)
  87  @victim   instruction   rig:show:damage_head -> keyframe timeline (rig)
  87  @victim   note          vfx ground impact dust + juice spray at socket_vfx_core (no effect id provided) -> documentation only
  92  @victim   note          movement dragged across the floor by the wrists -> documentation only
 100  @attacker note          other coiled backswing, dead stop (100-106) -> documentation only
 100  @attacker fov           1.1,8 -> PlayerAnimations.EVENT_CAMERA_FOV
 100  @victim   instruction   rig:hide:mouth_open -> keyframe timeline (rig)
 100  @victim   instruction   rig:show:mouth_scream -> keyframe timeline (rig)
 107  @attacker sound         respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND
 113  @attacker note          release hands open at the peak of the whip, victim hurled forward -> documentation only
 113  @attacker shake         0.5,4 -> PlayerAnimations.EVENT_CAMERA_SHAKE
 121  @attacker shake         1.7,14 -> PlayerAnimations.EVENT_CAMERA_SHAKE
 121  @attacker fov           0.8,8 -> PlayerAnimations.EVENT_CAMERA_FOV
 121  @attacker note          audio crash + squelch (no sound id provided) -> documentation only
 121  @scene    camera        30,3.4,1.2,0.3,0.84,-6 -> CameraTrack.Key
 121  @victim   fall          3,0.5 -> Finishers.EVENT_FALL
 121  @victim   instruction   rig:hide:shell -> keyframe timeline (rig)
 121  @victim   instruction   rig:hide:damage_head -> keyframe timeline (rig)
 121  @victim   instruction   rig:hide:damage_torso -> keyframe timeline (rig)
 121  @victim   instruction   rig:show:core -> keyframe timeline (rig)
 121  @victim   instruction   rig:hide:eyes_wide -> keyframe timeline (rig)
 121  @victim   instruction   rig:show:eyes_closed -> keyframe timeline (rig)
 121  @victim   note          pulp the crash bursts the shell: shell + crack plates hidden together, core exposed -> documentation only
 121  @victim   note          vfx big juice splat + seeds + rind shards at socket_vfx_core (no effect id provided) -> documentation only
 121  @victim   note          other crown/stem should fly off as separate props; extra_* bones only pop and land askew (still parented) -> documentation only
 129  @victim   fall          1,0.1 -> Finishers.EVENT_FALL
 146  @attacker note          other dominant stance over the burst fruit, neck crack 150-153 -> documentation only
 160  @scene    camera        10,4.6,2.6,0.4,1.0,0 -> CameraTrack.Key
 160  @scene    execute        -> FinisherExecution (scene end)

attacker (Java):
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
    .event(121, PlayerAnimations.EVENT_CAMERA_FOV, "0.8,8")

victim (Java):
    .event(87, Finishers.EVENT_FALL, "3,0.3")
    .event(121, Finishers.EVENT_FALL, "3,0.5")
    .event(129, Finishers.EVENT_FALL, "1,0.1")

camera (Java):
    new CameraTrack.Key(2, 130, 4.6, 1.6, 1.3, 1.0, 0)
    new CameraTrack.Key(28, 90, 5.2, 1.8, 1.2, 1.05, 0)
    new CameraTrack.Key(60, 0, 3.2, 2.6, 1.0, 1.12, 10)
    new CameraTrack.Key(78, 160, 4.2, 1.0, 3.2, 1.05, 4)
    new CameraTrack.Key(87, 45, 3.0, 1.0, 0.5, 0.9, -5)
    new CameraTrack.Key(121, 30, 3.4, 1.2, 0.3, 0.84, -6)
    new CameraTrack.Key(160, 10, 4.6, 2.6, 0.4, 1.0, 0)
```
