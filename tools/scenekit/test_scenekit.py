#!/usr/bin/env python3
"""Testes do Finisher Scene Authoring Kit (composicao, separacao, protecao). So biblioteca padrao; roda no CI.

  python3 tools/scenekit/test_scenekit.py
"""
import copy
import hashlib
import json
import math
import pathlib
import subprocess
import sys
import tempfile
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(REPO / "tools/player_anim"))
import bbsync  # noqa: E402
import make_templates  # noqa: E402
import scenekit as S  # noqa: E402
import split_scene  # noqa: E402

FAILS = []
PLAYER = REPO / "model_sources/player/respectcraft_animated_player.bbmodel"
COMBO = REPO / "model_sources/player/respectcraft_combo_scene.bbmodel"
GAME = REPO / "src/main/resources/assets/respectcraft/animations/player/respectcraft_player_combat.animation.json"
STRAWBERRY_SPEC = REPO / "scenekit/scenes/player_vs_strawberry/scene.spec.json"
MANNEQUIN_B_SPEC = REPO / "scenekit/scenes/dev_player_vs_mannequin_b/scene.spec.json"
ASSETS = REPO / "src/main/resources/assets/respectcraft"


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILS.append(what)


def sha(p):
    return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()


def errors(rep):
    return [m for lvl, m in rep if lvl == "ERROR"]


def compose(spec_path, textures="atlas", **over):
    spec = S.load_json(spec_path)
    spec.update(over)
    return S.compose(spec, spec_path.parent, textures)


def bb_animators(manifest, model, role, clip):
    """Clip do jogo (GeckoLib) -> animadores do .bbmodel conjunto (o caminho que o artista faz no Blockbench)."""
    uuids = {g["name"]: g["uuid"] for g in model["groups"]}
    prefix = manifest["participants"][role]["prefix"]
    out = {}
    for bone, chans in clip["bones"].items():
        kfs = []
        for ch, entries in chans.items():
            for t, e in bbsync._channel(entries).items():
                x, y, z = S.flip(ch, e["vector"])
                kf = {"channel": ch, "data_points": [{"x": x, "y": y, "z": z}], "uuid": S.uuid(role, bone, ch, t),
                      "time": float(t), "color": -1, "interpolation": "linear"}
                if "easing" in e:
                    kf["easing"] = e["easing"]
                kfs.append(kf)
        out[uuids[prefix + bone]] = {"name": prefix + bone, "type": "bone", "keyframes": kfs}
    return out


def test_combo_compat():
    """A cena dupla da Etapa 4 (sem prefixo + "Alvo_") separa nos dois clips do jogo, sem divergencia."""
    model = S.load_json(COMBO)
    manifest = S.manifest_from_prefixes(model, {"attacker": "", "victim": "Alvo_"}, 140, "combo_scene",
                                        stage_names=("Alvo_Palco",))
    export, problems = S.compile_bbmodel(model)
    clips, _, rep, _ = S.split(manifest, export)
    game = S.load_json(GAME)["animations"]
    diffs = bbsync.diff_clips(game, {"animation.respectcraft.player.finisher.combo_attacker": clips["attacker"],
                                     "animation.respectcraft.player.finisher.combo_victim": clips["victim"]},
                              only=["animation.respectcraft.player.finisher.combo_attacker",
                                    "animation.respectcraft.player.finisher.combo_victim"])
    check(not problems and not errors(rep) and not diffs, f"Combo PvP: split = clips do jogo ({len(diffs)} diff)")


def test_pvp_roundtrip():
    """Jogador x jogador: compoe, coloca os clips do Combo na cena, compila e separa de volta sem perder keyframe."""
    spec = S.load_json(STRAWBERRY_SPEC)
    spec["victim"] = dict(spec["attacker"], prefix="VIC_", export={"file": "x:y.json", "animation_prefix": "v."})
    model, manifest = S.compose(spec, STRAWBERRY_SPEC.parent)
    check(manifest["stage"]["victim_scale"] == 1 and "STAGE_VICTIM_SCALE" not in manifest["stage"]["groups"]
          and manifest["stage"]["distance_blocks"] == S.STAGE_DISTANCE,
          "PvP: mesma escala, sem grupo de calibracao, distancia 1,15 do palco")
    game = S.load_json(GAME)["animations"]
    att = game["animation.respectcraft.player.finisher.combo_attacker"]
    vic = game["animation.respectcraft.player.finisher.combo_victim"]
    anim = S.scene_animation(dict(manifest, duration_ticks=140), [bb_animators(manifest, model, "attacker", att),
                                                                  bb_animators(manifest, model, "victim", vic)])
    model["animations"] = [anim]
    export, problems = S.compile_bbmodel(model)
    m2 = dict(manifest, duration_ticks=140)
    clips, _, rep, _ = S.split(m2, export)
    diffs = bbsync.diff_clips({"a": att, "v": vic}, {"a": clips["attacker"], "v": clips["victim"]})
    check(not problems and not errors(rep) and not diffs, f"PvP: ida e volta preserva os keyframes ({len(diffs)} diff)")


def test_compose_preserves():
    before = {p: sha(p) for p in (PLAYER, REPO / "model_sources/rig/strawberry/strawberry.bbmodel")}
    model, manifest = compose(STRAWBERRY_SPEC)
    check(before == {p: sha(p) for p in before}, "modelos de origem intactos")
    src = {"attacker": S.load_json(PLAYER), "victim": S.load_json(REPO / "model_sources/rig/strawberry/strawberry.bbmodel")}
    groups = {g["name"]: g for g in model["groups"]}
    ok_groups = ok_elems = True
    for role, m in src.items():
        prefix = manifest["participants"][role]["prefix"]
        for g in m["groups"]:
            s = groups.get(prefix + g["name"])
            ok_groups &= s is not None and s["origin"] == g["origin"] and s.get("rotation") == g.get("rotation") \
                and s.get("visibility", True) == g.get("visibility", True)
        el = {e["name"]: e for e in model["elements"]}
        for e in m["elements"]:
            s = el.get(prefix + e.get("name", "cube"))
            ok_elems &= s is not None and all(s.get(k) == e.get(k) for k in ("from", "to", "origin", "rotation", "inflate",
                                                                              "box_uv", "mirror_uv"))
    check(ok_groups, "bones: nomes (com prefixo), pivos, rotacoes e visibilidade preservados")
    check(ok_elems, "cubos: from/to/origin/rotacao/inflate/box_uv preservados")

    def shape(node, uuid_to_name, prefix=""):
        if isinstance(node, str):
            return "cube"
        return (uuid_to_name[node["uuid"]][len(prefix):], [shape(c, uuid_to_name, prefix) for c in node.get("children", [])])

    names = {g["uuid"]: g["name"] for g in model["groups"]}
    outl = {names[n["uuid"]]: n for n in model["outliner"]}
    vic_root = outl["STAGE_VICTIM"]["children"][0]
    check(vic_root["uuid"] == manifest["stage"]["groups"]["STAGE_VICTIM_SCALE"], "vitima dentro de STAGE_VICTIM > STAGE_VICTIM_SCALE")
    for role, holder in (("attacker", outl["STAGE_ATTACKER"]), ("victim", vic_root)):
        sm = src[role]
        orig = {g["uuid"]: g["name"] for g in sm["groups"]}
        same = [shape(n, orig) for n in sm["outliner"]] == [shape(n, names, manifest["participants"][role]["prefix"])
                                                            for n in holder["children"]]
        check(same, f"{role}: hierarquia identica a original")
    ids = [g["uuid"] for g in model["groups"]] + [e["uuid"] for e in model["elements"]] + [t["uuid"] for t in model["textures"]]
    old = {x["uuid"] for m in src.values() for x in m["groups"] + m["elements"] + m["textures"]}
    check(len(ids) == len(set(ids)) and not (set(ids) & old), "UUIDs novos e unicos (sem colisao com as origens)")
    check(all(g.get("export") is False for g in model["groups"] if g["name"].startswith("STAGE_")),
          "grupos de palco com export = false")
    names_v = {p["name"] for p in manifest["victim_parts"]}
    check({"shell", "core", "face_normal", "eyes_wide", "leaves", "damage_torso"} <= names_v
          and all(g in groups for p in manifest["victim_parts"] for g in p["groups"])
          and {"VIC_socket_impact_point", "VIC_socket_grab_point"} <= set(groups),
          "partes (contrato + binding), expressoes e sockets da vitima no manifesto e na cena")


def test_textures():
    model, manifest = compose(STRAWBERRY_SPEC, "atlas")
    atlas = model["textures"]
    w, h, rows = S.png_decode(S.texture_png(atlas[0]))
    srcs = [S.load_json(PLAYER), S.load_json(REPO / "model_sources/rig/strawberry/strawberry.bbmodel")]
    ok, y = True, 0
    for m in srcs:
        tw, th, trows = S.png_decode(S.texture_png(m["textures"][0]))
        for j in range(th):
            ok &= rows[y + j][:tw * 4] == trows[j]
        y += th
    check(len(atlas) == 1 and (w, h) == (256, 320) and model["resolution"] == {"width": 256, "height": 320} and ok,
          "atlas: as duas texturas pixel a pixel (64x64 em y=0, 256x256 em y=64)")
    by = {e["name"]: e for e in model["elements"]}
    ok_uv = True
    for role, m, oy in (("attacker", srcs[0], 0), ("victim", srcs[1], 64)):
        p = manifest["participants"][role]["prefix"]
        for e in m["elements"]:
            s = by[p + e["name"]]
            for k, f in e["faces"].items():
                if "uv" in f and isinstance(f.get("texture"), int):
                    ok_uv &= s["faces"][k]["uv"] == [f["uv"][0], f["uv"][1] + oy, f["uv"][2], f["uv"][3] + oy]
            if e.get("box_uv") and "uv_offset" in e:
                ok_uv &= s["uv_offset"] == [e["uv_offset"][0], e["uv_offset"][1] + oy]
    check(ok_uv, "atlas: UVs (por face e box UV) deslocados exatamente para a regiao de cada textura")
    model, _ = compose(STRAWBERRY_SPEC, "multi")
    same = [t["source"] for t in model["textures"]] == [m["textures"][0]["source"] for m in srcs]
    faces_ok = all(f.get("texture") in (0, 1, None, False) for e in model["elements"] for f in e["faces"].values())
    check(same and faces_ok and [t["uv_width"] for t in model["textures"]] == [64, 256],
          "multi: texturas originais (bytes iguais), indice de textura remapeado, UV por textura")


def test_stage():
    for spec_path, dist, src in ((STRAWBERRY_SPEC, 1.15, "auto"), (MANNEQUIN_B_SPEC, 1.6, "binding")):
        model, manifest = compose(spec_path)
        st = manifest["stage"]
        g = {x["name"]: x for x in model["groups"]}["STAGE_VICTIM"]
        a = math.radians(g["rotation"][1])
        px, pz = g["origin"][0], g["origin"][2]
        # ponto (0,0,0) da vitima girado em volta da origem do grupo (rotacao em Y do three.js)
        x = px + math.cos(a) * (0 - px) + math.sin(a) * (0 - pz)
        z = pz - math.sin(a) * (0 - px) + math.cos(a) * (0 - pz)
        exp = st["victim_anchor_px"]
        check(abs(st["distance_blocks"] - dist) < 1e-9 and st["source"] == src
              and abs(x - exp[0]) < 1e-4 and abs(z - exp[2]) < 1e-4 and abs(z + dist * 16 / 0.9375) < 1e-3
              and abs(st["victim_scale"] - 16 / 15) < 1e-5,
              f"palco {spec_path.parent.name}: {dist} blocos ({src}), vitima de frente a -{dist * 16 / 0.9375:.2f} px, escala 16/15")
    _, m = compose(STRAWBERRY_SPEC, stage={"distance": 2.0, "lateral": 0.5, "yaw_offset": 30})
    check(abs(m["stage"]["victim_anchor_px"][0] - 0.5 * 16 / 0.9375) < 1e-3 and m["stage"]["victim_yaw"] == 210,
          "palco com lateral e yaw_offset (pivo resolvido para o giro)")
    try:
        compose(STRAWBERRY_SPEC, stage={"distance": 9})
        check(False, "distancia fora de 0.3..6 recusada")
    except S.SceneError:
        check(True, "distancia fora de 0.3..6 recusada")


def demo_export(spec_path=STRAWBERRY_SPEC, demo="scene.player_vs_strawberry_demo"):
    model, manifest = compose(spec_path)
    make_templates.add_demo(model, manifest, demo)
    export, problems = S.compile_bbmodel(model)
    return model, manifest, export, problems


def test_split_demo():
    model, manifest, export, problems = demo_export()
    geo = S.geo_bones(S.load_json(ASSETS / "geo/entity/strawberry.geo.json"))
    bones = {"attacker": {b["bone"] for b in manifest["bones"].values() if b["role"] == "attacker"}, "victim": geo}
    clips, events, rep, name = S.split(manifest, export, "scene.player_vs_strawberry_demo", bones)
    check(not problems and not errors(rep), "demo do morango: separacao sem erro")
    check(all(c["animation_length"] == 3 for c in clips.values()) and manifest["duration_ticks"] == 60,
          "mesma duracao (60 ticks) nos dois clips")
    allb = set(clips["attacker"]["bones"]) | set(clips["victim"]["bones"])
    check(not any(b.startswith(("STAGE_", "ATK_", "VIC_")) for b in allb) and set(clips["victim"]["bones"]) <= geo,
          "exports sem grupos de palco nem prefixos; bones da vitima existem no geo do jogo")
    scene_clip = export["animations"][name]
    same = all(clips[b["role"]]["bones"].get(b["bone"]) == scene_clip["bones"][n]
               for n, b in manifest["bones"].items() if n in scene_clip["bones"])
    check(same, "keyframes copiados sem mudanca do export (nenhuma segunda conversao de eixo)")
    tl = clips["victim"].get("timeline", {})
    check(tl.get("0.75", "").startswith("rig:hide:face_normal;") and tl.get("1.1") == "rig:hide:shell;rig:show:core;rig:show:damage_torso;"
          and "timeline" not in clips["attacker"],
          "timeline: rig: so no clip da vitima (dono unico), expressao e polpa nos ticks certos")
    js = S.java_snippets(events, manifest)
    check('.event(14, Finishers.EVENT_HIT, "2")' in js["attacker"] and len(js["camera"]) == 4,
          "eventos de jogador viram linhas Java do executor; camera vira CameraTrack")
    # bezier/step/molang no export do plugin: preservados como vieram
    ex2 = copy.deepcopy(export)
    ex2["animations"][name]["bones"]["ATK_Head"] = {"rotation": {"0.0": {"vector": ["math.sin(q.anim_time*90)*4", 0, 0]},
                                                                 "1.0": {"post": [0, 10, 0], "lerp_mode": "catmullrom"}}}
    c2, _, r2, _ = S.split(manifest, ex2, name)
    check(not errors(r2) and c2["attacker"]["bones"]["Head"] == ex2["animations"][name]["bones"]["ATK_Head"],
          "Molang e catmullrom do export do plugin preservados")
    model["animations"][1]["animators"][next(iter(model["animations"][1]["animators"]))]["keyframes"][0]["interpolation"] = "bezier"
    _, probs = S.compile_bbmodel(model)
    check(any("bezier" in p for p in probs), "compilacao do .bbmodel aponta bezier (nunca descarta em silencio)")


def test_split_errors():
    _, manifest, export, _ = demo_export()
    name = "scene.player_vs_strawberry_demo"

    def run(mut):
        ex = copy.deepcopy(export)
        mut(ex["animations"][name])
        return errors(S.split(manifest, ex, name)[2])

    cases = {
        "palco animado (movimento real)": lambda a: a["bones"].setdefault("STAGE_VICTIM", {})
        .update({"position": {"1.0": {"vector": [0, 0, 5]}}}),
        "bone desconhecido": lambda a: a["bones"].update({"VIC_banana": {"rotation": {"0": {"vector": [1, 0, 0]}}}}),
        "linha sem dono": lambda a: a["timeline"].update({"0.5": "hit 2"}),
        "hit na vitima": lambda a: a["timeline"].update({"0.5": "@victim hit 2"}),
        "execute fora do fim": lambda a: a["timeline"].update({"0.5": "@scene execute"}),
        "som nos dois donos": lambda a: a["timeline"].update({"0": "@attacker sound respectcraft:x\n@victim sound respectcraft:x"}),
        "parte inexistente": lambda a: a["timeline"].update({"0.5": "@victim rig:hide:banana"}),
        "rig: no jogador": lambda a: a["timeline"].update({"0.5": "@attacker rig:hide:shell"}),
        "sound_effects sem dono": lambda a: a.update({"sound_effects": {"0.5": {"effect": "x"}}}),
        "fora do tick": lambda a: a["timeline"].update({"0.52": "@attacker hit 1"}),
        "duracao diferente": lambda a: a.update({"animation_length": 2.5}),
    }
    for what, mut in cases.items():
        check(bool(run(mut)), f"ERROR: {what}")
    ex = copy.deepcopy(export)
    ex["animations"][name]["bones"]["ATK_Root"]["position"]["3"] = {"vector": [0, 0, -6]}
    rep = S.split(manifest, ex, name)[2]
    check(any("root bone Root ends displaced" in m for lvl, m in rep if lvl == "WARN"),
          "WARN: root que nao volta a zero (movimento visual, a entidade nao anda)")


def test_install_protection():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        saved = split_scene.INSTALLED
        split_scene.INSTALLED = tmp / "installed.json"
        try:
            root = tmp / "res"
            files = {"respectcraft:animations/entity/x.animation.json": {"a": {"animation_length": 1, "bones": {}}}}
            r1 = split_scene.install(files, root)
            r2 = split_scene.install({"respectcraft:animations/entity/x.animation.json": {"b": {"animation_length": 1, "bones": {}}}}, root)
            target = root / "assets/respectcraft/animations/entity/x.animation.json"
            merged = sorted(json.loads(target.read_text())["animations"]) == ["a", "b"]
            target.write_text(target.read_text() + " ")
            r3 = split_scene.install(files, root)
            check(r1 == 0 and r2 == 0 and merged and r3 == 3, "instalacao: cria, mescla clip a clip e recusa arquivo editado fora")
            r4 = split_scene.install({"respectcraft:animations/player/respectcraft_player_combat.animation.json": {}},
                                     REPO / "src/main/resources")
            check(r4 == 3 and sha(GAME) == S.load_json(REPO / "tools/player_anim/export_manifest.json")
                  .get("src/main/resources/assets/respectcraft/animations/player/respectcraft_player_combat.animation.json", sha(GAME)),
                  "instalacao: recusa a biblioteca de combate (protegida pelo export.py)")
        finally:
            split_scene.INSTALLED = saved


def test_cli_and_generated():
    with tempfile.TemporaryDirectory() as tmp:
        r = subprocess.run([sys.executable, str(HERE / "compose_scene.py"), str(STRAWBERRY_SPEC), "--out", tmp],
                           capture_output=True, text=True)
        man = pathlib.Path(tmp) / "respectcraft_player_vs_strawberry_template.scene.json"
        ex = REPO / "scenekit/examples/player_vs_strawberry/player_vs_strawberry_demo.compiled_export.animation.json"
        r2 = subprocess.run([sys.executable, str(HERE / "split_scene.py"), str(man), "--export", str(ex), "--out", tmp,
                             "--animation", "scene.player_vs_strawberry_demo"], capture_output=True, text=True)
        out = pathlib.Path(tmp) / "player_vs_strawberry_demo.victim.animation.json"
        ref = REPO / "scenekit/examples/player_vs_strawberry/player_vs_strawberry_demo.victim.animation.json"
        check(r.returncode == 0 and r2.returncode == 0 and out.read_text() == ref.read_text(),
              "CLI: compose + split reproduzem o exemplo do repositorio")
    r = subprocess.run([sys.executable, str(HERE / "make_templates.py"), "--check"], capture_output=True, text=True)
    check(r.returncode == 0, "templates, exemplos e clips da cena de teste em dia (make_templates --check)")
    for p in ("scenekit/scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template.bbmodel",
              "scenekit/scenes/player_vs_fruit_mannequin/respectcraft_player_vs_fruit_mannequin_template.bbmodel"):
        m = S.load_json(REPO / p)
        check(m["meta"]["model_format"] == "geckolib_model" and len(m["animations"]) == 2, f"{pathlib.Path(p).name}: GeckoLib, cena vazia + demo")
    with tempfile.TemporaryDirectory() as tmp:
        r = subprocess.run([sys.executable, str(HERE / "package_kit.py"), "--out", tmp], capture_output=True, text=True)
        zips = list(pathlib.Path(tmp).glob("*.zip"))
        names = zipfile.ZipFile(zips[0]).namelist() if zips else []
        need = ["respectcraft-scenekit/README.md", "respectcraft-scenekit/CONTRACT.md", "respectcraft-scenekit/CLAUDE_CHROME.md",
                "respectcraft-scenekit/tools/compose_scene.py", "respectcraft-scenekit/tools/split_scene.py",
                "respectcraft-scenekit/scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template.bbmodel",
                "respectcraft-scenekit/scenes/player_vs_fruit_mannequin/respectcraft_player_vs_fruit_mannequin_template.bbmodel"]
        check(r.returncode == 0 and all(n in names for n in need), "ZIP portatil com templates, ferramentas e guias")


def main():
    for t in (test_combo_compat, test_pvp_roundtrip, test_compose_preserves, test_textures, test_stage, test_split_demo,
              test_split_errors, test_install_protection, test_cli_and_generated):
        try:
            t()
        except Exception as e:  # um teste quebrado nao esconde os outros
            check(False, f"{t.__name__}: {type(e).__name__}: {e}")
    print(f"\n{'OK' if not FAILS else 'FALHOU'}: {len(FAILS)} falha(s)")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
