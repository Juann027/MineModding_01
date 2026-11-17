"""Previa por software (sem GPU) de um ou dois rigs: cubos com sombreamento plano, chao quadriculado.

Serve para conferir pose, contato com o chao e leitura da acao antes de abrir o jogo. Nao substitui o
Blockbench nem o teste no cliente: a skin e trocada por cores por parte (cabeca, tronco, bracos, pernas).
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from rig import RENDER_SCALE

PALETTES = {
    "executor": {"Head": (214, 170, 130), "Body": (40, 110, 190), "Arm": (214, 170, 130), "Leg": (45, 50, 90),
                 "other": (160, 160, 160)},
    "alvo": {"Head": (190, 140, 105), "Body": (200, 70, 60), "Arm": (190, 140, 105), "Leg": (70, 60, 50),
             "other": (160, 160, 160)},
}
FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]


def _color(palette, bone):
    for key in ("Head", "Body", "Arm", "Leg"):
        if key in bone:
            return palette[key]
    return palette["other"]


def placement(x=0.0, z=0.0, yaw_deg=0.0):
    """Matriz rig -> mundo da previa (px): escala 15/16 do PlayerRenderer, giro em Y e deslocamento."""
    a = math.radians(yaw_deg)
    c, s = math.cos(a), math.sin(a)
    m = np.array([[c, 0, s, x], [0, 1, 0, 0], [-s, 0, c, z], [0, 0, 0, 1]], float)
    return m @ np.diag([RENDER_SCALE, RENDER_SCALE, RENDER_SCALE, 1.0])


class Camera:
    def __init__(self, yaw=35.0, pitch=12.0, dist=90.0, target=(0, 16, 0), size=(220, 220), fov=40.0):
        self.yaw, self.pitch, self.dist = math.radians(yaw), math.radians(pitch), dist
        self.target = np.array(target, float)
        self.size = size
        self.f = (size[1] / 2) / math.tan(math.radians(fov) / 2)
        # yaw 0 = camera na frente do executor (em -Z), olhando para +Z
        d = np.array([math.sin(self.yaw) * math.cos(self.pitch), math.sin(self.pitch),
                      -math.cos(self.yaw) * math.cos(self.pitch)])
        self.eye = self.target + d * dist
        fwd = self.target - self.eye
        fwd /= np.linalg.norm(fwd)
        right = np.cross(fwd, [0, 1, 0])
        right /= np.linalg.norm(right)
        up = np.cross(right, fwd)
        self.basis = np.array([right, up, fwd])

    def project(self, p):
        v = self.basis @ (np.asarray(p, float) - self.eye)
        z = max(v[2], 1e-3)
        return (self.size[0] / 2 + v[0] * self.f / z, self.size[1] / 2 - v[1] * self.f / z, v[2])


def render(actors, cam, label=None, ground=True):
    """actors: lista de (rig, pose, matriz de colocacao, paleta)."""
    img = Image.new("RGB", cam.size, (236, 236, 232))
    draw = ImageDraw.Draw(img)
    if ground:
        for i in range(-40, 41, 8):
            for a, b in (((i, 0, -48), (i, 0, 40)), ((-40, 0, i - 4), (40, 0, i - 4))):
                pa, pb = cam.project(a), cam.project(b)
                draw.line([pa[:2], pb[:2]], fill=(205, 205, 198))
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    polys = []
    for rig, pose, place, palette in actors:
        for bone, corners in rig.boxes(pose):
            w = (place @ np.c_[corners, np.ones(8)].T).T[:, :3]
            base = np.array(_color(palette, bone), float)
            for face in FACES:
                pts = w[list(face)]
                n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
                norm = np.linalg.norm(n)
                if norm < 1e-9:
                    continue
                n /= norm
                center = pts.mean(axis=0)
                if np.dot(n, cam.eye - center) <= 0:
                    continue
                shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, light)))
                proj = [cam.project(p) for p in pts]
                depth = np.linalg.norm(center - cam.eye)
                polys.append((depth, [(p[0], p[1]) for p in proj], tuple(int(c * shade) for c in base)))
    for _, pts, col in sorted(polys, key=lambda x: -x[0]):
        draw.polygon(pts, fill=col, outline=tuple(int(c * 0.6) for c in col))
    if label:
        draw.text((4, 3), label, fill=(20, 20, 20), font=ImageFont.load_default())
    return img


def sheet(images, cols):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * w + (cols - 1) * 2, rows * h + (rows - 1) * 2), (120, 120, 120))
    for i, im in enumerate(images):
        out.paste(im, ((i % cols) * (w + 2), (i // cols) * (h + 2)))
    return out
