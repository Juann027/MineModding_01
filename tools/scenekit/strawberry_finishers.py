#!/usr/bin/env python3
"""Integracao das cinco finalizacoes do morango (Etapa 4.13.3) pelo Finisher Scene Authoring Kit.

Fonte artistica (nunca editada aqui): model_sources/rig/strawberry/finishers/
  respectcraft_strawberry_brutal_finishers_v2.animation.json  (Export Animations do plugin GeckoLib, 5 cenas)

Para cada cena: copia do manifesto do template Player x Strawberry com o duration_ticks e a animacao da cena
(o template original fica intacto), separacao pelo split_scene (mesmas regras/ERRORs) e, com --install, a mescla
protegida nos arquivos do jogo (clips do jogador e do morango).

Unica correcao aplicada (documentada no handoff 4.13.3): nas tres cenas V1 (fruit_punch, ground_smasher, shut_up) a
linha da polpa mostrava damage_head/damage_torso junto com rig:hide:shell (placas de rachadura flutuando sem a casca).
Aplica-se a mesma correcao que o artista fez na V2: as placas somem junto com a casca. Nada mais muda nos clips.

  python3 tools/scenekit/strawberry_finishers.py --out build/scenekit/strawberry_finishers [--install src/main/resources]
  python3 tools/scenekit/strawberry_finishers.py --check     # instalados == separacao atual (CI)
"""
import argparse
import copy
import json
import pathlib
import sys

KIT = pathlib.Path(__file__).resolve().parent
REPO = KIT.parents[1]
sys.path.insert(0, str(KIT))
import scenekit as S  # noqa: E402
import split_scene  # noqa: E402

SOURCE = REPO / "model_sources/rig/strawberry/finishers/respectcraft_strawberry_brutal_finishers_v2.animation.json"
TEMPLATE = REPO / "scenekit/scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template.scene.json"
GEO_ATTACKER = REPO / "src/main/resources/assets/respectcraft/geo/player/respectcraft_animated_player.geo.json"
GEO_VICTIM = REPO / "src/main/resources/assets/respectcraft/geo/entity/strawberry.geo.json"
RESOURCES = REPO / "src/main/resources"

# cena -> duracao em ticks (tabela do pedido; conferida contra animation_length da fonte)
SCENES = {"strawberry_crusher": 120, "fruit_punch": 140, "ground_smasher": 120, "shut_up": 80, "strawberry_blender": 160}
PULP_FIX = {"rig:show:damage_head": "rig:hide:damage_head", "rig:show:damage_torso": "rig:hide:damage_torso"}


def pulp_fix(export):
    """-> (export corrigido, lista do que mudou). So linhas da timeline que tem rig:hide:shell."""
    out, changed = copy.deepcopy(export), []
    for name, anim in out["animations"].items():
        for t, line in list(anim.get("timeline", {}).items()):
            lines = [line] if isinstance(line, str) else list(line)
            if not any("rig:hide:shell" in x for x in lines):
                continue
            fixed = []
            for x in lines:  # cada linha: "@dono cmd;cmd;" — troca so os tokens, mantem forma e ordem
                for old, new in PULP_FIX.items():
                    x = x.replace(old + ";", new + ";")
                fixed.append(x)
            if fixed != lines:
                changed.append(f"{name} @{t}s: damage_head/damage_torso shown with the pulp -> hidden with the shell")
                anim["timeline"][t] = fixed if not isinstance(line, str) else fixed[0]
    return out, changed


def manifest_for(scene, ticks):
    m = S.load_json(TEMPLATE)
    m["duration_ticks"] = ticks
    m["animation"] = "scene." + scene
    m["name"] = "respectcraft_strawberry_" + scene
    return m


def run_all(out, install=False, force=False, quiet=False):
    export, changed = pulp_fix(S.load_json(SOURCE))
    for c in changed:
        print("FIX  ", c)
    errors, files, metas = 0, {}, {}
    for scene, ticks in SCENES.items():
        length = export["animations"]["scene." + scene].get("animation_length")
        if round(float(length) * 20) != ticks:
            print(f"ERROR scene.{scene}: animation_length {length} s != {ticks} ticks")
            errors += 1
            continue
        manifest = manifest_for(scene, ticks)
        d = pathlib.Path(out) / scene
        d.mkdir(parents=True, exist_ok=True)
        (d / f"{scene}.scene.json").write_text(S.dumps(manifest, 2), encoding="utf-8")
        code, clips, meta = split_scene.run(manifest, export, d, None,
                                            {"attacker": str(GEO_ATTACKER), "victim": str(GEO_VICTIM)}, quiet=True)
        print(f"{'OK   ' if code == 0 else 'ERROR'} scene.{scene}: {ticks} ticks, {meta['errors']} error(s), "
              f"{meta['warnings']} warning(s) -> {d}")
        errors += code
        metas[scene] = meta
        for role in S.ROLES:
            p = meta["participants"][role]
            files.setdefault(p["file"], {})[p["animation"]] = clips[role]
    if errors:
        return 1, files, metas
    if install:
        return split_scene.install(files, str(RESOURCES), force), files, metas
    return 0, files, metas


def check(out):
    code, files, _ = run_all(out, quiet=True)
    if code:
        return code
    bad = 0
    for rl, clips in files.items():
        ns, _, rel = rl.partition(":")
        target = RESOURCES / "assets" / ns / rel
        installed = json.loads(target.read_text(encoding="utf-8"))["animations"] if target.exists() else {}
        for name, clip in clips.items():
            if installed.get(name) != json.loads(S.dumps(clip, 2)):
                print("DIFF ", rl, name)
                bad += 1
    print("installed clips match the split" if not bad else f"{bad} installed clip(s) differ")
    return 1 if bad else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=str(REPO / "build/scenekit/strawberry_finishers"))
    ap.add_argument("--install", action="store_true", help="grava os clips em src/main/resources (protegido)")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args(argv)
    if a.check:
        return check(a.out)
    return run_all(a.out, a.install, a.force)[0]


if __name__ == "__main__":
    sys.exit(main())
