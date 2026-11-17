#!/usr/bin/env python3
"""Confere se um .bbmodel e o .animation.json do jogo dizem a mesma coisa (so biblioteca padrao).

  python3 tools/player_anim/bbsync.py                         # confere os arquivos do repositorio
  python3 tools/player_anim/bbsync.py X.bbmodel Y.animation.json   # confere um par qualquer

Reproduz o "Export Animations" do plugin GeckoLib para o que o gerador usa (keyframes lineares com `easing`,
um ponto por keyframe): le os keyframes do .bbmodel, aplica a conversao do Blockbench (inverte X e Y da rotacao
e X da posicao, ver Keyframe.compileBedrockKeyframe) e compara com o .animation.json. Keyframes com recursos que
este script nao reproduz (bezier, step, catmullrom de interpolacao, varios pontos) sao listados como "nao
verificavel": nesse caso confira exportando pelo proprio Blockbench.

Nao converte nada: so aponta divergencias (ex.: .bbmodel editado e nao exportado, ou o contrario).
Saida 0 = em sincronia; 1 = divergencias.
"""
import json
import math
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
ANIM_FILE = REPO / "src/main/resources/assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json"
COMBAT_BBMODEL = REPO / "model_sources/player/tupinicraft_player_combat.bbmodel"
SCENE_BBMODEL = REPO / "model_sources/player/tupinicraft_combo_scene.bbmodel"
SCENE_TARGET_PREFIX = "Alvo_"
SCENE_CLIPS = {"": "animation.tupinicraft.player.finisher.combo_attacker",
               SCENE_TARGET_PREFIX: "animation.tupinicraft.player.finisher.combo_victim"}


class Unsupported(Exception):
    pass


def flip(channel, vector):
    """Mesma conversao nos dois sentidos (.bbmodel <-> .animation.json)."""
    x, y, z = vector
    if channel == "rotation":
        return [_neg(x), _neg(y), z]
    if channel == "position":
        return [_neg(x), y, z]
    return [x, y, z]


def _neg(v):
    return -v if isinstance(v, (int, float)) else ("-(" + v + ")")


def _value(v):
    if isinstance(v, (int, float)):
        return float(v)
    try:
        return float(str(v).strip() or 0)
    except ValueError:
        return str(v)  # Molang: exportado como texto


def animations_from_bbmodel(model, bone_prefix=None):
    """{nome do clip: clip} como o plugin GeckoLib exportaria. bone_prefix filtra/renomeia bones (cena dupla):
    None = todos; "" = so os sem o prefixo do alvo; "Alvo_" = so os do alvo, sem o prefixo."""
    out, skipped = {}, []
    for anim in model.get("animations", []):
        bones = {}
        for animator in anim.get("animators", {}).values():
            name = animator.get("name", "")
            if bone_prefix == "" and name.startswith(SCENE_TARGET_PREFIX):
                continue
            if bone_prefix:
                if not name.startswith(bone_prefix):
                    continue
                name = name[len(bone_prefix):]
            for kf in animator.get("keyframes", []):
                channel = kf.get("channel")
                if channel not in ("rotation", "position", "scale"):
                    continue
                try:
                    entry = _keyframe(kf)
                except Unsupported as why:
                    skipped.append(f"{anim['name']}/{name}/{channel}@{kf.get('time')}: {why}")
                    continue
                bones.setdefault(name, {}).setdefault(channel, {})[_time_key(kf["time"])] = entry
        clip = {"animation_length": anim.get("length", 0)}
        loop = anim.get("loop", "once")
        if loop == "loop":
            clip["loop"] = True
        elif loop == "hold":
            clip["loop"] = "hold_on_last_frame"
        clip["bones"] = bones
        out[anim["name"]] = clip
    return out, skipped


def _keyframe(kf):
    points = kf.get("data_points", [])
    if len(points) != 1:
        raise Unsupported(f"{len(points)} pontos no keyframe")
    interpolation = kf.get("interpolation", "linear")
    if interpolation != "linear":
        raise Unsupported(f"interpolacao '{interpolation}'")
    p = points[0]
    entry = {"vector": flip(kf["channel"], [_value(p.get(a, 0)) for a in "xyz"])}
    if kf.get("easing") and kf["easing"] != "linear":
        entry["easing"] = kf["easing"]
        if kf.get("easingArgs"):
            entry["easingArgs"] = kf["easingArgs"]
    return entry


def _time_key(t):
    return f"{float(t):.4f}".rstrip("0").rstrip(".") or "0"


def _channel(ch):
    if isinstance(ch, list):
        return {"0": {"vector": ch}}
    return {_time_key(t): (v if isinstance(v, dict) else {"vector": v}) for t, v in ch.items()}


def diff_clips(expected, actual, only=None):
    """Lista de divergencias entre dois dicionarios {clip: clip}. only = nomes a conferir (padrao: todos)."""
    problems = []
    names = set(expected) | set(actual) if only is None else set(only)
    for name in sorted(names):
        if name not in expected or name not in actual:
            problems.append(f"{name}: existe so em {'um' if name in expected else 'outro'} lado"
                            if only is None else f"{name}: ausente em {'esperado' if name not in expected else 'obtido'}")
            continue
        a, b = expected[name], actual[name]
        if abs(float(a.get("animation_length", 0)) - float(b.get("animation_length", 0))) > 1e-4:
            problems.append(f"{name}: duracao {a.get('animation_length')} != {b.get('animation_length')}")
        if bool(a.get("loop", False)) != bool(b.get("loop", False)) or (
                a.get("loop") == "hold_on_last_frame") != (b.get("loop") == "hold_on_last_frame"):
            problems.append(f"{name}: loop {a.get('loop', False)} != {b.get('loop', False)}")
        for bone in sorted(set(a.get("bones", {})) | set(b.get("bones", {}))):
            ba, bb = a.get("bones", {}).get(bone, {}), b.get("bones", {}).get(bone, {})
            for ch in ("rotation", "position", "scale"):
                ka, kb = _channel(ba.get(ch, {})), _channel(bb.get(ch, {}))
                if set(ka) != set(kb):
                    problems.append(f"{name}/{bone}/{ch}: tempos {sorted(ka, key=float)} != {sorted(kb, key=float)}")
                    continue
                for t in sorted(ka, key=float):
                    va, vb = ka[t]["vector"], kb[t]["vector"]
                    if not _same_vector(va, vb):
                        problems.append(f"{name}/{bone}/{ch}@{t}: {va} != {vb}")
                    if ka[t].get("easing", "linear") != kb[t].get("easing", "linear"):
                        problems.append(f"{name}/{bone}/{ch}@{t}: easing {ka[t].get('easing')} != {kb[t].get('easing')}")
    return problems


def _same_vector(va, vb):
    for x, y in zip(va, vb):
        if isinstance(x, str) or isinstance(y, str):
            if str(x) != str(y):
                return False
        elif not math.isclose(float(x), float(y), abs_tol=1e-3):
            return False
    return len(va) == len(vb)


def check_pair(bbmodel_path, anim_path, bone_prefix=None, clip_map=None):
    """Compara os clips de um .bbmodel com os do .animation.json. clip_map renomeia clips do .bbmodel."""
    model = json.loads(pathlib.Path(bbmodel_path).read_text(encoding="utf-8"))
    game = json.loads(pathlib.Path(anim_path).read_text(encoding="utf-8"))["animations"]
    exported, skipped = animations_from_bbmodel(model, bone_prefix)
    if clip_map:
        exported = {clip_map.get(k, k): v for k, v in exported.items()}
    problems = diff_clips(game, exported, only=list(exported))
    return problems, skipped


def check_repo():
    total = 0
    jobs = [(COMBAT_BBMODEL, None, None)]
    jobs += [(SCENE_BBMODEL, prefix, {"combo_scene": clip}) for prefix, clip in SCENE_CLIPS.items()]
    for path, prefix, clip_map in jobs:
        label = path.name + ("" if prefix is None else f" [{'alvo' if prefix else 'executor'}]")
        if not path.exists():
            print(f"{label}: arquivo ausente")
            total += 1
            continue
        problems, skipped = check_pair(path, ANIM_FILE, prefix, clip_map)
        status = "em sincronia" if not problems else f"{len(problems)} divergencia(s)"
        print(f"{label} x {ANIM_FILE.name}: {status}" + (f"; {len(skipped)} keyframe(s) nao verificavel(is)" if skipped else ""))
        for p in problems[:15]:
            print("   ", p)
        for s in skipped[:5]:
            print("    nao verificavel:", s)
        total += len(problems)
    if total:
        print("\nO .bbmodel e o .animation.json divergem. Se voce editou no Blockbench, exporte de la (Export Animations);"
              "\nse editou clips.py/poses.py, rode tools/player_anim/export.py. Veja HANDOFF_PLAYER_ANIMATIONS_STAGE4_1.md.")
    return total


def main(argv):
    if len(argv) == 3:
        problems, skipped = check_pair(argv[1], argv[2])
        for p in problems:
            print(p)
        for s in skipped:
            print("nao verificavel:", s)
        print(f"{len(problems)} divergencia(s)")
        return 1 if problems else 0
    return 1 if check_repo() else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
