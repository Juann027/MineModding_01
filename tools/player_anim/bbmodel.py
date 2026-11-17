"""Grava os clips em arquivos .bbmodel (formato 5.0 do Blockbench, plugin GeckoLib), a partir do rig existente.

- tupinicraft_player_combat.bbmodel: copia do rig do jogador com todos os clips da Etapa 4 (editavel e
  exportavel com "Export Animations" do plugin GeckoLib; os nomes dos clips ja sao os usados pelo jogo).
- tupinicraft_combo_scene.bbmodel: dois rigs no palco (executor na origem, alvo a 1 bloco olhando para ele) e
  uma animacao "combo_scene" com a coreografia inteira. Serve so para editar o tempo entre os dois; NAO exporte
  o geo desse arquivo (bones duplicados com prefixo Alvo_). Depois de mexer, copie os keyframes de cada rig para
  o clip correspondente no arquivo de clips (ou ajuste clips.py e rode export.py).
"""
import copy
import hashlib
import json

from rig import REPO

SRC = REPO / "model_sources/player/tupinicraft_animated_player.bbmodel"
OUT_CLIPS = REPO / "model_sources/player/tupinicraft_player_combat.bbmodel"
OUT_SCENE = REPO / "model_sources/player/tupinicraft_combo_scene.bbmodel"
PREFIX = "animation.tupinicraft.player."


def _uuid(*parts):
    h = hashlib.md5("|".join(parts).encode()).hexdigest()
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


def _groups_by_name(model):
    return {g["name"]: g["uuid"] for g in model["groups"]}


def to_blockbench(channel, vector):
    """Valor do .animation.json (convencao Bedrock/GeckoLib) -> valor guardado no .bbmodel.

    O Blockbench guarda os keyframes no proprio referencial e, ao exportar (Keyframe.compileBedrockKeyframe),
    inverte X e Y da rotacao e X da posicao. A conversao e a mesma nos dois sentidos.
    """
    x, y, z = vector
    if channel == "rotation":
        return [-x, -y, z]
    if channel == "position":
        return [-x, y, z]
    return [x, y, z]


from_blockbench = to_blockbench


def _num(v):
    v = float(v) + 0.0  # sem "-0.0"
    return str(int(v)) if v.is_integer() else repr(round(v, 6))


def _keyframes(name, bone_json, time_offset=0.0):
    out = []
    for channel in ("rotation", "position"):
        for t, entry in bone_json.get(channel, {}).items():
            x, y, z = (_num(v) for v in to_blockbench(channel, entry["vector"]))
            kf = {"channel": channel, "data_points": [{"x": x, "y": y, "z": z}],
                  "uuid": _uuid(name, channel, t), "time": round(float(t) + time_offset, 4), "color": -1,
                  "interpolation": "linear"}
            if "easing" in entry:
                kf["easing"] = entry["easing"]
            out.append(kf)
    return out


def _animation(name, length, loop, animators):
    return {"uuid": _uuid("anim", name), "name": name, "loop": "loop" if loop else "once", "override": False,
            "length": length, "snapping": 20, "selected": False, "saved": False, "scope": 0, "anim_time_update": "",
            "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}


def _animators(model_groups, clip_json, tag, name_map=None):
    animators = {}
    for bone, chans in clip_json["bones"].items():
        target = (name_map or {}).get(bone, bone)
        uuid = model_groups[target]
        animators[uuid] = {"name": target, "type": "bone", "rotation_global": False,
                           "quaternion_interpolation": False, "keyframes": _keyframes(tag + target, chans)}
    return animators


def write_clips(clips):
    model = json.loads(SRC.read_text(encoding="utf-8"))
    groups = _groups_by_name(model)
    model["name"] = "tupinicraft_player_combat"
    model["animations"] = []
    for name, clip in clips.items():
        js = clip.to_json()
        model["animations"].append(_animation(PREFIX + name, js["animation_length"], clip.loop,
                                              _animators(groups, js, name)))
    model["geckolib_filepath_cache"] = {"animation": "tupinicraft_player_combat.animation.json"}
    OUT_CLIPS.write_text(json.dumps(model, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def write_scene(clips, distance_px):
    model = json.loads(SRC.read_text(encoding="utf-8"))
    model["name"] = "tupinicraft_combo_scene"
    groups = copy.deepcopy(model["groups"])
    elements = copy.deepcopy(model["elements"])
    remap = {}
    for g in groups:
        remap[g["uuid"]] = _uuid("alvo", g["uuid"])
    for e in elements:
        remap[e["uuid"]] = _uuid("alvo", e["uuid"])

    def clone(node):
        if isinstance(node, str):
            return remap[node]
        n = copy.deepcopy(node)
        n["uuid"] = remap[node["uuid"]]
        n["children"] = [clone(c) for c in node.get("children", [])]
        return n

    new_groups = []
    for g in groups:
        g2 = copy.deepcopy(g)
        g2["uuid"] = remap[g["uuid"]]
        g2["name"] = "Alvo_" + g["name"]
        new_groups.append(g2)
    new_elements = []
    for e in elements:
        e2 = copy.deepcopy(e)
        e2["uuid"] = remap[e["uuid"]]
        e2["name"] = "Alvo_" + e["name"]
        new_elements.append(e2)
    stage_uuid = _uuid("palco_alvo")
    stage = {"name": "Alvo_Palco", "uuid": stage_uuid, "export": False, "locked": False, "scope": 0,
             "selected": False, "visibility": True, "origin": [0, 0, -distance_px / 2], "rotation": [0, 180, 0],
             "color": 1, "children": [], "reset": False, "shade": True, "mirror_uv": False, "autouv": 0,
             "isOpen": True, "primary_selected": False}
    # o alvo inteiro e girado 180 graus em volta do ponto medio por um grupo-palco: fica a distance_px a frente,
    # olhando para o executor (no jogo isso e o ancora da sessao, nao um keyframe)
    stage_outliner = {"uuid": stage_uuid, "isOpen": True, "children": [clone(n) for n in model["outliner"]]}
    model["groups"] = model["groups"] + [stage] + new_groups
    model["elements"] = model["elements"] + new_elements
    model["outliner"] = model["outliner"] + [stage_outliner]

    by_name = _groups_by_name(model)
    att = clips["finisher.combo_attacker"].to_json()
    vic = clips["finisher.combo_victim"].to_json()
    animators = _animators(by_name, att, "A:")
    animators.update(_animators(by_name, vic, "V:", {b: "Alvo_" + b for b in vic["bones"]}))
    model["animations"] = [_animation("combo_scene", att["animation_length"], False, animators)]
    OUT_SCENE.write_text(json.dumps(model, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def write_all(clips):
    write_clips(clips)
    write_scene(clips, 16 * 16 / 15)  # 1 bloco no mundo = 17,07 px do rig (o PlayerRenderer escala 15/16)
