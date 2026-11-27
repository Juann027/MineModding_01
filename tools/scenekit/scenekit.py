"""Finisher Scene Authoring Kit do RespectCraft: compoe dois rigs num unico projeto do Blockbench (GeckoLib Animated
Model) e separa as animacoes de volta por participante. So biblioteca padrao (roda fora do repositorio, Python 3.9+).

Generaliza o que ja existia: a cena dupla do Combo (tools/player_anim/bbmodel.build_scene: segundo rig com prefixo
"Alvo_" girado 180 graus por um grupo de palco em volta do ponto medio) e a separacao por prefixo do bbsync.py
(conversao de eixos bbsync.flip, a mesma do "Export Animations" do plugin GeckoLib). Nada aqui roda no jogo.

Referencial da cena = o do ATTACKER (px do rig dele; frente -Z, +X = direita, pes em y = 0):
  * jogador: o PlayerRenderer vanilla desenha o rig a 15/16, logo 1 bloco = 16 / 0.9375 = 17,0667 px;
  * CustomRig: 1 bloco = 16 / render_scale px (RigGeoRenderer);
  * a vitima fica a `distance` blocos a frente e `lateral` blocos a direita do executor, olhando para ele
    (yaw + 180 + yaw_offset), como FinisherTestMechanic.start/FinisherParticipants.stage; se a escala em px por bloco
    dos dois difere, um grupo de palco da vitima recebe um keyframe de escala constante (calibracao, nao exportado).

Nomes na cena = prefixo do participante + nome original (mapa explicito no manifesto). Grupos de palco (STAGE_*) nao
sao bones dos rigs: nunca vao para os clips exportados.
"""
import base64
import copy
import hashlib
import json
import math
import pathlib
import struct
import zlib

FORMAT = "respectcraft:finisher_scene"
FORMAT_VERSION = 1
TICKS_PER_SECOND = 20
PLAYER_RENDER_SCALE = 0.9375  # PlayerRenderer.scale (vanilla)
STAGE_DISTANCE = 1.15         # FinisherParticipants.STAGE_DISTANCE
STAGE_GAP = 0.25              # FinisherParticipants.STAGE_GAP
PLAYER_WIDTH = 0.6
STAGE_ATTACKER = "STAGE_ATTACKER"
STAGE_VICTIM = "STAGE_VICTIM"
STAGE_VICTIM_SCALE = "STAGE_VICTIM_SCALE"
STAGE_GROUPS = (STAGE_ATTACKER, STAGE_VICTIM, STAGE_VICTIM_SCALE)
ROLES = ("attacker", "victim")
KINDS = ("player", "custom_rig")


class SceneError(Exception):
    pass


def load_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def dumps(obj, indent=1):
    return json.dumps(obj, indent=indent, ensure_ascii=False) + "\n"


def uuid(*parts):
    h = hashlib.md5("|".join(str(p) for p in parts).encode()).hexdigest()
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


def num(v):
    v = round(float(v), 6) + 0.0
    return int(v) if v.is_integer() else v


def flip(channel, vec):
    """Blockbench <-> GeckoLib (a mesma regra de tools/player_anim/bbsync.flip, nos dois sentidos)."""
    x, y, z = vec
    neg = (lambda v: -v if isinstance(v, (int, float)) else "-(" + str(v) + ")")
    if channel == "rotation":
        return [neg(x), neg(y), z]
    if channel == "position":
        return [neg(x), y, z]
    return [x, y, z]


def time_key(t):
    return f"{float(t):.4f}".rstrip("0").rstrip(".") or "0"


def ticks_of(seconds):
    t = float(seconds) * TICKS_PER_SECOND
    return int(round(t)), abs(t - round(t)) < 1e-4


# ------------------------------------------------------------------------------------------------ PNG (decode/encode)

def png_decode(data):
    """PNG 8 bits RGB/RGBA/cinza/paleta sem entrelacamento -> (w, h, linhas RGBA). Erro claro no resto."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SceneError("texture is not a PNG")
    pos, idat, plte, trns = 8, b"", None, None
    w = h = depth = ctype = interlace = None
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif tag == b"PLTE":
            plte = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
    if depth != 8 or interlace:
        raise SceneError(f"PNG with bit depth {depth}/interlace {interlace} not supported (use 8-bit, no interlace)")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * channels
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        out = bytearray()
        for x in range(w):
            px = line[x * channels:(x + 1) * channels]
            if ctype == 6:
                out += px
            elif ctype == 2:
                out += px + b"\xff"
            elif ctype == 0:
                out += bytes((px[0], px[0], px[0], 255))
            elif ctype == 4:
                out += bytes((px[0], px[0], px[0], px[1]))
            else:
                k = px[0]
                alpha = trns[k] if trns and k < len(trns) else 255
                out += plte[k * 3:k * 3 + 3] + bytes((alpha,))
        rows.append(bytes(out))
    return w, h, rows


def png_encode(w, h, rows):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, body):
        c = struct.pack(">I", len(body)) + tag + body
        return c + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def texture_png(tex):
    src = tex.get("source", "")
    if not src.startswith("data:image/png;base64,"):
        raise SceneError(f"texture {tex.get('name')} is not embedded in the .bbmodel (save it with the texture inside)")
    return base64.b64decode(src.split(",", 1)[1])


# ------------------------------------------------------------------------------------------------ participantes

def px_per_block(part):
    """Px do rig por bloco do mundo (o que o renderer do jogo usa)."""
    if part["kind"] == "player":
        return 16.0 / PLAYER_RENDER_SCALE
    return 16.0 / float(part.get("render_scale", 1.0))


def stage_distance(spec):
    """Distancia do palco em blocos, como FinisherParticipants.stageDistance."""
    st = spec.get("stage", {})
    if st.get("distance") is not None:
        d = float(st["distance"])
        if not 0.3 <= d <= 6.0:
            raise SceneError(f"stage distance {d} outside 0.3..6 (EntityFinisherClip.Alignment)")
        return d
    wa = float(spec["attacker"].get("hitbox_width", PLAYER_WIDTH))
    wv = float(spec["victim"].get("hitbox_width", PLAYER_WIDTH))
    return max(STAGE_DISTANCE, (wa + wv) / 2.0 + STAGE_GAP)


def resolve_alignment(spec, base):
    """Palco efetivo, na ordem do FinisherParticipants: stage da spec > alinhamento do binding > da familia > auto."""
    st = {"lateral": 0.0, "yaw_offset": 0.0, "distance": None, "source": "auto"}
    v = spec["victim"]
    for src in ("rig", "binding"):  # o binding vence a familia
        if v.get(src):
            a = load_json(base / v[src]).get("alignment") or {}
            if a.get("distance") is not None or a.get("lateral") or a.get("yaw_offset"):
                st = {"distance": a.get("distance"), "lateral": a.get("lateral", 0.0),
                      "yaw_offset": a.get("yaw_offset", 0.0), "source": src}
    if spec.get("stage"):
        st.update({k: spec["stage"][k] for k in ("distance", "lateral", "yaw_offset") if k in spec["stage"]})
        st["source"] = "spec"
    if abs(float(st["lateral"])) > 3:
        raise SceneError("stage lateral outside -3..3")
    st["distance_blocks"] = stage_distance(dict(spec, stage=st))
    return st


def rig_parts(spec, base):
    """Partes (contrato + binding) e sockets da vitima CustomRig, para o manifesto (o artista ve o que alternar)."""
    v = spec["victim"]
    parts, sockets = [], []
    for src in ("rig", "binding"):
        if v.get(src):
            m = load_json(base / v[src])
            parts += m.get("parts", [])
            sockets += m.get("sockets", [])
    return parts, sockets


# ------------------------------------------------------------------------------------------------ composicao

def _group_index(model):
    return {g["uuid"]: g for g in model.get("groups", [])}


def _check_unique(names, what):
    seen, dup = set(), set()
    for n in names:
        (dup if n in seen else seen).add(n)
    if dup:
        raise SceneError(f"{what}: repeated names {sorted(dup)} (bone names must be unique per rig)")


def _stage_group(name, uuid_, origin, rotation, color):
    return {"name": name, "uuid": uuid_, "export": False, "locked": False, "scope": 0, "selected": False,
            "visibility": True, "origin": [num(v) for v in origin], "rotation": [num(v) for v in rotation],
            "color": color, "children": [], "reset": False, "shade": True, "mirror_uv": False, "autouv": 0,
            "isOpen": True, "primary_selected": False}


def _rotation_pivot(target_x, target_z, yaw_deg):
    """Origem P de um grupo girado por yaw (em Y) que leva o ponto (0,0,0) para (target_x, 0, target_z):
    P - R P = T. Grupos do Blockbench nao transladam; e o truque do Combo (ponto medio para 180 graus) generalizado."""
    a = math.radians(yaw_deg)
    c, s = math.cos(a), math.sin(a)
    # Rotacao em Y (sentido do three.js): x' = c x + s z ; z' = -s x + c z.  (I - R) P = T
    m11, m12, m21, m22 = 1 - c, -s, s, 1 - c
    det = m11 * m22 - m12 * m21
    if abs(det) < 1e-9:
        if abs(target_x) > 1e-6 or abs(target_z) > 1e-6:
            raise SceneError("victim yaw makes the stage rotation singular (yaw_offset = -180): not supported")
        return 0.0, 0.0
    px = (target_x * m22 - m12 * target_z) / det
    pz = (m11 * target_z - m21 * target_x) / det
    return px, pz


def _participant_textures(model, role):
    out = []
    for t in model.get("textures", []):
        png = texture_png(t)
        w, h, rows = png_decode(png)
        uvw, uvh = t.get("uv_width", model["resolution"]["width"]), t.get("uv_height", model["resolution"]["height"])
        out.append({"tex": t, "png": png, "w": w, "h": h, "rows": rows, "uv": (uvw, uvh), "role": role})
    if not out:
        raise SceneError(f"{role}: .bbmodel without textures")
    return out


def _atlas(texsets):
    """Empilha as texturas (1 px = 1 unidade de UV) num atlas so do projeto de autoria. -> (png, w, h, offsets)."""
    for t in texsets:
        if (t["w"], t["h"]) != tuple(t["uv"]):
            raise SceneError(f"texture {t['tex'].get('name')} has {t['w']}x{t['h']} px for UV {t['uv']}: atlas needs"
                             " 1 px per UV unit (use --textures multi)")
    width = max(t["w"] for t in texsets)
    rows, offsets, y = [], [], 0
    for t in texsets:
        offsets.append((0, y))
        for r in t["rows"]:
            rows.append(r + b"\x00\x00\x00\x00" * (width - t["w"]))
        y += t["h"]
    return png_encode(width, y, rows), width, y, offsets


def compose(spec, base_dir=".", textures="atlas"):
    """spec (dict, ver CONTRACT.md) -> (projeto .bbmodel, manifesto da cena). Nao toca nos arquivos de origem."""
    base = pathlib.Path(base_dir)
    if spec.get("format") != FORMAT or spec.get("format_version") != FORMAT_VERSION:
        raise SceneError(f"spec must have format {FORMAT} v{FORMAT_VERSION}")
    if textures not in ("atlas", "multi"):
        raise SceneError("textures must be atlas or multi")
    name = spec["name"]
    duration = int(spec["duration_ticks"])
    if duration <= 0:
        raise SceneError("duration_ticks must be positive")
    parts = {r: spec[r] for r in ROLES}
    if parts["attacker"]["kind"] != "player":
        raise SceneError("this kit authors player attackers (ATTACKER = player); victim may be player or custom_rig")
    for r, p in parts.items():
        if p["kind"] not in KINDS:
            raise SceneError(f"{r}: kind must be one of {KINDS}")
    pa, pv = parts["attacker"]["prefix"], parts["victim"]["prefix"]
    if pa == pv or pa.startswith(pv) or pv.startswith(pa):
        raise SceneError(f"prefixes {pa!r} and {pv!r} must be distinct and not prefix of each other")
    if any(n.startswith(p) for n in STAGE_GROUPS for p in (pa, pv) if p):
        raise SceneError("prefixes may not start the stage group names")

    for r, p in parts.items():
        if p["kind"] == "custom_rig" and p.get("binding") and "render_scale" not in p:
            p["render_scale"] = load_json(base / p["binding"]).get("render_scale", 1.0)
    stage = resolve_alignment(spec, base)
    ref = px_per_block(parts["attacker"])
    dist_px, lat_px = stage["distance_blocks"] * ref, float(stage["lateral"]) * ref
    victim_yaw = 180.0 + float(stage["yaw_offset"])
    victim_scale = ref / px_per_block(parts["victim"])
    pxp, pzp = _rotation_pivot(lat_px, -dist_px, victim_yaw)

    srcs = {r: load_json(base / parts[r]["bbmodel"]) for r in ROLES}
    for r, m in srcs.items():
        if m.get("meta", {}).get("model_format") != "geckolib_model":
            raise SceneError(f"{r}: {parts[r]['bbmodel']} is not a GeckoLib Animated Model project")
        _check_unique([g["name"] for g in m.get("groups", [])], f"{r} bones")

    texsets = {r: _participant_textures(srcs[r], r) for r in ROLES}
    all_tex = texsets["attacker"] + texsets["victim"]
    if textures == "atlas":
        atlas_png, aw, ah, offsets = _atlas(all_tex)
        uv_shift = {id(t["tex"]): off for t, off in zip(all_tex, offsets)}
        resolution = {"width": aw, "height": ah}
        out_textures = [{"path": "", "name": f"{name}_atlas.png", "folder": "", "namespace": "", "id": "0",
                         "group": "", "width": aw, "height": ah, "uv_width": aw, "uv_height": ah,
                         "particle": False, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
                         "render_mode": "default", "render_sides": "auto", "pbr_channel": "color",
                         "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
                         "visible": True, "internal": True, "saved": False, "uuid": uuid(name, "atlas"),
                         "relative_path": "", "source": "data:image/png;base64," + base64.b64encode(atlas_png).decode()}]
    else:
        resolution = dict(srcs["attacker"]["resolution"])
        out_textures = []
        for i, t in enumerate(all_tex):
            nt = copy.deepcopy(t["tex"])
            nt["uuid"] = uuid(name, t["role"], "tex", nt.get("uuid", i))
            nt["id"] = str(i)
            nt["name"] = parts[t["role"]]["prefix"] + nt.get("name", f"texture_{i}.png")
            nt.setdefault("uv_width", t["uv"][0])
            nt.setdefault("uv_height", t["uv"][1])
            out_textures.append(nt)

    model = copy.deepcopy(srcs["attacker"])
    model["name"] = name
    model["model_identifier"] = name
    model["meta"] = {"format_version": "5.0", "model_format": "geckolib_model", "box_uv": False}
    model["resolution"] = resolution
    model["geckolib_filepath_cache"] = {}
    model["textures"] = out_textures
    model["elements"], model["groups"], model["outliner"], model["animations"] = [], [], [], []

    stage_uuids = {n: uuid(name, "stage", n) for n in STAGE_GROUPS}
    model["groups"].append(_stage_group(STAGE_ATTACKER, stage_uuids[STAGE_ATTACKER], [0, 0, 0], [0, 0, 0], 2))
    model["groups"].append(_stage_group(STAGE_VICTIM, stage_uuids[STAGE_VICTIM], [pxp, 0, pzp], [0, victim_yaw, 0], 1))
    use_scale_group = abs(victim_scale - 1.0) > 1e-6
    if use_scale_group:
        model["groups"].append(_stage_group(STAGE_VICTIM_SCALE, stage_uuids[STAGE_VICTIM_SCALE], [0, 0, 0], [0, 0, 0], 1))

    bones, mapping_uuid, tex_base = {}, {}, 0
    manifest_parts = {}
    for role in ROLES:
        src, p = srcs[role], parts[role]
        prefix = p["prefix"]
        remap = {}
        for g in src.get("groups", []):
            remap[g["uuid"]] = uuid(name, role, g["uuid"])
        for e in src.get("elements", []):
            remap[e["uuid"]] = uuid(name, role, e["uuid"])
        for g in src.get("groups", []):
            g2 = copy.deepcopy(g)
            g2["uuid"] = remap[g["uuid"]]
            g2["name"] = prefix + g["name"]
            g2.pop("selected", None)
            bones[g2["name"]] = {"role": role, "bone": g["name"]}
            mapping_uuid[g2["uuid"]] = g2["name"]
            model["groups"].append(g2)
        n_tex = len(texsets[role])
        for e in src.get("elements", []):
            e2 = copy.deepcopy(e)
            e2["uuid"] = remap[e["uuid"]]
            e2["name"] = prefix + e.get("name", "cube")
            faces = e2.get("faces", {})
            shift = None
            for f in faces.values():
                ti = f.get("texture")
                if isinstance(ti, int) and not isinstance(ti, bool):
                    if not 0 <= ti < n_tex:
                        raise SceneError(f"{role}: element {e.get('name')} uses texture {ti} that does not exist")
                    tex = texsets[role][ti]
                    if textures == "atlas":
                        shift = uv_shift[id(tex["tex"])]
                        f["texture"] = 0
                        if "uv" in f:
                            f["uv"] = [num(f["uv"][0] + shift[0]), num(f["uv"][1] + shift[1]),
                                       num(f["uv"][2] + shift[0]), num(f["uv"][3] + shift[1])]
                    else:
                        f["texture"] = ti + tex_base
            if textures == "atlas" and e2.get("box_uv"):
                if shift is None:
                    shift = uv_shift[id(texsets[role][0]["tex"])]
                if "uv_offset" in e2 or shift != (0, 0):
                    off = e2.get("uv_offset", [0, 0])  # sem uv_offset o Blockbench usa [0, 0]
                    e2["uv_offset"] = [num(off[0] + shift[0]), num(off[1] + shift[1])]
            model["elements"].append(e2)
        tex_base += n_tex

        def clone(node):
            if isinstance(node, str):
                if node not in remap:
                    raise SceneError(f"{role}: outliner references unknown uuid {node}")
                return remap[node]
            n = {k: v for k, v in node.items() if k != "children"}
            n["uuid"] = remap[node["uuid"]]
            n["children"] = [clone(c) for c in node.get("children", [])]
            return n

        roots = [clone(n) for n in src.get("outliner", [])]
        if role == "attacker":
            model["outliner"].append({"uuid": stage_uuids[STAGE_ATTACKER], "isOpen": True, "children": roots})
        else:
            inner = roots
            if use_scale_group:
                inner = [{"uuid": stage_uuids[STAGE_VICTIM_SCALE], "isOpen": True, "children": roots}]
            model["outliner"].append({"uuid": stage_uuids[STAGE_VICTIM], "isOpen": True, "children": inner})
        root_bones = [mapping_uuid[r["uuid"]] for r in roots if isinstance(r, dict)]
        manifest_parts[role] = {
            "kind": p["kind"], "prefix": prefix, "source_bbmodel": p["bbmodel"],
            "source_sha256": hashlib.sha256((base / p["bbmodel"]).read_bytes()).hexdigest(),
            "entity": p.get("entity", "minecraft:player" if p["kind"] == "player" else None),
            "rig": p.get("rig_id"), "geo": p.get("geo"), "render_scale": p.get("render_scale", 1.0),
            "px_per_block": num(px_per_block(p)), "root_bones": [bones[b]["bone"] for b in root_bones],
            "bone_count": len(src.get("groups", [])), "element_count": len(src.get("elements", [])),
            "texture_count": len(texsets[role]), "export": p["export"],
        }
    _check_unique([g["name"] for g in model["groups"]], "scene bones")

    manifest = {
        "format": FORMAT + "_manifest", "format_version": FORMAT_VERSION, "name": name,
        "description": spec.get("description", ""), "duration_ticks": duration,
        "animation": spec.get("animation", "scene." + name),
        "textures": textures,
        "stage": {"distance_blocks": num(stage["distance_blocks"]), "lateral_blocks": num(stage["lateral"]),
                  "yaw_offset": num(stage["yaw_offset"]), "source": stage["source"],
                  "reference": "attacker px (1 block = %s px)" % num(ref),
                  "victim_anchor_px": [num(lat_px), 0, num(-dist_px)], "victim_yaw": num(victim_yaw),
                  "victim_scale": num(victim_scale), "groups": {n: stage_uuids[n] for n in STAGE_GROUPS
                                                                if n != STAGE_VICTIM_SCALE or use_scale_group}},
        "participants": manifest_parts,
        "bones": bones,
        "contacts": spec.get("contacts", []),
    }
    if parts["victim"]["kind"] == "custom_rig":
        rp, sk = rig_parts(spec, base)
        manifest["victim_parts"] = [{"name": x["name"], "default_visible": x.get("default_visible", True),
                                     "groups": [pv + b for b in x.get("bones", [])]} for x in rp]
        manifest["victim_sockets"] = [{"name": s["name"], "bone": pv + s["bone"], "offset": s.get("offset", [0, 0, 0])}
                                      for s in sk]
    for a in spec.get("animations", [manifest["animation"]]):
        model["animations"].append(scene_animation(manifest, [], a))
    model["respectcraft_scene"] = {"manifest": name + ".scene.json", "version": FORMAT_VERSION}
    return model, manifest


def scene_animation(manifest, animators, name=None):
    """Animacao da cena (vazia + calibracao de escala) para o projeto conjunto."""
    name = name or manifest["animation"]
    length = manifest["duration_ticks"] / TICKS_PER_SECOND
    anim = {"uuid": uuid(manifest["name"], "anim", name), "name": name,
            "loop": "once", "override": False, "length": num(length), "snapping": 20, "selected": False,
            "saved": False, "scope": 0, "anim_time_update": "", "blend_weight": "", "start_delay": "",
            "loop_delay": "", "animators": {}}
    groups = manifest["stage"]["groups"]
    if STAGE_VICTIM_SCALE in groups:
        s = manifest["stage"]["victim_scale"]
        anim["animators"][groups[STAGE_VICTIM_SCALE]] = {
            "name": STAGE_VICTIM_SCALE, "type": "bone", "rotation_global": False, "quaternion_interpolation": False,
            "keyframes": [{"channel": "scale", "data_points": [{"x": s, "y": s, "z": s}],
                           "uuid": uuid(manifest["name"], name, "calib"), "time": 0, "color": -1, "interpolation": "linear"}]}
    for a in animators:
        anim["animators"].update(a)
    return anim


# ------------------------------------------------------------------------------------------------ compilacao (.bbmodel -> export)

class Unsupported(Exception):
    pass


def _value(v):
    if isinstance(v, (int, float)):
        return float(v)
    try:
        return float(str(v).strip() or 0)
    except ValueError:
        return str(v)


def _compile_kf(kf):
    pts = kf.get("data_points", [])
    interp = kf.get("interpolation", "linear")
    if len(pts) != 1:
        raise Unsupported(f"{len(pts)} data points")
    elif interp != "linear":
        raise Unsupported(f"interpolation '{interp}' (export from Blockbench to keep it)")
    p = pts[0]
    entry = {"vector": [num(v) if isinstance(v, float) else v
                        for v in flip(kf["channel"], [_value(p.get(a, 0)) for a in "xyz"])]}
    if kf.get("easing") and kf["easing"] != "linear":
        entry["easing"] = kf["easing"]
        if kf.get("easingArgs"):
            entry["easingArgs"] = kf["easingArgs"]
    return entry


def compile_bbmodel(model):
    """Reproduz o "Export Animations" do plugin GeckoLib para o subconjunto que o bbsync.py confere (keyframes
    lineares com easing, um ponto). -> (export, problemas). Keyframes fora disso viram problema (nunca somem):
    para eles exporte pelo proprio Blockbench e passe o JSON ao split."""
    out, problems = {}, []
    for anim in model.get("animations", []):
        clip = {"animation_length": anim.get("length", 0), "bones": {}}
        if anim.get("loop") == "loop":
            clip["loop"] = True
        elif anim.get("loop") == "hold":
            clip["loop"] = "hold_on_last_frame"
        for key, animator in anim.get("animators", {}).items():
            if animator.get("type", "bone") == "effect" or key == "effects":
                for kf in animator.get("keyframes", []):
                    t = time_key(kf["time"])
                    ch = kf.get("channel")
                    if ch == "timeline":
                        lines = [d.get("script", "") for d in kf.get("data_points", []) if d.get("script", "").strip()]
                        prev = clip.setdefault("timeline", {}).get(t)
                        text = "\n".join(([prev] if prev else []) + lines)
                        clip["timeline"][t] = text
                    elif ch == "sound":
                        clip.setdefault("sound_effects", {})[t] = {"effect": kf["data_points"][0].get("effect", "")}
                    elif ch == "particle":
                        d = kf["data_points"][0]
                        clip.setdefault("particle_effects", {})[t] = {"effect": d.get("effect", ""),
                                                                      "locator": d.get("locator", "")}
                continue
            name = animator.get("name", key)
            for kf in animator.get("keyframes", []):
                ch = kf.get("channel")
                if ch not in ("rotation", "position", "scale"):
                    problems.append(f"{anim['name']}/{name}: channel {ch} not exported")
                    continue
                try:
                    entry = _compile_kf(kf)
                except Unsupported as why:
                    problems.append(f"{anim['name']}/{name}/{ch}@{kf.get('time')}: {why}")
                    continue
                clip["bones"].setdefault(name, {}).setdefault(ch, {})[time_key(kf["time"])] = entry
        out[anim["name"]] = clip
    return {"format_version": "1.8.0", "animations": out}, problems


# ------------------------------------------------------------------------------------------------ eventos da cena

# Cada linha do timeline (aba Effects do Blockbench, canal "timeline") = um evento com dono explicito:
#   @attacker hit 2                        -> Finishers.EVENT_HIT no clip do executor (forca 1..3)
#   @victim fall 2,0.4                     -> Finishers.EVENT_FALL no clip da vitima
#   @attacker shake 0.4,6 / @victim fov 1.1,10  -> PlayerAnimations.EVENT_CAMERA_SHAKE/FOV (camera DAQUELE participante)
#   @attacker sound respectcraft:finisher.whoosh -> PlayerAnimations.EVENT_SOUND (um dono so: o servidor toca para todos)
#   @victim rig:hide:shell;rig:show:core   -> keyframe de timeline do clip do CustomRig (RigVisuals, servidor)
#   @victim calamidade:<efeito>            -> keyframe de timeline do CustomRig (GeoFxKeyframes, servidor)
#   @scene camera yaw,dist,height,look,fov,roll -> CameraTrack.Key da cena (um por instante)
#   @scene execute                         -> golpe final real (FinisherExecution): SO no ultimo tick
#   @<dono> note <tipo> <texto>            -> documentacao (agarrao, reacao, contato...): vai so para o relatorio
EVENT_KINDS = {
    "hit": {"owners": ("attacker",), "java": "Finishers.EVENT_HIT", "data": "force"},
    "fall": {"owners": ("victim",), "java": "Finishers.EVENT_FALL", "data": "fall"},
    "shake": {"owners": ("attacker", "victim"), "java": "PlayerAnimations.EVENT_CAMERA_SHAKE", "data": "pair"},
    "fov": {"owners": ("attacker", "victim"), "java": "PlayerAnimations.EVENT_CAMERA_FOV", "data": "pair"},
    "sound": {"owners": ("attacker", "victim"), "java": "PlayerAnimations.EVENT_SOUND", "data": "id"},
    "camera": {"owners": ("scene",), "java": "CameraTrack.Key", "data": "camera"},
    "execute": {"owners": ("scene",), "java": "FinisherExecution (scene end)", "data": "none"},
    "note": {"owners": ("attacker", "victim", "scene"), "java": None, "data": "text"},
}
NOTE_TYPES = ("grab", "release", "reaction", "contact", "expression", "crack", "pulp", "hide", "camera", "audio",
              "vfx", "movement", "other")


def _rl(s):
    ns, _, path = s.partition(":")
    ok = lambda t: t and all(c.isdigit() or c.islower() or c in "_-./" for c in t)  # noqa: E731
    return bool(path) and ok(ns) and ok(path)


def parse_timeline(timeline, manifest, length):
    """-> (eventos, problemas). Nada e descartado em silencio: o que nao entende vira ERROR."""
    events, problems = [], []
    kinds = {r: manifest["participants"][r]["kind"] for r in ROLES}
    duration = manifest["duration_ticks"]
    for t, text in sorted((timeline or {}).items(), key=lambda kv: float(kv[0])):
        tick, exact = ticks_of(t)
        if not exact:
            problems.append(("ERROR", f"timeline @{t}s is between ticks (snap the keyframe to 20 fps)"))
        if isinstance(text, list):
            text = "\n".join(text)
        for raw in str(text).replace("\r", "").split("\n"):
            line = raw.strip()
            if not line or line.startswith("//"):
                continue
            where = f"timeline @{t}s '{line}'"
            if not line.startswith("@"):
                problems.append(("ERROR", f"{where}: no owner (start the line with @attacker, @victim or @scene)"))
                continue
            owner, _, rest = line[1:].partition(" ")
            rest = rest.strip()
            if owner not in ROLES + ("scene",):
                problems.append(("ERROR", f"{where}: unknown owner @{owner}"))
                continue
            if rest.startswith("rig:") or rest.startswith("calamidade:"):
                if owner == "scene" or kinds.get(owner) != "custom_rig":
                    problems.append(("ERROR", f"{where}: rig:/calamidade: instructions belong to a custom_rig participant"
                                              " (players use hit/fall markers and the FinisherFxDef)"))
                    continue
                for instr in [i.strip() for i in rest.split(";") if i.strip()]:
                    ok = _valid_instruction(instr, manifest)
                    if ok is not True:
                        problems.append(("ERROR", f"{where}: {ok}"))
                        continue
                    events.append({"tick": tick, "time": t, "owner": owner, "kind": "instruction", "data": instr,
                                   "runtime": "keyframe timeline (" + instr.split(":")[0] + ")"})
                continue
            kind, _, data = rest.partition(" ")
            data = data.strip()
            spec = EVENT_KINDS.get(kind)
            if spec is None:
                problems.append(("ERROR", f"{where}: unknown event '{kind}' (see CONTRACT.md)"))
                continue
            if owner not in spec["owners"]:
                problems.append(("ERROR", f"{where}: '{kind}' belongs to {'/'.join('@' + o for o in spec['owners'])}"))
                continue
            if owner in ROLES and spec["java"] and spec["java"].startswith(("Finishers", "PlayerAnimations")) \
                    and kinds[owner] != "player" and kind not in ("hit", "fall"):
                problems.append(("WARN", f"{where}: {kind} on a custom_rig participant goes to the scene's victim"
                                         " PlayerAnimationDef (the mob has no camera)"))
            err = _check_data(kind, spec["data"], data, tick, duration)
            if err:
                problems.append(("ERROR", f"{where}: {err}"))
                continue
            events.append({"tick": tick, "time": t, "owner": owner, "kind": kind, "data": data,
                           "runtime": spec["java"] or "documentation only"})
    # dono unico: o mesmo evento nos dois participantes dispararia duas vezes
    seen = {}
    for e in events:
        if e["kind"] in ("note",):
            continue
        key = (e["tick"], e["kind"], e["data"])
        if key in seen and seen[key] != e["owner"] and e["kind"] in ("sound", "hit", "fall", "instruction"):
            problems.append(("ERROR", f"tick {e['tick']}: {e['kind']} {e['data']} on @{seen[key]} and @{e['owner']}"
                                      " (one owner per event, or it fires twice)"))
        seen.setdefault(key, e["owner"])
    cams = [e["tick"] for e in events if e["kind"] == "camera"]
    if len(cams) != len(set(cams)):
        problems.append(("ERROR", "two camera keys on the same tick"))
    return events, problems


def _valid_instruction(instr, manifest):
    if instr.startswith("calamidade:"):
        return True if _rl(instr[len("calamidade:"):]) else f"calamidade:<effect id> invalid ({instr})"
    bits = instr.split(":")
    if bits == ["rig", "reset"]:
        return True
    if len(bits) == 3 and bits[1] in ("hide", "show"):
        names = {p["name"] for p in manifest.get("victim_parts", [])}
        if bits[2] not in names:
            return f"part '{bits[2]}' is not in the victim contract/binding ({sorted(names)})"
        return True
    return f"unknown rig instruction {instr} (rig:hide:<part>, rig:show:<part>, rig:reset)"


def _check_data(kind, shape, data, tick, duration):
    try:
        if shape == "force":
            if data not in ("1", "2", "3"):
                return "hit force must be 1, 2 or 3"
        elif shape == "fall":
            f, r = data.split(",")
            if f.strip() not in ("1", "2", "3"):
                return "fall force must be 1, 2 or 3"
            float(r)
        elif shape == "pair":
            a, b = data.split(",")
            float(a)
            int(b)
        elif shape == "id":
            if not _rl(data):
                return f"'{data}' is not a resource id"
        elif shape == "camera":
            v = [float(x) for x in data.split(",")]
            if len(v) != 6:
                return "camera needs yaw,distance,height,look_height,fov,roll"
        elif shape == "none":
            if data:
                return "execute takes no data"
            if tick != duration:
                return f"the real execution happens at the end of the scene (tick {duration}), not at tick {tick}"
        elif shape == "text":
            if data.split(" ")[0] not in NOTE_TYPES:
                return f"note type must be one of {NOTE_TYPES}"
    except ValueError:
        return f"bad data '{data}' for {kind}"
    return None


def java_snippets(events, manifest):
    """Linhas prontas para o registro do Finisher (o Claude Code integra; nada disto vira keyframe de jogador)."""
    out = {"attacker": [], "victim": [], "camera": []}
    for e in events:
        if e["kind"] in ("hit", "fall", "shake", "fov", "sound"):
            const = EVENT_KINDS[e["kind"]]["java"]
            out[e["owner"]].append(f'.event({e["tick"]}, {const}, "{e["data"]}")')
        elif e["kind"] == "camera":
            v = e["data"].split(",")
            out["camera"].append(f'new CameraTrack.Key({e["tick"]}, {", ".join(x.strip() for x in v)})')
    return out


# ------------------------------------------------------------------------------------------------ separacao

def split(manifest, export, scene_animation_name=None, participant_bones=None):
    """export = JSON do "Export Animations" (plugin GeckoLib) ou de compile_bbmodel. Keyframes dos bones ficam como
    vieram (interpolacao, easing, Molang): nenhuma conversao de eixo aqui (o export ja esta no referencial do GeckoLib).
    participant_bones = {papel: set(bones do rig original)} para conferir a correspondencia.
    -> (clips {papel: clip}, eventos, relatorio [(nivel, mensagem)])."""
    rep = []
    anims = export.get("animations", {})
    name = scene_animation_name or manifest["animation"]
    if name not in anims:
        if len(anims) == 1:
            rep.append(("WARN", f"scene animation {name} not found; using the only one: {next(iter(anims))}"))
            name = next(iter(anims))
        else:
            raise SceneError(f"scene animation {name} not in export ({sorted(anims)})")
    for other in sorted(set(anims) - {name}):
        rep.append(("WARN", f"animation {other} in the export is not the scene animation: ignored (split it with --animation)"))
    anim = anims[name]
    length = float(anim.get("animation_length", 0))
    ticks, exact = ticks_of(length)
    if not exact:
        rep.append(("ERROR", f"length {length}s is not a whole number of ticks"))
    if ticks != manifest["duration_ticks"]:
        rep.append(("ERROR", f"length {ticks} ticks != scene duration {manifest['duration_ticks']} ticks"))
    else:
        rep.append(("OK", f"duration {ticks} ticks ({length:g} s) for both participants"))
    if anim.get("loop"):
        rep.append(("WARN", "scene animation loops: finisher clips play once (loop dropped)"))

    clips = {r: {"animation_length": num(length), "bones": {}} for r in ROLES}
    stage = manifest["stage"]
    legacy_stage = set(stage.get("legacy_stage_groups", []))
    for bone, data in sorted(anim.get("bones", {}).items()):
        if bone in STAGE_GROUPS or bone in legacy_stage:
            if bone == STAGE_VICTIM_SCALE and set(data) == {"scale"} and _constant(data["scale"], stage["victim_scale"]):
                rep.append(("OK", f"stage calibration {bone} (scale {stage['victim_scale']}) kept out of the clips"))
            else:
                rep.append(("ERROR", f"stage group {bone} is animated: that would be real entity movement, which the"
                                     " session does not do (move the rig's root bone with offsets that return to zero)"))
            continue
        owner = manifest["bones"].get(bone)
        if owner is None:
            rep.append(("ERROR", f"bone {bone} in the export is not in the scene map (renamed or added in Blockbench?)"))
            continue
        role, original = owner["role"], owner["bone"]
        if participant_bones and original not in participant_bones[role]:
            rep.append(("ERROR", f"{role}: bone {original} does not exist in the runtime rig"))
            continue
        clips[role]["bones"][original] = copy.deepcopy(data)
    for r in ROLES:
        n = len(clips[r]["bones"])
        rep.append(("OK" if n else "WARN", f"{r}: {n} animated bone(s)" + ("" if n else " (holds the rest pose)")))
        _check_roots(r, clips[r], manifest, rep)

    events, problems = parse_timeline(anim.get("timeline"), manifest, length)
    rep += problems
    for ch in ("sound_effects", "particle_effects"):
        for t in anim.get(ch, {}) or {}:
            rep.append(("ERROR", f"{ch} keyframe @{t}s has no owner: write it on the timeline ('@attacker sound <id>'"
                                 " or a calamidade: instruction)"))
    for e in events:
        if e["kind"] == "instruction":
            tl = clips[e["owner"]].setdefault("timeline", {})
            prev = tl.get(time_key(e["time"]))
            tl[time_key(e["time"])] = (prev or "") + e["data"] + ";"
    if not any(e["kind"] == "execute" for e in events):
        rep.append(("WARN", f"no '@scene execute' marker: the real execution still happens at tick "
                            f"{manifest['duration_ticks']} (scene end)"))
    rep.append(("OK", f"{len(events)} timeline event(s) with owner"))
    return clips, events, rep, name


def _constant(channel, value):
    entries = channel.values() if isinstance(channel, dict) else [channel]
    for v in entries:
        vec = v.get("vector", v) if isinstance(v, dict) else v
        if isinstance(vec, (int, float)):
            vec = [vec] * 3
        if not all(isinstance(x, (int, float)) and abs(float(x) - value) < 1e-3 for x in vec):
            return False
    return True


def _check_roots(role, clip, manifest, rep):
    """Movimento visual x movimento real: a sessao segura a entidade no lugar; deslocamento do root que nao volta a
    zero no fim "teleporta" o modelo de volta quando o clip acaba."""
    for root in manifest["participants"][role]["root_bones"]:
        pos = clip["bones"].get(root, {}).get("position")
        if not isinstance(pos, dict) or not pos:
            continue
        last = pos[max(pos, key=float)]
        vec = last.get("vector", last) if isinstance(last, dict) else last
        if any(isinstance(x, (int, float)) and abs(x) > 0.05 for x in vec):
            rep.append(("WARN", f"{role}: root bone {root} ends displaced {vec} px: visual only, the entity does not"
                                " move (return to zero at the end)"))


def clip_name(manifest, role, scene_anim):
    """Nome do clip exportado: <animation_prefix do participante><sufixo da animacao da cena>_<papel>."""
    suffix = scene_anim[len("scene."):] if scene_anim.startswith("scene.") else scene_anim
    suffix = suffix.replace(".", "_").replace(" ", "_").lower()
    return manifest["participants"][role]["export"]["animation_prefix"] + suffix + "_" + role


def participant_file(role, manifest, clip, scene_anim):
    return {"format_version": "1.8.0", "animations": {clip_name(manifest, role, scene_anim): clip}}


def metadata(manifest, events, rep, scene_anim):
    return {
        "format": FORMAT + "_export", "format_version": FORMAT_VERSION, "scene": manifest["name"],
        "scene_animation": scene_anim,
        "duration_ticks": manifest["duration_ticks"], "duration_seconds": num(manifest["duration_ticks"] / TICKS_PER_SECOND),
        "stage": manifest["stage"],
        "participants": {r: {"kind": p["kind"], "entity": p["entity"], "rig": p["rig"], "file": p["export"]["file"],
                             "animation": clip_name(manifest, r, scene_anim), "ticks": manifest["duration_ticks"]}
                         for r, p in manifest["participants"].items()},
        "events": events, "java": java_snippets(events, manifest),
        "errors": sum(1 for lvl, _ in rep if lvl == "ERROR"), "warnings": sum(1 for lvl, _ in rep if lvl == "WARN"),
    }


def report_text(rep):
    return "".join(f"{lvl:5} {msg}\n" for lvl, msg in rep)


def events_text(events, manifest):
    lines = [f"Scene {manifest['name']}: {manifest['duration_ticks']} ticks", "tick  owner     event         data -> runtime"]
    for e in sorted(events, key=lambda e: (e["tick"], e["owner"])):
        lines.append(f"{e['tick']:>4}  @{e['owner']:<8} {e['kind']:<13} {e['data']} -> {e['runtime']}")
    js = java_snippets(events, manifest)
    for k in ("attacker", "victim", "camera"):
        if js[k]:
            lines.append(f"\n{k} (Java):")
            lines += ["    " + s for s in js[k]]
    return "\n".join(lines) + "\n"


def rig_bones(bbmodel):
    return {g["name"] for g in bbmodel.get("groups", [])}


def geo_bones(geo):
    out = set()
    for g in geo.get("minecraft:geometry", []):
        out |= {b["name"] for b in g.get("bones", [])}
    return out


def manifest_from_prefixes(model, prefixes, duration_ticks, animation, kinds=("player", "player"), stage_names=()):
    """Manifesto para um projeto conjunto antigo (ex.: respectcraft_combo_scene.bbmodel: executor sem prefixo,
    alvo com "Alvo_", grupo de palco "Alvo_Palco"). prefixes = {papel: prefixo}; o prefixo mais longo vence."""
    order = sorted(prefixes.items(), key=lambda kv: -len(kv[1]))
    bones = {}
    for g in model.get("groups", []):
        n = g["name"]
        if n in stage_names:
            continue
        role, prefix = next(((r, p) for r, p in order if n.startswith(p)), (None, None))
        if role is None:
            raise SceneError(f"group {n} matches no participant prefix")
        bones[n] = {"role": role, "bone": n[len(prefix):]}
    parts = {}
    for (role, prefix), kind in zip(prefixes.items(), kinds):
        roots = [b["bone"] for n, b in bones.items() if b["role"] == role and b["bone"] in ("Root", "root")]
        parts[role] = {"kind": kind, "prefix": prefix, "entity": None, "rig": None, "root_bones": roots,
                       "export": {"file": "", "animation_prefix": f"legacy.{role}."}}
    return {"format": FORMAT + "_manifest", "format_version": FORMAT_VERSION, "name": model.get("name", "legacy"),
            "duration_ticks": duration_ticks, "animation": animation,
            "stage": {"victim_scale": 1.0, "groups": {}, "legacy_stage_groups": list(stage_names)},
            "participants": parts, "bones": bones, "contacts": []}
