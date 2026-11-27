#!/usr/bin/env python3
"""Le o que probe_scene.js mediu no Blockbench real e confere: abertura (formato, plugin, texturas, bones, cubos),
reproducao sem erro, palco, contatos, ida e volta do save e o "Export Animations" do plugin separado pelo
split_scene.py = exemplo/clips do repositorio. Uso: verify_probe.py <repo> <saida_do_probe>"""
import json
import math
import pathlib
import sys

REPO, OUT = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
sys.path[:0] = [str(REPO / "tools/scenekit"), str(REPO / "tools/player_anim")]
import bbsync  # noqa: E402
import scenekit as S  # noqa: E402

FAILS = []


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILS.append(what)


JOBS = [("scenekit/scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template", "scenekit/examples/player_vs_strawberry", "scene.player_vs_strawberry_demo"),
        ("scenekit/scenes/player_vs_fruit_mannequin/respectcraft_player_vs_fruit_mannequin_template", "scenekit/examples/player_vs_fruit_mannequin", "scene.player_vs_fruit_mannequin_demo"),
        ("scenekit/scenes/dev_player_vs_mannequin_b/dev_player_vs_mannequin_b", None, "scene.authoring_demo"),
        (str(OUT / "multi/player_vs_strawberry_multi"), None, "scene.player_vs_strawberry_demo")]
for stem, example, demo in JOBS:
    base = pathlib.Path(stem) if stem.startswith("/") else REPO / stem
    name = base.name
    probe = json.loads((OUT / f"{name}.probe.json").read_text())
    model, manifest = S.load_json(f"{base}.bbmodel"), S.load_json(f"{base}.scene.json")
    print(f"== {name}: Blockbench {probe['version']}, formato {probe['formatName']}, plugin {probe['plugin']},"
          f" texturas por UV proprio {probe['perTextureUv']}, texturas {probe['textures']}")
    check(probe["plugin"] and probe["format"] == "geckolib_model", f"{name}: abre como GeckoLib Animated Model com o plugin")
    check(probe["groups"] == len(model["groups"]) and probe["cubes"] == len(model["elements"]),
          f"{name}: {probe['groups']} bones e {probe['cubes']} cubos carregados")
    tex_ok = [(t["w"], t["h"]) for t in probe["textures"]] == [(t["width"], t["height"]) for t in model["textures"]]
    if len(model["textures"]) > 1:
        # variante --textures multi: so diagnostico. O formato GeckoLib nao tem UV por textura; e por isso que os
        # templates usam o atlas. Se um dia o plugin passar a ter, esta linha avisa.
        print(f"     multi-textura: UV por textura no formato = {probe['perTextureUv']}"
              f" ({'possivel' if probe['perTextureUv'] else 'nao suportado: atlas obrigatorio'})")
    check(tex_ok, f"{name}: texturas com o tamanho esperado ({len(model['textures'])} textura(s))")
    check(not probe["errors"] and all(p["frames"] > 0 for p in probe["played"]),
          f"{name}: {len(probe['played'])} animacao(oes) tocadas sem erro {probe['errors'][:3]}")
    off, exp = probe["stage"]["offset"], manifest["stage"]["victim_anchor_px"]
    check(off is not None and all(abs(a - b) < 0.05 for a, b in zip(off, exp)),
          f"{name}: vitima no palco {off} (esperado {exp})")
    for c in probe["contacts"]:
        ok = c["distance"] is not None and c["distance"] <= c["max_px"]
        print(f"     contato {c['animation']} {c['event']}@{c['time']}s: {c['distance'] and round(c['distance'], 2)} px"
              f" (limite {c['max_px']}) {'ok' if ok else 'longe'}")
    sv = probe["saved"]
    check(sv["groups"] == len(model["groups"]) and sv["elements"] == len(model["elements"])
          and sv["textures"] == len(model["textures"]) and sv["animations"] == len(model["animations"]),
          f"{name}: salvar pelo Blockbench preserva bones/cubos/texturas/animacoes (campo do kit preservado: {sv['sceneField']})")
    export = json.loads((OUT / f"{name}.blockbench_export.json").read_text())
    clips, events, rep, _ = S.split(manifest, export, demo)
    errs = [m for lvl, m in rep if lvl == "ERROR"]
    check(not errs, f"{name}: export do plugin separado sem erro {errs[:3]}")
    if example:
        ref = {r: S.load_json(REPO / example / f"{demo[6:]}.{r}.animation.json")["animations"] for r in S.ROLES}
        diffs = []
        for r in S.ROLES:
            (k, v), = ref[r].items()
            diffs += bbsync.diff_clips({k: v}, {k: clips[r]})
            if v.get("timeline", {}) != clips[r].get("timeline", {}):
                diffs.append(f"{r}: timeline {clips[r].get('timeline')} != {v.get('timeline')}")
        check(not diffs, f"{name}: export real do Blockbench = exemplo do repositorio ({len(diffs)} diferenca(s)) {diffs[:3]}")
print(f"\n{'OK' if not FAILS else 'FALHOU'}: {len(FAILS)} falha(s)")
sys.exit(1 if FAILS else 0)
