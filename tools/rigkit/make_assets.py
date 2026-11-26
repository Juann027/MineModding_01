#!/usr/bin/env python3
"""Gera, a partir do contrato fruit_humanoid_v1.rig.json, o template do kit e as fixtures tecnicas (Etapa 4.12).

  python3 tools/rigkit/make_assets.py --dry-run   # mostra o que mudaria
  python3 tools/rigkit/make_assets.py             # grava (recusa arquivos editados fora daqui)
  python3 tools/rigkit/make_assets.py --check     # saida 1 se algo mudaria (CI/teste)
  python3 tools/rigkit/make_assets.py --force     # sobrescreve editados, com copia em tools/rigkit/.backup/

Saidas: o template editavel do kit (rigkit/fruit_humanoid_v1/), a geometria de referencia exportada, os dois
manequins de teste (geo, textura, .bbmodel fonte) e as animacoes de referencia da familia. Os manifestos de binding
(assets/respectcraft/rigs/entities/*.json) e o contrato sao escritos a mao e NUNCA sao gerados aqui.

Protecao: generated_manifest.json guarda o hash do que este script gravou por ultimo; arquivo com hash diferente foi
editado fora (Blockbench, a mao) e nada e gravado sem --force. Codigos: 0 ok | 1 --check com diferencas | 2 conflito.
"""
import argparse
import datetime
import hashlib
import shutil
import sys

import rigkit as K

CONTRACT = K.ASSETS / "rigs/fruit_humanoid_v1.rig.json"
KIT = K.REPO / "rigkit/fruit_humanoid_v1"
SOURCES = K.REPO / "model_sources/rig"
MANIFEST = K.KIT_DIR / "generated_manifest.json"
BACKUP = K.KIT_DIR / ".backup"

GREY, RIGHT, LEFT, HEAD, SHELL, CORE, FACE = (170, 170, 175), (205, 110, 100), (100, 130, 205), (220, 200, 120), \
    (110, 180, 110), (235, 150, 60), (60, 60, 70)


def palette(base):
    c = {}
    for side, col in (("right_", RIGHT), ("left_", LEFT)):
        for part in ("arm", "forearm", "hand", "thigh", "shin", "foot"):
            c[side + part] = col
    c.update({"head": HEAD, "face": FACE, "brow_right": FACE, "brow_left": FACE, "eye_right": FACE, "eye_left": FACE,
              "mouth": FACE, "head_shell": SHELL, "torso_shell": SHELL, "torso_core": CORE, "pelvis": base, "torso": base})
    return c


SPECS = {
    "template": {"name": "fruit_humanoid_v1_template", "proportion": [1, 1, 1], "include_optional": "all",
                 "colors": palette(GREY)},
    # Fixture A: alto e fino, sem casca/nucleo, antena extra (prefixo extra_).
    "a": {"name": "dev_rig_mannequin_a", "proportion": [0.85, 1.0, 0.85],
          "exclude": ["head_shell", "torso_shell", "torso_core"],
          "extra_bones": [{"name": "extra_antenna", "parent": "head", "pivot": [0, 32, 0],
                           "cubes": [([-0.5, 32, -0.5], [0.5, 36, 0.5])]}],
          "colors": {**palette((90, 120, 190)), "extra_antenna": (240, 240, 240)}},
    # Fixture B: baixo e largo, com casca (shell) e nucleo (core) e folha extra destacavel.
    "b": {"name": "dev_rig_mannequin_b", "proportion": [1.3, 0.8, 1.25], "include_optional": "all",
          "extra_bones": [{"name": "extra_leaf", "parent": "head", "pivot": [0, 25.6, 0],
                           "cubes": [([-1.5, 26.4, -0.5], [1.5, 30.4, 0.5]), ([-0.5, 26.4, -1.5], [0.5, 29.4, 1.5])]}],
          "colors": {**palette((60, 160, 150)), "extra_leaf": (80, 200, 80)}},
}


# ------------------------------------------------------------------------------------------------ animacoes

def track(**channels):
    """channels: rotation={t: [x,y,z]} em referencial do BLOCKBENCH -> dict do .animation.json (GeckoLib)."""
    out = {}
    for ch, keys in channels.items():
        out[ch] = {str(float(t)): {"vector": K.flip(ch, v)} for t, v in sorted(keys.items())}
    return out


Z = [0, 0, 0]


def shared_animations(prefix):
    a = {}
    a[prefix + "idle"] = {"loop": True, "animation_length": 2.0, "bones": {
        "torso": track(rotation={0: Z, 1: [2, 0, 0], 2: Z}),
        "head": track(rotation={0: Z, 1: [-2, 0, 0], 2: Z}),
        "right_arm": track(rotation={0: [0, 0, 4], 1: [0, 0, 6], 2: [0, 0, 4]}),
        "left_arm": track(rotation={0: [0, 0, -4], 1: [0, 0, -6], 2: [0, 0, -4]})}}
    # Reacao da vitima ao Takedown (3 s = 60 ticks; golpes em 14, 26 e 44 ticks = 0,7 / 1,3 / 2,2 s).
    a[prefix + "finisher.takedown_victim"] = {"animation_length": 3.0, "bones": {
        "pelvis": track(position={0: Z, 2.15: Z, 2.3: [0, -1.5, 1.5], 2.6: [0, -3, 2], 3: Z}),
        "torso": track(rotation={0: Z, 0.6: Z, 0.75: [-14, 0, 0], 1.1: [-4, 0, 0], 1.25: Z, 1.35: [-6, 0, 12],
                                 1.8: [-2, 0, 4], 2.15: Z, 2.3: [-28, 0, 0], 2.6: [-22, 0, -4], 3: [-10, 0, 0]}),
        "head": track(rotation={0: Z, 0.6: Z, 0.75: [-18, 0, 0], 1.25: Z, 1.35: [-6, 20, 0], 2.15: Z,
                                2.3: [-30, 0, 0], 3: [-10, 0, 0]}),
        "mouth": track(rotation={0: Z, 0.7: [15, 0, 0], 1.2: Z, 2.2: [25, 0, 0], 3: Z}),
        "right_arm": track(rotation={0: Z, 0.75: [-30, 0, 20], 1.35: [-10, 0, 10], 2.15: Z, 2.3: [-70, 0, 35], 3: Z}),
        "left_arm": track(rotation={0: Z, 0.75: [-30, 0, -20], 1.35: [-10, 0, -10], 2.15: Z, 2.3: [-70, 0, -35], 3: Z}),
        "right_forearm": track(rotation={0: Z, 0.75: [-25, 0, 0], 2.3: [-40, 0, 0], 3: Z}),
        "left_forearm": track(rotation={0: Z, 0.75: [-25, 0, 0], 2.3: [-40, 0, 0], 3: Z}),
        "right_thigh": track(rotation={0: Z, 2.15: Z, 2.3: [-20, 0, 0], 2.6: [-35, 0, 0], 3: Z}),
        "left_thigh": track(rotation={0: Z, 2.15: Z, 2.3: [-20, 0, 0], 2.6: [-35, 0, 0], 3: Z}),
        "right_shin": track(rotation={0: Z, 2.15: Z, 2.6: [40, 0, 0], 3: Z}),
        "left_shin": track(rotation={0: Z, 2.15: Z, 2.6: [40, 0, 0], 3: Z})}}
    # Cena de teste das partes: no golpe a casca some e o nucleo aparece (instrucoes rig: no timeline).
    a[prefix + "finisher.demo_peel"] = {"animation_length": 3.0, "timeline": {"1.1": "rig:hide:shell;rig:show:core;"},
                                        "bones": {
        "torso": track(rotation={0: Z, 0.5: [0, 25, 0], 1.1: [0, -35, 0], 1.6: [0, 10, 0], 2.2: [-20, 0, 0], 3: Z}),
        "head": track(rotation={0: Z, 1.1: [15, 0, 0], 2.2: [-25, 0, 0], 3: Z}),
        "right_arm": track(rotation={0: Z, 1.1: [-120, 0, 30], 2.2: [-40, 0, 20], 3: Z}),
        "left_arm": track(rotation={0: Z, 1.1: [-120, 0, -30], 2.2: [-40, 0, -20], 3: Z}),
        "pelvis": track(position={0: Z, 2.2: [0, -1, 1], 3: Z})}}
    return a


def mannequin_a_animations():
    """Override proprio do manequim A para o Takedown: gira no lugar com os bracos para o alto (bem diferente)."""
    return {"animation.dev_rig_mannequin_a.takedown_victim": {"animation_length": 3.0, "bones": {
        "torso": track(rotation={0: Z, 0.7: [0, 90, 0], 1.3: [0, 180, 0], 2.2: [0, 360, 0], 3: [0, 360, 0]}),
        "right_arm": track(rotation={0: Z, 0.7: [-160, 0, 0], 2.2: [-160, 0, 20], 3: Z}),
        "left_arm": track(rotation={0: Z, 1.3: [-160, 0, 0], 2.2: [-160, 0, -20], 3: Z}),
        "head": track(rotation={0: Z, 2.2: [-20, 0, 0], 3: Z})}}}


def anim_file(animations):
    return {"format_version": "1.8.0", "animations": animations, "geckolib_format_version": 2}


# ------------------------------------------------------------------------------------------------ saidas

def outputs():
    contract = K.load_json(CONTRACT)
    prefix = contract["animation_prefix"]
    shared = shared_animations(prefix)
    files = {}
    tpl = K.build_model(contract, SPECS["template"])
    tpl_png = K.texture_png(tpl)
    files[KIT / "fruit_humanoid_v1_template.bbmodel"] = K.dumps(
        K.bbmodel(tpl, tpl_png, shared, "fruit_humanoid_v1_template.png"))
    files[KIT / "reference/fruit_humanoid_v1_template.geo.json"] = K.dumps(K.geo_json(tpl, "geometry.fruit_humanoid_v1_template"))
    files[KIT / "reference/fruit_humanoid_v1_template.png"] = tpl_png
    files[KIT / "reference/fruit_humanoid_v1.animation.json"] = K.dumps(anim_file(shared))
    files[KIT / "fruit_humanoid_v1.rig.json"] = CONTRACT.read_text(encoding="utf-8")
    files[K.ASSETS / "animations/rig/fruit_humanoid_v1.animation.json"] = K.dumps(anim_file(shared))
    for key in ("a", "b"):
        spec = SPECS[key]
        model = K.build_model(contract, spec)
        tex = K.texture_png(model)
        own = mannequin_a_animations() if key == "a" else {}
        files[K.ASSETS / f"geo/entity/{spec['name']}.geo.json"] = K.dumps(K.geo_json(model, "geometry." + spec["name"]))
        files[K.ASSETS / f"textures/entity/{spec['name']}.png"] = tex
        files[SOURCES / f"{spec['name']}.bbmodel"] = K.dumps(K.bbmodel(model, tex, {**shared, **own}, spec["name"] + ".png"))
        if own:
            files[K.ASSETS / f"animations/entity/{spec['name']}.animation.json"] = K.dumps(anim_file(own))
    return files


def sha(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode("utf-8")).hexdigest()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args(argv)
    manifest = K.load_json(MANIFEST) if MANIFEST.exists() else {}
    files = outputs()
    changed, conflicts = [], []
    for path, data in files.items():
        rel = path.relative_to(K.REPO).as_posix()
        new = sha(data)
        if path.exists():
            current = sha(path.read_bytes())
            if current == new:
                continue
            if manifest.get(rel) not in (None, current):
                conflicts.append(rel)
                continue
            if manifest.get(rel) is None and not args.force:
                conflicts.append(rel)
                continue
        changed.append((path, rel, data))
    for _, rel, _ in changed:
        print("muda:", rel)
    for rel in conflicts:
        print("EDITADO FORA DO GERADOR (nao gravo sem --force):", rel)
    if args.check:
        return 1 if changed or conflicts else 0
    if args.dry_run:
        return 0
    if conflicts and not args.force:
        print("conflito: nada gravado. Use --force para sobrescrever (copia em tools/rigkit/.backup/).")
        return 2
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    for rel in conflicts:
        src = K.REPO / rel
        dest = BACKUP / stamp / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        changed.append((src, rel, files[src]))
    for path, rel, data in changed:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_bytes(data if isinstance(data, bytes) else data.encode("utf-8"))
        tmp.replace(path)
        manifest[rel] = sha(data)
    for path, data in files.items():
        manifest.setdefault(path.relative_to(K.REPO).as_posix(), sha(data))
    MANIFEST.write_text(K.dumps(dict(sorted(manifest.items())), indent=2), encoding="utf-8")
    print(f"gravado(s): {len(changed)} arquivo(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
