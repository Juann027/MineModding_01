#!/usr/bin/env python3
"""Validador de assets externos de rigs do RespectCraft (Etapa 4.12). Python 3.9+, so biblioteca padrao.

Confere um modelo exportado do Blockbench (GeckoLib 4.9.3) contra o contrato de uma familia de rig:

  # modelo solto (o que sai do Blockbench), contra o contrato do kit
  python3 validate_rig.py --rig fruit_humanoid_v1.rig.json --geo banana.geo.json --texture banana.png \\
      --anim banana.animation.json [--bbmodel banana.bbmodel] [--proportion 1.1 1.0 0.9]

  # manifesto de binding (resolve geo/textura/animacoes por ele; ids "ns:caminho" dentro de --assets-root)
  python3 validate_rig.py --rig fruit_humanoid_v1.rig.json --binding banana.json --assets-root src/main/resources

  # todos os bindings e familias do repositorio (o que o CI/teste roda)
  python3 validate_rig.py --repo

  # arquivo de animacoes compartilhadas da familia
  python3 validate_rig.py --rig fruit_humanoid_v1.rig.json --shared-anim fruit_humanoid_v1.animation.json

Niveis: ERROR (impede a integracao) | WARN (possivel incompatibilidade) | OK (recurso opcional valido encontrado) |
CHECK (so inspecao visual resolve). Nomes de bones iguais NUNCA bastam para compatibilidade visual: o resultado final
diz "estruturalmente compativel" e lista o que precisa ser visto no Blockbench/jogo. Saida 1 se houver ERROR.
O validador so le arquivos; nunca grava nada.
"""
import argparse
import json
import pathlib
import re
import struct
import sys

LEVELS = ("ERROR", "WARN", "OK", "CHECK")


class Report:
    def __init__(self, title):
        self.title = title
        self.items = []

    def add(self, level, where, text):
        self.items.append((level, where, text))

    def count(self, level):
        return sum(1 for i in self.items if i[0] == level)

    def print(self, out=None):
        out = out or sys.stdout
        print(f"== {self.title}", file=out)
        for level in LEVELS:
            for lv, where, text in self.items:
                if lv == level:
                    print(f"  {lv:5} {where}: {text}", file=out)
        e, w = self.count("ERROR"), self.count("WARN")
        verdict = ("NOT COMPATIBLE" if e else "STRUCTURALLY COMPATIBLE")
        print(f"  -> {verdict}: {e} error(s), {w} warning(s). Visual compatibility is NOT verified by this tool"
              f" (see CHECK items).", file=out)


def read_json(path, rep, where):
    try:
        return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        rep.add("ERROR", where, f"file not found: {path}")
    except json.JSONDecodeError as e:
        rep.add("ERROR", where, f"invalid JSON ({path}): line {e.lineno} col {e.colno}: {e.msg}")
    return None


def png_size(path):
    data = pathlib.Path(path).read_bytes()[:24]
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def vec3(v):
    return isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v)


# ------------------------------------------------------------------------------------------------ contrato

def check_contract(c, rep):
    where = "rig"
    if c.get("format") != "respectcraft:rig":
        rep.add("ERROR", where, "not a rig manifest (format must be respectcraft:rig)")
        return False
    names, roots = {}, 0
    for b in c.get("bones", []):
        n = b.get("name")
        if n in names:
            rep.add("ERROR", where, f"duplicate bone {n}")
        names[n] = b
        if b.get("parent") is None:
            roots += 1
        if not vec3(b.get("pivot")):
            rep.add("ERROR", where, f"bone {n} without pivot [x,y,z]")
    if roots != 1:
        rep.add("ERROR", where, f"expected one root bone, found {roots}")
    for b in c.get("bones", []):
        p = b.get("parent")
        if p is not None and p not in names:
            rep.add("ERROR", where, f"bone {b['name']} has unknown parent {p}")
    for s in c.get("sockets", []):
        if s.get("bone") not in names:
            rep.add("ERROR", where, f"socket {s.get('name')} on unknown bone {s.get('bone')}")
    for p in c.get("parts", []):
        for b in p.get("bones", []):
            if b not in names:
                rep.add("ERROR", where, f"part {p.get('name')} uses unknown bone {b}")
            elif names[b].get("required"):
                rep.add("ERROR", where, f"part {p.get('name')} would hide required bone {b}")
    return rep.count("ERROR") == 0


# ------------------------------------------------------------------------------------------------ geometria

def geo_bones(geo, rep):
    """-> (bones dict nome -> {parent, pivot (Blockbench), cubes, raw}), descricao. Desinverte X."""
    where = "geo"
    if not isinstance(geo, dict) or "minecraft:geometry" not in geo:
        rep.add("ERROR", where, "missing minecraft:geometry (export with the GeckoLib plugin: File > Export > GeckoLib Model)")
        return None, None
    geos = geo["minecraft:geometry"]
    if len(geos) != 1:
        rep.add("WARN", where, f"{len(geos)} geometries in one file; only the first is checked")
    g = geos[0]
    desc = g.get("description", {})
    bones = {}
    for b in g.get("bones", []):
        n = b.get("name")
        if not n:
            rep.add("ERROR", where, "bone without name")
            continue
        if n in bones:
            rep.add("ERROR", where, f"duplicate bone name {n} (GeckoLib keeps only one)")
            continue
        piv = b.get("pivot")
        bones[n] = {"parent": b.get("parent"), "pivot": [-piv[0], piv[1], piv[2]] if vec3(piv) else None,
                    "cubes": b.get("cubes", []), "raw": b}
    for n, b in bones.items():
        if b["parent"] is not None and b["parent"] not in bones:
            rep.add("ERROR", where, f"bone {n} references missing parent {b['parent']}")
    for n in bones:  # ciclos
        seen, cur = set(), n
        while cur is not None and cur in bones:
            if cur in seen:
                rep.add("ERROR", where, f"parent cycle at {n}")
                break
            seen.add(cur)
            cur = bones[cur]["parent"]
    return bones, desc


def check_geo(c, bones, desc, rep, proportion, binding=None):
    contract = {b["name"]: b for b in c["bones"]}
    tol = float(c.get("pivot_tolerance", 2.0))
    prefixes = tuple(c.get("optional_prefixes", []))
    extra = {b["name"]: b for b in (binding or {}).get("extra_bones", [])}
    for name, cb in contract.items():
        mb = bones.get(name)
        if mb is None:
            if cb.get("required"):
                rep.add("ERROR", "bones", f"required bone {name} is missing ({cb.get('role', '')})")
            continue
        if mb["parent"] != cb["parent"]:
            lvl = "ERROR" if cb.get("required") else "WARN"
            rep.add(lvl, "hierarchy", f"{name} is under {mb['parent']} but the contract says {cb['parent']}"
                                      " (shared animations would move it differently)")
        if mb["pivot"] is None:
            rep.add("ERROR" if cb.get("required") else "WARN", "pivots", f"{name} has no pivot")
            continue
        exp = cb["pivot"] if cb.get("strict") else [cb["pivot"][i] * proportion[i] for i in range(3)]
        d = max(abs(mb["pivot"][i] - exp[i]) for i in range(3))
        if cb.get("strict") and d > 1e-3:
            rep.add("ERROR", "pivots", f"{name} pivot {fmt(mb['pivot'])} must be exactly {fmt(exp)}")
        elif d > tol:
            rep.add("WARN", "pivots", f"{name} pivot {fmt(mb['pivot'])} is {d:.2f} px from expected {fmt(exp)}"
                                      f" (contract x proportion {fmt(proportion)}, tolerance {tol} px):"
                                      " shared animations will bend at a different joint")
        rot = mb["raw"].get("rotation")
        if cb.get("required") and vec3(rot) and any(abs(x) > 1e-6 for x in rot):
            rep.add("WARN", "pivots", f"{name} has a rest rotation {fmt(rot)}; shared animations add to it"
                                      " (keep required bones unrotated, rotate the cubes instead)")
        if not cb.get("required"):
            rep.add("OK", "bones", f"optional bone {name} present")
    for name, mb in bones.items():
        if name in contract:
            continue
        if name.startswith("socket_"):
            continue
        if name in extra:
            rep.add("OK", "bones", f"extra bone {name} declared by the binding")
        elif name.startswith(prefixes):
            rep.add("OK" if binding is None else "WARN", "bones",
                    f"extra bone {name} (free prefix)" + ("" if binding is None else "; declare it in extra_bones"))
        else:
            rep.add("WARN", "bones", f"bone {name} is not in the contract; rename it with one of {list(prefixes)}"
                                     " so shared animations and future contract versions never collide with it")
    for name in extra:
        if name not in bones:
            rep.add("WARN", "binding", f"extra bone {name} declared but missing from the geo")
    # orientacao e escala
    rh = float(c.get("reference_height", 32)) * proportion[1]
    ys, zs = [], []
    for b in bones.values():
        for cube in b["cubes"]:
            o, s = cube.get("origin"), cube.get("size")
            if vec3(o) and vec3(s):
                ys += [o[1], o[1] + s[1]]
                zs += [o[2], o[2] + s[2]]
    if ys:
        top, bottom = max(ys), min(ys)
        if abs(bottom) > 1.0:
            rep.add("WARN", "scale", f"lowest cube at y={bottom:g} px; feet should touch y=0 (entity origin)")
        if abs(top - rh) > 0.2 * rh:
            rep.add("WARN", "scale", f"model height {top:g} px vs expected {rh:g} px (reference_height x proportion);"
                                     " check the hitbox and the binding proportion")
    else:
        rep.add("WARN", "geo", "model has no cubes")
    if "right_arm" in bones and "left_arm" in bones and bones["right_arm"]["pivot"] and bones["left_arm"]["pivot"]:
        if bones["right_arm"]["pivot"][0] <= bones["left_arm"]["pivot"][0]:
            rep.add("ERROR", "orientation", "right_arm is not on the model's right (+X in Blockbench): left/right swapped"
                                            " or the model faces the wrong way")
    face = bones.get("face") or bones.get("mouth")
    head = bones.get("head")
    if face and head and face["pivot"] and head["pivot"] and face["pivot"][2] > head["pivot"][2]:
        rep.add("WARN", "orientation", "the face is behind the head pivot (+Z); the model front must face -Z (north)")
    # sockets
    for s in c.get("sockets", []):
        if s["bone"] not in bones:
            continue
        marker = bones.get("socket_" + s["name"])
        if marker is None:
            rep.add("OK", "sockets", f"socket {s['name']} from the manifest (no socket_{s['name']} marker bone)")
            continue
        if marker["parent"] != s["bone"]:
            rep.add("WARN", "sockets", f"socket_{s['name']} should be a child of {s['bone']}")
        base = bones[s["bone"]]["pivot"]
        exp = [base[i] + s["offset"][i] * proportion[i] for i in range(3)]
        if marker["pivot"] and max(abs(marker["pivot"][i] - exp[i]) for i in range(3)) > tol:
            rep.add("WARN", "sockets", f"socket_{s['name']} at {fmt(marker['pivot'])}, manifest says {fmt(exp)};"
                                       " override it in the binding 'sockets' if the model really differs")
        else:
            rep.add("OK", "sockets", f"socket {s['name']} marker matches")
        if marker["cubes"]:
            rep.add("WARN", "sockets", f"socket_{s['name']} has cubes; markers must be empty bones")
    for s in (binding or {}).get("sockets", []):
        if s.get("bone") not in bones:
            rep.add("ERROR", "sockets", f"binding socket {s.get('name')} on missing bone {s.get('bone')}")
    # partes
    for p in c.get("parts", []):
        missing = [b for b in p["bones"] if b not in bones]
        if len(missing) == len(p["bones"]):
            rep.add("OK", "parts", f"part {p['name']} not used by this model (no {p['bones']})")
        elif missing:
            rep.add("WARN", "parts", f"part {p['name']} is incomplete: missing {missing}")
        else:
            rep.add("OK", "parts", f"part {p['name']} present ({'visible' if p.get('default_visible', True) else 'hidden'} by default)")
    for p in (binding or {}).get("parts", []):
        for b in p.get("bones", []):
            if b not in bones:
                rep.add("ERROR", "parts", f"binding part {p.get('name')} uses missing bone {b}")
            elif contract.get(b, {}).get("required"):
                rep.add("ERROR", "parts", f"binding part {p.get('name')} would hide required bone {b}")


def check_texture(desc, bones, texture, rep):
    if texture is None:
        rep.add("CHECK", "texture", "no texture given; paint and check it in Blockbench")
        return
    if not pathlib.Path(texture).exists():
        rep.add("ERROR", "texture", f"texture not found: {texture}")
        return
    size = png_size(texture)
    if size is None:
        rep.add("ERROR", "texture", f"{texture} is not a PNG")
        return
    tw, th = desc.get("texture_width"), desc.get("texture_height")
    if (tw, th) != size:
        rep.add("WARN", "texture", f"PNG is {size[0]}x{size[1]} but the geo declares {tw}x{th} (UVs will stretch)")
    else:
        rep.add("OK", "texture", f"PNG {size[0]}x{size[1]} matches the geo")
    for name, b in bones.items():
        for cube in b["cubes"]:
            uv = cube.get("uv")
            if isinstance(uv, dict):
                for face, f in uv.items():
                    u, v = f.get("uv", [0, 0])
                    w, h = f.get("uv_size", [0, 0])
                    if min(u, v, u + w, v + h) < 0 or max(u, u + w) > tw or max(v, v + h) > th:
                        rep.add("WARN", "texture", f"{name} {face} UV outside the texture")
                        break


# ------------------------------------------------------------------------------------------------ animacoes

def check_animations(c, anim, bones, rep, where, shared=False, expected_names=None, extra_parts=()):
    if not isinstance(anim, dict) or not isinstance(anim.get("animations"), dict):
        rep.add("ERROR", where, "missing 'animations' object (export with GeckoLib: Export Animations)")
        return {}
    prefix = c.get("animation_prefix", "")
    shared_bones = set(c.get("shared_animation_bones", []))
    contract = {b["name"] for b in c["bones"]}
    # partes do contrato + as declaradas pelo binding (Etapa 4.13.3: clips proprios da entidade usam as do binding)
    parts = {p["name"] for p in c.get("parts", [])} | set(extra_parts)
    lengths = {}
    if not shared and prefix:
        dup = [n for n in anim["animations"] if n.startswith(prefix)]
        if dup:
            rep.add("WARN", where, f"entity file contains family animations {dup}: they would shadow the shared ones"
                                   " (export only the model's own clips)")
    for name, a in anim["animations"].items():
        length = a.get("animation_length")
        if not isinstance(length, (int, float)) or length <= 0:
            rep.add("WARN" if a.get("loop") else "ERROR", where, f"{name} has no animation_length")
            length = None
        lengths[name] = length
        for bone, chans in a.get("bones", {}).items():
            if bones is not None and bone not in bones:
                if bone in contract:
                    rep.add("WARN", where, f"{name} keys {bone}, which this model does not have (GeckoLib ignores it)")
                else:
                    rep.add("ERROR", where, f"{name} keys unknown bone {bone}")
            if shared and bone not in shared_bones:
                rep.add("WARN", where, f"shared {name} keys {bone}, outside shared_animation_bones")
            for ch, keys in chans.items():
                if ch not in ("rotation", "position", "scale"):
                    rep.add("WARN", where, f"{name}/{bone}: unknown channel {ch}")
                    continue
                if shared and ch == "position" and bone not in ("pelvis",) and bone in contract:
                    rep.add("WARN", where, f"shared {name} moves {bone} (position); only pelvis offsets scale with"
                                           " every model, prefer rotations")
                items = keys.items() if isinstance(keys, dict) else [("0", keys)]
                for t, v in items:
                    vec = v.get("vector") if isinstance(v, dict) else v
                    if not (isinstance(vec, list) and len(vec) == 3):
                        rep.add("ERROR", where, f"{name}/{bone}/{ch} at {t}: expected a 3-value vector")
                        break
                    try:
                        tt = float(t)
                    except ValueError:
                        rep.add("ERROR", where, f"{name}/{bone}/{ch}: keyframe time {t!r} is not a number")
                        break
                    if length and tt > length + 1e-6:
                        rep.add("WARN", where, f"{name}/{bone}/{ch}: keyframe at {tt}s after the end ({length}s)")
        for t, text in (a.get("timeline") or {}).items():
            texts = text if isinstance(text, list) else [text]
            for part_text in ";".join(texts).split(";"):
                m = re.fullmatch(r"\s*rig:(hide|show):([a-z0-9_]+)\s*", part_text)
                if m and m.group(2) not in parts and not part_text.strip().startswith("rig:reset"):
                    rep.add("WARN", where, f"{name} timeline {t}: part {m.group(2)} is not in the contract"
                                           " (declare it in the binding parts)")
    if expected_names:
        for key, info in expected_names.items():
            name = prefix + key if shared else key
            if name not in lengths:
                rep.add("ERROR", where, f"missing animation {name}")
                continue
            ticks = info.get("ticks")
            if ticks and lengths[name] and abs(lengths[name] * 20 - ticks) > 0.5:
                rep.add("ERROR", where, f"{name} lasts {lengths[name] * 20:g} ticks; the contract says {ticks}")
            else:
                rep.add("OK", where, f"{name} present ({ticks} ticks)")
    return lengths


# ------------------------------------------------------------------------------------------------ binding

def resolve(rid, assets_root, base_dir):
    """'ns:caminho' -> arquivo. Com --assets-root: <root>/assets/<ns>/<caminho>; senao, mesmo nome ao lado do binding."""
    if rid is None:
        return None
    ns, _, path = rid.partition(":")
    if assets_root:
        p = pathlib.Path(assets_root) / "assets" / ns / path
        if p.exists():
            return p
    return pathlib.Path(base_dir) / pathlib.Path(path).name


def check_binding(c, b, rep, assets_root, base_dir):
    where = "binding"
    if b.get("format") != "respectcraft:rig_binding":
        rep.add("ERROR", where, "not a binding manifest (format must be respectcraft:rig_binding)")
        return None
    if b.get("rig") != c.get("id"):
        rep.add("ERROR", where, f"binding uses rig {b.get('rig')} but the contract is {c.get('id')}")
    if b.get("contract_version") != c.get("contract_version"):
        rep.add("ERROR", where, f"contract_version {b.get('contract_version')} != {c.get('contract_version')}")
    prop = b.get("proportion", [1, 1, 1])
    if not vec3(prop) or min(prop) <= 0:
        rep.add("ERROR", where, "proportion must be three positive numbers")
        prop = [1, 1, 1]
    prefixes = tuple(c.get("optional_prefixes", []))
    for e in b.get("extra_bones", []):
        if not e.get("name", "").startswith(prefixes):
            rep.add("ERROR", where, f"extra bone {e.get('name')} must start with one of {list(prefixes)}")
    files = {"geo": resolve(b.get("geo"), assets_root, base_dir), "texture": resolve(b.get("texture"), assets_root, base_dir),
             "anim": resolve(b.get("animation_file"), assets_root, base_dir)}
    for k, p in files.items():
        if p is not None and not p.exists():
            rep.add("ERROR", where, f"{k} {b.get({'geo': 'geo', 'texture': 'texture', 'anim': 'animation_file'}[k])} not found ({p})")
    a = b.get("alignment")
    if a:
        d = a.get("distance")
        if d is not None and not 0.3 <= d <= 6.0:
            rep.add("ERROR", where, f"alignment distance {d} outside 0.3..6")
        if abs(a.get("lateral", 0)) > 3:
            rep.add("ERROR", where, "alignment lateral outside -3..3")
        rep.add("OK", where, f"own alignment {a}")
    seen = set()
    for o in b.get("clip_overrides", []):
        key = (o.get("finisher"), o.get("role"))
        if key in seen:
            rep.add("ERROR", where, f"repeated clip override {key}")
        seen.add(key)
        if o.get("role", "").lower() not in ("attacker", "victim"):
            rep.add("ERROR", where, f"override role {o.get('role')} must be attacker or victim")
    return prop, files


def check_override_clips(b, lengths, rep):
    for o in b.get("clip_overrides", []):
        name = o.get("animation")
        if name not in lengths:
            rep.add("ERROR", "binding", f"override {o.get('finisher')}/{o.get('role')}: animation {name} not in the"
                                        " entity animation file")
        elif lengths[name] and abs(lengths[name] * 20 - o.get("ticks", 0)) > 0.5:
            rep.add("ERROR", "binding", f"override {name} lasts {lengths[name] * 20:g} ticks, declared {o.get('ticks')}"
                                        " (must equal the finisher scene)")
        else:
            rep.add("OK", "binding", f"override {o.get('finisher')}/{o.get('role')} -> {name}")


def check_bbmodel(bbmodel_path, bones, rep):
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
    import rigkit  # noqa: E402
    bb = read_json(bbmodel_path, rep, "bbmodel")
    if bb is None:
        return
    fmt_ = bb.get("meta", {}).get("model_format")
    if fmt_ != "geckolib_model":
        rep.add("WARN", "bbmodel", f"model_format is {fmt_!r}; use the GeckoLib Animated Model format")
    bb_bones, _, dup = rigkit.model_from_bbmodel(bb)
    for d in dup:
        rep.add("ERROR", "bbmodel", f"duplicate group name {d}")
    by = {x["name"]: x for x in bb_bones}
    for name, mb in bones.items():
        x = by.get(name)
        if x is None:
            rep.add("WARN", "bbmodel", f"{name} is in the geo but not in the .bbmodel (stale export?)")
            continue
        if x["parent"] != mb["parent"]:
            rep.add("WARN", "bbmodel", f"{name}: parent {x['parent']} in .bbmodel vs {mb['parent']} in geo (stale export?)")
        if mb["pivot"] and max(abs(x["pivot"][i] - mb["pivot"][i]) for i in range(3)) > 1e-3:
            rep.add("WARN", "bbmodel", f"{name}: pivot {fmt(x['pivot'])} in .bbmodel vs {fmt(mb['pivot'])} in geo")
    for name in by:
        if name not in bones:
            rep.add("WARN", "bbmodel", f"group {name} not in the exported geo (export again)")


def fmt(v):
    return "[" + ", ".join(f"{x:g}" for x in v) + "]"


def visual_checks(rep):
    rep.add("CHECK", "visual", "proportions/silhouette in Blockbench and in game (hitbox vs model)")
    rep.add("CHECK", "visual", "play every shared animation on THIS model: joints bend where the geometry bends,"
                               " no limb passes through the body, hands reach the sockets")
    rep.add("CHECK", "visual", "finisher contact against the player at the binding alignment distance")
    rep.add("CHECK", "visual", "texture painting, UV seams and parts (shell/core) hidden/shown in game")


# ------------------------------------------------------------------------------------------------ entrada

def validate(rig, geo=None, texture=None, anims=(), bbmodel=None, binding=None, assets_root=None,
             shared_anim=None, proportion=None, title=None):
    rep = Report(title or pathlib.Path(binding or geo or shared_anim or rig).name)
    c = read_json(rig, rep, "rig")
    if c is None or not check_contract(c, rep):
        return rep
    prop = proportion or [1, 1, 1]
    b = None
    if binding:
        b = read_json(binding, rep, "binding")
        if b is None:
            return rep
        res = check_binding(c, b, rep, assets_root, pathlib.Path(binding).parent)
        if res is None:
            return rep
        prop = proportion or res[0]
        files = res[1]
        geo = geo or files["geo"]
        texture = texture or files["texture"]
        if files["anim"] is not None and not anims:
            anims = [files["anim"]]
    bones = None
    if geo:
        g = read_json(geo, rep, "geo")
        if g is not None:
            bones, desc = geo_bones(g, rep)
            if bones is not None:
                check_geo(c, bones, desc, rep, prop, b)
                check_texture(desc, bones, texture and str(texture), rep)
                if bbmodel:
                    check_bbmodel(bbmodel, bones, rep)
    lengths = {}
    for a in anims:
        data = read_json(a, rep, "animations")
        if data is not None:
            lengths.update(check_animations(c, data, bones, rep, f"anim:{pathlib.Path(a).name}",
                                            extra_parts=[p.get("name") for p in (b or {}).get("parts", [])]))
    if b is not None:
        check_override_clips(b, lengths, rep)
        fam = resolve(c.get("animation_file"), assets_root, pathlib.Path(binding).parent)
        if bones is not None and fam is not None and fam.exists() and not shared_anim:
            data = read_json(fam, rep, "shared")
            if data is not None:  # as animacoes da familia neste modelo (bones que faltam / extras)
                check_animations(c, data, bones, rep, "family-anim", shared=True)
    if shared_anim:
        data = read_json(shared_anim, rep, "shared")
        if data is not None:
            check_animations(c, data, bones, rep, "shared", shared=True, expected_names=c.get("animations", {}))
    if geo:
        visual_checks(rep)
    return rep


def repo_reports():
    root = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources"
    rigs = sorted((root / "assets").glob("*/rigs/*.rig.json"))
    reps = []
    for rig in rigs:
        c = json.loads(rig.read_text(encoding="utf-8"))
        ns, _, path = c["animation_file"].partition(":")
        reps.append(validate(rig, shared_anim=root / "assets" / ns / path, title=f"{c['id']} (shared animations)"))
        for binding in sorted(rig.parent.glob("entities/*.json")):
            b = json.loads(binding.read_text(encoding="utf-8"))
            if b.get("rig") != c["id"]:
                continue
            reps.append(validate(rig, binding=binding, assets_root=root, title=b.get("entity")))
    return reps


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--rig", help="contract (.rig.json)")
    ap.add_argument("--geo")
    ap.add_argument("--texture")
    ap.add_argument("--anim", action="append", default=[], help="entity animation file (repeatable)")
    ap.add_argument("--bbmodel")
    ap.add_argument("--binding", help="binding manifest (rigs/entities/<entity>.json)")
    ap.add_argument("--assets-root", help="folder that contains assets/<ns>/... (resolves the binding ids)")
    ap.add_argument("--shared-anim", help="the family's shared animation file")
    ap.add_argument("--proportion", nargs=3, type=float)
    ap.add_argument("--repo", action="store_true", help="every rig and binding of this repository")
    ap.add_argument("--quiet", action="store_true", help="only errors and warnings")
    args = ap.parse_args(argv)
    if args.repo:
        reps = repo_reports()
    elif args.rig:
        reps = [validate(args.rig, args.geo, args.texture, args.anim, args.bbmodel, args.binding, args.assets_root,
                         args.shared_anim, args.proportion)]
    else:
        ap.error("--rig (or --repo) is required")
    for rep in reps:
        if args.quiet:
            rep.items = [i for i in rep.items if i[0] in ("ERROR", "WARN")]
        rep.print()
    return 1 if any(r.count("ERROR") for r in reps) else 0


if __name__ == "__main__":
    sys.exit(main())
