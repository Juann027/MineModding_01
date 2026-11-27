"""Biblioteca do kit de rigs do RespectCraft (Etapa 4.12): contrato, geometria de referencia, .geo.json, .bbmodel,
animacoes GeckoLib e PNG. So biblioteca padrao (o validador roda em qualquer Python 3.9+, inclusive fora do repo).

Convencoes (iguais as do contrato e do jogo):
  * coordenadas do Blockbench em px (16 px = 1 bloco), pes em y = 0, frente -Z, +X = direita do modelo;
  * o .geo.json (Bedrock/GeckoLib) guarda X invertido: pivo (-x, y, z), cubo origin x = -(from.x + size.x);
  * keyframes: o .bbmodel guarda o referencial do Blockbench e o .animation.json o do GeckoLib, com rotacao X e Y e
    posicao X invertidas (a mesma regra do "Export Animations" do plugin e de tools/player_anim/bbsync.flip).
"""
import base64
import hashlib
import json
import pathlib
import struct
import zlib

KIT_DIR = pathlib.Path(__file__).resolve().parent
REPO = KIT_DIR.parents[1]
ASSETS = REPO / "src/main/resources/assets/respectcraft"


def load_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def dumps(obj, indent=1):
    return json.dumps(obj, indent=indent, ensure_ascii=False) + "\n"


def uuid(*parts):
    h = hashlib.md5("|".join(parts).encode()).hexdigest()
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


def num(v):
    v = round(float(v), 4) + 0.0
    return int(v) if v.is_integer() else v


def flip(channel, vec):
    """Blockbench <-> GeckoLib (mesma conversao nos dois sentidos)."""
    x, y, z = vec
    if channel == "rotation":
        return [num(-x), num(-y), num(z)]
    if channel == "position":
        return [num(-x), num(y), num(z)]
    return [num(x), num(y), num(z)]


# ------------------------------------------------------------------------------------------------ PNG

def png(width, height, pixels):
    """pixels: funcao (x, y) -> (r, g, b, a). PNG RGBA sem dependencias."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels(x, y))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


# ------------------------------------------------------------------------------------------------ geometria

SWATCH = 8  # cada bone usa um quadrado 8x8 da textura (todas as faces); 64x64 = ate 64 cores


def reference_cubes():
    """Manequim de referencia (proporcao 1) em coordenadas do Blockbench: bone -> lista de (from, to)."""
    c = {
        "pelvis": [([-4, 10, -2], [4, 13, 2])],
        "torso": [([-4, 13, -2], [4, 23, 2])],
        "head": [([-4, 24, -4], [4, 32, 4])],
        "face": [([-0.5, 26.5, -4.6], [0.5, 27.5, -4])],
        "brow_right": [([0.5, 29.8, -4.3], [2.5, 30.4, -4])],
        "brow_left": [([-2.5, 29.8, -4.3], [-0.5, 30.4, -4])],
        "eye_right": [([1, 28, -4.2], [2, 29, -4])],
        "eye_left": [([-2, 28, -4.2], [-1, 29, -4])],
        "mouth": [([-1.5, 25.4, -4.2], [1.5, 26.2, -4])],
        "head_shell": [([-4.5, 31, -4.5], [4.5, 33, 4.5])],
        "torso_shell": [([-4.5, 14, -3], [4.5, 22, -2])],
        "torso_core": [([-2, 15, -2.8], [2, 20, -2.1])],
        "right_arm": [([4, 17, -1.5], [7, 23, 1.5])],
        "right_forearm": [([4.25, 12.5, -1.25], [6.75, 17, 1.25])],
        "right_hand": [([4.5, 10, -1.25], [6.5, 12.5, 1.25])],
        "right_thigh": [([0.5, 6.5, -1.75], [3.5, 12, 1.75])],
        "right_shin": [([0.75, 1.5, -1.5], [3.25, 6.5, 1.5])],
        "right_foot": [([0.5, 0, -2.5], [3.5, 1.5, 1.5])],
    }
    for name in list(c):
        if name.startswith("right_"):
            c["left_" + name[6:]] = [([-t[0], f[1], f[2]], [-f[0], t[1], t[2]]) for f, t in c[name]]
    return c


def scaled(v, prop):
    return [num(v[0] * prop[0]), num(v[1] * prop[1]), num(v[2] * prop[2])]


def build_model(contract, spec):
    """Modelo neutro (bones com pivo, pai e cubos em coordenadas do Blockbench) a partir do contrato + spec.

    spec: {"name", "proportion", "include_optional": [...] ou "all", "exclude": [...], "extra_bones": [{name,parent,
    pivot, cubes}], "sockets": bool, "colors": {bone: (r,g,b)}, "default_color"}.
    """
    prop = spec.get("proportion", [1, 1, 1])
    cubes = reference_cubes()
    include = spec.get("include_optional", "all")
    exclude = set(spec.get("exclude", []))
    bones = []
    for b in contract["bones"]:
        if b["name"] in exclude or (not b["required"] and include != "all" and b["name"] not in include):
            continue
        strict = b.get("strict", False)
        bones.append({"name": b["name"], "parent": b["parent"],
                      "pivot": list(b["pivot"]) if strict else scaled(b["pivot"], prop),
                      "cubes": [(scaled(f, prop), scaled(t, prop)) for f, t in cubes.get(b["name"], [])]})
    names = {b["name"] for b in bones}
    if spec.get("sockets", True):
        for s in contract["sockets"]:
            if s["bone"] not in names:
                continue
            base = next(b for b in bones if b["name"] == s["bone"])["pivot"]
            off = scaled(s["offset"], prop)
            bones.append({"name": "socket_" + s["name"], "parent": s["bone"],
                          "pivot": [num(base[i] + off[i]) for i in range(3)], "cubes": []})
    for e in spec.get("extra_bones", []):
        bones.append({"name": e["name"], "parent": e["parent"], "pivot": e["pivot"],
                      "cubes": [(f, t) for f, t in e.get("cubes", [])]})
    return {"name": spec["name"], "bones": bones, "colors": spec.get("colors", {}),
            "default_color": spec.get("default_color", (180, 180, 180)), "tex": 64}


def _swatches(model):
    """bone -> (u, v) do quadrado de cor (so bones com cubos)."""
    out, i = {}, 0
    for b in model["bones"]:
        if b["cubes"]:
            out[b["name"]] = ((i % (model["tex"] // SWATCH)) * SWATCH, (i // (model["tex"] // SWATCH)) * SWATCH)
            i += 1
    return out


def texture_png(model):
    sw = _swatches(model)
    colors = {}
    for name, (u, v) in sw.items():
        colors[(u // SWATCH, v // SWATCH)] = model["colors"].get(name, model["default_color"])

    def px(x, y):
        c = colors.get((x // SWATCH, y // SWATCH))
        if c is None:
            return (0, 0, 0, 0)
        edge = x % SWATCH in (0, SWATCH - 1) or y % SWATCH in (0, SWATCH - 1)
        k = 0.8 if edge else 1.0
        return (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)

    return png(model["tex"], model["tex"], px)


FACES = ("north", "east", "south", "west", "up", "down")


def geo_json(model, identifier):
    """.geo.json (formato 1.12.0, GeckoLib 4) com X invertido e UV por face (cada face = o quadrado do bone)."""
    sw = _swatches(model)
    bones = []
    top = 0
    for b in model["bones"]:
        px, py, pz = b["pivot"]
        out = {"name": b["name"], "pivot": [num(-px), num(py), num(pz)]}
        if b["parent"]:
            out["parent"] = b["parent"]
        if b["cubes"]:
            u, v = sw[b["name"]]
            out["cubes"] = []
            for f, t in b["cubes"]:
                size = [num(t[i] - f[i]) for i in range(3)]
                top = max(top, t[1])
                out["cubes"].append({"origin": [num(-(f[0] + size[0])), num(f[1]), num(f[2])], "size": size,
                                     "uv": {face: {"uv": [u, v], "uv_size": [SWATCH, SWATCH]} for face in FACES}})
        bones.append(out)
    height = max(top / 16.0, 1.0)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": model["tex"], "texture_height": model["tex"],
                        "visible_bounds_width": 3, "visible_bounds_height": num(height + 1),
                        "visible_bounds_offset": [0, num(height / 2 + 0.25), 0]},
        "bones": bones}]}


def bbmodel(model, texture_bytes, animations=None, texture_name="texture.png"):
    """.bbmodel 5.0 (formato GeckoLib) editavel: grupos = bones (origin = pivo), cubos com UV por face, textura
    embutida e, opcionalmente, animacoes (keyframes em referencial do Blockbench)."""
    sw = _swatches(model)
    groups, elements, by_name = [], [], {}
    for b in model["bones"]:
        g = {"name": b["name"], "uuid": uuid(model["name"], "g", b["name"]), "export": True, "locked": False, "scope": 0,
             "selected": False, "visibility": True, "origin": b["pivot"], "rotation": [0, 0, 0], "color": 0,
             "children": [], "reset": False, "shade": True, "mirror_uv": False, "autouv": 0, "isOpen": True,
             "primary_selected": False}
        groups.append(g)
        by_name[b["name"]] = g
    children = {b["name"]: [] for b in model["bones"]}
    roots = []
    for b in model["bones"]:
        for i, (f, t) in enumerate(b["cubes"]):
            u, v = sw[b["name"]]
            e = {"name": b["name"] if i == 0 else f"{b['name']}_{i}", "box_uv": False, "render_order": "default",
                 "locked": False, "export": True, "scope": 0, "allow_mirror_modeling": True, "from": f, "to": t,
                 "autouv": 0, "color": 0, "origin": b["pivot"],
                 "faces": {face: {"uv": [u, v, u + SWATCH, v + SWATCH], "texture": 0} for face in FACES},
                 "type": "cube", "uuid": uuid(model["name"], "e", b["name"], str(i))}
            elements.append(e)
            children[b["name"]].append(e["uuid"])
    nodes = {}
    for b in model["bones"]:
        nodes[b["name"]] = {"uuid": by_name[b["name"]]["uuid"], "isOpen": True, "children": list(children[b["name"]])}
    for b in model["bones"]:
        if b["parent"]:
            nodes[b["parent"]]["children"].append(nodes[b["name"]])
        else:
            roots.append(nodes[b["name"]])
    tex = {"name": texture_name, "path": "", "folder": "", "namespace": "", "id": "0", "group": "", "scope": 0,
           "width": model["tex"], "height": model["tex"], "uv_width": model["tex"], "uv_height": model["tex"],
           "particle": False, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
           "file_format": "png", "render_mode": "default", "render_sides": "auto", "wrap_mode": "limited",
           "pbr_channel": "color", "visible": True, "internal": True, "saved": True,
           "uuid": uuid(model["name"], "texture"),
           "source": "data:image/png;base64," + base64.b64encode(texture_bytes).decode()}
    anims = []
    for name, clip in (animations or {}).items():
        animators = {}
        for bone, chans in clip.get("bones", {}).items():
            if bone not in by_name:
                continue
            kfs = []
            for ch in ("rotation", "position", "scale"):
                for t, entry in chans.get(ch, {}).items():
                    x, y, z = flip(ch, entry["vector"])  # .animation.json -> Blockbench
                    kfs.append({"channel": ch, "data_points": [{"x": x, "y": y, "z": z}],
                                "uuid": uuid(model["name"], name, bone, ch, t), "time": float(t), "color": -1,
                                "interpolation": "linear"})
            animators[by_name[bone]["uuid"]] = {"name": bone, "type": "bone", "rotation_global": False,
                                                "quaternion_interpolation": False, "keyframes": kfs}
        timeline = clip.get("timeline", {})
        if timeline:
            animators["effects"] = {"name": "Effects", "type": "effect", "keyframes": [
                {"channel": "timeline", "data_points": [{"script": text}], "uuid": uuid(model["name"], name, "tl", t),
                 "time": float(t), "color": -1, "interpolation": "linear"} for t, text in timeline.items()]}
        anims.append({"uuid": uuid(model["name"], "anim", name), "name": name,
                      "loop": "loop" if clip.get("loop") else "once", "override": False,
                      "length": clip["animation_length"], "snapping": 20, "selected": False, "saved": False,
                      "scope": 0, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                      "animators": animators})
    return {"meta": {"format_version": "5.0", "model_format": "geckolib_model", "box_uv": False},
            "name": model["name"], "model_identifier": model["name"], "visible_box": [3, 3.5, 1.25],
            "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
            "unhandled_root_fields": {}, "geckolib_modid": "respectcraft", "geckolib_filepath_cache": {},
            "resolution": {"width": model["tex"], "height": model["tex"]}, "elements": elements, "groups": groups,
            "outliner": roots, "textures": [tex], "animations": anims, "geckolib_model_type": "Entity"}


# ------------------------------------------------------------------------------------------------ .bbmodel -> modelo

def model_from_bbmodel(bb):
    """Le bones (nome, pai, pivo em coordenadas do Blockbench) e locators de um .bbmodel."""
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    elements = {e["uuid"]: e for e in bb.get("elements", [])}
    bones, locators, dup = [], {}, []
    seen = set()

    def walk(nodes, parent):
        for n in nodes:
            if isinstance(n, str):
                e = elements.get(n)
                if e and e.get("type") == "locator" and parent:
                    locators[e["name"]] = (parent, e.get("position", e.get("from", [0, 0, 0])))
                continue
            g = groups.get(n["uuid"], n)
            name = g.get("name")
            if name in seen:
                dup.append(name)
            seen.add(name)
            bones.append({"name": name, "parent": parent, "pivot": g.get("origin", [0, 0, 0]),
                          "cubes": sum(1 for c in n.get("children", []) if isinstance(c, str)
                                       and elements.get(c, {}).get("type", "cube") == "cube")})
            walk(n.get("children", []), name)

    walk(bb.get("outliner", []), None)
    return bones, locators, dup
