#!/usr/bin/env python3
"""Gera os clips das finalizacoes (e da vitima caida) no arquivo de animacoes do rig do jogador.

Uso: python3 tools/gen_finisher_clips.py   (reescreve so os clips listados aqui; os demais ficam intactos)

Convencoes Bedrock/GeckoLib: Body x+ inclina para a frente; braco x- levanta para a frente; braco direito z+
abre para o lado (esquerdo: z-); Root gira em torno dos pes. Tempos em segundos (tick / 20).
"""
import json
import pathlib

FILE = pathlib.Path(__file__).resolve().parent.parent / \
    "src/main/resources/assets/tupinicraft/animations/player/tupinicraft_animated_player.animation.json"
P = "animation.tupinicraft.player."
EASE = "easeInOutSine"


def track(*frames, easing=EASE):
    """frames: (tick, [x, y, z]) -> dict de keyframes."""
    out = {}
    for i, (tick, vec) in enumerate(frames):
        key = f"{tick / 20:.2f}".rstrip("0").rstrip(".") if tick else "0.0"
        if "." not in key:
            key += ".0"
        entry = {"vector": [float(v) for v in vec]}
        if i:
            entry["easing"] = easing
        out[key] = entry
    return out


def bone(rotation=None, position=None):
    b = {}
    if rotation:
        b["rotation"] = track(*rotation)
    if position:
        b["position"] = track(*position)
    return b


Z = [0, 0, 0]

clips = {
    # ---------------------------------------------------------------- takedown (60 ticks)
    "finisher.takedown_attacker": (3.0, {
        "Body": bone(rotation=[(0, Z), (6, [5, -20, 0]), (14, [8, 18, 0]), (18, [5, 0, 0]), (26, [8, -18, 0]),
                               (30, [4, 0, 0]), (36, [-12, -30, 0]), (44, [35, 10, 0]), (50, [30, 5, 0]),
                               (56, [10, 0, 0]), (60, Z)]),
        "Head": bone(rotation=[(0, Z), (6, [10, 15, 0]), (14, [10, -10, 0]), (26, [10, 10, 0]), (36, [-5, 20, 0]),
                               (44, [25, 0, 0]), (56, [5, 0, 0]), (60, Z)]),
        "RightArm": bone(rotation=[(0, Z), (6, [-30, 0, 25]), (10, [-45, -20, 40]), (14, [-95, 0, 0]),
                                   (18, [-35, 0, 20]), (22, [-50, -20, 40]), (26, [-100, 5, 0]), (30, [-40, 0, 15]),
                                   (36, [-165, 0, 15]), (40, [-170, 0, 10]), (44, [-55, 0, 0]), (50, [-45, 0, 5]),
                                   (56, [-10, 0, 0]), (60, Z)]),
        "LeftArm": bone(rotation=[(0, Z), (6, [-75, 10, 0]), (10, [-85, 15, 0]), (36, [-85, 15, 0]),
                                  (44, [-35, 0, -10]), (50, [-30, 0, -10]), (56, [-5, 0, 0]), (60, Z)]),
        "RightLeg": bone(rotation=[(0, Z), (6, [15, 0, 0]), (30, [15, 0, 0]), (36, [25, 0, 3]), (44, [-20, 0, 0]),
                                   (56, [0, 0, 0]), (60, Z)]),
        "LeftLeg": bone(rotation=[(0, Z), (6, [-20, 0, 0]), (30, [-20, 0, 0]), (36, [-10, 0, -3]), (44, [25, 0, 0]),
                                  (56, [0, 0, 0]), (60, Z)]),
        "Root": bone(position=[(0, Z), (36, [0, 0.5, 0]), (44, [0, -2.5, 0]), (52, [0, -2, 0]), (60, Z)]),
    }),
    "finisher.takedown_victim": (3.0, {
        "Body": bone(rotation=[(0, Z), (6, [12, 0, 0]), (14, [-6, -15, 4]), (18, [6, 0, 0]), (26, [-8, 18, -4]),
                               (30, [8, 0, 0]), (36, [-12, 0, 0]), (44, [-20, 0, 0]), (60, [-20, 0, 0])]),
        "Head": bone(rotation=[(0, Z), (6, [15, 0, 0]), (14, [-15, -40, -10]), (18, [5, -10, 0]),
                               (26, [-15, 40, 10]), (30, [5, 10, 0]), (36, [10, 0, 0]), (44, [-30, 0, 0]),
                               (60, [-25, 0, 0])]),
        "RightArm": bone(rotation=[(0, Z), (6, [-40, 0, 10]), (14, [-20, 0, 45]), (26, [-30, 0, 20]),
                                   (36, [-60, 0, 15]), (44, [-160, 0, 30]), (60, [-165, 0, 25])]),
        "LeftArm": bone(rotation=[(0, Z), (6, [-40, 0, -10]), (14, [-30, 0, -20]), (26, [-20, 0, -45]),
                                  (36, [-60, 0, -15]), (44, [-160, 0, -30]), (60, [-165, 0, -25])]),
        "RightLeg": bone(rotation=[(0, Z), (36, [10, 0, 0]), (44, [-25, 0, 5]), (60, [-20, 0, 8])]),
        "LeftLeg": bone(rotation=[(0, Z), (36, [-5, 0, 0]), (44, [-10, 0, -5]), (60, [-5, 0, -8])]),
        "Root": bone(rotation=[(0, Z), (36, [-8, 0, 0]), (44, [-84, 0, 0]), (48, [-80, 0, 0]), (52, [-84, 0, 0]),
                               (60, [-84, 0, 0])],
                     position=[(0, Z), (36, Z), (44, [0, 2, 0]), (60, [0, 2, 0])]),
    }),
    # ---------------------------------------------------------------- rasteira (40 ticks)
    "finisher.rasteira_attacker": (2.0, {
        "Root": bone(rotation=[(0, Z), (4, [0, 30, 0]), (10, [0, -120, 0]), (14, [0, -240, 0]), (18, [0, -360, 0]),
                               (40, [0, -360, 0])],
                     position=[(0, Z), (4, [0, -5, 0]), (18, [0, -6, 0]), (28, [0, -4, 0]), (36, [0, -0.5, 0]),
                               (40, Z)]),
        "Body": bone(rotation=[(0, Z), (4, [25, 0, 0]), (18, [30, 0, 0]), (28, [20, 0, 0]), (40, Z)]),
        "Head": bone(rotation=[(0, Z), (4, [-20, 0, 0]), (18, [-25, 0, 0]), (40, Z)]),
        "RightLeg": bone(rotation=[(0, Z), (4, [-20, 0, 40]), (10, [-10, 0, 75]), (14, [-5, 0, 85]),
                                   (20, [-10, 0, 40]), (30, [-40, 0, 5]), (40, Z)]),
        "LeftLeg": bone(rotation=[(0, Z), (4, [-80, 0, 0]), (18, [-85, 0, 0]), (30, [-40, 0, 0]), (40, Z)]),
        "RightArm": bone(rotation=[(0, Z), (4, [-30, 0, 20]), (14, [-10, 0, 70]), (24, [-40, 0, 20]), (40, Z)]),
        "LeftArm": bone(rotation=[(0, Z), (4, [-60, 0, -10]), (14, [-20, 0, -40]), (24, [-50, 0, -10]), (40, Z)]),
    }),
    "finisher.rasteira_victim": (2.0, {
        "Root": bone(rotation=[(0, Z), (12, Z), (16, [-30, 0, 5]), (22, [-84, 0, 0]), (26, [-80, 0, 0]),
                               (30, [-84, 0, 0]), (40, [-84, 0, 0])],
                     position=[(0, Z), (12, Z), (22, [0, 2, 0]), (40, [0, 2, 0])]),
        "RightLeg": bone(rotation=[(0, Z), (12, Z), (16, [-60, 0, 10]), (22, [-30, 0, 8]), (40, [-20, 0, 8])]),
        "LeftLeg": bone(rotation=[(0, Z), (12, Z), (16, [-45, 0, -10]), (22, [-15, 0, -8]), (40, [-10, 0, -8])]),
        "RightArm": bone(rotation=[(0, Z), (12, Z), (16, [-120, 0, 60]), (22, [-160, 0, 30]), (40, [-165, 0, 25])]),
        "LeftArm": bone(rotation=[(0, Z), (12, Z), (16, [-120, 0, -60]), (22, [-160, 0, -30]), (40, [-165, 0, -25])]),
        "Head": bone(rotation=[(0, Z), (12, Z), (16, [30, 0, 0]), (22, [-30, 0, 0]), (40, [-25, 0, 0])]),
    }),
    # ---------------------------------------------------------------- caido (loop de 40 ticks)
    "knocked_down": (2.0, {
        "Root": bone(rotation=[(0, [-84, 0, 0]), (40, [-84, 0, 0])], position=[(0, [0, 2, 0]), (40, [0, 2, 0])]),
        "Body": bone(rotation=[(0, [-20, 0, 0]), (20, [-22, 0, 0]), (40, [-20, 0, 0])]),
        "Head": bone(rotation=[(0, [-25, 0, 0]), (10, [-25, 12, 0]), (30, [-25, -12, 0]), (40, [-25, 0, 0])]),
        "RightArm": bone(rotation=[(0, [-165, 0, 25]), (20, [-160, 0, 28]), (40, [-165, 0, 25])]),
        "LeftArm": bone(rotation=[(0, [-165, 0, -25]), (20, [-160, 0, -28]), (40, [-165, 0, -25])]),
        "RightLeg": bone(rotation=[(0, [-20, 0, 8]), (40, [-20, 0, 8])]),
        "LeftLeg": bone(rotation=[(0, [-10, 0, -8]), (40, [-10, 0, -8])]),
    }),
}

data = json.loads(FILE.read_text(encoding="utf-8"))
for name, (length, bones) in clips.items():
    clip = {"animation_length": length, "bones": bones}
    if name == "knocked_down":
        clip["loop"] = True
    data["animations"][P + name] = clip
FILE.write_text(json.dumps(data, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
print("Clips gravados:", ", ".join(P + n for n in clips))
