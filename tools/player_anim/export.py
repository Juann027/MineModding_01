#!/usr/bin/env python3
"""Exporta os clips da Etapa 4 e valida contato com o chao e entre os dois participantes.

  python3 tools/player_anim/export.py            # grava o .animation.json e os .bbmodel, imprime o relatorio
  python3 tools/player_anim/export.py --sheets D # tambem gera folhas de quadros (PNG) em D

Saidas:
  src/main/resources/assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json
  model_sources/player/tupinicraft_player_combat.bbmodel        (rig + todos os clips, editavel)
  model_sources/player/tupinicraft_combo_scene.bbmodel          (dois rigs, coreografia lado a lado)
"""
import json
import pathlib
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from rig import Rig, REPO, sample_clip, RENDER_SCALE  # noqa: E402
import clips as C  # noqa: E402
import bbmodel  # noqa: E402

ANIM_FILE = REPO / "src/main/resources/assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json"
PREFIX = "animation.tupinicraft.player."
STAGE_DISTANCE = 1.15  # blocos entre executor e alvo (palco da Finisher Engine)


def export_json(clips):
    data = {"format_version": "1.8.0", "animations": {PREFIX + n: c.to_json() for n, c in clips.items()}}
    ANIM_FILE.write_text(json.dumps(data, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    return data


def _boxes_world(rig, pose, place):
    out = []
    for name, corners in rig.boxes(pose):
        w = (place @ np.c_[corners, np.ones(8)].T).T[:, :3]
        out.append((name, w))
    return out


def _inside(points, box, margin=0.25):
    c0, e1, e2, e3 = box[0], box[4] - box[0], box[2] - box[0], box[1] - box[0]
    m = np.column_stack([e1, e2, e3])
    try:
        coef = np.linalg.solve(m, (points - c0).T).T
    except np.linalg.LinAlgError:
        return 0.0
    # profundidade aproximada (px) do ponto mais enterrado
    depth = 0.0
    for a in coef:
        if np.all(a > 0) and np.all(a < 1):
            d = min(min(a[i], 1 - a[i]) * np.linalg.norm([e1, e2, e3][i]) for i in range(3))
            depth = max(depth, d)
    return depth if depth > margin else 0.0


def _box_distance(p, box):
    """Distancia (px) do ponto ate o cubo orientado; negativa = dentro."""
    c0, e = box[0], [box[4] - box[0], box[2] - box[0], box[1] - box[0]]
    coef = np.linalg.solve(np.column_stack(e), p - c0)
    near = c0 + sum(np.clip(coef[i], 0, 1) * e[i] for i in range(3))
    d = float(np.linalg.norm(p - near))
    return -min(min(coef[i], 1 - coef[i]) * np.linalg.norm(e[i]) for i in range(3)) if d == 0 else d


def _probe(w):
    face = [w[[0, 1, 3, 2]].mean(0), w[[4, 6, 7, 5]].mean(0), w[[0, 4, 5, 1]].mean(0), w[[2, 3, 7, 6]].mean(0),
            w[[0, 2, 6, 4]].mean(0), w[[1, 5, 7, 3]].mean(0)]
    return np.vstack([w, face, w.mean(0, keepdims=True)])


def placements():
    from preview import placement
    d = STAGE_DISTANCE * 16
    return placement(), placement(0, -d, 180)


def report(clips, rig):
    print("clip".ljust(34), "ticks  chao_min(px)")
    for name, clip in clips.items():
        js = clip.to_json()
        low = min(min(c[:, 1].min() for _, c in rig.boxes(sample_clip(js, t / 20))) for t in range(clip.length + 1))
        flag = "  <-- atravessa o chao" if low < -0.6 else ""
        print(name.ljust(34), str(clip.length).rjust(5), f"{low:7.2f}{flag}")
    # coreografia: contato dos golpes e interpenetracao
    pa, pv = placements()
    att = clips["finisher.combo_attacker"].to_json()
    vic = clips["finisher.combo_victim"].to_json()
    worst = (0.0, -1, "")
    for t in range(C.COMBO_LEN + 1):
        A = _boxes_world(rig, sample_clip(att, t / 20), pa)
        V = _boxes_world(rig, sample_clip(vic, t / 20), pv)
        for an, ab in A:
            for vn, vb in V:
                d = max(_inside(_probe(ab), vb), _inside(_probe(vb), ab))
                ok_contact = t in range(C.IMPACT_PUNCH - 1, C.IMPACT_PUNCH + 3) or t in range(C.IMPACT_KICK - 1, C.IMPACT_KICK + 4)
                if d > worst[0] and not ok_contact:
                    worst = (d, t, f"{an} x {vn}")
    print(f"maior interpenetracao fora dos impactos: {worst[0]:.2f} px no tick {worst[1]} ({worst[2]})")
    for label, tick, bone, local in (("soco", C.IMPACT_PUNCH, "RightArm", (6, 12, 0)),
                                     ("chute", C.IMPACT_KICK, "RightLeg", (1.9, 0, 0))):
        tip = (pa @ np.r_[rig.point(to_pose(sample_clip(att, tick / 20)), bone, local), 1])[:3]
        V = _boxes_world(rig, sample_clip(vic, tick / 20), pv)
        body = [w for n, w in V if n == "Body"][0]
        dist = _box_distance(tip, body)
        print(f"{label}: ponta a {dist:.1f} px da superficie do tronco do alvo no tick {tick} (altura {tip[1]:.1f} px)")


def to_pose(p):
    return p


def sheets(clips, rig, out):
    from preview import render, Camera, sheet, PALETTES, placement
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for name, clip in clips.items():
        if name.startswith("finisher."):
            continue
        js = clip.to_json()
        step = max(1, clip.length // 12)
        ims = [render([(rig, sample_clip(js, t / 20), placement(), PALETTES["executor"])],
                      Camera(yaw=70, pitch=10, dist=66, target=(0, 13, 0), size=(170, 170)), label=f"{name} t{t}")
               for t in range(0, clip.length + 1, step)]
        sheet(ims, 7).save(out / f"clip_{name}.png")
    pa, pv = placements()
    att = clips["finisher.combo_attacker"].to_json()
    vic = clips["finisher.combo_victim"].to_json()
    ticks = list(range(0, C.COMBO_LEN + 1, 5))
    for view, yaw in (("lado", 90), ("diag", 40)):
        ims = [render([(rig, sample_clip(att, t / 20), pa, PALETTES["executor"]),
                       (rig, sample_clip(vic, t / 20), pv, PALETTES["alvo"])],
                      Camera(yaw=yaw, pitch=12, dist=95, target=(0, 12, -8), size=(200, 160)), label=f"t{t}")
               for t in ticks]
        sheet(ims, 6).save(out / f"combo_{view}.png")


def main():
    clips = C.all_clips()
    export_json(clips)
    rig = Rig()
    bbmodel.write_all(clips)
    report(clips, rig)
    if "--sheets" in sys.argv:
        sheets(clips, rig, sys.argv[sys.argv.index("--sheets") + 1])
    print("gravado:", ANIM_FILE.relative_to(REPO))


if __name__ == "__main__":
    main()
