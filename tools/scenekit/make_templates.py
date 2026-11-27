#!/usr/bin/env python3
"""Gera os templates do Finisher Scene Authoring Kit, a coreografia tecnica de demonstracao e os exemplos de
exportacao; instala os clips da cena de TESTE do jogo (dev/authoring_demo, manequim B).

  python3 tools/scenekit/make_templates.py --dry-run   # o que mudaria
  python3 tools/scenekit/make_templates.py             # grava (recusa arquivos editados fora daqui)
  python3 tools/scenekit/make_templates.py --check     # saida 1 se algo mudaria (CI/teste)
  python3 tools/scenekit/make_templates.py --force     # sobrescreve editados (copia em tools/scenekit/.backup/)

Protecao (a mesma do tools/rigkit/make_assets.py e do tools/player_anim/export.py): generated_manifest.json guarda
o hash do que este script gravou por ultimo; um template salvo pelo Blockbench (ou a mao) tem outro hash e nada e
gravado por cima sem --force. Os modelos de origem (jogador, morango, manequins) so sao lidos.
Codigos: 0 ok | 1 --check com diferencas | 2 conflito | 4 a demonstracao nao passou na validacao.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import shutil
import sys

import scenekit as S

KIT_DIR = pathlib.Path(__file__).resolve().parent
REPO = KIT_DIR.parents[1]
SCENES = REPO / "scenekit/scenes"
EXAMPLES = REPO / "scenekit/examples"
RESOURCES = REPO / "src/main/resources"
MANIFEST = KIT_DIR / "generated_manifest.json"
BACKUP = KIT_DIR / ".backup"
# cena -> (pasta, animacao de demonstracao, exemplo? , instalar no jogo?)
JOBS = [
    ("player_vs_strawberry", "scene.player_vs_strawberry_demo", True, False),
    ("player_vs_fruit_mannequin", "scene.player_vs_fruit_mannequin_demo", True, False),
    ("dev_player_vs_mannequin_b", "scene.authoring_demo", False, True),
]

# ------------------------------------------------------------------ coreografia tecnica (valores no referencial do
# GeckoLib, convertidos para o Blockbench por S.flip; tempos em segundos, cena de 3 s = 60 ticks). So bones do
# contrato (familia) e do rig do jogador: serve para qualquer vitima da familia. NAO e uma finalizacao definitiva.
ATTACKER_DEMO = {
    "Root": {"position": [(0, (0, 0, 0)), (0.7, (0, 0, -2)), (1.6, (0, 0, -1)), (3.0, (0, 0, 0))]},
    "Body": {"rotation": [(0, (0, 0, 0)), (0.45, (0, 18, 0)), (0.7, (8, -22, 0)), (1.6, (6, 10, 0)), (2.4, (6, 10, 0)),
                          (3.0, (0, 0, 0))]},
    "Head": {"rotation": [(0, (0, 0, 0)), (0.7, (6, 16, 0)), (1.6, (8, -6, 0)), (3.0, (0, 0, 0))]},
    "RightArm": {"rotation": [(0, (0, 0, 0)), (0.45, (-50, 10, 22)), (0.7, (-92, -4, 0)), (1.0, (-70, 0, 6)),
                              (1.6, (-20, 0, 8)), (3.0, (0, 0, 0))]},
    "LeftArm": {"rotation": [(0, (0, 0, 0)), (0.7, (-30, 0, -10)), (1.6, (-84, 18, 0)), (2.4, (-84, 18, 0)),
                             (3.0, (0, 0, 0))]},
    "RightLeg": {"rotation": [(0, (0, 0, 0)), (0.7, (18, 0, 4)), (1.6, (6, 0, 2)), (3.0, (0, 0, 0))]},
    "LeftLeg": {"rotation": [(0, (0, 0, 0)), (0.7, (-14, 0, -3)), (1.6, (-6, 0, -2)), (3.0, (0, 0, 0))]},
}
VICTIM_DEMO = {
    "root": {"position": [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.75, (0, 0, 1.5)), (1.6, (0, 0, 0)), (3.0, (0, 0, 0))]},
    "torso": {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.75, (16, 0, 0)), (1.2, (4, 0, 0)), (1.6, (0, 0, 0)),
                           (2.4, (8, 0, 6)), (3.0, (0, 0, 0))]},
    "head": {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.75, (20, 0, 0)), (1.3, (0, 0, 0)), (2.4, (6, -14, 0)),
                          (3.0, (0, 0, 0))]},
    "right_arm": {"rotation": [(0, (0, 0, 0)), (0.75, (30, 0, 20)), (1.6, (10, 0, 8)), (3.0, (0, 0, 0))]},
    "left_arm": {"rotation": [(0, (0, 0, 0)), (0.75, (30, 0, -20)), (1.6, (10, 0, -8)), (3.0, (0, 0, 0))]},
}
EXPRESSION_SHOCK = "rig:hide:face_normal;rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_open"


def demo_timeline(manifest):
    parts = {p["name"] for p in manifest.get("victim_parts", [])}
    tl = {
        "0": ["@scene camera 150,4.6,1.7,1.1,1.0,0", "@attacker sound respectcraft:finisher.whoosh"],
        "0.7": ["@attacker hit 2", "@attacker shake 0.5,8", "@scene camera 75,3.0,1.2,1.0,0.95,-2",
                "@attacker note contact RightHandAttachment -> victim socket_impact_point"],
        "0.75": ["@victim note reaction recoil (root offset returns to zero at 1.6 s)"],
        "1.6": ["@attacker note grab LeftHandAttachment -> victim socket_grab_point",
                "@scene camera 40,2.6,0.9,0.6,0.85,-5"],
        "3": ["@scene camera 20,4.2,2.3,0.4,1.0,0", "@scene execute"],
    }
    if {"face_normal", "eyes_wide", "brows_panic", "mouth_open"} <= parts:
        tl["0.75"].insert(0, "@victim " + EXPRESSION_SHOCK)
    if {"shell", "core"} <= parts:
        crack = "@victim rig:hide:shell;rig:show:core" + (";rig:show:damage_torso" if "damage_torso" in parts else "")
        tl["1.1"] = [crack, "@victim note pulp shell hidden, core exposed (restored by the session at the end)"]
    return tl


def _animator(manifest, scene_anim, bone, channels):
    by_name = {g: u for g, u in manifest["_uuids"].items()}
    out = {"name": bone, "type": "bone", "rotation_global": False, "quaternion_interpolation": False, "keyframes": []}
    for ch, keys in channels.items():
        for t, vec in keys:
            x, y, z = (S.num(v) for v in S.flip(ch, list(vec)))
            out["keyframes"].append({"channel": ch, "data_points": [{"x": x, "y": y, "z": z}],
                                     "uuid": S.uuid(manifest["name"], scene_anim, bone, ch, t), "time": t, "color": -1,
                                     "interpolation": "linear"})
    return {by_name[bone]: out}


def add_demo(model, manifest, scene_anim):
    manifest["_uuids"] = {g["name"]: g["uuid"] for g in model["groups"]}
    pa, pv = manifest["participants"]["attacker"]["prefix"], manifest["participants"]["victim"]["prefix"]
    animators = []
    for bone, ch in ATTACKER_DEMO.items():
        animators.append(_animator(manifest, scene_anim, pa + bone, ch))
    for bone, ch in VICTIM_DEMO.items():
        animators.append(_animator(manifest, scene_anim, pv + bone, ch))
    effects = {"name": "Effects", "type": "effect", "keyframes": []}
    for t, lines in demo_timeline(manifest).items():
        effects["keyframes"].append({"channel": "timeline", "data_points": [{"script": "\n".join(lines)}],
                                     "uuid": S.uuid(manifest["name"], scene_anim, "timeline", t), "time": float(t),
                                     "color": -1, "interpolation": "linear"})
    animators.append({"effects": effects})
    del manifest["_uuids"]
    for i, a in enumerate(model["animations"]):
        if a["name"] == scene_anim:
            model["animations"][i] = S.scene_animation(manifest, animators, scene_anim)
            return
    raise S.SceneError(f"demo animation {scene_anim} not in the spec animations")


# ------------------------------------------------------------------ geracao

def render():
    """{caminho no repo: bytes} de tudo que este script grava."""
    out, installs, problems = {}, {}, []
    for folder, demo, example, install in JOBS:
        spec_path = SCENES / folder / "scene.spec.json"
        spec = S.load_json(spec_path)
        model, manifest = S.compose(spec, spec_path.parent, "atlas")
        add_demo(model, manifest, demo)
        rel = f"scenekit/scenes/{folder}/{spec['name']}"
        out[rel + ".bbmodel"] = S.dumps(model).encode()
        out[rel + ".scene.json"] = S.dumps(manifest, 2).encode()
        export, comp = S.compile_bbmodel(model)
        problems += comp
        export["animations"] = {demo: export["animations"][demo]}
        geo = {}
        for role in S.ROLES:
            g = spec[role].get("geo")
            if g:
                ns, _, p = g.partition(":")
                geo[role] = S.geo_bones(S.load_json(RESOURCES / "assets" / ns / p))
        bones = {r: ({b["bone"] for b in manifest["bones"].values() if b["role"] == r} & geo[r]) if r in geo else
                 {b["bone"] for b in manifest["bones"].values() if b["role"] == r} for r in S.ROLES}
        clips, events, rep, scene_anim = S.split(manifest, export, demo, bones)
        problems += [f"{folder}: {m}" for lvl, m in rep if lvl == "ERROR"]
        meta = S.metadata(manifest, events, rep, scene_anim)
        stem = demo.replace("scene.", "", 1)
        if example:
            ex = f"scenekit/examples/{folder}/"
            out[ex + f"{stem}.compiled_export.animation.json"] = S.dumps(export, 2).encode()
            for role in S.ROLES:
                out[ex + f"{stem}.{role}.animation.json"] = S.dumps(
                    S.participant_file(role, manifest, clips[role], scene_anim), 2).encode()
            out[ex + f"{stem}.export_meta.json"] = S.dumps(meta, 2).encode()
            out[ex + f"{stem}.events.txt"] = S.events_text(events, manifest).encode()
            out[ex + f"{stem}.validation.txt"] = S.report_text(rep).encode()
        if install:
            for role in S.ROLES:
                p = meta["participants"][role]
                installs.setdefault(p["file"], {})[p["animation"]] = clips[role]
    for rl, clips in installs.items():
        ns, _, p = rl.partition(":")
        data = {"format_version": "1.8.0", "animations": dict(sorted(clips.items()))}
        out[f"src/main/resources/assets/{ns}/{p}"] = S.dumps(data, 2).encode()
    return out, problems


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args(argv)
    files, problems = render()
    if problems:
        for p in problems:
            print("ERROR", p)
        return 4
    recorded = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}
    changes, conflicts = [], []
    for rel, data in sorted(files.items()):
        path = REPO / rel
        if not path.exists():
            changes.append((rel, "new"))
            continue
        current = path.read_bytes()
        if current == data:
            continue
        if recorded.get(rel) != _sha(current):
            conflicts.append(rel)
            changes.append((rel, "EDITED"))
        else:
            changes.append((rel, "update"))
    for rel, state in changes:
        print(f"{state:7} {rel}")
    if not changes:
        print("scene kit: everything up to date")
    if args.check or args.dry_run:
        return 1 if (args.check and changes) else 0
    if conflicts and not args.force:
        print(f"\n{len(conflicts)} file(s) edited outside this script (Blockbench?): nothing written. Keep the edit"
              " (do not regenerate) or run with --force (backup in tools/scenekit/.backup/).")
        return 2
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    for rel, state in changes:
        path = REPO / rel
        if state == "EDITED":
            (BACKUP / stamp).mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, BACKUP / stamp / path.name)
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_name(path.name + ".tmp")
        tmp.write_bytes(files[rel])
        tmp.replace(path)
    MANIFEST.write_text(S.dumps({rel: _sha(data) for rel, data in sorted(files.items())}, 2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
