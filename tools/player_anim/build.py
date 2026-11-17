"""Construtor de clips: poses-chave, defasagem por bone (overlapping action), apoio no chao e exportacao.

Valores no padrao do arquivo .animation.json (Blockbench / Bedrock), em graus e pixels:
  Body/Head/Root  x+ inclina para a frente, y+ gira o ombro direito para tras (olha para a direita),
                  z+ inclina para a esquerda do personagem.
  RightArm        x- levanta para a frente, z+ abre para fora, y- (braco a frente) fecha para dentro.
  LeftArm         x- levanta para a frente, z- abre para fora, y+ fecha para dentro.
  Pernas          x- para a frente (chute), x+ para tras; RightLeg z+ / LeftLeg z- abrem para fora.
  Root posicao    x+ esquerda do personagem, y+ para cima, z+ para tras (pes na origem).
"""
import copy

from rig import Rig, min_y

BONES = ("Root", "Body", "Head", "RightArm", "LeftArm", "RightLeg", "LeftLeg")
LEGS = ("RightLeg", "LeftLeg")
ARMS = ("RightArmClassic", "LeftArmClassic")
_RIG = Rig()


def P(base=None, **bones):
    """Pose = {bone: {"r": (x,y,z), "p": (x,y,z)}}. Aceita tupla (so rotacao) ou dict; herda de base."""
    pose = copy.deepcopy(base) if base else {}
    for name, val in bones.items():
        entry = pose.setdefault(name, {})
        if isinstance(val, dict):
            entry.update({k: tuple(map(float, v)) for k, v in val.items()})
        else:
            entry["r"] = tuple(map(float, val))
    return pose


def to_rig_pose(pose):
    return {b: {"rotation": v.get("r", (0, 0, 0)), "position": v.get("p", (0, 0, 0))} for b, v in pose.items()}


def grounded(pose, contacts=LEGS, clearance=0.0):
    """Ajusta a altura do Root para os cubos de contato tocarem o chao (y = clearance)."""
    pose = copy.deepcopy(pose)
    root = pose.setdefault("Root", {})
    x, y, z = root.get("p", (0.0, 0.0, 0.0))
    root["p"] = (x, 0.0, z)
    low = min_y(_RIG, to_rig_pose(pose), only=set(contacts))
    root["p"] = (x, round(clearance - low, 2), z)
    return pose


class Clip:
    def __init__(self, name, length, loop=False):
        self.name, self.length, self.loop = name, length, loop
        self.keys = {}  # (bone, canal) -> {tick: (vec, ease)}

    def key(self, tick, pose, ease="easeInOutSine", lag=None, only=None):
        """Grava a pose no tick. Bones em BONES ausentes da pose voltam a zero. lag: {bone: ticks a mais}."""
        lag = lag or {}
        for bone in (only or BONES):
            entry = pose.get(bone, {})
            t = min(self.length, max(0, tick + lag.get(bone, 0)))
            for ch, default in (("r", (0, 0, 0)), ("p", None)):
                vec = entry.get(ch, default)
                if vec is None:
                    if (bone, "p") not in self.keys and bone != "Root":
                        continue
                    vec = (0.0, 0.0, 0.0)
                self.keys.setdefault((bone, ch), {})[t] = (tuple(vec), ease)
        return self

    def segment(self, offset, frames):
        """frames: lista (tick, pose, ease, lag) relativos a offset."""
        for f in frames:
            tick, pose = f[0], f[1]
            ease = f[2] if len(f) > 2 else "easeInOutSine"
            lag = f[3] if len(f) > 3 else None
            self.key(offset + tick, pose, ease, lag)
        return self

    def to_json(self):
        bones = {}
        for (bone, ch), frames in sorted(self.keys.items(), key=lambda kv: (BONES.index(kv[0][0]) if kv[0][0] in BONES else 99, kv[0][1])):
            if all(abs(c) < 1e-6 for vec, _ in frames.values() for c in vec):
                continue
            out = {}
            for i, tick in enumerate(sorted(frames)):
                vec, ease = frames[tick]
                entry = {"vector": [round(c, 2) + 0.0 for c in vec]}
                if i:
                    entry["easing"] = ease
                out[_time(tick)] = entry
            bones.setdefault(bone, {})["rotation" if ch == "r" else "position"] = out
        clip = {"animation_length": self.length / 20}
        if self.loop:
            clip["loop"] = True
        clip["bones"] = bones
        return clip


def _time(tick):
    s = f"{tick / 20:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s
