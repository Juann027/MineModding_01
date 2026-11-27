#!/usr/bin/env python3
"""Testes do kit de rigs (Etapa 4.12): validador com casos quebrados, template e gerador em dia. Saida 1 se falhar.

  python3 tools/rigkit/test_rigkit.py
"""
import copy
import io
import json
import pathlib
import sys
import tempfile
from contextlib import redirect_stdout

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import rigkit as K  # noqa: E402
import validate_rig as V  # noqa: E402
import make_assets  # noqa: E402

RIG = K.ASSETS / "rigs/fruit_humanoid_v1.rig.json"
FAILS = []


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILS.append(what)


def levels(rep, level, needle=""):
    return [t for lv, _, t in rep.items if lv == level and needle in t]


def run_geo(tmp, geo, anim=None, binding=None, texture=None, name="m"):
    gp = tmp / f"{name}.geo.json"
    gp.write_text(json.dumps(geo), encoding="utf-8")
    anims = []
    if anim is not None:
        ap = tmp / f"{name}.animation.json"
        ap.write_text(json.dumps(anim), encoding="utf-8")
        anims = [ap]
    bp = None
    if binding is not None:
        bp = tmp / f"{name}.binding.json"
        bp.write_text(json.dumps(binding), encoding="utf-8")
    return V.validate(RIG, geo=gp, texture=texture, anims=anims, binding=bp)


def main():
    with redirect_stdout(io.StringIO()):
        reps = V.repo_reports()
    check(len(reps) == 4 and all(r.count("ERROR") == 0 and r.count("WARN") == 0 for r in reps),
          "repo: contrato, animacoes compartilhadas, os dois manequins e o morango sem erro nem aviso")
    check(all(levels(r, "CHECK") for r in reps[1:]), "repo: o validador sempre pede inspecao visual (nunca declara compatibilidade visual)")
    check(make_assets.main(["--check"]) == 0, "gerador: template, manequins e animacoes em dia com o contrato")
    check((K.REPO / "rigkit/fruit_humanoid_v1/fruit_humanoid_v1.rig.json").read_text() == RIG.read_text(),
          "kit: copia do contrato igual a do jar")

    contract = K.load_json(RIG)
    model = K.build_model(contract, make_assets.SPECS["template"])
    base = K.geo_json(model, "geometry.t")
    tex = K.texture_png(model)
    with tempfile.TemporaryDirectory() as d:
        tmp = pathlib.Path(d)
        (tmp / "t.png").write_bytes(tex)
        rep = run_geo(tmp, base, texture=tmp / "t.png")
        check(rep.count("ERROR") == 0 and rep.count("WARN") == 0, "template exportado passa limpo")

        bb = tmp / "t.bbmodel"
        bb.write_text((K.REPO / "rigkit/fruit_humanoid_v1/fruit_humanoid_v1_template.bbmodel").read_text())
        rep = V.validate(RIG, geo=K.REPO / "rigkit/fruit_humanoid_v1/reference/fruit_humanoid_v1_template.geo.json",
                         texture=K.REPO / "rigkit/fruit_humanoid_v1/reference/fruit_humanoid_v1_template.png", bbmodel=bb)
        check(rep.count("ERROR") == 0 and rep.count("WARN") == 0, ".bbmodel do template = geo de referencia (bones, pais, pivos)")
        bones, _, dup = K.model_from_bbmodel(json.loads(bb.read_text()))
        names = {b["name"] for b in bones}
        check(not dup and {b["name"] for b in contract["bones"]} <= names
              and {"socket_" + s["name"] for s in contract["sockets"]} <= names, "template: todos os bones e marcadores de socket")
        tpl = json.loads(bb.read_text())
        check(tpl["meta"]["model_format"] == "geckolib_model" and len(tpl["animations"]) == len(contract["animations"]),
              "template: formato GeckoLib com as animacoes compartilhadas embutidas")

        def geo_without(name):
            g = copy.deepcopy(base)
            g["minecraft:geometry"][0]["bones"] = [b for b in g["minecraft:geometry"][0]["bones"] if b["name"] != name]
            return g

        def bone(g, name):
            return next(b for b in g["minecraft:geometry"][0]["bones"] if b["name"] == name)

        rep = run_geo(tmp, geo_without("right_hand"))
        check(levels(rep, "ERROR", "required bone right_hand is missing"), "bone obrigatorio ausente = ERROR")

        g = copy.deepcopy(base)
        g["minecraft:geometry"][0]["bones"].append(copy.deepcopy(bone(g, "head")))
        check(levels(run_geo(tmp, g), "ERROR", "duplicate bone name head"), "nome duplicado = ERROR")

        g = copy.deepcopy(base)
        bone(g, "left_hand")["parent"] = "left_arm"
        check(levels(run_geo(tmp, g), "ERROR", "left_hand is under left_arm"), "hierarquia incompativel = ERROR")

        g = copy.deepcopy(base)
        bone(g, "right_forearm")["pivot"][1] += 4
        rep = run_geo(tmp, g)
        check(levels(rep, "WARN", "right_forearm pivot") and rep.count("ERROR") == 0, "pivo fora da tolerancia = WARN")

        g = copy.deepcopy(base)
        bone(g, "root")["pivot"] = [0, 1, 0]
        check(levels(run_geo(tmp, g), "ERROR", "root pivot"), "pivo estrito (root) diferente = ERROR")

        g = copy.deepcopy(base)
        for b in g["minecraft:geometry"][0]["bones"]:
            b["pivot"][0] = -b["pivot"][0]
        check(levels(run_geo(tmp, g), "ERROR", "right_arm is not on the model's right"), "esquerda/direita trocadas = ERROR")

        g = copy.deepcopy(base)
        del bone(g, "pelvis")["pivot"]
        check(levels(run_geo(tmp, g), "ERROR", "pelvis has no pivot"), "pivo ausente = ERROR")

        g = copy.deepcopy(base)
        g["minecraft:geometry"][0]["bones"].append({"name": "cape", "parent": "torso", "pivot": [0, 23, 2]})
        check(levels(run_geo(tmp, g), "WARN", "bone cape is not in the contract"), "bone fora do contrato sem prefixo = WARN")

        g = copy.deepcopy(base)
        g["minecraft:geometry"][0]["bones"].append({"name": "extra_cape", "parent": "torso", "pivot": [0, 23, 2]})
        check(levels(run_geo(tmp, g), "OK", "extra bone extra_cape"), "bone extra_ = recurso opcional valido")

        g = geo_without("torso_core")
        check(levels(run_geo(tmp, g), "WARN", "part core is incomplete") == [] and
              levels(run_geo(tmp, g), "OK", "part core not used"), "parte opcional ausente = so informacao")
        g = geo_without("head_shell")
        check(levels(run_geo(tmp, g), "WARN", "part shell is incomplete"), "parte pela metade = WARN")

        g = copy.deepcopy(base)
        bone(g, "socket_grab_point")["pivot"] = [0, 0, 0]
        check(levels(run_geo(tmp, g), "WARN", "socket_grab_point at"), "socket no lugar errado = WARN")

        anim = {"format_version": "1.8.0", "animations": {"animation.x.bad": {"animation_length": 1.0, "bones": {
            "tail": {"rotation": {"0.0": {"vector": [0, 0, 0]}}},
            "head": {"rotation": {"0.0": {"vector": [0, 0]}, "2.0": {"vector": [0, 1, 0]}}}}}}}
        rep = run_geo(tmp, base, anim=anim)
        check(levels(rep, "ERROR", "keys unknown bone tail"), "animacao com bone inexistente = ERROR")
        check(levels(rep, "ERROR", "expected a 3-value vector"), "vetor de keyframe invalido = ERROR")

        binding = K.load_json(K.ASSETS / "rigs/entities/dev_rig_mannequin_a.json")
        a_anim = K.load_json(K.ASSETS / "animations/entity/dev_rig_mannequin_a.animation.json")
        a_anim["animations"]["animation.dev_rig_mannequin_a.takedown_victim"]["animation_length"] = 2.0
        a_geo = K.load_json(K.ASSETS / "geo/entity/dev_rig_mannequin_a.geo.json")
        rep = run_geo(tmp, a_geo, anim=a_anim, binding=binding, name="a")
        check(levels(rep, "ERROR", "lasts 40 ticks, declared 60"), "override com duracao diferente da cena = ERROR")
        bad = dict(binding, contract_version=2, rig="respectcraft:other")
        rep = run_geo(tmp, a_geo, binding=bad, name="a2")
        check(levels(rep, "ERROR", "contract_version") and levels(rep, "ERROR", "binding uses rig"), "binding de outra versao/familia = ERROR")
        bad = dict(binding, extra_bones=[{"name": "antenna", "parent": "head", "pivot": [0, 32, 0]}])
        check(levels(run_geo(tmp, a_geo, binding=bad, name="a3"), "ERROR", "must start with"), "extra sem prefixo no binding = ERROR")

        shared = K.load_json(K.ASSETS / "animations/rig/fruit_humanoid_v1.animation.json")
        shared["animations"]["animation.fruit_humanoid_v1.finisher.demo_peel"]["animation_length"] = 2.5
        del shared["animations"]["animation.fruit_humanoid_v1.idle"]
        sp = tmp / "shared.animation.json"
        sp.write_text(json.dumps(shared))
        rep = V.validate(RIG, shared_anim=sp)
        check(levels(rep, "ERROR", "demo_peel lasts 50 ticks") and levels(rep, "ERROR", "missing animation animation.fruit_humanoid_v1.idle"),
              "animacoes compartilhadas: duracao e ausencia = ERROR")

        (tmp / "broken.geo.json").write_text("{ not json")
        rep = V.validate(RIG, geo=tmp / "broken.geo.json")
        check(levels(rep, "ERROR", "invalid JSON"), "JSON invalido = ERROR com linha/coluna")
        small = tmp / "small.png"
        small.write_bytes(K.png(32, 32, lambda x, y: (0, 0, 0, 255)))
        check(levels(run_geo(tmp, base, texture=small), "WARN", "PNG is 32x32"), "textura de tamanho diferente = WARN")
        check(levels(run_geo(tmp, base, texture=tmp / "none.png"), "ERROR", "texture not found"), "textura ausente = ERROR")
        with redirect_stdout(io.StringIO()):
            code = V.main(["--rig", str(RIG), "--geo", str(tmp / "broken.geo.json")])
        check(code == 1, "CLI: saida 1 com erro")

    print(f"\n{'OK' if not FAILS else 'FALHOU'}: {len(FAILS)} falha(s)")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
