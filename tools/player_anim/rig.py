"""Cinematica direta do rig do jogador com as convencoes reais do GeckoLib 4.9.3.

Le o geo do rig (Bedrock JSON) e reproduz o que o GeckoLib faz ao assar e desenhar:
  * pivo e cubos espelhados em X (BakedModelFactory: pivot (-x, y, z));
  * rotacao do keyframe em graus -> radianos (-x, -y, +z) (BakedAnimationsAdapter);
  * posicao do keyframe: translate(-x, y, z) / 16 (RenderUtil.translateMatrixToBone);
  * ordem Z * Y * X em torno do pivo (RenderUtil.rotateMatrixAroundBone).
No espaco "assado" o personagem olha para -Z, a direita dele e +X e Y e para cima (pes em y = 0).
Tudo em pixels (1 bloco = 16 px; o PlayerRenderer ainda escala o rig por 15/16).

Tambem amostra clips no formato .animation.json com as curvas do GeckoLib (linear, sine, quad, cubic,
catmullrom), para checar contato com o chao e gerar previas sem abrir o jogo.
"""
import json
import math
import pathlib

import numpy as np

REPO = pathlib.Path(__file__).resolve().parents[2]
GEO = REPO / "src/main/resources/assets/respectcraft/geo/player/respectcraft_animated_player.geo.json"
RENDER_SCALE = 15.0 / 16.0

# Bones que nunca aparecem no rig em jogo (Body Layers desenham busto e cabelo por conta propria).
HIDDEN = {"FemaleBust", "FemaleBustLayer", "HairPlaceholder"}


def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], float)


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], float)


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], float)


def _t(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


class Rig:
    def __init__(self, slim=False):
        g = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
        self.bones = {}
        self.order = []
        for b in g["bones"]:
            px, py, pz = b.get("pivot", [0, 0, 0])
            cubes = []
            for c in b.get("cubes", []):
                (ox, oy, oz), (sx, sy, sz) = c["origin"], c["size"]
                cubes.append(((-(ox + sx), oy, oz), (sx, sy, sz)))
            self.bones[b["name"]] = {"parent": b.get("parent"), "pivot": (-px, py, pz), "cubes": cubes}
            self.order.append(b["name"])
        hide = set(HIDDEN)
        hide |= {"RightArmClassic", "LeftArmClassic", "RightArmLayerClassic", "LeftArmLayerClassic"} if slim else \
            {"RightArmSlim", "LeftArmSlim", "RightArmLayerSlim", "LeftArmLayerSlim"}
        # Camadas externas (casaco, mangas, chapeu) so engrossam a silhueta; para a previa bastam as internas.
        hide |= {n for n in self.bones if n.endswith("Layer") or "Layer" in n}
        self.hidden = hide

    def local(self, name, rot=(0, 0, 0), pos=(0, 0, 0)):
        px, py, pz = self.bones[name]["pivot"]
        rx, ry, rz = (math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2]))
        return _t(-pos[0], pos[1], pos[2]) @ _t(px, py, pz) @ _rz(rz) @ _ry(ry) @ _rx(rx) @ _t(-px, -py, -pz)

    def world(self, pose):
        """pose: {bone: {"rotation": (x,y,z), "position": (x,y,z)}} em valores do arquivo. -> {bone: 4x4}."""
        out = {}
        for name in self.order:
            p = pose.get(name, {})
            m = self.local(name, p.get("rotation", (0, 0, 0)), p.get("position", (0, 0, 0)))
            parent = self.bones[name]["parent"]
            out[name] = (out[parent] @ m) if parent else m
        return out

    def boxes(self, pose, hidden_extra=()):
        """Lista (bone, 8 cantos 3D) dos cubos visiveis."""
        mats = self.world(pose)
        res = []
        for name in self.order:
            if name in self.hidden or name in hidden_extra:
                continue
            for (ox, oy, oz), (sx, sy, sz) in self.bones[name]["cubes"]:
                corners = np.array([[ox + dx * sx, oy + dy * sy, oz + dz * sz, 1]
                                    for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)], float)
                res.append((name, (mats[name] @ corners.T).T[:, :3]))
        return res

    def point(self, pose, bone, local_point):
        """Ponto do espaco de repouso (assado, px) preso a um bone, na pose."""
        m = self.world(pose)[bone]
        return (m @ np.array([*local_point, 1.0]))[:3]


# ------------------------------------------------------------------ amostragem de clips (curvas do GeckoLib)

def _ease(name, t):
    n = (name or "linear").lower()
    if n in ("linear", "none"):
        return t
    if n == "step":
        return 0.0 if t < 1 else 1.0
    kind = n.replace("easeinout", "io:").replace("easein", "i:").replace("easeout", "o:")
    mode, fn = kind.split(":")
    f = {"sine": lambda x: 1 - math.cos(x * math.pi / 2), "quad": lambda x: x * x, "cubic": lambda x: x ** 3,
         "quart": lambda x: x ** 4, "quint": lambda x: x ** 5,
         "circ": lambda x: 1 - math.sqrt(max(0.0, 1 - x * x))}[fn]
    if mode == "i":
        return f(t)
    if mode == "o":
        return 1 - f(1 - t)
    return f(2 * t) / 2 if t < 0.5 else 1 - f(2 - 2 * t) / 2


def _catmull(p0, p1, p2, p3, t):
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3)


def sample_channel(keys, time):
    """keys: dict "tempo" -> {"vector": [...], "easing": ...} (ou lista). Igual ao GeckoLib: a curva de um trecho
    e a do keyframe de chegada; catmullrom usa o ponto anterior ao trecho e o seguinte."""
    items = sorted(((float(k), v) for k, v in keys.items()), key=lambda kv: kv[0])
    vec = [np.array(v["vector"] if isinstance(v, dict) else v, float) for _, v in items]
    times = [k for k, _ in items]
    if time <= times[0]:
        return vec[0]
    for i in range(1, len(times)):
        if time <= times[i]:
            span = max(1e-9, times[i] - times[i - 1])
            t = (time - times[i - 1]) / span
            ease = items[i][1].get("easing") if isinstance(items[i][1], dict) else None
            if (ease or "").lower() == "catmullrom":
                p0 = vec[i - 2] if i >= 2 else vec[i - 1]
                p3 = vec[i + 1] if i + 1 < len(vec) else vec[i]
                return _catmull(p0, vec[i - 1], vec[i], p3, t)
            return vec[i - 1] + (vec[i] - vec[i - 1]) * _ease(ease, t)
    return vec[-1]


def sample_clip(clip, time):
    pose = {}
    for bone, chans in clip.get("bones", {}).items():
        p = {}
        for ch in ("rotation", "position"):
            if ch in chans:
                p[ch] = tuple(sample_channel(chans[ch], time))
        pose[bone] = p
    return pose


def min_y(rig, pose, only=None):
    ys = [c[:, 1].min() for name, c in rig.boxes(pose) if only is None or name in only]
    return min(ys) if ys else 0.0
